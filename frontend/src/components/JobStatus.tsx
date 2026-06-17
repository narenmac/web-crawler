import { Job, JobStatus as JobState } from '../types';

interface JobStatusProps {
  job: Job;
}

const statusStyles: Record<JobState, string> = {
  [JobState.Pending]: 'bg-amber-100 text-amber-700',
  [JobState.Running]: 'bg-blue-100 text-blue-700',
  [JobState.Completed]: 'bg-emerald-100 text-emerald-700',
  [JobState.Stopped]: 'bg-rose-100 text-rose-700',
  [JobState.Failed]: 'bg-red-100 text-red-700'
};

export default function JobStatus({ job }: JobStatusProps) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-semibold text-slate-900">Active Crawl Job</h2>
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${statusStyles[job.status]}`}>
              {job.status}
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500">Tracking progress for seed file {job.seedFileName ?? 'N/A'}.</p>
        </div>
        <div className="text-right">
          <p className="text-xs uppercase tracking-wide text-slate-400">BFS level</p>
          <p className="text-2xl font-bold text-slate-900">{job.bfsLevel}</p>
        </div>
      </div>

      <div className="mt-6">
        <div className="mb-2 flex items-center justify-between text-sm text-slate-600">
          <span>Crawl completion</span>
          <span>{job.progress}%</span>
        </div>
        <div className="h-3 overflow-hidden rounded-full bg-slate-200">
          <div
            className="h-full rounded-full bg-blue-600 transition-all duration-500"
            style={{ width: `${job.progress}%` }}
          />
        </div>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">URLs crawled</p>
          <p className="mt-1 text-2xl font-semibold text-slate-900">{job.crawledUrls}</p>
          <p className="text-sm text-slate-500">of {job.totalUrls}</p>
        </div>
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">Fetcher pods</p>
          <p className="mt-1 text-2xl font-semibold text-slate-900">{job.fetcherPods}</p>
        </div>
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">Parser pods</p>
          <p className="mt-1 text-2xl font-semibold text-slate-900">{job.parserPods}</p>
        </div>
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-400">Started</p>
          <p className="mt-1 text-sm font-semibold text-slate-900">
            {new Date(job.createdAt).toLocaleString()}
          </p>
        </div>
      </div>
    </div>
  );
}
