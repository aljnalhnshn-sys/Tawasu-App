import React, { useMemo, useState } from 'react';
import { Check, Copy, Download, FileCode2, Search, X } from 'lucide-react';

const androidFileModules = import.meta.glob('/android/**/*', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

export const AndroidProjectModal: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const files = useMemo(
    () =>
      Object.entries(androidFileModules)
        .map(([rawPath, content]) => {
          const path = rawPath.replace(/^\//, '');
          return { path, name: path.split('/').pop() || path, content };
        })
        .sort((a, b) => a.path.localeCompare(b.path)),
    []
  );

  const [query, setQuery] = useState('');
  const [selectedPath, setSelectedPath] = useState(files[0]?.path || '');
  const [copied, setCopied] = useState(false);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? files.filter((f) => f.path.toLowerCase().includes(q)) : files;
  }, [files, query]);

  const activeFile = files.find((f) => f.path === selectedPath) || filtered[0];

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-5xl h-[85vh] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden flex flex-col">
        <div className="px-5 py-3.5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-4">
          <div>
            <h2 className="font-bold text-base font-display">مصدر مشروع Android (Kotlin + Jetpack Compose)</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {files.length} ملفًا جاهزًا للفتح في Android Studio
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <a
              href="/tawasul-pwa-publish.zip"
              download="tawasul-pwa-publish.zip"
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-xl border border-emerald-700/30 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-900 dark:text-emerald-300 hover:bg-emerald-100"
            >
              <Download className="w-4 h-4" />
              <span>حزمة النشر الجاهزة (PWA)</span>
            </a>
            <a
              href="/tawasul-android-project.zip"
              download="tawasul-android-project.zip"
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-xl bg-emerald-800 hover:bg-emerald-700 text-white"
            >
              <Download className="w-4 h-4" />
              <span>تنزيل مشروع Android (.zip)</span>
            </a>
            <button
              type="button"
              onClick={onClose}
              className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800"
              aria-label="إغلاق"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="flex-1 grid grid-cols-1 md:grid-cols-12 overflow-hidden">
          <div className="md:col-span-4 border-l border-slate-200 dark:border-slate-800 flex flex-col overflow-hidden">
            <div className="p-3 border-b border-slate-200 dark:border-slate-800">
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="ابحث في الملفات..."
                  className="w-full pr-9 pl-3 py-2 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800"
                />
              </div>
            </div>
            <div className="flex-1 overflow-y-auto p-2 space-y-0.5">
              {filtered.map((f) => (
                <button
                  key={f.path}
                  type="button"
                  onClick={() => setSelectedPath(f.path)}
                  className={`w-full text-right px-2.5 py-2 rounded-lg text-xs flex items-center gap-2 ${
                    activeFile?.path === f.path
                      ? 'bg-emerald-800 text-white'
                      : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <FileCode2 className="w-3.5 h-3.5 shrink-0" />
                  <span className="truncate font-mono" dir="ltr">
                    {f.name}
                  </span>
                </button>
              ))}
            </div>
          </div>

          <div className="md:col-span-8 flex flex-col overflow-hidden">
            {activeFile && (
              <>
                <div className="px-4 py-2.5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-2">
                  <span className="text-xs font-mono truncate" dir="ltr">
                    {activeFile.path}
                  </span>
                  <button
                    type="button"
                    onClick={async () => {
                      await navigator.clipboard.writeText(activeFile.content);
                      setCopied(true);
                      setTimeout(() => setCopied(false), 1500);
                    }}
                    className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700"
                  >
                    {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copied ? 'تم النسخ' : 'نسخ'}</span>
                  </button>
                </div>
                <pre
                  dir="ltr"
                  className="flex-1 overflow-auto bg-slate-950 text-slate-100 p-4 text-xs font-mono leading-6"
                >
                  {activeFile.content}
                </pre>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
