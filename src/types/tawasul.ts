import type { Timestamp } from 'firebase/firestore';

export const USERNAME_REGEX = /^[a-zA-Z0-9_]{3,20}$/;
export const USERNAME_LOWER_REGEX = /^[a-z0-9_]{3,20}$/;
export const MAX_DISPLAY_NAME_LENGTH = 50;
export const MAX_BIO_LENGTH = 160;
export const MAX_MESSAGE_LENGTH = 4000;
export const MAX_GROUP_TITLE_LENGTH = 60;
export const MAX_STATUS_TEXT_LENGTH = 500;
export const MESSAGES_PAGE_SIZE = 30;
export const STATUS_TTL_MS = 24 * 60 * 60 * 1000;

const RESERVED_USERNAMES = new Set([
  'admin',
  'administrator',
  'support',
  'tawasul',
  'system',
  'root',
  'help',
  'moderator',
]);

export type GroupPrivacySetting = 'EVERYONE' | 'CONTACTS_ONLY';
export type ChatType = 'DIRECT' | 'GROUP';
export type MessageType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO' | 'SYSTEM';
export type DeliveryStatus = 'SENDING' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';
export type StatusMediaType = 'TEXT' | 'IMAGE' | 'VIDEO';
export type CallType = 'AUDIO' | 'VIDEO';
export type CallStatus = 'RINGING' | 'CONNECTED' | 'ENDED' | 'DECLINED' | 'MISSED';

export interface UserPublicProfile {
  uid: string;
  username: string;
  usernameLower: string;
  displayName: string;
  bio: string;
  avatarUrl: string;
  isOnline: boolean;
  lastSeen: Timestamp | null;
  showOnlineStatus: boolean;
  showReadReceipts: boolean;
  whoCanAddToGroups: GroupPrivacySetting;
  profileCompleted: boolean;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface BlockedUser {
  blockedUid: string;
  blockedUsername: string;
  blockedDisplayName: string;
  createdAt: Timestamp | null;
}

export interface Chat {
  id: string;
  type: ChatType;
  title: string;
  avatarUrl: string;
  createdBy: string;
  participantIds: string[];
  lastMessageText: string;
  lastMessageType: MessageType;
  lastMessageSenderId: string;
  lastMessageAt: Timestamp | null;
  unreadCounts: Record<string, number>;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface Message {
  id: string;
  chatId: string;
  senderId: string;
  type: MessageType;
  text: string;
  mediaUrl: string;
  mediaDurationSec: number;
  deliveryStatus: DeliveryStatus;
  readBy: Record<string, number>;
  deletedForUids: string[];
  localClientId: string;
  createdAt: Timestamp | null;
}

export interface StatusItem {
  id: string;
  authorUid: string;
  authorName: string;
  authorUsername: string;
  authorAvatarUrl: string;
  type: StatusMediaType;
  textContent: string;
  backgroundColorHex: string;
  mediaUrl: string;
  viewerUids: string[];
  createdAt: Timestamp | null;
  expiresAt: Timestamp | null;
}

export interface CallSession {
  id: string;
  callerUid: string;
  callerName: string;
  callerAvatarUrl: string;
  calleeUid: string;
  calleeName: string;
  calleeAvatarUrl: string;
  type: CallType;
  status: CallStatus;
  offerSdp: string;
  answerSdp: string;
  createdAt: Timestamp | null;
  endedAt?: Timestamp | null;
}

export interface IceCandidateItem {
  senderUid: string;
  sdpMid: string;
  sdpMLineIndex: number;
  sdpCandidate: string;
}

export function validateUsername(raw: string):
  | { valid: true; normalized: string; lower: string }
  | { valid: false; error: string } {
  const trimmed = raw.trim().replace(/^@+/, '');
  if (trimmed.length < 3 || trimmed.length > 20) {
    return { valid: false, error: 'يجب أن يكون اسم المستخدم بين 3 و20 حرفًا.' };
  }
  if (!USERNAME_REGEX.test(trimmed)) {
    return {
      valid: false,
      error: 'يُسمح فقط بالأحرف الإنجليزية (a-z) والأرقام (0-9) والشرطة السفلية (_).',
    };
  }
  const lower = trimmed.toLowerCase();
  if (RESERVED_USERNAMES.has(lower)) {
    return { valid: false, error: 'اسم المستخدم هذا محجوز للنظام، اختر اسمًا آخر.' };
  }
  return { valid: true, normalized: trimmed, lower };
}

export function directChatId(uidA: string, uidB: string): string {
  return uidA < uidB ? `${uidA}_${uidB}` : `${uidB}_${uidA}`;
}
