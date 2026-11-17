import React, { useEffect, useMemo, useRef, useState } from 'react';
import { onAuthStateChanged, type User as FirebaseUser } from 'firebase/auth';
import type { QueryDocumentSnapshot } from 'firebase/firestore';
import {
  ArrowRight,
  Ban,
  Check,
  CheckCheck,
  CircleDot,
  Code2,
  Image as ImageIcon,
  LogOut,
  MessageSquare,
  Mic,
  Moon,
  Phone,
  RefreshCw,
  Search,
  Send,
  Settings,
  ShieldAlert,
  Square,
  Sun,
  Trash2,
  UserPlus,
  Users,
  Video,
  WifiOff,
} from 'lucide-react';
import { auth } from './firebase';
import { useOnlineStatus } from './hooks/useOnlineStatus';
import { PWAInstallButton } from './components/PWAInstallButton';
import { AndroidProjectModal } from './components/AndroidProjectModal';
import { WebRtcCallOverlay } from './components/WebRtcCallOverlay';
import {
  blockUser,
  completeProfileSetup,
  compressImageFileToDataUrl,
  createGroupChat,
  deleteAccountAndOwnedData,
  deleteMessageForCurrentUser,
  getOrCreateDirectChat,
  getUserProfile,
  isUsernameAvailable,
  loadOlderMessages,
  markChatAsRead,
  markStatusViewed,
  publishStatus,
  searchUsersByUsername,
  sendMessage,
  signInWithGooglePopup,
  signOutAccount,
  subscribeActiveStatuses,
  subscribeBlockedUsers,
  subscribeCallHistory,
  subscribeIncomingCall,
  subscribeLatestMessages,
  subscribeUserChats,
  subscribeUserProfile,
  unblockUser,
  updateCallStatus,
  updatePresence,
  updatePrivacySettings,
  updateProfileDetails,
} from './services/tawasulService';
import {
  MAX_BIO_LENGTH,
  MAX_STATUS_TEXT_LENGTH,
  type BlockedUser,
  type CallSession,
  type CallType,
  type Chat,
  type GroupPrivacySetting,
  type Message,
  type StatusItem,
  type UserPublicProfile,
} from './types/tawasul';

type MainTab = 'chats' | 'search' | 'status' | 'groups' | 'calls' | 'profile';

function formatArabicTime(ts: { toDate?: () => Date } | null | undefined): string {
  if (!ts || typeof ts.toDate !== 'function') return '';
  return new Intl.DateTimeFormat('ar-EG', {
    hour: 'numeric',
    minute: '2-digit',
  }).format(ts.toDate());
}

const Avatar: React.FC<{
  name: string;
  url?: string;
  size?: 'sm' | 'md' | 'lg';
  isOnline?: boolean;
}> = ({ name, url, size = 'md', isOnline }) => {
  const dim =
    size === 'sm' ? 'w-9 h-9 text-sm' : size === 'lg' ? 'w-16 h-16 text-xl' : 'w-11 h-11 text-base';
  return (
    <div className={`relative shrink-0 ${dim}`}>
      {url ? (
        <img
          src={url}
          alt={name}
          referrerPolicy="no-referrer"
          className="w-full h-full rounded-full object-cover bg-emerald-100 dark:bg-emerald-900"
        />
      ) : (
        <div className="w-full h-full rounded-full bg-emerald-100 dark:bg-emerald-900/60 text-emerald-900 dark:text-emerald-200 font-bold flex items-center justify-center">
          {(name || 'ت').trim().charAt(0).toUpperCase()}
        </div>
      )}
      {isOnline && (
        <span
          aria-label="متصل الآن"
          className="absolute bottom-0 left-0 w-3 h-3 rounded-full bg-emerald-500 ring-2 ring-white dark:ring-slate-900"
        />
      )}
    </div>
  );
};

