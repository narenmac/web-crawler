export enum JobStatus {
  Pending = 'Pending',
  Running = 'Running',
  Completed = 'Completed',
  Stopped = 'Stopped',
  Failed = 'Failed'
}

export interface Job {
  id: string;
  name: string;
  status: JobStatus;
  createdAt: string;
  completedAt?: string;
  progress: number;
  totalUrls: number;
  crawledUrls: number;
  bfsLevel: number;
  fetcherPods: number;
  parserPods: number;
  seedFileName?: string;
}

export interface Schedule {
  id: string;
  name: string;
  interval: 'Hourly' | 'Daily' | 'Weekly';
  status: 'active' | 'paused';
  nextRun: string;
  lastRun?: string;
}

export interface UrlMetadata {
  title?: string;
  contentType?: string;
  contentLength?: number;
  discoveredFrom?: string;
}

export interface CrawlResult {
  id: string;
  jobId: string;
  url: string;
  statusCode: number;
  level: number;
  fetchedAt: string;
  rawHtml: string;
  metadata: UrlMetadata;
}
