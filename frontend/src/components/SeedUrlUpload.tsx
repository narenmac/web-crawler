import { ChangeEvent, DragEvent, useRef, useState } from 'react';

interface SeedUrlUploadProps {
  selectedFile: File | null;
  onFileSelect: (file: File | null) => void;
}

const acceptedTypes = '.txt,.csv';

export default function SeedUrlUpload({ selectedFile, onFileSelect }: SeedUrlUploadProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  const validateAndSelectFile = (file: File | undefined) => {
    if (!file) {
      return;
    }

    const extension = file.name.split('.').pop()?.toLowerCase();

    if (!extension || !['txt', 'csv'].includes(extension)) {
      return;
    }

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
            inputRef.current?.click();
          }
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        className={`flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-10 text-center transition ${
          isDragging ? 'border-blue-500 bg-blue-50' : 'border-slate-300 hover:border-blue-400 hover:bg-slate-50'
        }`}
      >
        <p className="text-sm font-medium text-slate-700">
          {selectedFile ? selectedFile.name : 'Drag and drop your seed file here'}
        </p>
        <p className="mt-2 text-xs text-slate-500">or click to browse</p>
        {selectedFile && (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onFileSelect(null);
            }}
            className="mt-4 rounded-lg border border-slate-300 px-3 py-2 text-xs font-medium text-slate-600 hover:bg-slate-50"
          >
            Remove file
          </button>
        )}
      </div>

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
