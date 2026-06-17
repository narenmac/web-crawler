export enum JobStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  STOPPING = 'STOPPING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

export enum UrlStatus {
  QUEUED = 'QUEUED',
  CRAWLING = 'CRAWLING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  SKIPPED_DUPLICATE = 'SKIPPED_DUPLICATE'
}

export type ScheduleIntervalType = 'HOURLY' | 'DAILY' | 'WEEKLY' | 'CRON';

export interface Job {
  id: string;
  status: JobStatus | string;
  totalUrls: number;
  crawledUrls: number;
  currentBfsLevel: number;
  maxUrls: number;
  createdAt: string;
  completedAt?: string | null;
  source: string;
}

export interface Schedule {
  id: string;
  seedFileName: string;
  intervalType: ScheduleIntervalType | string;
  cronExpression?: string | null;
  nextRunAt?: string | null;
  lastRunStatus?: string | null;
  enabled: boolean;
}

export interface SchedulePayload {
  seedFileId: string;
  intervalType: ScheduleIntervalType;
  cronExpression?: string;
  enabled: boolean;
}

export interface CrawlResult {
  urlHash: string;
  url: string;
  status: UrlStatus | string;
  bfsLevel: number;
  contentHash?: string | null;
  blobPath?: string | null;
  parentUrl?: string | null;
  crawledAt?: string | null;
}

export interface PaginatedResponse<T> {
  jobId: string;
  page: number;
  size: number;
  statusFilter?: string | null;
  searchQuery?: string | null;
  total: number;
  items: T[];
}

export interface AuthUser {
  name: string;
  username: string;
  initials: string;
}
