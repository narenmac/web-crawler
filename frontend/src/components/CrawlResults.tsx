import { useMemo, useState } from 'react';
import { CrawlResult, Job, JobStatus } from '../types';

const jobs: Job[] = [
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
    fetcherPods: 0,
    parserPods: 0,
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
    fetcherPods: 0,
    parserPods: 0,
    seedFileName: 'docs.csv'
  }
];

const allResults: CrawlResult[] = [
  {
    id: 'result-1',
    jobId: 'job-1042',
    url: 'https://example.com',
    statusCode: 200,
    level: 0,
    fetchedAt: new Date(Date.now() - 1000 * 60 * 150).toISOString(),
    rawHtml: '<html><body><h1>Example Domain</h1></body></html>',
    metadata: {
      title: 'Example Domain',
      contentType: 'text/html',
      contentLength: 1256
    }
  },
  {
    id: 'result-2',
    jobId: 'job-1042',
    url: 'https://example.com/catalog',
    statusCode: 200,
    level: 1,
    fetchedAt: new Date(Date.now() - 1000 * 60 * 148).toISOString(),
    rawHtml: '<html><body><div>Catalog page</div></body></html>',
    metadata: {
      title: 'Catalog',
      contentType: 'text/html',
      contentLength: 2048
    }
  },
  {
    id: 'result-3',
    jobId: 'job-1042',
    url: 'https://example.com/category/widgets',
    statusCode: 200,
    level: 2,
    fetchedAt: new Date(Date.now() - 1000 * 60 * 146).toISOString(),
    rawHtml: '<html><body><section>Widgets</section></body></html>',
    metadata: {
      title: 'Widgets',
      contentType: 'text/html',
      contentLength: 1844
    }
  },
  {
    id: 'result-4',
    jobId: 'job-1041',
    url: 'https://docs.example.com',
    statusCode: 200,
    level: 0,
    fetchedAt: new Date(Date.now() - 1000 * 60 * 320).toISOString(),
    rawHtml: '<html><body><h1>Documentation Home</h1></body></html>',
    metadata: {
      title: 'Documentation Home',
      contentType: 'text/html',
      contentLength: 1490
    }
  },
  {
    id: 'result-5',
    jobId: 'job-1041',
    url: 'https://docs.example.com/getting-started',
    statusCode: 503,
    level: 1,
    fetchedAt: new Date(Date.now() - 1000 * 60 * 318).toISOString(),
    rawHtml: '<html><body><p>Service unavailable</p></body></html>',
    metadata: {
      title: 'Service unavailable',
      contentType: 'text/html',
      contentLength: 910
    }
  },
  {
    id: 'result-6',
    jobId: 'job-1041',
    url: 'https://docs.example.com/api',
    statusCode: 200,
    level: 2,
    fetchedAt: new Date(Date.now() - 1000 * 60 * 316).toISOString(),
    rawHtml: '<html><body><article>API Reference</article></body></html>',
    metadata: {
      title: 'API Reference',
      contentType: 'text/html',
      contentLength: 2320
    }
  }
];

const pageSize = 5;

export default function CrawlResults() {
  const [selectedJobId, setSelectedJobId] = useState<string>(jobs[0]?.id ?? '');
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedResult, setSelectedResult] = useState<CrawlResult | null>(null);

  const filteredResults = useMemo(
    () => allResults.filter((result) => result.jobId === selectedJobId),
    [selectedJobId]
  );

  const pageCount = Math.max(1, Math.ceil(filteredResults.length / pageSize));
  const paginatedResults = filteredResults.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Crawl Results</h2>
          <p className="mt-1 text-sm text-slate-500">Browse fetched URLs, HTTP status codes, and raw HTML output.</p>
        </div>

        <div className="w-full max-w-sm">
          <label htmlFor="job-selector" className="block text-sm font-medium text-slate-700">
            Select job
          </label>
          <select
            id="job-selector"
            value={selectedJobId}
            onChange={(event) => {
              setSelectedJobId(event.target.value);
              setCurrentPage(1);
            }}
            className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
          >
            {jobs.map((job) => (
              <option key={job.id} value={job.id}>
                {job.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="mt-6 overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <th className="pb-3 pr-4">URL</th>
              <th className="pb-3 pr-4">Status</th>
              <th className="pb-3 pr-4">Level</th>
              <th className="pb-3 pr-4">Fetched</th>
              <th className="pb-3">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm text-slate-700">
            {paginatedResults.map((result) => (
              <tr key={result.id}>
                <td className="py-4 pr-4">
                  <div>
                    <p className="font-medium text-slate-900">{result.url}</p>
                    <p className="text-xs text-slate-500">{result.metadata.title ?? 'Untitled'}</p>
                  </div>
                </td>
                <td className="py-4 pr-4">{result.statusCode}</td>
                <td className="py-4 pr-4">{result.level}</td>
                <td className="py-4 pr-4">{new Date(result.fetchedAt).toLocaleString()}</td>
                <td className="py-4">
                  <button
                    type="button"
                    onClick={() => setSelectedResult(result)}
                    className="rounded-lg border border-blue-300 px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-50"
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-6 flex items-center justify-between">
        <p className="text-sm text-slate-500">
          Showing {paginatedResults.length} of {filteredResults.length} URLs
        </p>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
            disabled={currentPage === 1}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
          >
            Previous
          </button>
          <span className="text-sm text-slate-600">
            Page {currentPage} of {pageCount}
          </span>
          <button
            type="button"
            onClick={() => setCurrentPage((page) => Math.min(pageCount, page + 1))}
            disabled={currentPage === pageCount}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
          >
            Next
          </button>
        </div>
      </div>

      {selectedResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
          <div className="max-h-[85vh] w-full max-w-4xl overflow-hidden rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-4">
              <div>
                <h3 className="text-lg font-semibold text-slate-900">Raw HTML</h3>
                <p className="mt-1 break-all text-sm text-slate-500">{selectedResult.url}</p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedResult(null)}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Close
              </button>
            </div>
            <div className="max-h-[70vh] overflow-auto bg-slate-950 px-6 py-4">
              <pre className="whitespace-pre-wrap break-words text-sm text-emerald-300">{selectedResult.rawHtml}</pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
