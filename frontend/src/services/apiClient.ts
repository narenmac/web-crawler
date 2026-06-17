import axios, { AxiosHeaders } from 'axios';
import { msalInstance, loginRequest } from '../auth/authConfig';

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL ?? 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json'
  }
});

apiClient.interceptors.request.use(
  async (config) => {
    const account = msalInstance.getActiveAccount() ?? msalInstance.getAllAccounts()[0];

    if (!account) {
      return config;
    }

    const tokenResponse = await msalInstance.acquireTokenSilent({
      ...loginRequest,
      account
    });

    const headers = config.headers instanceof AxiosHeaders ? config.headers : new AxiosHeaders(config.headers);
    headers.set('Authorization', `Bearer ${tokenResponse.accessToken}`);
    config.headers = headers;

    return config;
  },
  async (error) => Promise.reject(error)
);

export default apiClient;
