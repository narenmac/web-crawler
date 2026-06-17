import { useEffect, useMemo, useState } from 'react';
import SeedUrlUpload from './SeedUrlUpload';
import JobStatusPanel from './JobStatus';
import { Job, JobStatus } from '../types';

const createInitialJobs = (): Job[] => [
  {
    id: 'job-1042',
    name: 'Retail catalog crawl',
    status: JobStatus.Completed,
    createdAt: new Date(Date.now() - 1000 * 60 * 180).toISOString(),
    completedAt: new Date(Date.now() - 1000 * 60 * 140).toISOString(),
    progress: 100,
    totalUrls: 1200,
    crawledUrls: 1200,
    bfsLevel: 6,
    fetcherPods: 4,
    parserPods: 2,
    seedFileName: 'catalog.txt'
  },
  {
    id: 'job-1041',
    name: 'Docs refresh crawl',
    status: JobStatus.Stopped,
    createdAt: new Date(Date.now() - 1000 * 60 * 360).toISOString(),
    completedAt: new Date(Date.now() - 1000 * 60 * 330).toISOString(),
    progress: 42,
    totalUrls: 950,
    crawledUrls: 401,
    bfsLevel: 3,
    fetcherPods: 2,
    parserPods: 2,
    seedFileName: 'docs.csv'
  }
];

export default function JobDashboard() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [activeJob, setActiveJob] = useState<Job | null>(null);
  const [recentJobs, setRecentJobs] = useState<Job[]>(createInitialJobs);

  useEffect(() => {
    if (!activeJob || activeJob.status !== JobStatus.Running) {
      return;
    }

    const intervalId = window.setInterval(() => {
      setActiveJob((currentJob) => {
        if (!currentJob || currentJob.status !== JobStatus.Running) {
          return currentJob;
        }

        const nextCrawledUrls = Math.min(currentJob.crawledUrls + 28, currentJob.totalUrls);
        const nextProgress = Math.round((nextCrawledUrls / currentJob.totalUrls) * 100);
        const completed = nextCrawledUrls >= currentJob.totalUrls;

        const updatedJob: Job = {
          ...currentJob,
          crawledUrls: nextCrawledUrls,
          progress: nextProgress,
          bfsLevel: Math.min(currentJob.bfsLevel + (completed ? 0 : 1), 8),
          fetcherPods: completed ? 0 : currentJob.fetcherPods,
          parserPods: completed ? 0 : currentJob.parserPods,
          status: completed ? JobStatus.Completed : JobStatus.Running,
          completedAt: completed ? new Date().toISOString() : currentJob.completedAt
        };

        if (completed) {
          setRecentJobs((jobs) => [updatedJob, ...jobs]);
          setSelectedFile(null);
        }

        return updatedJob;
      });
    }, 1200);

    return () => window.clearInterval(intervalId);
  }, [activeJob]);

  const canStart = useMemo(() => selectedFile !== null && activeJob?.status !== JobStatus.Running, [activeJob, selectedFile]);

  const handleStartCrawling = () => {
    if (!selectedFile) {
      return;
    }

    setActiveJob({
      id: `job-${Date.now()}`,
      name: `Crawl for ${selectedFile.name}`,
      status: JobStatus.Running,
      createdAt: new Date().toISOString(),
      progress: 0,
      totalUrls: 500,
      crawledUrls: 0,
      bfsLevel: 0,
      fetcherPods: 3,
      parserPods: 2,
      seedFileName: selectedFile.name
    });
  };

  const handleStopCrawling = () => {
    setActiveJob((currentJob) => {
      if (!currentJob) {
        return currentJob;
      }

      const stoppedJob: Job = {
        ...currentJob,
        status: JobStatus.Stopped,
        completedAt: new Date().toISOString(),
        fetcherPods: 0,
        parserPods: 0
      };

      setRecentJobs((jobs) => [stoppedJob, ...jobs]);
      return stoppedJob;
    });
  };

  return (
    <div className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <SeedUrlUpload selectedFile={selectedFile} onFileSelect={setSelectedFile} />

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Crawl Controls</h2>
          <p className="mt-1 text-sm text-slate-500">
            Start a new crawl when you have uploaded a seed URL file.
          </p>

          <div className="mt-6 rounded-xl bg-slate-50 p-4">
            <p className="text-xs uppercase tracking-wide text-slate-400">Selected seed file</p>
            <p className="mt-2 text-sm font-medium text-slate-800">
              {selectedFile?.name ?? 'No file selected'}
            </p>
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={handleStartCrawling}
              disabled={!canStart}
              className="rounded-lg bg-blue-600 px-4 py-3 text-sm font-semibold text-white transition enabled:hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              Start Crawling
            </button>
            <button
              type="button"
              onClick={handleStopCrawling}
              disabled={!activeJob || activeJob.status !== JobStatus.Running}
              className="rounded-lg border border-rose-300 px-4 py-3 text-sm font-semibold text-rose-600 transition enabled:hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
            >
              Stop
            </button>
          </div>
        </div>
      </div>

      {activeJob && <JobStatusPanel job={activeJob} />}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Recent Jobs</h2>
            <p className="mt-1 text-sm text-slate-500">Most recent crawling activity and completion stats.</p>
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="pb-3 pr-4">Job</th>
                <th className="pb-3 pr-4">Status</th>
                <th className="pb-3 pr-4">Progress</th>
                <th className="pb-3 pr-4">URLs</th>
                <th className="pb-3">Started</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm text-slate-700">
              {recentJobs.map((job) => (
                <tr key={job.id}>
                  <td className="py-4 pr-4">
                    <div>
                      <p className="font-medium text-slate-900">{job.name}</p>
                      <p className="text-xs text-slate-500">{job.seedFileName}</p>
                    </div>
                  </td>
                  <td className="py-4 pr-4">
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                      {job.status}
                    </span>
                  </td>
                  <td className="py-4 pr-4">{job.progress}%</td>
                  <td className="py-4 pr-4">
                    {job.crawledUrls}/{job.totalUrls}
                  </td>
                  <td className="py-4">{new Date(job.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
