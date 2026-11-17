# Tawasul Security Specification (Phase 0)

## Data Invariants
1. Public `users/{uid}` never contains `email` or `fcmTokens`; PII is isolated in `users/{uid}/private/info` readable only by `uid`.
2. Every user profile must hold an atomic reservation in `usernames/{usernameLower}` matching `^[a-z0-9_]{3,20}$`.
3. Direct 1:1 chats use deterministic sorted IDs (`uidA_uidB`) preventing duplicates.
4. Chat messages can only be read or created by verified chat members present in `chats/{chatId}/members/{uid}`.
5. `senderId` on messages and `callerUid` on calls must strictly match `request.auth.uid`.
6. Client writes must use `request.time` for `createdAt` / `updatedAt`.

## Dirty Dozen Payloads (Must Return PERMISSION_DENIED)
1. Unauthenticated read on `/users/{uid}`.
2. Cross-user read on `/users/{otherUid}/private/info`.
3. Creating `/users/{uid}` with `email_verified: false` or spoofed `uid`.
4. Claiming an existing `/usernames/{usernameLower}` owned by another user.
5. Updating `/users/{uid}` with a shadow field `isVerified: true`.
6. Creating a message with `senderId` set to another user's UID.
7. Reading `/chats/{chatId}/messages/{msgId}` as a non-member.
8. Sending a text message exceeding `4000` characters.
9. Creating a direct chat with a non-deterministic ID (`random_123`).
10. Modifying message `text` after creation.
11. Reading `/calls/{callId}` when neither `callerUid` nor `calleeUid`.
12. Deleting another user's `/statuses/{statusId}`.
