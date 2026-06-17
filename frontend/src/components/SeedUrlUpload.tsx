import { ChangeEvent, DragEvent, useEffect, useRef, useState } from 'react';

interface SeedUrlUploadProps {
  selectedFile: File | null;
  onFileSelect: (file: File | null) => void;
}

const acceptedTypes = '.txt,.csv';
const maxFileSizeBytes = 1024 * 1024;

const countUrlsInFile = async (file: File) => {
  const content = await file.text();
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean).length;
};

export default function SeedUrlUpload({ selectedFile, onFileSelect }: SeedUrlUploadProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [error, setError] = useState('');
  const [urlCount, setUrlCount] = useState<number | null>(null);
  const [isReading, setIsReading] = useState(false);

  useEffect(() => {
    let cancelled = false;

    if (!selectedFile) {
      setUrlCount(null);
      setIsReading(false);
      return undefined;
    }

    setIsReading(true);
    setError('');

    void countUrlsInFile(selectedFile)
      .then((count) => {
        if (!cancelled) {
          setUrlCount(count);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError('Unable to read file contents.');
          setUrlCount(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsReading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedFile]);

  const validateAndSelectFile = (file: File | undefined) => {
    if (!file) {
      return;
    }

    const extension = file.name.split('.').pop()?.toLowerCase();

    if (!extension || !['txt', 'csv'].includes(extension)) {
      setError('Only .txt and .csv files are supported.');
      onFileSelect(null);
      return;
    }

    if (file.size > maxFileSizeBytes) {
      setError('Seed file must be 1MB or smaller.');
      onFileSelect(null);
      return;
    }

    setError('');
    onFileSelect(file);
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    validateAndSelectFile(event.target.files?.[0]);
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
    validateAndSelectFile(event.dataTransfer.files?.[0]);
  };

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4">
        <h2 className="text-lg font-semibold text-slate-900">Seed URL Upload</h2>
        <p className="mt-1 text-sm text-slate-500">Upload a .txt or .csv file containing one URL per line.</p>
      </div>

      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            inputRef.current?.click();
          }
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-10 text-center transition ${
          isDragging
            ? 'border-blue-500 bg-blue-50'
            : 'border-slate-300 hover:border-blue-400 hover:bg-slate-50'
        }`}
      >
        <div className="rounded-full bg-slate-100 p-3 text-2xl">📄</div>
        <p className="mt-4 text-sm font-semibold text-slate-800">
          {selectedFile ? selectedFile.name : 'Drag and drop your seed file here'}
        </p>
        <p className="mt-2 text-xs text-slate-500">or click to browse · max 1MB · .txt / .csv only</p>

        {selectedFile && (
          <div className="mt-5 rounded-xl bg-white px-4 py-3 shadow-sm ring-1 ring-slate-200">
            <p className="text-sm font-medium text-slate-900">{selectedFile.name}</p>
            <p className="mt-1 text-xs text-slate-500">
              {(selectedFile.size / 1024).toFixed(1)} KB
              {isReading ? ' · counting URLs...' : urlCount !== null ? ` · ${urlCount} URLs detected` : ''}
            </p>
          </div>
        )}
      </div>

      {error && (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {error}
        </div>
      )}

      {selectedFile && (
        <div className="mt-4 flex items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
          <div>
            <p className="text-sm font-medium text-slate-800">Seed file ready</p>
            <p className="text-xs text-slate-500">You can now start a crawl job from the dashboard.</p>
          </div>
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onFileSelect(null);
              setUrlCount(null);
              setError('');
              if (inputRef.current) {
                inputRef.current.value = '';
              }
            }}
            className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
          >
            Remove
          </button>
        </div>
      )}

      <input
        ref={inputRef}
        type="file"
        accept={acceptedTypes}
        className="hidden"
        onChange={handleFileChange}
      />
    </div>
  );
}