export default function App() {
  const isOnline = useOnlineStatus();
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('tawasul_theme') === 'dark');
  const [authReady, setAuthReady] = useState(false);
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  const [profile, setProfile] = useState<UserPublicProfile | null>(null);
  const [profileChecked, setProfileChecked] = useState(false);
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  // Onboarding state
  const [setupDisplayName, setSetupDisplayName] = useState('');
  const [setupUsername, setSetupUsername] = useState('');
  const [setupBio, setSetupBio] = useState('');
  const [usernameHint, setUsernameHint] = useState<string | null>(null);
  const [usernameValid, setUsernameValid] = useState(false);

  // Main hub state
  const [activeTab, setActiveTab] = useState<MainTab>('chats');
  const [chats, setChats] = useState<Chat[]>([]);
  const [peerProfiles, setPeerProfiles] = useState<Record<string, UserPublicProfile>>({});
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>([]);
  const [statuses, setStatuses] = useState<StatusItem[]>([]);
  const [callHistory, setCallHistory] = useState<CallSession[]>([]);
  const [incomingCall, setIncomingCall] = useState<CallSession | null>(null);
  const [activeCall, setActiveCall] = useState<{
    peer?: UserPublicProfile | null;
    incoming?: CallSession | null;
    type: CallType;
  } | null>(null);

  // Active Chat state
  const [selectedChat, setSelectedChat] = useState<Chat | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [olderMessages, setOlderMessages] = useState<Message[]>([]);
  const [pendingMessages, setPendingMessages] = useState<Message[]>([]);
  const [oldestCursor, setOldestCursor] = useState<QueryDocumentSnapshot | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasMoreOlder, setHasMoreOlder] = useState(true);
  const [draftText, setDraftText] = useState('');
  const [isRecordingAudio, setIsRecordingAudio] = useState(false);
  const [deleteTargetMsg, setDeleteTargetMsg] = useState<Message | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);

  // Search & Groups & Status & Profile UI state
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<UserPublicProfile[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [groupTitle, setGroupTitle] = useState('');
  const [groupMemberQuery, setGroupMemberQuery] = useState('');
  const [groupCandidates, setGroupCandidates] = useState<UserPublicProfile[]>([]);
  const [selectedGroupUids, setSelectedGroupUids] = useState<string[]>([]);
  const [statusText, setStatusText] = useState('');
  const [activeStatusPreview, setActiveStatusPreview] = useState<StatusItem | null>(null);

  // Profile edit state
  const [editName, setEditName] = useState('');
  const [editUsername, setEditUsername] = useState('');
  const [editBio, setEditBio] = useState('');
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [showAndroidModal, setShowAndroidModal] = useState(false);
  const [confirmDeleteAccount, setConfirmDeleteAccount] = useState(false);

  const imageInputRef = useRef<HTMLInputElement | null>(null);
  const statusImageInputRef = useRef<HTMLInputElement | null>(null);
  const avatarInputRef = useRef<HTMLInputElement | null>(null);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 3200);
  };

  useEffect(() => {
    localStorage.setItem('tawasul_theme', darkMode ? 'dark' : 'light');
  }, [darkMode]);

  // Auth listener
  useEffect(() => {
    return onAuthStateChanged(auth, async (user) => {
      setFirebaseUser(user);
      setAuthReady(true);
      if (!user) {
        setProfile(null);
        setProfileChecked(true);
        setSelectedChat(null);
        return;
      }
      setSetupDisplayName(user.displayName || '');
      const existing = await getUserProfile(user.uid);
      setProfile(existing);
      setProfileChecked(true);
      if (existing?.profileCompleted) {
        updatePresence(user.uid, true);
      }
    });
  }, []);

  // Subscriptions when authenticated and profile completed
  const blockedUidSet = useMemo(
    () => new Set(blockedUsers.map((b) => b.blockedUid)),
    [blockedUsers]
  );

  useEffect(() => {
    if (!firebaseUser || !profile?.profileCompleted) return;
    const uid = firebaseUser.uid;
    const unsubProfile = subscribeUserProfile(uid, (p) => {
      if (p) {
        setProfile(p);
        setEditName(p.displayName);
        setEditUsername(p.username);
        setEditBio(p.bio);
      }
    });
    const unsubBlocked = subscribeBlockedUsers(uid, setBlockedUsers);
    const unsubChats = subscribeUserChats(uid, setChats);
    const unsubIncoming = subscribeIncomingCall(uid, setIncomingCall);
    const unsubCalls = subscribeCallHistory(uid, setCallHistory);

    const handleVisibility = () => {
      updatePresence(uid, document.visibilityState === 'visible');
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      unsubProfile();
      unsubBlocked();
      unsubChats();
      unsubIncoming();
      unsubCalls();
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [firebaseUser, profile?.profileCompleted]);

  useEffect(() => {
    if (!firebaseUser || !profile?.profileCompleted) return;
    return subscribeActiveStatuses(blockedUidSet, setStatuses);
  }, [firebaseUser, profile?.profileCompleted, blockedUidSet]);

  // Fetch missing peer profiles for direct chats
  useEffect(() => {
    if (!firebaseUser) return;
    const missing = new Set<string>();
    chats.forEach((c) => {
      if (c.type === 'DIRECT') {
        c.participantIds.forEach((id) => {
          if (id !== firebaseUser.uid && !peerProfiles[id]) missing.add(id);
        });
      }
    });
    missing.forEach(async (peerUid) => {
      const p = await getUserProfile(peerUid);
      if (p) {
        setPeerProfiles((prev) => ({ ...prev, [peerUid]: p }));
      }
    });
  }, [chats, firebaseUser, peerProfiles]);

  // Active chat messages subscription
  useEffect(() => {
    if (!selectedChat || !firebaseUser) return;
    setOlderMessages([]);
    setPendingMessages([]);
    setOldestCursor(null);
    setHasMoreOlder(true);

    return subscribeLatestMessages(selectedChat.id, (latest, oldestDoc) => {
      setMessages(latest);
      setOldestCursor((prev) => prev || oldestDoc);
      markChatAsRead(
        selectedChat.id,
        firebaseUser.uid,
        profile?.showReadReceipts ?? true,
        latest
      );
    });
  }, [selectedChat?.id, firebaseUser?.uid]);

  const handleGoogleLogin = async () => {
    setAuthBusy(true);
    setAuthError(null);
    try {
      await signInWithGooglePopup();
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : 'تعذر تسجيل الدخول عبر Google.');
    } finally {
      setAuthBusy(false);
    }
  };

  const handleUsernameCheck = async (val: string) => {
    setSetupUsername(val);
    if (!val.trim()) {
      setUsernameHint(null);
      setUsernameValid(false);
      return;
    }
    const res = await isUsernameAvailable(val);
    setUsernameValid(res.available);
    setUsernameHint(res.hint);
  };

  const handleCompleteOnboarding = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser) return;
    setAuthBusy(true);
    setAuthError(null);
    try {
      await completeProfileSetup({
        uid: firebaseUser.uid,
        email: firebaseUser.email || '',
        displayName: setupDisplayName,
        rawUsername: setupUsername,
        bio: setupBio,
        avatarUrl: firebaseUser.photoURL || '',
      });
      const saved = await getUserProfile(firebaseUser.uid);
      setProfile(saved);
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : 'تعذر حفظ الملف الشخصي.');
    } finally {
      setAuthBusy(false);
    }
  };

  const handleSearch = async (val: string) => {
    setSearchQuery(val);
    if (!firebaseUser || !val.trim()) {
      setSearchResults([]);
      return;
    }
    setIsSearching(true);
    try {
      const results = await searchUsersByUsername(val, firebaseUser.uid, blockedUidSet);
      setSearchResults(results);
    } finally {
      setIsSearching(false);
    }
  };

  const handleOpenDirectChatWith = async (target: UserPublicProfile) => {
    if (!firebaseUser) return;
    setPeerProfiles((prev) => ({ ...prev, [target.uid]: target }));
    const chat = await getOrCreateDirectChat(firebaseUser.uid, target.uid);
    setSelectedChat(chat);
    setActiveTab('chats');
  };

  const dispatchMessage = async (msgDraft: Message) => {
    if (!selectedChat) return;
    if (!isOnline) {
      setPendingMessages((prev) =>
        prev.map((m) =>
          m.localClientId === msgDraft.localClientId ? { ...m, deliveryStatus: 'FAILED' } : m
        )
      );
      showToast('لا يوجد اتصال بالإنترنت. اضغط على الرسالة لإعادة المحاولة.');
      return;
    }

    try {
      await sendMessage({
        chatId: selectedChat.id,
        senderId: msgDraft.senderId,
        participantIds: selectedChat.participantIds,
        type: msgDraft.type,
        text: msgDraft.text,
        mediaUrl: msgDraft.mediaUrl,
        mediaDurationSec: msgDraft.mediaDurationSec,
        localClientId: msgDraft.localClientId,
      });
      setPendingMessages((prev) =>
        prev.filter((m) => m.localClientId !== msgDraft.localClientId)
      );
    } catch {
      setPendingMessages((prev) =>
        prev.map((m) =>
          m.localClientId === msgDraft.localClientId ? { ...m, deliveryStatus: 'FAILED' } : m
        )
      );
    }
  };

  const handleSendText = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser || !selectedChat || !draftText.trim()) return;
    const localId = crypto.randomUUID();
    const optimistic: Message = {
      id: `local_${localId}`,
      chatId: selectedChat.id,
      senderId: firebaseUser.uid,
      type: 'TEXT',
      text: draftText.trim(),
      mediaUrl: '',
      mediaDurationSec: 0,
      deliveryStatus: 'SENDING',
      readBy: {},
      deletedForUids: [],
      localClientId: localId,
      createdAt: null,
    };
    setDraftText('');
    setPendingMessages((prev) => [...prev, optimistic]);
    await dispatchMessage(optimistic);
  };

  const handleRetryFailed = async (failed: Message) => {
    const retrying: Message = { ...failed, deliveryStatus: 'SENDING' };
    setPendingMessages((prev) =>
      prev.map((m) => (m.localClientId === failed.localClientId ? retrying : m))
    );
    await dispatchMessage(retrying);
  };

  const handleChatImageSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !firebaseUser || !selectedChat) return;
    try {
      const dataUrl = await compressImageFileToDataUrl(file, 720);
      const localId = crypto.randomUUID();
      await sendMessage({
        chatId: selectedChat.id,
        senderId: firebaseUser.uid,
        participantIds: selectedChat.participantIds,
        type: 'IMAGE',
        mediaUrl: dataUrl,
        localClientId: localId,
      });
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'تعذر إرسال الصورة.');
    }
  };

  const handleToggleVoiceRecord = async () => {
    if (!firebaseUser || !selectedChat) return;
    if (isRecordingAudio && mediaRecorderRef.current) {
      mediaRecorderRef.current.stop();
      setIsRecordingAudio(false);
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      audioChunksRef.current = [];
      const startedAt = Date.now();

      recorder.ondataavailable = (ev) => {
        if (ev.data.size > 0) audioChunksRef.current.push(ev.data);
      };
      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
        const durationSec = Math.max(1, Math.round((Date.now() - startedAt) / 1000));
        if (blob.size > 650_000) {
          showToast('الرسالة الصوتية طويلة جدًا، يرجى تسجيل رسالة أقصر.');
          return;
        }
        const reader = new FileReader();
        reader.onloadend = async () => {
          const base64 = typeof reader.result === 'string' ? reader.result : '';
          if (!base64) return;
          await sendMessage({
            chatId: selectedChat.id,
            senderId: firebaseUser.uid,
            participantIds: selectedChat.participantIds,
            type: 'AUDIO',
            mediaUrl: base64,
            mediaDurationSec: durationSec,
            localClientId: crypto.randomUUID(),
          });
        };
        reader.readAsDataURL(blob);
      };

      mediaRecorderRef.current = recorder;
      recorder.start();
      setIsRecordingAudio(true);
    } catch {
      showToast('تعذر الوصول إلى الميكروفون.');
    }
  };

  const handleLoadOlder = async () => {
    if (!selectedChat || !oldestCursor || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const res = await loadOlderMessages(selectedChat.id, oldestCursor);
      setOlderMessages((prev) => [...res.messages, ...prev]);
      setOldestCursor(res.nextOldest);
      if (res.messages.length === 0) setHasMoreOlder(false);
    } finally {
      setLoadingOlder(false);
    }
  };

  const combinedMessages = useMemo(() => {
    if (!firebaseUser) return [];
    const map = new Map<string, Message>();
    [...olderMessages, ...messages, ...pendingMessages].forEach((m) => {
      if (!m.deletedForUids?.includes(firebaseUser.uid)) {
        map.set(m.id, m);
      }
    });
    return Array.from(map.values());
  }, [olderMessages, messages, pendingMessages, firebaseUser]);

  const wrapperClass = darkMode ? 'dark' : '';

  if (!authReady || (firebaseUser && !profileChecked)) {
    return (
      <div className={wrapperClass}>
        <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100">
          <div className="text-center space-y-3">
            <div className="w-12 h-12 mx-auto rounded-2xl bg-emerald-800 text-white flex items-center justify-center font-bold text-xl">
              ت
            </div>
            <p className="text-sm text-slate-500">جاري تحميل تواصل...</p>
          </div>
        </div>
      </div>
    );
  }

  // Screen 1: Welcome / Google Sign-In
  if (!firebaseUser) {
    return (
      <div className={wrapperClass}>
        <div className="min-h-screen flex flex-col justify-between bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 p-6">
          <div className="max-w-md w-full mx-auto flex items-center justify-between">
            <span className="text-lg font-bold text-emerald-800 dark:text-emerald-400 font-display">
              تواصل
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setDarkMode((p) => !p)}
                className="p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900"
                aria-label="تبديل المظهر"
              >
                {darkMode ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
              </button>
              <button
                type="button"
                onClick={() => setShowAndroidModal(true)}
                className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900"
              >
                <Code2 className="w-4 h-4" />
                <span>كود Android</span>
              </button>
            </div>
          </div>

          <div className="max-w-md w-full mx-auto text-center space-y-5 my-auto">
            <div className="w-20 h-20 mx-auto rounded-3xl bg-emerald-800 text-white flex items-center justify-center shadow-lg">
              <MessageSquare className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold font-display">مرحبًا بك في تواصل</h1>
            <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
              تطبيق مراسلة فورية ومكالمات صوتية ومرئية عبر اسم المستخدم الفريد، دون الحاجة لمشاركة رقم هاتفك أو بريدك الإلكتروني.
            </p>

            {authError && (
              <div className="p-3 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 text-xs text-red-700 dark:text-red-300">
                {authError}
              </div>
            )}

            <div className="space-y-3 pt-2">
              <button
                type="button"
                onClick={handleGoogleLogin}
                disabled={authBusy}
                className="w-full py-3.5 px-5 rounded-2xl bg-emerald-800 hover:bg-emerald-700 text-white font-semibold text-sm transition-colors min-h-[48px] disabled:opacity-60"
              >
                {authBusy ? 'جاري تسجيل الدخول...' : 'المتابعة باستخدام Google'}
              </button>

              <PWAInstallButton variant="primary" />
            </div>

            <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
              يعتمد التطبيق على Firebase Authentication وقواعد Firestore الآمنة لحماية الوصول، ولا يوفّر تشفيرًا من الطرف إلى الطرف (E2EE) افتراضيًا.
            </p>
          </div>

          <div className="text-center text-xs text-slate-400">تواصل · تطبيق مراسلة عربية</div>
        </div>
        {showAndroidModal && <AndroidProjectModal onClose={() => setShowAndroidModal(false)} />}
      </div>
    );
  }

  // Screen 2: Profile Setup for new users
  if (!profile?.profileCompleted) {
    return (
      <div className={wrapperClass}>
        <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 p-6">
          <form
            onSubmit={handleCompleteOnboarding}
            className="max-w-md w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 space-y-4"
          >
            <div className="flex flex-col items-center text-center space-y-2">
              <Avatar name={setupDisplayName || 'ت'} url={firebaseUser.photoURL || ''} size="lg" />
              <h1 className="text-xl font-bold font-display">إعداد ملفك الشخصي</h1>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                اختر اسمًا ظاهرًا واسم مستخدم فريدًا ليعثر عليك الآخرون دون كشف بريدك الإلكتروني.
              </p>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium">الاسم الظاهر</label>
              <input
                type="text"
                required
                minLength={2}
                maxLength={50}
                value={setupDisplayName}
                onChange={(e) => setSetupDisplayName(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium">اسم المستخدم الفريد (3-20 حرفًا إنجليزيًا)</label>
              <div className="relative">
                <input
                  type="text"
                  required
                  dir="ltr"
                  value={setupUsername}
                  onChange={(e) => handleUsernameCheck(e.target.value)}
                  placeholder="ahmad_99"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm font-mono"
                />
              </div>
              {usernameHint && (
                <p
                  className={`text-xs ${
                    usernameValid ? 'text-emerald-700 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'
                  }`}
                >
                  {usernameHint}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium">نبذة شخصية (اختياري)</label>
              <textarea
                maxLength={MAX_BIO_LENGTH}
                value={setupBio}
                onChange={(e) => setSetupBio(e.target.value)}
                rows={2}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
              />
            </div>

            {authError && (
              <p className="text-xs text-red-600 dark:text-red-400">{authError}</p>
            )}

            <button
              type="submit"
              disabled={authBusy || !usernameValid || setupDisplayName.trim().length < 2}
              className="w-full py-3 rounded-xl bg-emerald-800 hover:bg-emerald-700 text-white text-sm font-semibold transition-colors disabled:opacity-50 min-h-[44px]"
            >
              {authBusy ? 'جاري الإنشاء...' : 'إنشاء الحساب والبدء'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  const activePeerUid =
    selectedChat?.type === 'DIRECT'
      ? selectedChat.participantIds.find((id) => id !== firebaseUser.uid)
      : undefined;
  const activePeer = activePeerUid ? peerProfiles[activePeerUid] : undefined;

  return (
    <div className={wrapperClass}>
      <div className="h-screen flex flex-col bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 overflow-hidden">
        {/* Top Bar Contract (3 zones) */}
        <header className="h-14 shrink-0 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 px-4 flex items-center justify-between gap-6">
          <span className="text-lg font-bold text-emerald-800 dark:text-emerald-400 font-display whitespace-nowrap shrink-0">
            تواصل
          </span>

          <nav className="hidden md:flex items-center gap-5 text-sm font-medium">
            {(
              [
                ['chats', 'المحادثات'],
                ['search', 'البحث'],
                ['status', 'الحالات'],
                ['groups', 'المجموعات'],
                ['calls', 'المكالمات'],
                ['profile', 'حسابي'],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => {
                  setActiveTab(key);
                  if (key !== 'chats') setSelectedChat(null);
                }}
                className={`py-1 whitespace-nowrap shrink-0 border-b-2 transition-colors ${
                  activeTab === key
                    ? 'border-emerald-700 text-emerald-800 dark:text-emerald-400 dark:border-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100'
                }`}
              >
                {label}
              </button>
            ))}
          </nav>

          <div className="flex items-center gap-2 shrink-0">
            <PWAInstallButton variant="compact" />
          </div>
        </header>

        {!isOnline && (
          <div className="bg-red-50 dark:bg-red-950/60 border-b border-red-200 dark:border-red-900 px-4 py-2 text-xs text-red-700 dark:text-red-300 flex items-center gap-2">
            <WifiOff className="w-4 h-4 shrink-0" />
            <span>لا يوجد اتصال بالإنترنت — قد يتعذر إرسال الرسائل الجديدة حتى عودة الشبكة.</span>
          </div>
        )}

        {toastMsg && (
          <div className="fixed top-16 left-1/2 -translate-x-1/2 z-50 bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 px-4 py-2 rounded-xl text-xs font-medium shadow-lg">
            {toastMsg}
          </div>
        )}

        {/* Incoming Call Banner */}
        {incomingCall && !activeCall && (
          <div className="bg-emerald-900 text-white px-4 py-3 flex items-center justify-between gap-4">
            <div className="text-sm font-medium">
              مكالمة {incomingCall.type === 'VIDEO' ? 'مرئية' : 'صوتية'} واردة من{' '}
              <strong>{incomingCall.callerName}</strong>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => updateCallStatus(incomingCall.id, 'DECLINED')}
                className="px-3 py-1.5 rounded-lg bg-red-600 text-xs font-semibold"
              >
                رفض
              </button>
              <button
                type="button"
                onClick={() =>
                  setActiveCall({
                    incoming: incomingCall,
                    type: incomingCall.type,
                  })
                }
                className="px-3 py-1.5 rounded-lg bg-emerald-500 text-slate-950 text-xs font-semibold"
              >
                رد
              </button>
            </div>
          </div>
        )}

        {/* Main Workspace */}
        <div className="flex-1 overflow-hidden flex max-w-7xl w-full mx-auto">
          {/* Left/Right List Column (hidden on mobile when a chat is open) */}
          <div
            className={`w-full md:w-96 shrink-0 border-l border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col overflow-hidden ${
              selectedChat && activeTab === 'chats' ? 'hidden md:flex' : 'flex'
            }`}
          >
            {activeTab === 'chats' && (
              <div className="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
                {chats.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center p-8 text-center space-y-3">
                    <div className="w-14 h-14 rounded-full bg-emerald-100 dark:bg-emerald-900/40 text-emerald-800 dark:text-emerald-300 flex items-center justify-center">
                      <MessageSquare className="w-6 h-6" />
                    </div>
                    <h2 className="font-bold text-base">لا توجد محادثات بعد</h2>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      انتقل إلى تبويب البحث للعثور على أصدقائك عبر اسم المستخدم وبدء المحادثة.
                    </p>
                    <button
                      type="button"
                      onClick={() => setActiveTab('search')}
                      className="px-4 py-2 rounded-xl bg-emerald-800 text-white text-xs font-medium"
                    >
                      ابحث عن اسم مستخدم
                    </button>
                  </div>
                ) : (
                  chats.map((chat) => {
                    const peerUid =
                      chat.type === 'DIRECT'
                        ? chat.participantIds.find((id) => id !== firebaseUser.uid)
                        : undefined;
                    const peer = peerUid ? peerProfiles[peerUid] : undefined;
                    const name =
                      chat.type === 'GROUP' ? chat.title : peer?.displayName || 'مستخدم';
                    const unread = chat.unreadCounts?.[firebaseUser.uid] || 0;
                    const isSelected = selectedChat?.id === chat.id;

                    return (
                      <button
                        key={chat.id}
                        type="button"
                        onClick={() => setSelectedChat(chat)}
                        className={`w-full text-right px-4 py-3 flex items-center gap-3 transition-colors ${
                          isSelected
                            ? 'bg-emerald-50 dark:bg-emerald-950/40'
                            : 'hover:bg-slate-50 dark:hover:bg-slate-800/50'
                        }`}
                      >
                        <Avatar
                          name={name}
                          url={chat.type === 'GROUP' ? chat.avatarUrl : peer?.avatarUrl}
                          isOnline={peer?.showOnlineStatus && peer?.isOnline}
                        />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-semibold text-sm truncate">{name}</span>
                            <span className="text-[11px] text-slate-400 tabular-nums shrink-0">
                              {formatArabicTime(chat.lastMessageAt)}
                            </span>
                          </div>
                          <div className="flex items-center justify-between gap-2 mt-1">
                            <span className="text-xs text-slate-500 dark:text-slate-400 truncate">
                              {chat.lastMessageText || (peer ? `@${peer.username}` : '')}
                            </span>
                            {unread > 0 && (
                              <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-emerald-800 text-white text-[11px] font-bold flex items-center justify-center tabular-nums">
                                {unread}
                              </span>
                            )}
                          </div>
                        </div>
                      </button>
                    );
                  })
                )}
              </div>
            )}

            {activeTab === 'search' && (
              <div className="flex-1 flex flex-col overflow-hidden p-4 space-y-4">
                <div className="relative">
                  <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => handleSearch(e.target.value)}
                    placeholder="ابحث عن اسم المستخدم"
                    className="w-full pr-9 pl-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
                  />
                </div>

                <div className="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
                  {isSearching ? (
                    <p className="text-center text-xs text-slate-500 py-8">جاري البحث...</p>
                  ) : !searchQuery.trim() ? (
                    <p className="text-center text-xs text-slate-500 py-10 leading-relaxed">
                      أدخل اسم المستخدم الإنجليزي للعثور على أي شخص ومراسلتهم مباشرة دون معرفة رقم هاتفه.
                    </p>
                  ) : searchResults.length === 0 ? (
                    <p className="text-center text-xs text-slate-500 py-10">
                      لا يوجد مستخدم مطابق لهذا الاسم.
                    </p>
                  ) : (
                    searchResults.map((u) => (
                      <div key={u.uid} className="py-3 flex items-center gap-3">
                        <Avatar name={u.displayName} url={u.avatarUrl} />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold truncate">{u.displayName}</p>
                          <p className="text-xs text-slate-500 font-mono truncate" dir="ltr">
                            @{u.username}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={async () => {
                            await blockUser(firebaseUser.uid, u);
                            setSearchResults((prev) => prev.filter((item) => item.uid !== u.uid));
                            showToast(`تم حظر @${u.username}`);
                          }}
                          className="p-2 rounded-lg text-slate-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/30"
                          title="حظر"
                        >
                          <Ban className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleOpenDirectChatWith(u)}
                          className="px-3 py-2 rounded-xl bg-emerald-800 hover:bg-emerald-700 text-white text-xs font-medium shrink-0"
                        >
                          مراسلة
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {activeTab === 'status' && (
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                <div className="space-y-2 border-b border-slate-200 dark:border-slate-800 pb-4">
                  <textarea
                    value={statusText}
                    onChange={(e) => setStatusText(e.target.value.slice(0, MAX_STATUS_TEXT_LENGTH))}
                    placeholder="أضف حالة نصية تختفي بعد 24 ساعة..."
                    rows={2}
                    className="w-full p-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
                  />
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={!statusText.trim()}
                      onClick={async () => {
                        await publishStatus({
                          author: profile,
                          type: 'TEXT',
                          textContent: statusText,
                        });
                        setStatusText('');
                        showToast('تم نشر حالتك لمدة 24 ساعة.');
                      }}
                      className="flex-1 py-2 rounded-xl bg-emerald-800 text-white text-xs font-medium disabled:opacity-50"
                    >
                      نشر حالة نصية
                    </button>
                    <input
                      ref={statusImageInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={async (e) => {
                        const file = e.target.files?.[0];
                        e.target.value = '';
                        if (!file) return;
                        try {
                          const dataUrl = await compressImageFileToDataUrl(file, 640);
                          await publishStatus({
                            author: profile,
                            type: 'IMAGE',
                            mediaUrl: dataUrl,
                          });
                          showToast('تم نشر صورة الحالة لمدة 24 ساعة.');
                        } catch (err) {
                          showToast(err instanceof Error ? err.message : 'تعذر نشر الصورة.');
                        }
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => statusImageInputRef.current?.click()}
                      className="px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-medium"
                    >
                      نشر صورة
                    </button>
                  </div>
                </div>

                <div className="space-y-2">
                  {statuses.length === 0 ? (
                    <p className="text-center text-xs text-slate-500 py-8">
                      لا توجد حالات نشطة خلال آخر 24 ساعة.
                    </p>
                  ) : (
                    statuses.map((st) => (
                      <button
                        key={st.id}
                        type="button"
                        onClick={() => {
                          setActiveStatusPreview(st);
                          markStatusViewed(st.id, firebaseUser.uid);
                        }}
                        className="w-full text-right p-3 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-800/60"
                      >
                        <Avatar name={st.authorName} url={st.authorAvatarUrl} />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold truncate">{st.authorName}</p>
                          <p className="text-xs text-slate-500">
                            @{st.authorUsername} · {formatArabicTime(st.createdAt)}
                          </p>
                        </div>
                        <span className="text-xs text-emerald-700 dark:text-emerald-400">
                          {st.type === 'IMAGE' ? 'صورة' : 'نص'}
                        </span>
                      </button>
                    ))
                  )}
                </div>
              </div>
            )}

            {activeTab === 'groups' && (
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                <div className="space-y-2 border-b border-slate-200 dark:border-slate-800 pb-4">
                  <h2 className="font-bold text-sm flex items-center gap-1.5">
                    <UserPlus className="w-4 h-4 text-emerald-700" />
                    إنشاء مجموعة جديدة
                  </h2>
                  <input
                    type="text"
                    value={groupTitle}
                    onChange={(e) => setGroupTitle(e.target.value)}
                    placeholder="اسم المجموعة"
                    className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
                  />
                  <input
                    type="text"
                    value={groupMemberQuery}
                    onChange={async (e) => {
                      const val = e.target.value;
                      setGroupMemberQuery(val);
                      if (!val.trim()) {
                        setGroupCandidates([]);
                        return;
                      }
                      const res = await searchUsersByUsername(val, firebaseUser.uid, blockedUidSet);
                      setGroupCandidates(res);
                    }}
                    placeholder="ابحث باسم المستخدم لإضافة أعضاء..."
                    className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
                  />
                  {groupCandidates.length > 0 && (
                    <div className="max-h-36 overflow-y-auto border border-slate-200 dark:border-slate-700 rounded-xl divide-y divide-slate-100 dark:divide-slate-800">
                      {groupCandidates.map((cand) => {
                        const checked = selectedGroupUids.includes(cand.uid);
                        return (
                          <label
                            key={cand.uid}
                            className="flex items-center gap-2 px-3 py-2 text-xs cursor-pointer"
                          >
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() =>
                                setSelectedGroupUids((prev) =>
                                  checked ? prev.filter((id) => id !== cand.uid) : [...prev, cand.uid]
                                )
                              }
                            />
                            <span>
                              {cand.displayName} (@{cand.username})
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  )}
                  <button
                    type="button"
                    disabled={groupTitle.trim().length < 2 || selectedGroupUids.length === 0}
                    onClick={async () => {
                      const created = await createGroupChat({
                        creatorUid: firebaseUser.uid,
                        title: groupTitle,
                        memberUids: selectedGroupUids,
                      });
                      setGroupTitle('');
                      setSelectedGroupUids([]);
                      setGroupMemberQuery('');
                      setGroupCandidates([]);
                      setSelectedChat(created);
                      setActiveTab('chats');
                    }}
                    className="w-full py-2.5 rounded-xl bg-emerald-800 text-white text-xs font-semibold disabled:opacity-50"
                  >
                    إنشاء المجموعة ({selectedGroupUids.length} مختارين)
                  </button>
                </div>

                <div className="space-y-2">
                  {chats
                    .filter((c) => c.type === 'GROUP')
                    .map((g) => (
                      <button
                        key={g.id}
                        type="button"
                        onClick={() => {
                          setSelectedChat(g);
                          setActiveTab('chats');
                        }}
                        className="w-full text-right p-3 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-800/50"
                      >
                        <Avatar name={g.title} />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold truncate">{g.title}</p>
                          <p className="text-xs text-slate-500">
                            {g.participantIds.length} أعضاء · {g.lastMessageText}
                          </p>
                        </div>
                      </button>
                    ))}
                </div>
              </div>
            )}

            {activeTab === 'calls' && (
              <div className="flex-1 overflow-y-auto p-4 space-y-3">
                {callHistory.length === 0 ? (
                  <p className="text-center text-xs text-slate-500 py-10 leading-relaxed">
                    سجل المكالمات فارغ. افتح أي محادثة ثنائية واضغط على أيقونة المكالمة الصوتية أو المرئية لبدء اتصال WebRTC مباشر.
                  </p>
                ) : (
                  callHistory.map((call) => (
                    <div
                      key={call.id}
                      className="p-3 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between"
                    >
                      <div>
                        <p className="text-sm font-semibold">{call.calleeName}</p>
                        <p className="text-xs text-slate-500">
                          {call.type === 'VIDEO' ? 'مرئية' : 'صوتية'} · {formatArabicTime(call.createdAt)}
                        </p>
                      </div>
                      {call.type === 'VIDEO' ? (
                        <Video className="w-4 h-4 text-emerald-700" />
                      ) : (
                        <Phone className="w-4 h-4 text-emerald-700" />
                      )}
                    </div>
                  ))
                )}
              </div>
            )}

            {activeTab === 'profile' && (
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                <div className="flex items-center gap-3">
                  <Avatar name={profile.displayName} url={profile.avatarUrl} size="lg" />
                  <div className="flex-1 min-w-0">
                    <p className="font-bold text-base truncate">{profile.displayName}</p>
                    <p className="text-xs text-emerald-700 dark:text-emerald-400 font-mono" dir="ltr">
                      @{profile.username}
                    </p>
                  </div>
                  <input
                    ref={avatarInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={async (e) => {
                      const file = e.target.files?.[0];
                      e.target.value = '';
                      if (!file) return;
                      try {
                        const dataUrl = await compressImageFileToDataUrl(file, 256);
                        await updateProfileDetails({
                          uid: firebaseUser.uid,
                          displayName: editName || profile.displayName,
                          bio: editBio,
                          avatarUrl: dataUrl,
                          currentUsernameLower: profile.usernameLower,
                        });
                        showToast('تم تحديث الصورة الشخصية.');
                      } catch (err) {
                        showToast(err instanceof Error ? err.message : 'تعذر تحديث الصورة.');
                      }
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => avatarInputRef.current?.click()}
                    className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium"
                  >
                    تغيير الصورة
                  </button>
                </div>

                <div className="space-y-2.5">
                  <input
                    type="text"
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    placeholder="الاسم الظاهر"
                    className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
                  />
                  <input
                    type="text"
                    dir="ltr"
                    value={editUsername}
                    onChange={(e) => setEditUsername(e.target.value)}
                    placeholder="اسم المستخدم"
                    className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm font-mono"
                  />
                  <textarea
                    value={editBio}
                    onChange={(e) => setEditBio(e.target.value.slice(0, MAX_BIO_LENGTH))}
                    rows={2}
                    placeholder="نبذة قصيرة"
                    className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm"
                  />
                  <button
                    type="button"
                    onClick={async () => {
                      try {
                        await updateProfileDetails({
                          uid: firebaseUser.uid,
                          displayName: editName,
                          bio: editBio,
                          avatarUrl: profile.avatarUrl,
                          currentUsernameLower: profile.usernameLower,
                          newRawUsername:
                            editUsername.trim().toLowerCase() !== profile.usernameLower
                              ? editUsername
                              : undefined,
                        });
                        showToast('تم حفظ تعديلات الملف الشخصي.');
                      } catch (err) {
                        showToast(err instanceof Error ? err.message : 'تعذر التحديث.');
                      }
                    }}
                    className="w-full py-2.5 rounded-xl bg-emerald-800 text-white text-xs font-semibold"
                  >
                    حفظ التعديلات
                  </button>
                </div>

                <div className="border-t border-slate-200 dark:border-slate-800 pt-3 space-y-2.5 text-xs">
                  <label className="flex items-center justify-between">
                    <span>إظهار حالة الاتصال (متصل الآن)</span>
                    <input
                      type="checkbox"
                      checked={profile.showOnlineStatus}
                      onChange={(e) =>
                        updatePrivacySettings(firebaseUser.uid, {
                          showOnlineStatus: e.target.checked,
                          showReadReceipts: profile.showReadReceipts,
                          whoCanAddToGroups: profile.whoCanAddToGroups,
                        })
                      }
                    />
                  </label>
                  <label className="flex items-center justify-between">
                    <span>إرسال إشعارات القراءة</span>
                    <input
                      type="checkbox"
                      checked={profile.showReadReceipts}
                      onChange={(e) =>
                        updatePrivacySettings(firebaseUser.uid, {
                          showOnlineStatus: profile.showOnlineStatus,
                          showReadReceipts: e.target.checked,
                          whoCanAddToGroups: profile.whoCanAddToGroups,
                        })
                      }
                    />
                  </label>
                  <div className="flex items-center justify-between">
                    <span>إضافة المجموعات</span>
                    <select
                      value={profile.whoCanAddToGroups}
                      onChange={(e) =>
                        updatePrivacySettings(firebaseUser.uid, {
                          showOnlineStatus: profile.showOnlineStatus,
                          showReadReceipts: profile.showReadReceipts,
                          whoCanAddToGroups: e.target.value as GroupPrivacySetting,
                        })
                      }
                      className="px-2 py-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800"
                    >
                      <option value="EVERYONE">الجميع</option>
                      <option value="CONTACTS_ONLY">جهات الاتصال فقط</option>
                    </select>
                  </div>
                  <label className="flex items-center justify-between">
                    <span>الوضع الداكن</span>
                    <input
                      type="checkbox"
                      checked={darkMode}
                      onChange={(e) => setDarkMode(e.target.checked)}
                    />
                  </label>
                </div>

                {blockedUsers.length > 0 && (
                  <div className="border-t border-slate-200 dark:border-slate-800 pt-3 space-y-2">
                    <p className="text-xs font-bold">المحظورون ({blockedUsers.length})</p>
                    {blockedUsers.map((b) => (
                      <div key={b.blockedUid} className="flex items-center justify-between text-xs">
                        <span>
                          {b.blockedDisplayName} (@{b.blockedUsername})
                        </span>
                        <button
                          type="button"
                          onClick={() => unblockUser(firebaseUser.uid, b.blockedUid)}
                          className="text-emerald-700 dark:text-emerald-400 font-medium"
                        >
                          إلغاء الحظر
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <div className="border-t border-slate-200 dark:border-slate-800 pt-3 space-y-2">
                  <PWAInstallButton variant="primary" />
                  <button
                    type="button"
                    onClick={() => setShowAndroidModal(true)}
                    className="w-full py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-medium flex items-center justify-center gap-2"
                  >
                    <Code2 className="w-4 h-4" />
                    <span>مشروع Android (Kotlin) وحزمة ZIP</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => signOutAccount(firebaseUser.uid)}
                    className="w-full py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-medium flex items-center justify-center gap-2"
                  >
                    <LogOut className="w-4 h-4" />
                    <span>تسجيل الخروج</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmDeleteAccount(true)}
                    className="w-full py-2.5 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-semibold"
                  >
                    حذف الحساب نهائيًا
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Right/Detail Chat Workspace */}
          <div
            className={`flex-1 flex flex-col bg-slate-100/70 dark:bg-slate-950 overflow-hidden ${
              selectedChat && activeTab === 'chats' ? 'flex' : 'hidden md:flex'
            }`}
          >
            {selectedChat ? (
              <>
                <div className="h-14 shrink-0 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 px-4 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <button
                      type="button"
                      onClick={() => setSelectedChat(null)}
                      className="md:hidden p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800"
                      aria-label="رجوع"
                    >
                      <ArrowRight className="w-5 h-5" />
                    </button>
                    <Avatar
                      name={
                        selectedChat.type === 'GROUP'
                          ? selectedChat.title
                          : activePeer?.displayName || 'مستخدم'
                      }
                      url={
                        selectedChat.type === 'GROUP'
                          ? selectedChat.avatarUrl
                          : activePeer?.avatarUrl
                      }
                      size="sm"
                      isOnline={activePeer?.showOnlineStatus && activePeer?.isOnline}
                    />
                    <div className="min-w-0">
                      <p className="font-bold text-sm truncate">
                        {selectedChat.type === 'GROUP'
                          ? selectedChat.title
                          : activePeer?.displayName || 'محادثة خاصة'}
                      </p>
                      <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate">
                        {selectedChat.type === 'GROUP'
                          ? `${selectedChat.participantIds.length} أعضاء`
                          : activePeer?.showOnlineStatus && activePeer?.isOnline
                          ? 'متصل الآن'
                          : activePeer
                          ? `@${activePeer.username}`
                          : ''}
                      </p>
                    </div>
                  </div>

                  {selectedChat.type === 'DIRECT' && activePeer && (
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => setActiveCall({ peer: activePeer, type: 'AUDIO' })}
                        className="p-2.5 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 text-emerald-800 dark:text-emerald-400"
                        title="مكالمة صوتية"
                      >
                        <Phone className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setActiveCall({ peer: activePeer, type: 'VIDEO' })}
                        className="p-2.5 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 text-emerald-800 dark:text-emerald-400"
                        title="مكالمة مرئية"
                      >
                        <Video className="w-4 h-4" />
                      </button>
                    </div>
                  )}
                </div>

                <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
                  {hasMoreOlder && combinedMessages.length >= 20 && (
                    <div className="text-center">
                      <button
                        type="button"
                        onClick={handleLoadOlder}
                        disabled={loadingOlder}
                        className="px-3 py-1.5 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-xs text-slate-600 dark:text-slate-300"
                      >
                        {loadingOlder ? 'جاري التحميل...' : 'تحميل رسائل أقدم'}
                      </button>
                    </div>
                  )}

                  {combinedMessages.map((msg) => {
                    const isMine = msg.senderId === firebaseUser.uid;
                    return (
                      <div
                        key={msg.id}
                        className={`flex ${isMine ? 'justify-start' : 'justify-end'}`}
                      >
                        <div
                          className={`group relative max-w-[78%] sm:max-w-md rounded-2xl px-3.5 py-2.5 text-sm shadow-xs ${
                            isMine
                              ? 'bg-emerald-800 text-white rounded-tr-sm'
                              : 'bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 border border-slate-200 dark:border-slate-800 rounded-tl-sm'
                          }`}
                        >
                          {msg.type === 'IMAGE' && msg.mediaUrl ? (
                            <img
                              src={msg.mediaUrl}
                              alt="صورة"
                              className="max-h-64 rounded-xl object-cover mb-1"
                            />
                          ) : msg.type === 'AUDIO' && msg.mediaUrl ? (
                            <audio controls src={msg.mediaUrl} className="max-w-[230px] h-9" />
                          ) : (
                            <p className="whitespace-pre-wrap break-words leading-relaxed">
                              {msg.text}
                            </p>
                          )}

                          <div
                            className={`mt-1 flex items-center justify-between gap-3 text-[10px] ${
                              isMine ? 'text-emerald-100/80' : 'text-slate-400'
                            }`}
                          >
                            <span className="tabular-nums">{formatArabicTime(msg.createdAt)}</span>
                            <div className="flex items-center gap-1.5">
                              {isMine && msg.deliveryStatus === 'FAILED' && (
                                <button
                                  type="button"
                                  onClick={() => handleRetryFailed(msg)}
                                  className="inline-flex items-center gap-1 text-red-200 underline"
                                >
                                  <RefreshCw className="w-3 h-3" />
                                  إعادة
                                </button>
                              )}
                              {isMine && msg.deliveryStatus === 'READ' && (
                                <CheckCheck className="w-3.5 h-3.5 text-emerald-300" />
                              )}
                              {isMine && msg.deliveryStatus === 'SENT' && (
                                <Check className="w-3.5 h-3.5" />
                              )}
                              {!msg.id.startsWith('local_') && (
                                <button
                                  type="button"
                                  onClick={() => setDeleteTargetMsg(msg)}
                                  className="opacity-70 hover:opacity-100"
                                  title="حذف من حسابك"
                                >
                                  <Trash2 className="w-3 h-3" />
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                <form
                  onSubmit={handleSendText}
                  className="p-3 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-800 flex items-center gap-2"
                >
                  <input
                    ref={imageInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={handleChatImageSelected}
                  />
                  <button
                    type="button"
                    onClick={() => imageInputRef.current?.click()}
                    className="p-2.5 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-300"
                    aria-label="إرسال صورة"
                  >
                    <ImageIcon className="w-5 h-5" />
                  </button>

                  <input
                    type="text"
                    value={draftText}
                    onChange={(e) => setDraftText(e.target.value)}
                    placeholder="اكتب رسالة..."
                    className="flex-1 px-4 py-2.5 rounded-full border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-700"
                  />

                  {draftText.trim() ? (
                    <button
                      type="submit"
                      className="p-2.5 rounded-full bg-emerald-800 hover:bg-emerald-700 text-white"
                      aria-label="إرسال"
                    >
                      <Send className="w-5 h-5" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleToggleVoiceRecord}
                      className={`p-2.5 rounded-full ${
                        isRecordingAudio
                          ? 'bg-red-600 text-white'
                          : 'bg-emerald-800 text-white hover:bg-emerald-700'
                      }`}
                      aria-label={isRecordingAudio ? 'إيقاف وإرسال الصوت' : 'تسجيل صوتي'}
                    >
                      {isRecordingAudio ? <Square className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
                    </button>
                  )}
                </form>
              </>
            ) : (
              <div className="flex-1 flex flex-col items-center justify-center p-8 text-center space-y-3 text-slate-500">
                <div className="w-16 h-16 rounded-3xl bg-emerald-800/10 text-emerald-800 dark:text-emerald-400 flex items-center justify-center">
                  <MessageSquare className="w-8 h-8" />
                </div>
                <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 font-display">
                  تواصل للمراسلة الفورية
                </h2>
                <p className="text-xs max-w-sm leading-relaxed">
                  اختر محادثة من القائمة أو ابحث عن اسم مستخدم جديد لبدء الدردشة أو إجراء مكالمة صوتية ومرئية.
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Mobile Bottom Navigation */}
        {!selectedChat && (
          <nav className="md:hidden shrink-0 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-800 grid grid-cols-6 py-1.5 text-[10px]">
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
                onClick={() => setActiveTab(key)}
                className={`flex flex-col items-center justify-center gap-1 py-1 min-h-[44px] ${
                  activeTab === key
                    ? 'text-emerald-800 dark:text-emerald-400 font-bold'
                    : 'text-slate-500 dark:text-slate-400'
                }`}
              >
                <IconComp className="w-5 h-5" />
                <span>{label}</span>
              </button>
            ))}
          </nav>
        )}
      </div>

      {/* Delete message policy dialog */}
      {deleteTargetMsg && selectedChat && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="max-w-sm w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-5 space-y-4">
            <h3 className="font-bold text-base">حذف الرسالة من واجهتك</h3>
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              سيتم إخفاء هذه الرسالة من حسابك فقط، ولن يتم حذفها من أجهزة المشاركين الآخرين في المحادثة.
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeleteTargetMsg(null)}
                className="px-4 py-2 rounded-xl text-xs border border-slate-200 dark:border-slate-700"
              >
                إلغاء
              </button>
              <button
                type="button"
                onClick={async () => {
                  await deleteMessageForCurrentUser(
                    selectedChat.id,
                    deleteTargetMsg.id,
                    firebaseUser.uid
                  );
                  setDeleteTargetMsg(null);
                }}
                className="px-4 py-2 rounded-xl text-xs bg-red-600 text-white font-semibold"
              >
                حذف لديّ فقط
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Status Preview Modal */}
      {activeStatusPreview && (
        <div className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4">
          <div className="max-w-md w-full bg-white dark:bg-slate-900 rounded-2xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <span className="font-bold text-sm">
                {activeStatusPreview.authorName} (@{activeStatusPreview.authorUsername})
              </span>
              <span className="text-xs text-slate-400">
                المشاهدات: {activeStatusPreview.viewerUids.length}
              </span>
            </div>
            {activeStatusPreview.type === 'IMAGE' && activeStatusPreview.mediaUrl ? (
              <img
                src={activeStatusPreview.mediaUrl}
                alt="صورة الحالة"
                className="w-full max-h-80 rounded-xl object-cover"
              />
            ) : (
              <div className="p-8 rounded-xl bg-emerald-800 text-white text-center text-base font-medium">
                {activeStatusPreview.textContent}
              </div>
            )}
            <button
              type="button"
              onClick={() => setActiveStatusPreview(null)}
              className="w-full py-2.5 rounded-xl bg-slate-100 dark:bg-slate-800 text-xs font-semibold"
            >
              إغلاق
            </button>
          </div>
        </div>
      )}

      {/* Delete Account Dialog */}
      {confirmDeleteAccount && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="max-w-sm w-full bg-white dark:bg-slate-900 rounded-2xl p-5 space-y-4">
            <div className="flex items-center gap-2 text-red-600 font-bold">
              <ShieldAlert className="w-5 h-5" />
              <h3>تأكيد حذف الحساب</h3>
            </div>
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              سيتم حذف ملفك الشخصي وبياناتك الخاصة وقائمة الحظر وتحرير اسم المستخدم @{profile.username} نهائيًا.
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmDeleteAccount(false)}
                className="px-4 py-2 rounded-xl text-xs border border-slate-200 dark:border-slate-700"
              >
                إلغاء
              </button>
              <button
                type="button"
                onClick={async () => {
                  setConfirmDeleteAccount(false);
                  try {
                    await deleteAccountAndOwnedData(firebaseUser.uid, profile.usernameLower);
                  } catch (err) {
                    showToast(
                      err instanceof Error
                        ? err.message
                        : 'يرجى إعادة تسجيل الدخول ثم محاولة حذف الحساب.'
                    );
                  }
                }}
                className="px-4 py-2 rounded-xl text-xs bg-red-600 text-white font-semibold"
              >
                حذف نهائي
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Active WebRTC Call */}
      {activeCall && profile && (
        <WebRtcCallOverlay
          currentUser={profile}
          peerUser={activeCall.peer}
          incomingSession={activeCall.incoming}
          callType={activeCall.type}
          onClose={() => setActiveCall(null)}
        />
      )}

      {/* Android Source & ZIP Modal */}
      {showAndroidModal && <AndroidProjectModal onClose={() => setShowAndroidModal(false)} />}
    </div>
  );
}
