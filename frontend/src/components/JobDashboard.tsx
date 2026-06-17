import { useCallback, useEffect, useMemo, useState } from 'react';
import JobStatusPanel from './JobStatus';
import LoadingSpinner from './LoadingSpinner';
import SeedUrlUpload from './SeedUrlUpload';
import { useToast } from './ToastProvider';
import { createJob, deleteJob, getJob, getJobs, stopJob } from '../services/apiClient';
import { Job, JobStatus } from '../types';

const activeStatuses = [JobStatus.PENDING, JobStatus.RUNNING, JobStatus.STOPPING];
const sortableTime = (job: Job) => new Date(job.createdAt).getTime();

const getStatusBadge = (status: string) => {
  switch (status) {
    case JobStatus.RUNNING:
      return 'bg-blue-100 text-blue-800';
    case JobStatus.PENDING:
    case JobStatus.STOPPING:
      return 'bg-amber-100 text-amber-800';
    case JobStatus.COMPLETED:
      return 'bg-emerald-100 text-emerald-800';
    case JobStatus.FAILED:
      return 'bg-rose-100 text-rose-800';
    case JobStatus.CANCELLED:
      return 'bg-slate-200 text-slate-800';
    default:
      return 'bg-slate-100 text-slate-700';
  }
};

export default function JobDashboard() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isStarting, setIsStarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [deletingJobId, setDeletingJobId] = useState<string | null>(null);
  const { showToast } = useToast();

  const syncJob = useCallback((job: Job) => {
    setJobs((current) => {
      const updated = current.some((item) => item.id === job.id)
        ? current.map((item) => (item.id === job.id ? job : item))
        : [job, ...current];

      return updated.sort((left, right) => sortableTime(right) - sortableTime(left));
    });
  }, []);

  const loadJobs = useCallback(async () => {
    const response = await getJobs();
    setJobs(response.sort((left, right) => sortableTime(right) - sortableTime(left)));
  }, []);

  useEffect(() => {
    let mounted = true;

    void (async () => {
      try {
        await loadJobs();
      } catch (error) {
        if (mounted) {
          showToast(error instanceof Error ? error.message : 'Failed to load jobs.', 'error', 'Jobs unavailable');
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      mounted = false;
    };
  }, [loadJobs, showToast]);

  const activeJob = useMemo(
    () => jobs.find((job) => activeStatuses.includes(job.status as JobStatus)) ?? null,
    [jobs]
  );
  const runningJob = useMemo(() => jobs.find((job) => job.status === JobStatus.RUNNING) ?? null, [jobs]);
  const recentJobs = useMemo(() => [...jobs].sort((left, right) => sortableTime(right) - sortableTime(left)), [jobs]);

  useEffect(() => {
    if (!activeJob || !activeStatuses.includes(activeJob.status as JobStatus)) {
      return undefined;
    }

    const intervalId = window.setInterval(() => {
      void getJob(activeJob.id)
        .then((job) => {
          syncJob(job);
        })
        .catch(() => {
          // Keep polling lightweight; the next manual refresh or user action will surface errors.
        });
    }, 3000);

    return () => window.clearInterval(intervalId);
  }, [activeJob, syncJob]);

  const handleStartCrawling = async () => {
    if (!selectedFile || runningJob) {
      return;
    }

    setIsStarting(true);

    try {
      const job = await createJob(selectedFile);
      syncJob(job);
      setSelectedFile(null);
      showToast('Crawl job created successfully.', 'success', 'Job started');
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to start crawl job.', 'error', 'Start failed');
    } finally {
      setIsStarting(false);
    }
  };

  const handleStopCrawling = async (jobId: string) => {
    setIsStopping(true);

    try {
      const job = await stopJob(jobId);
      syncJob({
        ...job,
        status: job.status === 'STOP_REQUESTED' ? JobStatus.STOPPING : job.status
      });
      showToast('Stop request queued for the active crawl.', 'warning', 'Stopping job');
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to stop crawl job.', 'error', 'Stop failed');
    } finally {
      setIsStopping(false);
    }
  };

  const handleDeleteJob = async (jobId: string) => {
    if (!window.confirm('Delete this job and all associated crawl results?')) {
      return;
    }

    setDeletingJobId(jobId);

    try {
      await deleteJob(jobId);
      setJobs((current) => current.filter((job) => job.id !== jobId));
      showToast('Job deleted successfully.', 'success', 'Job removed');
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to delete job.', 'error', 'Delete failed');
    } finally {
      setDeletingJobId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <SeedUrlUpload selectedFile={selectedFile} onFileSelect={setSelectedFile} />

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Crawl Controls</h2>
          <p className="mt-1 text-sm text-slate-500">
            Upload a seed file, then start a crawl job through the API Gateway.
          </p>

          {runningJob && (
            <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              A crawl job is already running. Stop or wait for it to finish before starting another job.
            </div>
          )}

          <div className="mt-6 rounded-2xl bg-slate-50 p-4">
            <p className="text-xs uppercase tracking-wide text-slate-400">Selected seed file</p>
            <p className="mt-2 text-sm font-semibold text-slate-800">{selectedFile?.name ?? 'No file selected'}</p>
            <p className="mt-1 text-xs text-slate-500">
              {selectedFile ? 'Ready to upload to the crawler.' : 'Choose a .txt or .csv seed list to continue.'}
            </p>
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void handleStartCrawling()}
              disabled={!selectedFile || Boolean(runningJob) || isStarting}
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-3 text-sm font-semibold text-white transition enabled:hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {isStarting && <LoadingSpinner size="sm" className="text-white" />}
              {isStarting ? 'Starting...' : 'Start Crawling'}
            </button>
            <button
              type="button"
              onClick={() => activeJob && void handleStopCrawling(activeJob.id)}
              disabled={!activeJob || activeJob.status !== JobStatus.RUNNING || isStopping}
              className="rounded-lg border border-rose-300 px-4 py-3 text-sm font-semibold text-rose-700 transition enabled:hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
            >
              Stop
            </button>
          </div>
        </div>
      </div>

      {activeJob && (
        <JobStatusPanel job={activeJob} onStop={handleStopCrawling} isStopping={isStopping} />
      )}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Recent Jobs</h2>
            <p className="mt-1 text-sm text-slate-500">All crawl jobs for the signed-in user, sorted by most recent first.</p>
          </div>
          {isLoading && <LoadingSpinner size="sm" className="text-blue-600" />}
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="pb-3 pr-4">Job ID</th>
                <th className="pb-3 pr-4">Status</th>
                <th className="pb-3 pr-4">URLs</th>
                <th className="pb-3 pr-4">BFS</th>
                <th className="pb-3 pr-4">Created</th>
                <th className="pb-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm text-slate-700">
              {!isLoading && recentJobs.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-sm text-slate-500">
                    No jobs found yet. Upload a seed file above to create your first crawl job.
                  </td>
                </tr>
              )}

              {recentJobs.map((job) => (
                <tr key={job.id}>
                  <td className="py-4 pr-4">
                    <div>
                      <p className="font-semibold text-slate-900">{job.id}</p>
                      <p className="text-xs text-slate-500 break-all">{job.source}</p>
                    </div>
                  </td>
                  <td className="py-4 pr-4">
                    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${getStatusBadge(String(job.status))}`}>
                      {job.status === 'STOP_REQUESTED' ? JobStatus.STOPPING : job.status}
                    </span>
                  </td>
                  <td className="py-4 pr-4">
                    {job.crawledUrls}/{job.maxUrls}
                  </td>
                  <td className="py-4 pr-4">{job.currentBfsLevel}</td>
                  <td className="py-4 pr-4">{new Date(job.createdAt).toLocaleString()}</td>
                  <td className="py-4">
                    {job.status === JobStatus.COMPLETED ? (
                      <button
                        type="button"
                        onClick={() => void handleDeleteJob(job.id)}
                        disabled={deletingJobId === job.id}
                        className="rounded-lg border border-rose-300 px-3 py-2 text-xs font-semibold text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                      >
                        {deletingJobId === job.id ? 'Deleting...' : 'Delete'}
                      </button>
                    ) : (
                      <span className="text-xs text-slate-400">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
