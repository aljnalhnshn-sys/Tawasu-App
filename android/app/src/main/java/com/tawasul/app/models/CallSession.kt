package com.tawasul.app.models

import com.google.firebase.Timestamp

enum class CallType {
    AUDIO,
    VIDEO
}

enum class CallStatus {
    RINGING,
    CONNECTED,
    ENDED,
    DECLINED,
    MISSED
}

data class CallSession(
    val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerAvatarUrl: String = "",
    val calleeUid: String = "",
    val calleeName: String = "",
    val calleeAvatarUrl: String = "",
    val participantUids: List<String> = emptyList(),
    val type: String = CallType.AUDIO.name,
    val status: String = CallStatus.RINGING.name,
    val offerSdp: String = "",
    val answerSdp: String = "",
    val createdAt: Timestamp? = null,
    val endedAt: Timestamp? = null
)

data class IceCandidateModel(
    val id: String = "",
    val senderUid: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdpCandidate: String = ""
)
