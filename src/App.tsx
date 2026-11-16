import React, { useMemo, useState } from 'react';
import {
  Check,
  Copy,
  FileCode2,
  FolderOpen,
  MessageSquare,
  Moon,
  Phone,
  Search,
  Settings,
  ShieldCheck,
  Sun,
  Users,
  Video,
  Wrench,
  CircleDot,
  Send,
  Mic,
  Image as ImageIcon,
  ArrowRight,
  CheckCircle2,
  AlertTriangle,
} from 'lucide-react';

const androidFileModules = import.meta.glob('/android/**/*', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

type StudioTab = 'explorer' | 'preview' | 'security' | 'setup';
type MobileScreenKey =
  | 'welcome'
  | 'onboarding'
  | 'chats'
  | 'chatDetail'
  | 'search'
  | 'status'
  | 'groups'
  | 'calls'
  | 'profile';

interface AndroidFileEntry {
  path: string;
  name: string;
  layer: string;
  content: string;
  lines: number;
}

function classifyLayer(path: string): string {
  if (path.includes('/authentication/')) return 'المصادقة (authentication)';
  if (path.includes('/models/')) return 'النماذج (models)';
  if (path.includes('/repositories/')) return 'المستودعات (repositories)';
  if (path.includes('/messaging/')) return 'المراسلة والمكالمات (messaging)';
  if (path.includes('/notifications/')) return 'الإشعارات (notifications)';
  if (path.includes('/data/')) return 'البيانات (data)';
  if (path.includes('/navigation/')) return 'التنقل (navigation)';
  if (path.includes('/ui/')) return 'الواجهات (ui)';
  if (path.includes('/src/test/') || path.endsWith('firestore.rules.test.ts')) return 'الاختبارات (tests)';
  if (
    path.endsWith('.rules') ||
    path.endsWith('.indexes.json') ||
    path.includes('/functions/')
  ) {
    return 'الأمان والسحابة (firebase)';
  }
  return 'إعدادات Gradle والمشروع';
}

const LAYER_ORDER = [
  'إعدادات Gradle والمشروع',
  'النماذج (models)',
  'البيانات (data)',
  'المصادقة (authentication)',
  'المستودعات (repositories)',
  'المراسلة والمكالمات (messaging)',
  'الإشعارات (notifications)',
  'التنقل (navigation)',
  'الواجهات (ui)',
  'الأمان والسحابة (firebase)',
  'الاختبارات (tests)',
];

const SECURITY_PILLARS = [
  {
    title: 'عزل البيانات الحساسة (PII Isolation)',
    detail:
      'تم فصل البريد الإلكتروني ورموز الإشعارات FCM Tokens داخل المسار users/{uid}/private/info بحيث لا يستطيع قراءته سوى صاحب الحساب فقط، بينما يقتصر البحث العام على الاسم الظاهر واسم المستخدم والصورة.',
  },
  {
    title: 'حجز اسم المستخدم ذريًا (Atomic Transaction)',
    detail:
      'يتم حجز الاسم داخل usernames/{usernameLower} باستخدام Firestore Transaction مع قواعد تمنع التعديل المباشر أو الاستيلاء على اسم مستخدم مملوك لـ UID آخر، مع التحقق غير الحساس لحالة الأحرف (3-20 حرفًا إنجليزيًا ورقمًا و_).',
  },
  {
    title: 'منع المحادثات الثنائية المكررة',
    detail:
      'يُبنى معرّف المحادثة الثنائية بشكل حتمي عبر ترتيب المعرفين أبجديًا (uidA_uidB)، وتفرض قواعد Firestore مطابقة المعرّف للمشاركين في المحادثة الثنائية.',
  },
  {
    title: 'تقييد قراءة الرسائل وكتابتها (Master Gate)',
    detail:
      'يخضع الوصول إلى chats/{chatId}/messages/{messageId} للتحقق من وجود العضو داخل المجموعة الفرعية members/{uid}، مع منع تزوير senderId أو تجاوز 4000 حرف أو استخدام طوابع زمنية من العميل.',
  },
  {
    title: 'إشعارات FCM عبر الخادم الموثوق',
    detail:
      'لا توجد أي مفاتيح إدارية داخل تطبيق Android؛ تتولى دالة Cloud Function (notifyOnNewMessage) إرسال الإشعارات بعد التحقق من عدم وجود حظر بين الطرفين.',
  },
  {
    title: 'شفافية سياسة التشفير والحذف',
    detail:
      'تم توضيح أن مصادقة Firebase وقواعد الأمان لا تعني تشفيرًا من الطرف إلى الطرف (E2EE)، كما أن حذف الرسالة يقتصر على إخفائها من حساب المستخدم الحالي وفق سياسة صريحة.',
  },
];

const SETUP_STEPS = [
  {
    step: '01',
    title: 'إنشاء مشروع Firebase وتفعيل Google Sign-In',
    items: [
      'افتح Firebase Console وأنشئ مشروعًا جديدًا باسم Tawasul.',
      'من قسم Authentication > Sign-in method فعّل مزود Google.',
      'أضف تطبيق Android بحزمة com.tawasul.app مع بصمات SHA-1 وSHA-256 الخاصة بجهاز التطوير.',
      'حمّل ملف google-services.json وضعه داخل المسار android/app/google-services.json.',
    ],
  },
  {
    step: '02',
    title: 'ضبط Credential Manager و Web Client ID',
    items: [
      'انسخ Web Client ID (من نوع client_type: 3 داخل google-services.json أو من إعدادات مزود Google في Firebase).',
      'أنشئ ملف android/local.properties وأضف السطر: GOOGLE_WEB_CLIENT_ID=YOUR_CLIENT_ID.apps.googleusercontent.com.',
    ],
  },
  {
    step: '03',
    title: 'تفعيل Firestore و Storage ونشر قواعد الأمان',
    items: [
      'فعّل Cloud Firestore و Firebase Storage في وحدة تحكم Firebase.',
      'من مجلد android/ نفّذ: firebase deploy --only firestore:rules,firestore:indexes,storage.',
      'انشر دالة الإشعارات السحابية داخل android/functions عبر: firebase deploy --only functions (يتطلب خطة Blaze في Firebase).',
    ],
  },
  {
    step: '04',
    title: 'بناء APK عبر الخدمة السحابية أو محليًا وتثبيته على هاتفك',
    items: [
      'للبناء السحابي بدون حاسوب قوي: ارفع كامل المشروع إلى مستودع GitHub (Tawasul) وأضف ملف google-services.json المشفّر بـ Base64 في Secret باسم GOOGLE_SERVICES_JSON_BASE64، ثم شغّل workflow (.github/workflows/android-build.yml) لتنزيل tawasul-debug-apk مباشرة إلى هاتفك.',
      'للبناء المحلي: افتح مجلد android/ في Android Studio مع JDK 17 وAndroid SDK 35، أو نفّذ gradle assembleDebug (أو assembleRelease بعد ضبط التوقيع).',
      'لتثبيت الـ APK على هاتفك مباشرة: نزّل ملف app-debug.apk على الهاتف وافتحه واسمح بـ "تثبيت التطبيقات من مصادر غير معروفة" للمتصفح/مدير الملفات، أو عبر USB: adb install app/build/outputs/apk/debug/app-debug.apk.',
    ],
  },
];

export default function App() {
  const [darkMode, setDarkMode] = useState(false);
  const [activeTab, setActiveTab] = useState<StudioTab>('explorer');
  const [fileQuery, setFileQuery] = useState('');
  const [copiedPath, setCopiedPath] = useState<string | null>(null);
  const [mobileScreen, setMobileScreen] = useState<MobileScreenKey>('chats');
  const [previewDark, setPreviewDark] = useState(false);

  const files = useMemo<AndroidFileEntry[]>(() => {
    return Object.entries(androidFileModules)
      .map(([rawPath, content]) => {
        const cleanPath = rawPath.replace(/^\//, '');
        const name = cleanPath.split('/').pop() || cleanPath;
        return {
          path: cleanPath,
          name,
          layer: classifyLayer(cleanPath),
          content,
          lines: content.split('\n').length,
        };
      })
      .sort((a, b) => a.path.localeCompare(b.path));
  }, []);

  const [selectedPath, setSelectedPath] = useState<string>(() => {
    const preferred = 'android/app/src/main/java/com/tawasul/app/repositories/UserRepository.kt';
    return files.find((f) => f.path === preferred)?.path || files[0]?.path || '';
  });

  const filteredFiles = useMemo(() => {
    const q = fileQuery.trim().toLowerCase();
    if (!q) return files;
    return files.filter(
      (f) => f.path.toLowerCase().includes(q) || f.layer.toLowerCase().includes(q)
    );
  }, [files, fileQuery]);

  const groupedFiles = useMemo(() => {
    const map = new Map<string, AndroidFileEntry[]>();
    LAYER_ORDER.forEach((l) => map.set(l, []));
    filteredFiles.forEach((f) => {
      const list = map.get(f.layer) || [];
      list.push(f);
      map.set(f.layer, list);
    });
    return Array.from(map.entries()).filter(([, list]) => list.length > 0);
  }, [filteredFiles]);

  const selectedFile = useMemo(
    () => files.find((f) => f.path === selectedPath) || files[0],
    [files, selectedPath]
  );

  const totalLines = useMemo(
    () => files.reduce((acc, item) => acc + item.lines, 0),
    [files]
  );

  const handleCopy = async (path: string, content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      setCopiedPath(path);
      setTimeout(() => setCopiedPath(null), 1800);
    } catch {
      // Clipboard API fallback ignored if restricted
    }
  };

  return (
    <div className={darkMode ? 'dark' : ''}>
      <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 transition-colors">
        {/* Top Bar Contract: 3 zones */}
        <header className="sticky top-0 z-30 bg-white/95 dark:bg-slate-900/95 backdrop-blur-md border-b border-slate-200 dark:border-slate-800">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-8">
            <a
              href="#top"
              onClick={(e) => {
                e.preventDefault();
                setActiveTab('explorer');
              }}
              className="text-xl font-bold tracking-tight text-emerald-800 dark:text-emerald-400 whitespace-nowrap shrink-0 font-display"
            >
              تواصل
            </a>

            <nav className="hidden md:flex items-center gap-6 text-sm font-medium">
              <button
                type="button"
                onClick={() => setActiveTab('explorer')}
                className={`py-1 whitespace-nowrap shrink-0 transition-colors border-b-2 ${
                  activeTab === 'explorer'
                    ? 'border-emerald-700 text-emerald-800 dark:text-emerald-400 dark:border-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100'
                }`}
              >
                ملفات المشروع
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('preview')}
                className={`py-1 whitespace-nowrap shrink-0 transition-colors border-b-2 ${
                  activeTab === 'preview'
                    ? 'border-emerald-700 text-emerald-800 dark:text-emerald-400 dark:border-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100'
                }`}
              >
                معاينة الواجهات
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('security')}
                className={`py-1 whitespace-nowrap shrink-0 transition-colors border-b-2 ${
                  activeTab === 'security'
                    ? 'border-emerald-700 text-emerald-800 dark:text-emerald-400 dark:border-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100'
                }`}
              >
                الأمان وقواعد Firestore
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('setup')}
                className={`py-1 whitespace-nowrap shrink-0 transition-colors border-b-2 ${
                  activeTab === 'setup'
                    ? 'border-emerald-700 text-emerald-800 dark:text-emerald-400 dark:border-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100'
                }`}
              >
                خطوات الربط وAPK
              </button>
            </nav>

            <div className="flex items-center gap-3 shrink-0">
              <button
                type="button"
                onClick={() => setDarkMode((prev) => !prev)}
                className="inline-flex items-center gap-2 px-4 py-2 text-xs font-medium rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors whitespace-nowrap shrink-0 min-h-[40px]"
              >
                {darkMode ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
                <span>{darkMode ? 'الوضع الفاتح' : 'الوضع الداكن'}</span>
              </button>
            </div>
          </div>

          {/* Mobile navigation row */}
          <div className="md:hidden border-t border-slate-200 dark:border-slate-800 px-3 py-2 flex items-center gap-2 overflow-x-auto">
            {(
              [
                ['explorer', 'ملفات المشروع'],
                ['preview', 'الواجهات'],
                ['security', 'الأمان'],
                ['setup', 'البناء وAPK'],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => setActiveTab(key)}
                className={`px-3 py-2 text-xs font-medium rounded-lg whitespace-nowrap shrink-0 min-h-[40px] ${
                  activeTab === key
                    ? 'bg-emerald-800 text-white'
                    : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </header>

        <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
          {/* Summary Header */}
          <section className="mb-8 pb-6 border-b border-slate-200 dark:border-slate-800">
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-slate-50 font-display">
              مشروع تواصل لنظام Android — Kotlin وJetpack Compose وFirebase
            </h1>
            <p className="mt-2 text-sm sm:text-base text-slate-600 dark:text-slate-300 max-w-3xl leading-relaxed">
              بنية مشروع Android متكاملة وقابلة للبناء داخل مجلد{' '}
              <code className="px-1.5 py-0.5 rounded bg-slate-200/70 dark:bg-slate-800 text-xs font-mono">
                android/
              </code>{' '}
              تشمل تسجيل الدخول عبر Google (Credential Manager)، وحجز اسم المستخدم الفريد ذريًا، والمحادثات الثنائية والجماعية، والوسائط والرسائل الصوتية، والحالات (24 ساعة)، ومكالمات WebRTC، وقواعد أمان Firestore وStorage، ودالة Cloud Function لإشعارات FCM.
            </p>
            <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-slate-500 dark:text-slate-400 tabular-nums">
              <span>{files.length} ملفًا مصدريًا</span>
              <span aria-hidden="true">·</span>
              <span>{totalLines.toLocaleString('en-US')} سطرًا برمجيًا</span>
              <span aria-hidden="true">·</span>
              <span>Package: com.tawasul.app</span>
              <span aria-hidden="true">·</span>
              <span>Compile SDK 35 · Min SDK 26</span>
            </div>
          </section>

          {activeTab === 'explorer' && (
            <section className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* File Tree Sidebar */}
              <aside className="lg:col-span-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden">
                <div className="p-4 border-b border-slate-200 dark:border-slate-800">
                  <div className="relative">
                    <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      value={fileQuery}
                      onChange={(e) => setFileQuery(e.target.value)}
                      placeholder="ابحث عن ملف أو طبقة..."
                      className="w-full pr-9 pl-3 py-2 text-sm rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-emerald-700"
                    />
                  </div>
                </div>

                <div className="max-h-[640px] overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
                  {groupedFiles.map(([layer, items]) => (
                    <div key={layer} className="p-3">
                      <div className="flex items-center justify-between px-2 py-1 text-xs font-semibold text-emerald-800 dark:text-emerald-400">
                        <span className="flex items-center gap-1.5">
                          <FolderOpen className="w-3.5 h-3.5" />
                          {layer}
                        </span>
                        <span className="tabular-nums text-slate-400">{items.length}</span>
                      </div>
                      <ul className="mt-1 space-y-0.5">
                        {items.map((item) => {
                          const isSelected = selectedFile?.path === item.path;
                          return (
                            <li key={item.path}>
                              <button
                                type="button"
                                onClick={() => setSelectedPath(item.path)}
                                className={`w-full text-right px-2.5 py-2 rounded-lg text-xs flex items-center justify-between gap-2 transition-colors min-h-[38px] ${
                                  isSelected
                                    ? 'bg-emerald-800 text-white'
                                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                                }`}
                              >
                                <span className="flex items-center gap-2 min-w-0">
                                  <FileCode2 className="w-3.5 h-3.5 shrink-0" />
                                  <span className="truncate font-mono" dir="ltr">
                                    {item.name}
                                  </span>
                                </span>
                                <span
                                  className={`text-[11px] tabular-nums shrink-0 ${
                                    isSelected ? 'text-emerald-100' : 'text-slate-400'
                                  }`}
                                >
                                  {item.lines}
                                </span>
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    </div>
                  ))}
                </div>
              </aside>

              {/* Code Viewer */}
              <div className="lg:col-span-8 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden">
                {selectedFile ? (
                  <>
                    <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {selectedFile.layer}
                        </p>
                        <p
                          className="text-sm font-mono font-medium text-slate-900 dark:text-slate-100 truncate"
                          dir="ltr"
                        >
                          {selectedFile.path}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleCopy(selectedFile.path, selectedFile.content)}
                        className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors shrink-0 min-h-[40px]"
                      >
                        {copiedPath === selectedFile.path ? (
                          <>
                            <Check className="w-3.5 h-3.5 text-emerald-600" />
                            <span>تم النسخ</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span>نسخ الكود</span>
                          </>
                        )}
                      </button>
                    </div>
                    <div className="max-h-[640px] overflow-auto bg-slate-950 text-slate-100 p-4 text-xs leading-6 font-mono" dir="ltr">
                      <pre className="whitespace-pre">{selectedFile.content}</pre>
                    </div>
                  </>
                ) : (
                  <div className="p-8 text-center text-sm text-slate-500">
                    اختر ملفًا من القائمة لعرض محتواه.
                  </div>
                )}
              </div>
            </section>
          )}

          {activeTab === 'preview' && (
            <section className="space-y-6">
              <div className="bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-2xl p-4 flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 text-amber-700 dark:text-amber-400 shrink-0 mt-0.5" />
                <div className="text-sm text-amber-900 dark:text-amber-200">
                  <p className="font-semibold">معاينة تصميمية لواجهات Jetpack Compose (UI Blueprint)</p>
                  <p className="mt-1 text-xs leading-relaxed opacity-90">
                    هذه المعاينة تستعرض بنية الشاشات والهوية البصرية لتطبيق تواصل (أخضر زمردي داكن + RTL كامل + الوضع الفاتح والداكن) ولا تحاكي مراسلة فعلية؛ يتطلب التشغيل الحقيقي ربط مشروع Firebase الخاص بك ووضع ملف google-services.json داخل مجلد android/app/.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
                <div className="lg:col-span-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold font-display">شاشات التطبيق التفاعلية</h2>
                    <button
                      type="button"
                      onClick={() => setPreviewDark((p) => !p)}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800"
                    >
                      {previewDark ? 'مظهر الهاتف: فاتح' : 'مظهر الهاتف: داكن'}
                    </button>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    {(
                      [
                        ['welcome', 'الترحيب (Google)'],
                        ['onboarding', 'إعداد اسم المستخدم'],
                        ['chats', 'المحادثات'],
                        ['chatDetail', 'داخل المحادثة'],
                        ['search', 'البحث وجهات الاتصال'],
                        ['status', 'الحالات (24 ساعة)'],
                        ['groups', 'المجموعات'],
                        ['calls', 'المكالمات (WebRTC)'],
                        ['profile', 'الملف والخصوصية'],
                      ] as const
                    ).map(([key, label]) => (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setMobileScreen(key)}
                        className={`px-3 py-2.5 text-xs font-medium rounded-xl text-right transition-colors min-h-[44px] ${
                          mobileScreen === key
                            ? 'bg-emerald-800 text-white'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700'
                        }`}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Android Phone Preview Frame */}
                <div className="lg:col-span-7 flex justify-center">
                  <div
                    className={`w-full max-w-[380px] h-[680px] rounded-[34px] border-[6px] border-slate-800 dark:border-slate-700 overflow-hidden flex flex-col shadow-xl ${
                      previewDark ? 'bg-[#0B1117] text-slate-100' : 'bg-[#F8FAFC] text-slate-900'
                    }`}
                  >
                    {/* App Top Bar */}
                    <div
                      className={`px-4 py-3.5 border-b flex items-center justify-between ${
                        previewDark
                          ? 'bg-[#131C24] border-slate-800'
                          : 'bg-white border-slate-200'
                      }`}
                    >
                      <span className="font-bold text-base text-emerald-700 dark:text-emerald-400 font-display">
                        تواصل
                      </span>
                      <span className="text-xs opacity-70">
                        {mobileScreen === 'welcome' && 'تسجيل الدخول'}
                        {mobileScreen === 'onboarding' && 'الملف الشخصي الجديد'}
                        {mobileScreen === 'chats' && 'المحادثات'}
                        {mobileScreen === 'chatDetail' && 'غرفة المحادثة'}
                        {mobileScreen === 'search' && 'البحث عن الأشخاص'}
                        {mobileScreen === 'status' && 'الحالات'}
                        {mobileScreen === 'groups' && 'المجموعات'}
                        {mobileScreen === 'calls' && 'المكالمات'}
                        {mobileScreen === 'profile' && 'الإعدادات والخصوصية'}
                      </span>
                    </div>

                    {/* Screen Canvas */}
                    <div className="flex-1 overflow-y-auto p-4">
                      {mobileScreen === 'welcome' && (
                        <div className="h-full flex flex-col justify-between py-6 text-center">
                          <div className="mt-8 space-y-4">
                            <div className="w-16 h-16 mx-auto rounded-2xl bg-emerald-800 text-white flex items-center justify-center">
                              <MessageSquare className="w-8 h-8" />
                            </div>
                            <h3 className="text-xl font-bold font-display">مرحبًا بك في تواصل</h3>
                            <p className="text-xs opacity-75 leading-relaxed px-4">
                              تواصل مع الآخرين مباشرة عبر اسم المستخدم الفريد دون الحاجة لمشاركة رقم هاتفك أو بريدك الإلكتروني.
                            </p>
                          </div>
                          <div className="space-y-3">
                            <div className="w-full py-3 rounded-xl bg-emerald-800 text-white text-sm font-semibold">
                              المتابعة باستخدام Google
                            </div>
                            <p className="text-[11px] opacity-65">
                              يعتمد على Firebase Auth وقواعد Firestore — لا يدّعي تشفيرًا من الطرف إلى الطرف.
                            </p>
                          </div>
                        </div>
                      )}

                      {mobileScreen === 'onboarding' && (
                        <div className="space-y-4 py-4">
                          <div className="w-16 h-16 mx-auto rounded-full bg-emerald-100 text-emerald-900 flex items-center justify-center font-bold text-xl">
                            ت
                          </div>
                          <h3 className="text-center font-bold">إعداد الملف الشخصي</h3>
                          <div className="space-y-3 text-xs">
                            <div className="p-3 rounded-xl border border-emerald-700/30">
                              <div className="opacity-60 mb-1">الاسم الظاهر</div>
                              <div className="font-medium">حقل إدخال الاسم الظاهر</div>
                            </div>
                            <div className="p-3 rounded-xl border border-emerald-700/30">
                              <div className="opacity-60 mb-1">اسم المستخدم الفريد (3-20 حرفًا)</div>
                              <div className="font-mono" dir="ltr">
                                @username_example
                              </div>
                            </div>
                            <p className="text-[11px] text-emerald-700 dark:text-emerald-400">
                              يُحجز عبر Firestore Transaction لضمان عدم التكرار أو التحسس لحالة الأحرف.
                            </p>
                          </div>
                        </div>
                      )}

                      {mobileScreen === 'chats' && (
                        <div className="h-full flex flex-col items-center justify-center text-center px-4 space-y-3">
                          <div className="w-14 h-14 rounded-full bg-emerald-800/10 text-emerald-700 dark:text-emerald-400 flex items-center justify-center">
                            <MessageSquare className="w-7 h-7" />
                          </div>
                          <p className="font-bold text-sm">قائمة المحادثات الحية</p>
                          <p className="text-xs opacity-70 leading-relaxed">
                            عند ربط Firebase، تظهر هنا المحادثات مرتبة حسب آخر رسالة مع عداد الرسائل غير المقروءة وحالة الاتصال عند تفعيلها.
                          </p>
                          <button
                            type="button"
                            onClick={() => setMobileScreen('search')}
                            className="mt-2 px-4 py-2 rounded-xl bg-emerald-800 text-white text-xs font-medium"
                          >
                            الانتقال للبحث عن اسم مستخدم
                          </button>
                        </div>
                      )}

                      {mobileScreen === 'chatDetail' && (
                        <div className="h-full flex flex-col justify-between">
                          <div className="space-y-3">
                            <div className="flex items-center justify-between text-xs pb-2 border-b border-slate-200 dark:border-slate-800">
                              <span className="flex items-center gap-1 font-semibold">
                                <ArrowRight className="w-4 h-4" />
                                شاشة المحادثة
                              </span>
                              <span className="flex items-center gap-3 text-emerald-700 dark:text-emerald-400">
                                <Phone className="w-4 h-4" />
                                <Video className="w-4 h-4" />
                              </span>
                            </div>
                            <div className="p-3 rounded-xl border border-dashed border-emerald-700/40 text-xs leading-relaxed opacity-80">
                              تدعم الشاشة: فقاعات RTL، تحميل الصفحات الأقدم (Pagination)، إعادة الإرسال عند انقطاع الإنترنت، إرسال الصور والفيديو والرسائل الصوتية، وخيار حذف الرسالة من واجهة المستخدم فقط.
                            </div>
                          </div>
                          <div className="flex items-center gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
                            <ImageIcon className="w-5 h-5 opacity-70" />
                            <div className="flex-1 px-3 py-2 rounded-full border border-slate-300 dark:border-slate-700 text-xs opacity-60">
                              اكتب رسالة...
                            </div>
                            <Mic className="w-5 h-5 text-emerald-700" />
                            <Send className="w-5 h-5 text-emerald-700" />
                          </div>
                        </div>
                      )}

                      {mobileScreen === 'search' && (
                        <div className="space-y-4">
                          <div className="px-3 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 flex items-center gap-2 text-xs opacity-75">
                            <Search className="w-4 h-4" />
                            <span>ابحث عن اسم المستخدم</span>
                          </div>
                          <p className="text-xs opacity-75 leading-relaxed">
                            يبحث الاستعلام في حقل usernameLower المعياري ويعرض الصورة والاسم الظاهر واسم المستخدم فقط مع زر &quot;مراسلة&quot; و&quot;حظر&quot;، دون كشف البريد الإلكتروني.
                          </p>
                        </div>
                      )}

                      {mobileScreen === 'status' && (
                        <div className="space-y-4 text-center py-8">
                          <CircleDot className="w-10 h-10 mx-auto text-emerald-700 dark:text-emerald-400" />
                          <h3 className="font-bold text-sm">الحالات (24 ساعة)</h3>
                          <p className="text-xs opacity-75 leading-relaxed">
                            تدعم نشر الحالات النصية أو الصور أو الفيديو مع انتهاء صلاحية تلقائي بعد 24 ساعة عبر الحقل expiresAt في Firestore.
                          </p>
                        </div>
                      )}

                      {mobileScreen === 'groups' && (
                        <div className="space-y-4 text-center py-8">
                          <Users className="w-10 h-10 mx-auto text-emerald-700 dark:text-emerald-400" />
                          <h3 className="font-bold text-sm">المجموعات</h3>
                          <p className="text-xs opacity-75 leading-relaxed">
                            إنشاء مجموعات حتى 50 عضوًا وإضافتهم عبر اسم المستخدم مع صلاحيات ADMIN وMEMBER داخل المجموعة الفرعية members.
                          </p>
                        </div>
                      )}

                      {mobileScreen === 'calls' && (
                        <div className="space-y-4 text-center py-8">
                          <Phone className="w-10 h-10 mx-auto text-emerald-700 dark:text-emerald-400" />
                          <h3 className="font-bold text-sm">مكالمات WebRTC الصوتية والمرئية</h3>
                          <p className="text-xs opacity-75 leading-relaxed">
                            تعتمد على stream-webrtc-android وتتبادل إشارات SDP وICE Candidates عبر مجموعة calls المحمية في Firestore.
                          </p>
                        </div>
                      )}

                      {mobileScreen === 'profile' && (
                        <div className="space-y-3 text-xs">
                          <h3 className="font-bold text-sm">الملف الشخصي والخصوصية</h3>
                          <div className="p-3 rounded-xl border border-slate-200 dark:border-slate-800 space-y-2">
                            <p>• تعديل الاسم الظاهر والنبذة والصورة الشخصية</p>
                            <p>• تغيير اسم المستخدم ذريًا دون تكرار</p>
                            <p>• إخفاء حالة الاتصال وإشعارات القراءة</p>
                            <p>• إدارة قائمة المستخدمين المحظورين</p>
                            <p>• تسجيل الخروج أو حذف الحساب مع تحرير اسم المستخدم</p>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Bottom Tab Bar */}
                    <div
                      className={`border-t px-1 py-2 grid grid-cols-6 text-[10px] text-center ${
                        previewDark
                          ? 'bg-[#131C24] border-slate-800'
                          : 'bg-white border-slate-200'
                      }`}
                    >
                      {(
                        [
                          ['chats', 'المحادثات', MessageSquare],
                          ['search', 'البحث', Search],
                          ['status', 'الحالات', CircleDot],
                          ['groups', 'المجموعات', Users],
                          ['calls', 'المكالمات', Phone],
                          ['profile', 'حسابي', Settings],
                        ] as const
                      ).map(([key, label, IconComp]) => (
                        <button
                          key={key}
                          type="button"
                          onClick={() => setMobileScreen(key)}
                          className={`flex flex-col items-center gap-1 py-1 ${
                            mobileScreen === key
                              ? 'text-emerald-700 dark:text-emerald-400 font-bold'
                              : 'opacity-65'
                          }`}
                        >
                          <IconComp className="w-4 h-4" />
                          <span>{label}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </section>
          )}

          {activeTab === 'security' && (
            <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {SECURITY_PILLARS.map((pillar) => (
                <article
                  key={pillar.title}
                  className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-6 space-y-2"
                >
                  <div className="flex items-center gap-2 text-emerald-800 dark:text-emerald-400 font-bold">
                    <ShieldCheck className="w-5 h-5 shrink-0" />
                    <h2 className="text-base font-display">{pillar.title}</h2>
                  </div>
                  <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
                    {pillar.detail}
                  </p>
                </article>
              ))}
            </section>
          )}

          {activeTab === 'setup' && (
            <section className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {SETUP_STEPS.map((item) => (
                  <article
                    key={item.step}
                    className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-6 space-y-3"
                  >
                    <div className="flex items-center gap-2 text-emerald-800 dark:text-emerald-400 font-bold">
                      <Wrench className="w-4 h-4 shrink-0" />
                      <h2 className="text-base font-display">
                        {item.step}. {item.title}
                      </h2>
                    </div>
                    <ul className="space-y-2 text-sm text-slate-600 dark:text-slate-300">
                      {item.items.map((line, i) => (
                        <li key={i} className="flex items-start gap-2 leading-relaxed">
                          <CheckCircle2 className="w-4 h-4 text-emerald-700 dark:text-emerald-400 shrink-0 mt-1" />
                          <span>{line}</span>
                        </li>
                      ))}
                    </ul>
                  </article>
                ))}
              </div>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}
