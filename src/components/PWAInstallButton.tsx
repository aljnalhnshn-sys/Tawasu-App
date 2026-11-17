import React, { useState } from 'react';
import { Check, Copy, Download, ExternalLink, Smartphone, X } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

export const PWAInstallButton: React.FC<{ variant?: 'compact' | 'primary' }> = ({
  variant = 'compact',
}) => {
  const { isInstallable, isInstalled, isIOS, isInIframe, standaloneUrl, install } =
    usePWAInstall();
  const [showGuide, setShowGuide] = useState(false);
  const [copiedUrl, setCopiedUrl] = useState(false);

  if (isInstalled) {
    return null;
  }

  const handleClick = async () => {
    if (isInstallable) {
      const accepted = await install();
      if (!accepted) setShowGuide(true);
    } else {
      setShowGuide(true);
    }
  };

  const handleCopyUrl = async () => {
    try {
      await navigator.clipboard.writeText(standaloneUrl);
      setCopiedUrl(true);
      setTimeout(() => setCopiedUrl(false), 1800);
    } catch {
      // Ignore clipboard failures in restricted contexts
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={handleClick}
        className={
          variant === 'primary'
            ? 'w-full inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-700/30 px-4 py-3 text-sm font-semibold text-emerald-900 dark:text-emerald-300 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 transition-colors min-h-[44px]'
            : 'inline-flex items-center gap-1.5 rounded-xl bg-emerald-800 hover:bg-emerald-700 px-3 py-2 text-xs font-medium text-white transition-colors whitespace-nowrap shrink-0 min-h-[40px]'
        }
      >
        {isInstallable ? (
          <Download className="w-4 h-4 shrink-0" />
        ) : (
          <Smartphone className="w-4 h-4 shrink-0" />
        )}
        <span>تثبيت التطبيق على الهاتف</span>
      </button>

      {showGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 shadow-xl space-y-4 text-right">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold font-display">
                {isIOS ? 'تثبيت تواصل على iPhone / iPad' : 'تثبيت تواصل على الهاتف'}
              </h3>
              <button
                type="button"
                onClick={() => setShowGuide(false)}
                className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800"
                aria-label="إغلاق"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {(isInIframe ||
              (typeof window !== 'undefined' &&
                window.location.hostname.includes('ais-dev-'))) && (
              <div className="rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900 p-3 text-xs text-amber-900 dark:text-amber-200 space-y-2 leading-relaxed">
                <p className="font-semibold">
                  لماذا لم يظهر التثبيت هنا مباشرة؟
                </p>
                <p>
                  المتصفحات تمنع تثبيت التطبيقات من داخل نافذة المعاينة المدمجة أو رابط المطور المحمي. افتح رابط التطبيق العام المستقل في متصفح Chrome أو Safari مباشرة:
                </p>
                <div className="flex flex-wrap items-center gap-2 pt-1">
                  <a
                    href={standaloneUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-800 hover:bg-emerald-700 text-white font-semibold"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>فتح الرابط المستقل للتثبيت</span>
                  </a>
                  <button
                    type="button"
                    onClick={handleCopyUrl}
                    className="inline-flex items-center gap-1 px-3 py-2 rounded-lg border border-amber-300 dark:border-amber-700 bg-white dark:bg-slate-900 font-medium"
                  >
                    {copiedUrl ? (
                      <Check className="w-3.5 h-3.5 text-emerald-600" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                    <span>{copiedUrl ? 'تم النسخ' : 'نسخ الرابط'}</span>
                  </button>
                </div>
              </div>
            )}

            {isIOS ? (
              <ol className="text-sm text-slate-600 dark:text-slate-300 space-y-2 list-decimal pr-4 leading-relaxed">
                <li>افتح الرابط المستقل أعلاه في متصفح <strong>Safari</strong>.</li>
                <li>اضغط على زر المشاركة (Share) في شريط المتصفح بالأسفل.</li>
                <li>اختر <strong>إضافة إلى الشاشة الرئيسية (Add to Home Screen)</strong>.</li>
              </ol>
            ) : (
              <ol className="text-sm text-slate-600 dark:text-slate-300 space-y-2 list-decimal pr-4 leading-relaxed">
                <li>افتح الرابط المستقل أعلاه في متصفح <strong>Chrome</strong> على هاتفك (خارج المعاينة).</li>
                <li>اضغط على زر <strong>تثبيت التطبيق على الهاتف</strong> أو قائمة النقاط الثلاث أعلى المتصفح.</li>
                <li>اختر <strong>تثبيت التطبيق (Install app)</strong> ليظهر كتطبيق مستقل على الشاشة الرئيسية.</li>
              </ol>
            )}

            <div className="border-t border-slate-200 dark:border-slate-800 pt-3 flex flex-col gap-2">
              <a
                href="/tawasul-pwa-publish.zip"
                download="tawasul-pwa-publish.zip"
                className="w-full py-2.5 px-4 rounded-xl border border-emerald-700/30 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-900 dark:text-emerald-300 text-xs font-semibold flex items-center justify-center gap-2 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 transition-colors"
              >
                <Download className="w-4 h-4" />
                <span>تنزيل حزمة النشر الجاهزة (tawasul-pwa-publish.zip)</span>
              </a>
              <button
                type="button"
                onClick={() => setShowGuide(false)}
                className="w-full py-2.5 rounded-xl bg-emerald-800 hover:bg-emerald-700 text-white text-sm font-medium transition-colors"
              >
                إغلاق
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
