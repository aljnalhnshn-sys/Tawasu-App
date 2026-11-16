package com.tawasul.app.models

import com.google.firebase.Timestamp

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    SYSTEM
}

enum class DeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val type: String = MessageType.TEXT.name,
    val text: String = "",
    val mediaUrl: String = "",
    val mediaDurationSec: Int = 0,
    val deliveryStatus: String = DeliveryStatus.SENT.name,
    val readBy: Map<String, Long> = emptyMap(),
    val deletedForUids: List<String> = emptyList(),
    val localClientId: String = "",
    val createdAt: Timestamp? = null
) {
    val messageTypeEnum: MessageType
        get() = MessageType.entries.firstOrNull { it.name == type } ?: MessageType.TEXT

    val statusEnum: DeliveryStatus
        get() = DeliveryStatus.entries.firstOrNull { it.name == deliveryStatus } ?: DeliveryStatus.SENT
}
