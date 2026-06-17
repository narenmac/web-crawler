import { useCallback, useEffect, useMemo, useState } from 'react';
import LoadingSpinner from './LoadingSpinner';
import { useToast } from './ToastProvider';
import { getContent, getJobs, getResults } from '../services/apiClient';
import { CrawlResult, Job, JobStatus, PaginatedResponse, UrlStatus } from '../types';

const pageSize = 50;

const statusStyles: Record<string, string> = {
  [UrlStatus.QUEUED]: 'bg-slate-100 text-slate-700',
  [UrlStatus.CRAWLING]: 'bg-blue-100 text-blue-800',
  [UrlStatus.COMPLETED]: 'bg-emerald-100 text-emerald-800',
  [UrlStatus.FAILED]: 'bg-rose-100 text-rose-800',
  [UrlStatus.SKIPPED_DUPLICATE]: 'bg-amber-100 text-amber-800'
};

const statusIcons: Record<string, string> = {
  [UrlStatus.QUEUED]: '⏳',
  [UrlStatus.CRAWLING]: '🔄',
  [UrlStatus.COMPLETED]: '✔',
  [UrlStatus.FAILED]: '⚠',
  [UrlStatus.SKIPPED_DUPLICATE]: '⤼'
};

export default function CrawlResults() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selectedJobId, setSelectedJobId] = useState('');
  const [results, setResults] = useState<PaginatedResponse<CrawlResult> | null>(null);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedResult, setSelectedResult] = useState<CrawlResult | null>(null);
  const [content, setContent] = useState('');
  const [isLoadingJobs, setIsLoadingJobs] = useState(true);
  const [isLoadingResults, setIsLoadingResults] = useState(false);
  const [isLoadingContent, setIsLoadingContent] = useState(false);
  const { showToast } = useToast();

  const completedJobs = useMemo(
    () =>
      jobs.filter((job) => job.status === JobStatus.COMPLETED || (job.completedAt && job.status !== JobStatus.RUNNING)),
    [jobs]
  );

  const selectedJob = useMemo(
    () => completedJobs.find((job) => job.id === selectedJobId) ?? null,
    [completedJobs, selectedJobId]
  );

  const loadJobs = useCallback(async () => {
    const response = await getJobs();
    const sorted = [...response].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
    setJobs(sorted);

    const completed = sorted.filter((job) => job.status === JobStatus.COMPLETED || (job.completedAt && job.status !== JobStatus.RUNNING));
    setSelectedJobId((current) => current || completed[0]?.id || '');
  }, []);

  const loadResults = useCallback(async () => {
    if (!selectedJobId) {
      setResults(null);
      return;
    }

    setIsLoadingResults(true);

    try {
      const response = await getResults(selectedJobId, page, pageSize, statusFilter, searchQuery);
      setResults(response);
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to load crawl results.', 'error', 'Results unavailable');
    } finally {
      setIsLoadingResults(false);
    }
  }, [page, searchQuery, selectedJobId, showToast, statusFilter]);

  useEffect(() => {
    let mounted = true;

    void (async () => {
      try {
        await loadJobs();
      } catch (error) {
        if (mounted) {
          showToast(error instanceof Error ? error.message : 'Unable to load jobs.', 'error', 'Jobs unavailable');
        }
      } finally {
        if (mounted) {
          setIsLoadingJobs(false);
        }
      }
    })();

    return () => {
      mounted = false;
    };
  }, [loadJobs, showToast]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setPage(0);
      setSearchQuery(searchInput.trim());
    }, 350);

    return () => window.clearTimeout(timeoutId);
  }, [searchInput]);

  useEffect(() => {
    void loadResults();
  }, [loadResults]);

  const handleOpenContent = async (result: CrawlResult) => {
    setSelectedResult(result);
    setContent('');
    setIsLoadingContent(true);

    try {
      const response = await getContent(selectedJobId, result.urlHash);
      setContent(response);
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to load HTML content.', 'error', 'Content unavailable');
      setSelectedResult(null);
    } finally {
      setIsLoadingContent(false);
    }
  };

  const totalPages = results ? Math.max(1, Math.ceil(results.total / results.size)) : 1;

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Crawl Results</h2>
            <p className="mt-1 text-sm text-slate-500">Inspect crawled URLs, statuses, and raw HTML content.</p>
          </div>

          <div className="w-full max-w-md">
            <label htmlFor="job-selector" className="block text-sm font-medium text-slate-700">
              Completed job
            </label>
            <select
              id="job-selector"
              value={selectedJobId}
              onChange={(event) => {
                setSelectedJobId(event.target.value);
                setPage(0);
              }}
              className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            >
              <option value="">Select a completed job</option>
              {completedJobs.map((job) => (
                <option key={job.id} value={job.id}>
                  {job.id} · {new Date(job.createdAt).toLocaleString()}
                </option>
              ))}
            </select>
            {isLoadingJobs && <p className="mt-2 text-xs text-slate-500">Loading jobs...</p>}
          </div>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-400">Job status</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{selectedJob?.status ?? '—'}</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-400">URLs crawled</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{selectedJob?.crawledUrls ?? 0}</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-400">Total discovered</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{selectedJob?.totalUrls ?? 0}</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-400">BFS level</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{selectedJob?.currentBfsLevel ?? 0}</p>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap gap-4">
          <div className="min-w-[200px] flex-1">
            <label htmlFor="status-filter" className="block text-sm font-medium text-slate-700">
              Status filter
            </label>
            <select
              id="status-filter"
              value={statusFilter}
              onChange={(event) => {
                setStatusFilter(event.target.value);
                setPage(0);
              }}
              className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            >
              <option value="">All statuses</option>
              {Object.values(UrlStatus).map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
          <div className="min-w-[240px] flex-[2]">
            <label htmlFor="url-search" className="block text-sm font-medium text-slate-700">
              Search URL text
            </label>
            <input
              id="url-search"
              type="text"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Search by URL or parent URL"
              className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="pb-3 pr-4">#</th>
                <th className="pb-3 pr-4">URL</th>
                <th className="pb-3 pr-4">BFS Level</th>
                <th className="pb-3 pr-4">Status</th>
                <th className="pb-3 pr-4">Crawled</th>
                <th className="pb-3">Content</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm text-slate-700">
              {isLoadingResults && (
                <tr>
                  <td colSpan={6} className="py-10 text-center">
                    <div className="inline-flex items-center gap-2 text-slate-500">
                      <LoadingSpinner size="sm" className="text-blue-600" />
                      Loading crawl results...
                    </div>
                  </td>
                </tr>
              )}

              {!isLoadingResults && (!results || results.items.length === 0) && (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-sm text-slate-500">
                    {selectedJobId ? 'No crawl results match the current filters.' : 'Choose a completed job to view results.'}
                  </td>
                </tr>
              )}

              {!isLoadingResults &&
                results?.items.map((result, index) => {
                  const badgeClasses = statusStyles[result.status] ?? 'bg-slate-100 text-slate-700';
                  const statusIcon = statusIcons[result.status] ?? '•';

                  return (
                    <tr key={result.urlHash}>
                      <td className="py-4 pr-4">{page * pageSize + index + 1}</td>
                      <td className="py-4 pr-4">
                        <div>
                          <p className="break-all font-medium text-slate-900">{result.url}</p>
                          <p className="mt-1 break-all text-xs text-slate-500">{result.parentUrl ?? 'Seed URL'}</p>
                        </div>
                      </td>
                      <td className="py-4 pr-4">{result.bfsLevel}</td>
                      <td className="py-4 pr-4">
                        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${badgeClasses}`}>
                          {statusIcon} {result.status}
                        </span>
                      </td>
                      <td className="py-4 pr-4">{result.crawledAt ? new Date(result.crawledAt).toLocaleString() : '—'}</td>
                      <td className="py-4">
                        <button
                          type="button"
                          onClick={() => void handleOpenContent(result)}
                          className="rounded-lg border border-blue-300 px-3 py-2 text-xs font-semibold text-blue-700 transition hover:bg-blue-50"
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-slate-500">
            Showing {results?.items.length ?? 0} of {results?.total ?? 0} URLs
          </p>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={page === 0}
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
            >
              Previous
            </button>
            <span className="text-sm text-slate-600">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
              disabled={page + 1 >= totalPages}
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
            >
              Next
            </button>
          </div>
        </div>
      </div>

      {selectedResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
          <div className="max-h-[88vh] w-full max-w-5xl overflow-hidden rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-4">
              <div>
                <h3 className="text-lg font-semibold text-slate-900">Raw HTML Content</h3>
                <p className="mt-1 break-all text-sm text-slate-500">{selectedResult.url}</p>
              </div>
              <button
                type="button"
                onClick={() => {
                  setSelectedResult(null);
                  setContent('');
                }}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
              >
                Close
              </button>
            </div>
            <div className="max-h-[72vh] overflow-auto bg-slate-950 px-6 py-4">
              {isLoadingContent ? (
                <div className="flex min-h-[200px] items-center justify-center gap-3 text-emerald-300">
                  <LoadingSpinner size="md" className="text-emerald-300" />
                  Loading HTML content...
                </div>
              ) : (
                <pre className="whitespace-pre-wrap break-words font-mono text-sm text-emerald-300">{content}</pre>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
