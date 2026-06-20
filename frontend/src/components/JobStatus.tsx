import { useEffect, useMemo, useState } from 'react';
import LoadingSpinner from './LoadingSpinner';
import { Job, JobStatus as JobState } from '../types';

interface JobStatusProps {
  job: Job;
  onStop: (jobId: string) => Promise<void>;
  isStopping: boolean;
}

const statusMeta: Record<string, { badge: string; icon: string; progress: string }> = {
  [JobState.PENDING]: { badge: 'bg-amber-100 text-amber-800', icon: '⏳', progress: 'bg-amber-500' },
  [JobState.RUNNING]: { badge: 'bg-blue-100 text-blue-800', icon: '▶', progress: 'bg-blue-600' },
  [JobState.STOPPING]: { badge: 'bg-amber-100 text-amber-800', icon: '⏹', progress: 'bg-amber-500' },
  [JobState.COMPLETED]: { badge: 'bg-emerald-100 text-emerald-800', icon: '✔', progress: 'bg-emerald-500' },
  [JobState.FAILED]: { badge: 'bg-rose-100 text-rose-800', icon: '⚠', progress: 'bg-rose-500' },
  [JobState.CANCELLED]: { badge: 'bg-slate-200 text-slate-800', icon: '■', progress: 'bg-slate-400' }
};

const formatDuration = (milliseconds: number) => {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  return [hours, minutes, seconds].map((value) => value.toString().padStart(2, '0')).join(':');
};

export default function JobStatus({ job, onStop, isStopping }: JobStatusProps) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(intervalId);
  }, []);

  const normalizedStatus = String(job.status).toUpperCase();
  const status = statusMeta[normalizedStatus] ?? statusMeta[JobState.PENDING];
  const progress = useMemo(() => {
    if (!job.maxUrls) {
      return 0;
    }

    return Math.min(100, Math.round((job.crawledUrls / job.maxUrls) * 100));
  }, [job.crawledUrls, job.maxUrls]);

  const elapsed = formatDuration(
    (job.completedAt ? new Date(job.completedAt).getTime() : now) - new Date(job.createdAt).getTime()
  );
  const canStop = normalizedStatus === JobState.RUNNING || normalizedStatus === JobState.PENDING;

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-semibold text-slate-900">Active Crawl Job</h2>
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${status.badge}`}>
              {normalizedStatus === JobState.RUNNING ? (
                <span className="inline-flex items-center gap-2">
                  <LoadingSpinner size="sm" className="text-blue-700" />
                  {normalizedStatus}
                </span>
              ) : (
                `${status.icon} ${normalizedStatus}`
              )}
            </span>
          </div>
          <p className="mt-2 text-sm text-slate-500">Seed source: {job.source}</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-2xl bg-slate-50 px-4 py-3 text-right">
            <p className="text-xs uppercase tracking-wide text-slate-400">Elapsed</p>
            <p className="mt-1 text-lg font-semibold text-slate-900">{elapsed}</p>
          </div>
          <button
            type="button"
            onClick={() => {
              if (canStop && window.confirm('Stop the active crawl job?')) {
                void onStop(job.id);
              }
            }}
            disabled={!canStop || isStopping}
            className="rounded-lg border border-rose-300 px-4 py-3 text-sm font-semibold text-rose-700 transition enabled:hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
          >
            {isStopping ? 'Stopping...' : 'Stop Job'}
          </button>
        </div>
      </div>

      <div className="mt-6">
        <div className="mb-2 flex items-center justify-between text-sm text-slate-600">
          <span>Progress</span>
          <span>{progress}%</span>
        </div>
        <div className="h-3 overflow-hidden rounded-full bg-slate-200">
          <div className={`h-full rounded-full transition-all duration-500 ${status.progress}`} style={{ width: `${progress}%` }} />
        </div>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-2xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">Current BFS Level</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{job.currentBfsLevel}</p>
        </div>
        <div className="rounded-2xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">Total URLs Discovered</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{job.totalUrls}</p>
        </div>
        <div className="rounded-2xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">URLs Crawled</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{job.crawledUrls}</p>
        </div>
        <div className="rounded-2xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">Started</p>
          <p className="mt-2 text-sm font-semibold text-slate-900">{new Date(job.createdAt).toLocaleString()}</p>
        </div>
      </div>
    </div>
  );
}
