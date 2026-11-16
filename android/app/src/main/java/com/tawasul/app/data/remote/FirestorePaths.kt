package com.tawasul.app.data.remote

object FirestorePaths {
    const val USERS = "users"
    const val PRIVATE_SUBCOLLECTION = "private"
    const val PRIVATE_INFO_DOC = "info"
    const val BLOCKED_SUBCOLLECTION = "blocked"
    const val USERNAMES = "usernames"
    const val CHATS = "chats"
    const val MEMBERS_SUBCOLLECTION = "members"
    const val MESSAGES_SUBCOLLECTION = "messages"
    const val STATUSES = "statuses"
    const val CALLS = "calls"
    const val ICE_CANDIDATES_SUBCOLLECTION = "iceCandidates"

    const val MAX_MESSAGE_LENGTH = 4000
    const val MAX_GROUP_MEMBERS = 50
    const val MESSAGES_PAGE_SIZE = 30L
    const val STATUS_TTL_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Generates a deterministic, order-independent 1:1 chat document ID.
     * Prevents duplicate direct chats between the same pair of UIDs.
     */
    fun directChatId(uidA: String, uidB: String): String {
        require(uidA.isNotBlank() && uidB.isNotBlank()) { "UIDs must not be blank" }
        require(uidA != uidB) { "Cannot create direct chat with self" }
        return if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
    }
}
