import {
  GoogleAuthProvider,
  deleteUser,
  signInWithPopup,
  signOut as firebaseSignOut,
} from 'firebase/auth';
import {
  Timestamp,
  addDoc,
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  limit,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
  type QueryDocumentSnapshot,
  type Unsubscribe,
} from 'firebase/firestore';
import { OperationType, auth, db, handleFirestoreError } from '../firebase';
import {
  MAX_BIO_LENGTH,
  MAX_DISPLAY_NAME_LENGTH,
  MAX_GROUP_TITLE_LENGTH,
  MAX_MESSAGE_LENGTH,
  MAX_STATUS_TEXT_LENGTH,
  MESSAGES_PAGE_SIZE,
  STATUS_TTL_MS,
  directChatId,
  validateUsername,
  type BlockedUser,
  type CallSession,
  type CallStatus,
  type CallType,
  type Chat,
  type GroupPrivacySetting,
  type IceCandidateItem,
  type Message,
  type MessageType,
  type StatusItem,
  type StatusMediaType,
  type UserPublicProfile,
} from '../types/tawasul';

export async function signInWithGooglePopup() {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  const cred = await signInWithPopup(auth, provider);
  return cred.user;
}

export async function signOutAccount(uid?: string) {
  if (uid) {
    try {
      await updateDoc(doc(db, 'users', uid), {
        isOnline: false,
        lastSeen: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    } catch {
      // Ignore presence update error during sign out
    }
  }
  await firebaseSignOut(auth);
}

function toMillis(ts: { toMillis?: () => number } | null | undefined): number {
  return ts && typeof ts.toMillis === 'function' ? ts.toMillis() : 0;
}

function forwardListenerError(
  err: unknown,
  op: OperationType,
  path: string,
  onError?: (err: Error) => void
) {
  try {
    handleFirestoreError(err, op, path);
  } catch (structured) {
    if (onError && structured instanceof Error) {
      onError(structured);
      return;
    }
    throw structured;
  }
}

export async function getUserProfile(uid: string): Promise<UserPublicProfile | null> {
  const path = `users/${uid}`;
  try {
    const snap = await getDoc(doc(db, 'users', uid));
    return snap.exists() ? (snap.data() as UserPublicProfile) : null;
  } catch (err) {
    handleFirestoreError(err, OperationType.GET, path);
  }
}

export function subscribeUserProfile(
  uid: string,
  onData: (profile: UserPublicProfile | null) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const path = `users/${uid}`;
  return onSnapshot(
    doc(db, 'users', uid),
    (snap) => onData(snap.exists() ? (snap.data() as UserPublicProfile) : null),
    (err) => forwardListenerError(err, OperationType.GET, path, onError)
  );
}

export async function isUsernameAvailable(rawUsername: string): Promise<{
  available: boolean;
  hint: string;
}> {
  const validation = validateUsername(rawUsername);
  if (validation.valid === false) {
    return { available: false, hint: validation.error };
  }
  const path = `usernames/${validation.lower}`;
  try {
    const snap = await getDoc(doc(db, 'usernames', validation.lower));
    if (snap.exists()) {
      const ownerUid = snap.data()?.uid;
      if (ownerUid === auth.currentUser?.uid) {
        return { available: true, hint: `اسم المستخدم @${validation.lower} خاص بك.` };
      }
      return { available: false, hint: `اسم المستخدم @${validation.lower} محجوز بالفعل.` };
    }
    return { available: true, hint: `اسم المستخدم @${validation.lower} متاح.` };
  } catch (err) {
    handleFirestoreError(err, OperationType.GET, path);
  }
}

export async function completeProfileSetup(params: {
  uid: string;
  email: string;
  displayName: string;
  rawUsername: string;
  bio: string;
  avatarUrl: string;
}): Promise<void> {
  const cleanName = params.displayName.trim().slice(0, MAX_DISPLAY_NAME_LENGTH);
  if (cleanName.length < 2) {
    throw new Error('يجب أن يكون الاسم الظاهر بين حرفين و50 حرفًا.');
  }
  const validation = validateUsername(params.rawUsername);
  if (validation.valid === false) {
    throw new Error(validation.error);
  }
  const cleanBio = params.bio.trim().slice(0, MAX_BIO_LENGTH);
  const cleanAvatar = params.avatarUrl.slice(0, 1024);

  const userRef = doc(db, 'users', params.uid);
  const privateRef = doc(db, 'users', params.uid, 'private', 'info');
  const usernameRef = doc(db, 'usernames', validation.lower);

  try {
    await runTransaction(db, async (tx) => {
      const existingReservation = await tx.get(usernameRef);
      if (existingReservation.exists() && existingReservation.data()?.uid !== params.uid) {
        throw new Error('اسم المستخدم محجوز بالفعل لمستخدم آخر.');
      }

      tx.set(usernameRef, {
        uid: params.uid,
        usernameLower: validation.lower,
        createdAt: serverTimestamp(),
      });

      tx.set(userRef, {
        uid: params.uid,
        username: validation.normalized,
        usernameLower: validation.lower,
        displayName: cleanName,
        bio: cleanBio,
        avatarUrl: cleanAvatar,
        isOnline: true,
        lastSeen: serverTimestamp(),
        showOnlineStatus: true,
        showReadReceipts: true,
        whoCanAddToGroups: 'EVERYONE',
        profileCompleted: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });

      tx.set(privateRef, {
        uid: params.uid,
        email: params.email.slice(0, 254),
        updatedAt: serverTimestamp(),
      });
    });
  } catch (err) {
    if (err instanceof Error && err.message.includes('محجوز')) throw err;
    handleFirestoreError(err, OperationType.WRITE, `users/${params.uid}`);
  }
}

export async function updateProfileDetails(params: {
  uid: string;
  displayName: string;
  bio: string;
  avatarUrl: string;
  currentUsernameLower: string;
  newRawUsername?: string;
}): Promise<void> {
  const cleanName = params.displayName.trim().slice(0, MAX_DISPLAY_NAME_LENGTH);
  if (cleanName.length < 2) {
    throw new Error('يجب أن يكون الاسم الظاهر بين حرفين و50 حرفًا.');
  }
  const cleanBio = params.bio.trim().slice(0, MAX_BIO_LENGTH);
  const cleanAvatar = params.avatarUrl.slice(0, 1024);

  let targetUsername: { normalized: string; lower: string } | null = null;
  if (params.newRawUsername !== undefined) {
    const v = validateUsername(params.newRawUsername);
    if (v.valid === false) throw new Error(v.error);
    targetUsername = { normalized: v.normalized, lower: v.lower };
  }

  const userRef = doc(db, 'users', params.uid);

  try {
    await runTransaction(db, async (tx) => {
      if (targetUsername && targetUsername.lower !== params.currentUsernameLower) {
        const newRef = doc(db, 'usernames', targetUsername.lower);
        const newSnap = await tx.get(newRef);
        if (newSnap.exists() && newSnap.data()?.uid !== params.uid) {
          throw new Error('اسم المستخدم الجديد محجوز لمستخدم آخر.');
        }

        const oldRef = doc(db, 'usernames', params.currentUsernameLower);
        const oldSnap = await tx.get(oldRef);
        if (oldSnap.exists() && oldSnap.data()?.uid === params.uid) {
          tx.delete(oldRef);
        }

        tx.set(newRef, {
          uid: params.uid,
          usernameLower: targetUsername.lower,
          createdAt: serverTimestamp(),
        });
      }

      const updates: Record<string, unknown> = {
        displayName: cleanName,
        bio: cleanBio,
        avatarUrl: cleanAvatar,
        updatedAt: serverTimestamp(),
      };
      if (targetUsername) {
        updates.username = targetUsername.normalized;
        updates.usernameLower = targetUsername.lower;
      }
      tx.update(userRef, updates);
    });
  } catch (err) {
    if (err instanceof Error && err.message.includes('محجوز')) throw err;
    handleFirestoreError(err, OperationType.UPDATE, `users/${params.uid}`);
  }
}

export async function updatePresence(uid: string, isOnline: boolean) {
  try {
    await updateDoc(doc(db, 'users', uid), {
      isOnline,
      lastSeen: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  } catch {
    // Presence updates are best-effort
  }
}

export async function updatePrivacySettings(
  uid: string,
  settings: {
    showOnlineStatus: boolean;
    showReadReceipts: boolean;
    whoCanAddToGroups: GroupPrivacySetting;
  }
) {
  const path = `users/${uid}`;
  try {
    await updateDoc(doc(db, 'users', uid), {
      showOnlineStatus: settings.showOnlineStatus,
      showReadReceipts: settings.showReadReceipts,
      whoCanAddToGroups: settings.whoCanAddToGroups,
      updatedAt: serverTimestamp(),
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.UPDATE, path);
  }
}

export async function searchUsersByUsername(
  rawQuery: string,
  currentUid: string,
  blockedUids: Set<string>
): Promise<UserPublicProfile[]> {
  const normalized = rawQuery.trim().replace(/^@+/, '').toLowerCase();
  if (!normalized) return [];

  const path = 'users';
  try {
    const q = query(
      collection(db, 'users'),
      where('profileCompleted', '==', true),
      where('usernameLower', '>=', normalized),
      where('usernameLower', '<=', `${normalized}\uf8ff`),
      limit(20)
    );
    const snap = await getDocs(q);
    return snap.docs
      .map((d) => d.data() as UserPublicProfile)
      .filter((u) => u.uid !== currentUid && !blockedUids.has(u.uid));
  } catch (err) {
    handleFirestoreError(err, OperationType.LIST, path);
  }
}

export function subscribeBlockedUsers(
  uid: string,
  onData: (blocked: BlockedUser[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const path = `users/${uid}/blocked`;
  return onSnapshot(
    collection(db, 'users', uid, 'blocked'),
    (snap) => onData(snap.docs.map((d) => d.data() as BlockedUser)),
    (err) => forwardListenerError(err, OperationType.LIST, path, onError)
  );
}

export async function blockUser(currentUid: string, target: UserPublicProfile) {
  const path = `users/${currentUid}/blocked/${target.uid}`;
  try {
    await runTransaction(db, async (tx) => {
      tx.set(doc(db, 'users', currentUid, 'blocked', target.uid), {
        blockedUid: target.uid,
        blockedUsername: target.username,
        blockedDisplayName: target.displayName,
        createdAt: serverTimestamp(),
      });
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, path);
  }
}

export async function unblockUser(currentUid: string, targetUid: string) {
  const path = `users/${currentUid}/blocked/${targetUid}`;
  try {
    await deleteDoc(doc(db, 'users', currentUid, 'blocked', targetUid));
  } catch (err) {
    handleFirestoreError(err, OperationType.DELETE, path);
  }
}

export function subscribeUserChats(
  uid: string,
  onData: (chats: Chat[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const path = 'chats';
  const q = query(
    collection(db, 'chats'),
    where('participantIds', 'array-contains', uid)
  );
  return onSnapshot(
    q,
    (snap) => {
      const sorted = snap.docs
        .map((d) => ({ ...(d.data() as Chat), id: d.id }))
        .sort((a, b) => toMillis(b.lastMessageAt) - toMillis(a.lastMessageAt));
      onData(sorted);
    },
    (err) => forwardListenerError(err, OperationType.LIST, path, onError)
  );
}

export async function getOrCreateDirectChat(currentUid: string, otherUid: string): Promise<Chat> {
  const chatId = directChatId(currentUid, otherUid);
  const path = `chats/${chatId}`;
  const chatRef = doc(db, 'chats', chatId);

  try {
    const existingMember = await getDoc(doc(db, 'chats', chatId, 'members', currentUid));
    if (existingMember.exists()) {
      const existingChat = await getDoc(chatRef);
      if (existingChat.exists()) {
        return { ...(existingChat.data() as Chat), id: chatId };
      }
    }

    const sortedParticipants = [currentUid, otherUid].sort();
    const batch = writeBatch(db);
    batch.set(chatRef, {
      id: chatId,
      type: 'DIRECT',
      title: '',
      avatarUrl: '',
      createdBy: currentUid,
      participantIds: sortedParticipants,
      lastMessageText: '',
      lastMessageType: 'TEXT',
      lastMessageSenderId: '',
      lastMessageAt: serverTimestamp(),
      unreadCounts: { [currentUid]: 0, [otherUid]: 0 },
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    for (const memberUid of sortedParticipants) {
      batch.set(doc(db, 'chats', chatId, 'members', memberUid), {
        uid: memberUid,
        role: 'MEMBER',
        joinedAt: serverTimestamp(),
      });
    }
    await batch.commit();

    return {
      id: chatId,
      type: 'DIRECT',
      title: '',
      avatarUrl: '',
      createdBy: currentUid,
      participantIds: sortedParticipants,
      lastMessageText: '',
      lastMessageType: 'TEXT',
      lastMessageSenderId: '',
      lastMessageAt: null,
      unreadCounts: { [currentUid]: 0, [otherUid]: 0 },
      createdAt: null,
      updatedAt: null,
    };
  } catch (err) {
    handleFirestoreError(err, OperationType.WRITE, path);
  }
}

export async function createGroupChat(params: {
  creatorUid: string;
  title: string;
  memberUids: string[];
}): Promise<Chat> {
  const cleanTitle = params.title.trim().slice(0, MAX_GROUP_TITLE_LENGTH);
  if (cleanTitle.length < 2) {
    throw new Error('يجب أن يكون اسم المجموعة بين حرفين و60 حرفًا.');
  }
  const participants = Array.from(new Set([params.creatorUid, ...params.memberUids])).slice(0, 50);
  if (participants.length < 2) {
    throw new Error('اختر عضوًا واحدًا على الأقل لإضافته إلى المجموعة.');
  }

  const chatRef = doc(collection(db, 'chats'));
  const chatId = chatRef.id;
  const unreadCounts: Record<string, number> = {};
  participants.forEach((uid) => {
    unreadCounts[uid] = 0;
  });

  try {
    const batch = writeBatch(db);
    batch.set(chatRef, {
      id: chatId,
      type: 'GROUP',
      title: cleanTitle,
      avatarUrl: '',
      createdBy: params.creatorUid,
      participantIds: participants,
      lastMessageText: 'تم إنشاء المجموعة',
      lastMessageType: 'SYSTEM',
      lastMessageSenderId: params.creatorUid,
      lastMessageAt: serverTimestamp(),
      unreadCounts,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    for (const uid of participants) {
      batch.set(doc(db, 'chats', chatId, 'members', uid), {
        uid,
        role: uid === params.creatorUid ? 'ADMIN' : 'MEMBER',
        joinedAt: serverTimestamp(),
      });
    }
    await batch.commit();

    return {
      id: chatId,
      type: 'GROUP',
      title: cleanTitle,
      avatarUrl: '',
      createdBy: params.creatorUid,
      participantIds: participants,
      lastMessageText: 'تم إنشاء المجموعة',
      lastMessageType: 'SYSTEM',
      lastMessageSenderId: params.creatorUid,
      lastMessageAt: null,
      unreadCounts,
      createdAt: null,
      updatedAt: null,
    };
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, `chats/${chatId}`);
  }
}

export function subscribeLatestMessages(
  chatId: string,
  onData: (messages: Message[], oldestDoc: QueryDocumentSnapshot | null) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const path = `chats/${chatId}/messages`;
  const q = query(
    collection(db, 'chats', chatId, 'messages'),
    orderBy('createdAt', 'desc'),
    limit(MESSAGES_PAGE_SIZE)
  );
  return onSnapshot(
    q,
    (snap) => {
      const docs = snap.docs;
      const oldest = docs.length > 0 ? docs[docs.length - 1] : null;
      const msgs = docs.map((d) => ({ ...(d.data() as Message), id: d.id })).reverse();
      onData(msgs, oldest);
    },
    (err) => forwardListenerError(err, OperationType.LIST, path, onError)
  );
}

export async function loadOlderMessages(
  chatId: string,
  oldestDoc: QueryDocumentSnapshot
): Promise<{ messages: Message[]; nextOldest: QueryDocumentSnapshot | null }> {
  const path = `chats/${chatId}/messages`;
  try {
    const { startAfter } = await import('firebase/firestore');
    const pagedQ = query(
      collection(db, 'chats', chatId, 'messages'),
      orderBy('createdAt', 'desc'),
      startAfter(oldestDoc),
      limit(MESSAGES_PAGE_SIZE)
    );
    const snap = await getDocs(pagedQ);
    const docs = snap.docs;
    const nextOldest = docs.length > 0 ? docs[docs.length - 1] : null;
    const messages = docs.map((d) => ({ ...(d.data() as Message), id: d.id })).reverse();
    return { messages, nextOldest };
  } catch (err) {
    handleFirestoreError(err, OperationType.LIST, path);
  }
}

export async function sendMessage(params: {
  chatId: string;
  senderId: string;
  participantIds: string[];
  type: MessageType;
  text?: string;
  mediaUrl?: string;
  mediaDurationSec?: number;
  localClientId: string;
}) {
  const trimmedText = (params.text || '').trim().slice(0, MAX_MESSAGE_LENGTH);
  const mediaUrl = params.mediaUrl || '';
  if (params.type === 'TEXT' && !trimmedText) {
    throw new Error('لا يمكن إرسال رسالة فارغة.');
  }
  const msgRef = doc(collection(db, 'chats', params.chatId, 'messages'));
  const chatRef = doc(db, 'chats', params.chatId);

  const preview =
    params.type === 'IMAGE'
      ? 'صورة'
      : params.type === 'VIDEO'
      ? 'فيديو'
      : params.type === 'AUDIO'
      ? 'رسالة صوتية'
      : trimmedText.slice(0, 120);

  const batch = writeBatch(db);
  batch.set(msgRef, {
    id: msgRef.id,
    chatId: params.chatId,
    senderId: params.senderId,
    type: params.type,
    text: trimmedText,
    mediaUrl,
    mediaDurationSec: Math.max(0, Math.round(params.mediaDurationSec || 0)),
    deliveryStatus: 'SENT',
    readBy: { [params.senderId]: Date.now() },
    deletedForUids: [],
    localClientId: params.localClientId.slice(0, 64),
    createdAt: serverTimestamp(),
  });

  const chatUpdates: Record<string, unknown> = {
    lastMessageText: preview,
    lastMessageType: params.type,
    lastMessageSenderId: params.senderId,
    lastMessageAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  params.participantIds
    .filter((uid) => uid !== params.senderId)
    .forEach((recipientUid) => {
      chatUpdates[`unreadCounts.${recipientUid}`] = increment(1);
    });
  batch.update(chatRef, chatUpdates);

  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.WRITE, `chats/${params.chatId}/messages/${msgRef.id}`);
  }
}

export async function markChatAsRead(
  chatId: string,
  currentUid: string,
  sendReadReceipts: boolean,
  messages: Message[]
) {
  try {
    await updateDoc(doc(db, 'chats', chatId), {
      [`unreadCounts.${currentUid}`]: 0,
      updatedAt: serverTimestamp(),
    });
    if (sendReadReceipts) {
      const toMark = messages.filter(
        (m) => m.senderId !== currentUid && m.deliveryStatus !== 'READ' && !m.id.startsWith('local_')
      );
      if (toMark.length > 0) {
        const batch = writeBatch(db);
        toMark.slice(-15).forEach((m) => {
          batch.update(doc(db, 'chats', chatId, 'messages', m.id), {
            deliveryStatus: 'READ',
            [`readBy.${currentUid}`]: Date.now(),
          });
        });
        await batch.commit();
      }
    }
  } catch {
    // Non-critical read-receipt update
  }
}

export async function deleteMessageForCurrentUser(
  chatId: string,
  messageId: string,
  currentUid: string
) {
  const path = `chats/${chatId}/messages/${messageId}`;
  try {
    await updateDoc(doc(db, 'chats', chatId, 'messages', messageId), {
      deletedForUids: arrayUnion(currentUid),
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.UPDATE, path);
  }
}

export function subscribeActiveStatuses(
  blockedUids: Set<string>,
  onData: (items: StatusItem[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const path = 'statuses';
  const q = query(
    collection(db, 'statuses'),
    where('expiresAt', '>', Timestamp.now()),
    orderBy('expiresAt', 'desc'),
    limit(50)
  );
  return onSnapshot(
    q,
    (snap) => {
      const items = snap.docs
        .map((d) => ({ ...(d.data() as StatusItem), id: d.id }))
        .filter((s) => !blockedUids.has(s.authorUid));
      onData(items);
    },
    (err) => forwardListenerError(err, OperationType.LIST, path, onError)
  );
}

export async function publishStatus(params: {
  author: UserPublicProfile;
  type: StatusMediaType;
  textContent?: string;
  mediaUrl?: string;
  backgroundColorHex?: string;
}) {
  const ref = doc(collection(db, 'statuses'));
  const expiresAt = Timestamp.fromMillis(Date.now() + STATUS_TTL_MS);
  try {
    await runTransaction(db, async (tx) => {
      tx.set(ref, {
        id: ref.id,
        authorUid: params.author.uid,
        authorName: params.author.displayName,
        authorUsername: params.author.username,
        authorAvatarUrl: params.author.avatarUrl,
        type: params.type,
        textContent: (params.textContent || '').trim().slice(0, MAX_STATUS_TEXT_LENGTH),
        backgroundColorHex: params.backgroundColorHex || '#065F46',
        mediaUrl: params.mediaUrl || '',
        viewerUids: [],
        createdAt: serverTimestamp(),
        expiresAt,
      });
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, `statuses/${ref.id}`);
  }
}

export async function markStatusViewed(statusId: string, viewerUid: string) {
  try {
    await updateDoc(doc(db, 'statuses', statusId), {
      viewerUids: arrayUnion(viewerUid),
    });
  } catch {
    // Non-critical status view count
  }
}

export function subscribeIncomingCall(
  uid: string,
  onData: (session: CallSession | null) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const path = 'calls';
  const q = query(
    collection(db, 'calls'),
    where('calleeUid', '==', uid),
    limit(10)
  );
  return onSnapshot(
    q,
    (snap) => {
      const ringing = snap.docs
        .map((d) => ({ ...(d.data() as CallSession), id: d.id }))
        .find((c) => c.status === 'RINGING');
      onData(ringing || null);
    },
    (err) => forwardListenerError(err, OperationType.LIST, path, onError)
  );
}

export function subscribeCallHistory(
  uid: string,
  onData: (sessions: CallSession[]) => void
): Unsubscribe {
  const q = query(
    collection(db, 'calls'),
    where('callerUid', '==', uid),
    limit(20)
  );
  return onSnapshot(
    q,
    (snap) => {
      const sorted = snap.docs
        .map((d) => ({ ...(d.data() as CallSession), id: d.id }))
        .sort((a, b) => toMillis(b.createdAt) - toMillis(a.createdAt));
      onData(sorted);
    },
    () => {
      onData([]);
    }
  );
}

export async function createOutgoingCall(params: {
  caller: UserPublicProfile;
  callee: UserPublicProfile;
  type: CallType;
  offerSdp: string;
}): Promise<CallSession> {
  const ref = doc(collection(db, 'calls'));
  try {
    await runTransaction(db, async (tx) => {
      tx.set(ref, {
        id: ref.id,
        callerUid: params.caller.uid,
        callerName: params.caller.displayName,
        callerAvatarUrl: params.caller.avatarUrl,
        calleeUid: params.callee.uid,
        calleeName: params.callee.displayName,
        calleeAvatarUrl: params.callee.avatarUrl,
        type: params.type,
        status: 'RINGING',
        offerSdp: params.offerSdp,
        answerSdp: '',
        createdAt: serverTimestamp(),
      });
    });
    return {
      id: ref.id,
      callerUid: params.caller.uid,
      callerName: params.caller.displayName,
      callerAvatarUrl: params.caller.avatarUrl,
      calleeUid: params.callee.uid,
      calleeName: params.callee.displayName,
      calleeAvatarUrl: params.callee.avatarUrl,
      type: params.type,
      status: 'RINGING',
      offerSdp: params.offerSdp,
      answerSdp: '',
      createdAt: null,
    };
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, `calls/${ref.id}`);
  }
}

export function subscribeCallSession(
  callId: string,
  onData: (session: CallSession | null) => void
): Unsubscribe {
  const path = `calls/${callId}`;
  return onSnapshot(
    doc(db, 'calls', callId),
    (snap) => onData(snap.exists() ? ({ ...(snap.data() as CallSession), id: callId }) : null),
    (err) => handleFirestoreError(err, OperationType.GET, path)
  );
}

export async function answerCall(callId: string, answerSdp: string) {
  const path = `calls/${callId}`;
  try {
    await updateDoc(doc(db, 'calls', callId), {
      answerSdp,
      status: 'CONNECTED',
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.UPDATE, path);
  }
}

export async function updateCallStatus(callId: string, status: CallStatus) {
  const path = `calls/${callId}`;
  try {
    const payload: Record<string, unknown> = { status };
    if (status === 'ENDED' || status === 'DECLINED' || status === 'MISSED') {
      payload.endedAt = serverTimestamp();
    }
    await updateDoc(doc(db, 'calls', callId), payload);
  } catch (err) {
    handleFirestoreError(err, OperationType.UPDATE, path);
  }
}

export async function addIceCandidate(callId: string, candidate: IceCandidateItem) {
  const path = `calls/${callId}/iceCandidates`;
  try {
    await addDoc(collection(db, 'calls', callId, 'iceCandidates'), candidate);
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, path);
  }
}

export function subscribeIceCandidates(
  callId: string,
  currentUid: string,
  onCandidate: (candidate: IceCandidateItem) => void
): Unsubscribe {
  const path = `calls/${callId}/iceCandidates`;
  return onSnapshot(
    collection(db, 'calls', callId, 'iceCandidates'),
    (snap) => {
      snap.docChanges().forEach((change) => {
        if (change.type === 'added') {
          const item = change.doc.data() as IceCandidateItem;
          if (item.senderUid !== currentUid) {
            onCandidate(item);
          }
        }
      });
    },
    (err) => handleFirestoreError(err, OperationType.LIST, path)
  );
}

export async function deleteAccountAndOwnedData(uid: string, usernameLower: string) {
  const path = `users/${uid}`;
  try {
    const blockedSnap = await getDocs(collection(db, 'users', uid, 'blocked'));
    const batch = writeBatch(db);
    blockedSnap.docs.forEach((d) => batch.delete(d.ref));
    batch.delete(doc(db, 'users', uid, 'private', 'info'));
    if (usernameLower) {
      batch.delete(doc(db, 'usernames', usernameLower));
    }
    batch.delete(doc(db, 'users', uid));
    await batch.commit();
    if (auth.currentUser) {
      await deleteUser(auth.currentUser);
    }
  } catch (err) {
    handleFirestoreError(err, OperationType.DELETE, path);
  }
}

export async function compressImageFileToDataUrl(file: File, maxDimension = 720): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      const scale = Math.min(1, maxDimension / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error('تعذر معالجة الصورة.'));
        return;
      }
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL('image/jpeg', 0.72);
      if (dataUrl.length > 750_000) {
        reject(new Error('حجم الصورة كبير جدًا بعد الضغط، اختر صورة أصغر.'));
        return;
      }
      resolve(dataUrl);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('تعذر قراءة ملف الصورة.'));
    };
    img.src = url;
  });
}
