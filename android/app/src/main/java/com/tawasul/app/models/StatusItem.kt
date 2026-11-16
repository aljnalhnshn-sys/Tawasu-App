package com.tawasul.app.models

import com.google.firebase.Timestamp

enum class StatusMediaType {
    TEXT,
    IMAGE,
    VIDEO
}

data class StatusItem(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val type: String = StatusMediaType.TEXT.name,
    val textContent: String = "",
    val backgroundColorHex: String = "#065F46",
    val mediaUrl: String = "",
    val viewerUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
)
