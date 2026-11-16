package com.tawasul.app.models

import com.google.firebase.Timestamp

enum class ChatType {
    DIRECT,
    GROUP
}

enum class MemberRole {
    ADMIN,
    MEMBER
}

data class Chat(
    val id: String = "",
    val type: String = ChatType.DIRECT.name,
    val title: String = "",
    val avatarUrl: String = "",
    val createdBy: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageType: String = MessageType.TEXT.name,
    val lastMessageSenderId: String = "",
    val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isGroup: Boolean get() = type == ChatType.GROUP.name
}

data class ChatMember(
    val uid: String = "",
    val role: String = MemberRole.MEMBER.name,
    val joinedAt: Timestamp? = null,
    val lastReadAt: Timestamp? = null
)
