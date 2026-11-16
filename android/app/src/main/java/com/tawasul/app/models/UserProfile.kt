package com.tawasul.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

enum class GroupPrivacySetting {
    EVERYONE,
    CONTACTS_ONLY
}

/**
 * Public user profile stored at `users/{uid}`.
 * Intentionally excludes PII such as `email` or `fcmTokens` (isolated in `users/{uid}/private/info`).
 */
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val showOnlineStatus: Boolean = true,
    val showReadReceipts: Boolean = true,
    val whoCanAddToGroups: String = GroupPrivacySetting.EVERYONE.name,
    val profileCompleted: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Private user PII stored at `users/{uid}/private/info`.
 * Accessible strictly by the account owner (and trusted Cloud Functions for FCM delivery).
 */
data class PrivateUserInfo(
    val uid: String = "",
    val email: String = "",
    val fcmTokens: List<String> = emptyList(),
    val updatedAt: Timestamp? = null
)

/**
 * Atomic username lock document stored at `usernames/{usernameLower}`.
 */
data class UsernameReservation(
    val uid: String = "",
    val usernameLower: String = "",
    val createdAt: Timestamp? = null
)

data class BlockedUser(
    val blockedUid: String = "",
    val blockedUsername: String = "",
    val blockedDisplayName: String = "",
    val createdAt: Timestamp? = null
)
