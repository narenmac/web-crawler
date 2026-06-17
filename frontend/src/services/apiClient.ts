import axios, { AxiosError, AxiosHeaders } from 'axios';
import { InteractionRequiredAuthError } from '@azure/msal-browser';
import { loginRequest, msalInstance } from '../auth/authConfig';
import { CrawlResult, Job, PaginatedResponse, Schedule, SchedulePayload } from '../types';

const configuredBaseUrl = process.env.REACT_APP_API_BASE_URL
  ? process.env.REACT_APP_API_BASE_URL
  : process.env.REACT_APP_API_URL
    ? `${process.env.REACT_APP_API_URL.replace(/\/$/, '')}/api`
    : 'http://localhost:8080/api';

const normalizeBaseUrl = (baseUrl: string) => baseUrl.replace(/\/$/, '');

const apiClient = axios.create({
  baseURL: normalizeBaseUrl(configuredBaseUrl),
  headers: {
    Accept: 'application/json'
  }
});

const getErrorMessage = (error: unknown, fallbackMessage: string) => {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data;

    if (typeof responseData === 'string' && responseData.trim()) {
      return responseData;
    }

    if (responseData && typeof responseData === 'object') {
      const message = (responseData as { message?: string; error?: string }).message
        ?? (responseData as { message?: string; error?: string }).error;

      if (message) {
        return message;
      }
    }

    return error.message || fallbackMessage;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallbackMessage;
};

apiClient.interceptors.request.use(async (config) => {
  const account = msalInstance.getActiveAccount() ?? msalInstance.getAllAccounts()[0];

  if (!account) {
    return config;
  }

  try {
    const tokenResponse = await msalInstance.acquireTokenSilent({
      ...loginRequest,
      account
    });

    const headers = config.headers instanceof AxiosHeaders ? config.headers : new AxiosHeaders(config.headers);
    headers.set('Authorization', `Bearer ${tokenResponse.accessToken}`);
    config.headers = headers;
    return config;
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      throw new Error('Authentication expired. Please sign in again.');
    }

    throw new Error(getErrorMessage(error, 'Unable to acquire access token.'));
  }
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => Promise.reject(new Error(getErrorMessage(error, 'API request failed.')))
);

export async function createJob(file: File) {
  const formData = new FormData();
  formData.append('seedFile', file);

  const response = await apiClient.post<Job>('/jobs', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });

  return response.data;
}

export async function getJobs() {
  const response = await apiClient.get<Job[]>('/jobs');
  return response.data;
}

export async function getJob(id: string) {
  const response = await apiClient.get<Job>(`/jobs/${id}`);
  return response.data;
}

export async function stopJob(id: string) {
  const response = await apiClient.post<Job>(`/jobs/${id}/stop`);
  return response.data;
}

export async function deleteJob(id: string) {
  await apiClient.delete(`/jobs/${id}`);
}

export async function getSchedules() {
  const response = await apiClient.get<Schedule[]>('/schedules');
  return response.data;
}

export async function createSchedule(data: SchedulePayload) {
  const response = await apiClient.post<Schedule>('/schedules', data);
  return response.data;
}

export async function updateSchedule(id: string, data: SchedulePayload) {
  const response = await apiClient.put<Schedule>(`/schedules/${id}`, data);
  return response.data;
}

export async function deleteSchedule(id: string) {
  await apiClient.delete(`/schedules/${id}`);
}

export async function triggerSchedule(id: string) {
  const response = await apiClient.post<Job>(`/schedules/${id}/trigger`);
  return response.data;
}

export async function getResults(jobId: string, page: number, size: number, status?: string, search?: string) {
  const response = await apiClient.get<PaginatedResponse<CrawlResult>>(`/results/${jobId}`, {
    params: {
      page,
      size,
      status: status || undefined,
      q: search || undefined
    }
  });

  return response.data;
}

export async function getContent(jobId: string, urlHash: string) {
  const response = await apiClient.get<string>(`/results/${jobId}/${urlHash}/content`, {
    headers: {
      Accept: 'text/html'
    },
    responseType: 'text'
  });

  return response.data;
}

export default apiClient;
