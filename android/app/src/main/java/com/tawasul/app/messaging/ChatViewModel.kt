package com.tawasul.app.messaging

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.tawasul.app.data.local.ConnectivityObserver
import com.tawasul.app.models.DeliveryStatus
import com.tawasul.app.models.Message
import com.tawasul.app.models.MessageType
import com.tawasul.app.models.UserProfile
import com.tawasul.app.repositories.AuthRepository
import com.tawasul.app.repositories.ChatRepository
import com.tawasul.app.repositories.MediaRepository
import com.tawasul.app.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatDetailUiState(
    val chatId: String = "",
    val chatTitle: String = "",
    val peerProfile: UserProfile? = null,
    val isGroup: Boolean = false,
    val participantIds: List<String> = emptyList(),
    val messages: List<Message> = emptyList(),
    val pendingOrFailedMessages: List<Message> = emptyList(),
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val isOnline: Boolean = true,
    val isRecordingVoice: Boolean = false,
    val isUploadingMedia: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val voiceRecorderManager: VoiceRecorderManager,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    private val olderPages = MutableStateFlow<List<Message>>(emptyList())
    private var activeChatId: String = ""

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    fun openChat(
        chatId: String,
        participantIds: List<String>,
        isGroup: Boolean,
        groupTitle: String,
        peerUid: String?
    ) {
        if (activeChatId == chatId) return
        activeChatId = chatId
        val currentUid = authRepository.currentUser?.uid ?: return

        _uiState.update {
            it.copy(
                chatId = chatId,
                isGroup = isGroup,
                chatTitle = groupTitle,
                participantIds = participantIds
            )
        }

        if (!isGroup && !peerUid.isNullOrBlank()) {
            viewModelScope.launch {
                userRepository.observeUserProfile(peerUid).collect { peer ->
                    _uiState.update {
                        it.copy(
                            peerProfile = peer,
                            chatTitle = peer?.displayName ?: it.chatTitle
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            val myProfile = userRepository.getUserProfile(currentUid)
            chatRepository.observeLatestMessages(chatId).collect { latest ->
                val merged = (olderPages.value + latest)
                    .distinctBy { it.id }
                    .filter { currentUid !in it.deletedForUids }
                _uiState.update { it.copy(messages = merged) }
                chatRepository.markChatAsRead(
                    chatId = chatId,
                    currentUid = currentUid,
                    sendReadReceipt = myProfile?.showReadReceipts ?: true
                )
            }
        }
    }

    fun loadOlderMessages() {
        val oldest = uiState.value.messages.firstOrNull()?.createdAt ?: return
        if (uiState.value.isLoadingOlder || !uiState.value.hasMoreOlder) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }
            chatRepository.loadOlderMessages(activeChatId, oldest)
                .onSuccess { older ->
                    olderPages.update { current -> (older + current).distinctBy { it.id } }
                    _uiState.update { state ->
                        val combined = (olderPages.value + state.messages).distinctBy { it.id }
                        state.copy(
                            messages = combined,
                            isLoadingOlder = false,
                            hasMoreOlder = older.isNotEmpty()
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingOlder = false,
                            errorMessage = err.localizedMessage ?: "تعذر تحميل الرسائل السابقة."
                        )
                    }
                }
        }
    }

    fun sendTextMessage(rawText: String) {
        val senderUid = authRepository.currentUser?.uid ?: return
        val cleanText = rawText.trim()
        if (cleanText.isEmpty()) return

        val localId = UUID.randomUUID().toString()
        val optimisticFailedDraft = Message(
            id = "local_$localId",
            chatId = activeChatId,
            senderId = senderUid,
            type = MessageType.TEXT.name,
            text = cleanText,
            deliveryStatus = DeliveryStatus.SENDING.name,
            localClientId = localId,
            createdAt = Timestamp.now()
        )
        _uiState.update {
            it.copy(pendingOrFailedMessages = it.pendingOrFailedMessages + optimisticFailedDraft)
        }
        dispatchMessage(optimisticFailedDraft)
    }

    fun retryFailedMessage(failedMessage: Message) {
        val retrying = failedMessage.copy(deliveryStatus = DeliveryStatus.SENDING.name)
        _uiState.update { state ->
            state.copy(
                pendingOrFailedMessages = state.pendingOrFailedMessages.map {
                    if (it.localClientId == failedMessage.localClientId) retrying else it
                }
            )
        }
        dispatchMessage(retrying)
    }

    private fun dispatchMessage(pendingMsg: Message) {
        viewModelScope.launch {
            if (!uiState.value.isOnline) {
                markLocalMessageFailed(pendingMsg.localClientId, "لا يوجد اتصال بالإنترنت. اضغط لإعادة المحاولة.")
                return@launch
            }

            chatRepository.sendMessage(
                chatId = activeChatId,
                senderId = pendingMsg.senderId,
                participantIds = uiState.value.participantIds,
                type = pendingMsg.messageTypeEnum,
                text = pendingMsg.text,
                mediaUrl = pendingMsg.mediaUrl,
                mediaDurationSec = pendingMsg.mediaDurationSec,
                localClientId = pendingMsg.localClientId
            ).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        pendingOrFailedMessages = state.pendingOrFailedMessages.filterNot {
                            it.localClientId == pendingMsg.localClientId
                        }
                    )
                }
            }.onFailure { err ->
                markLocalMessageFailed(
                    pendingMsg.localClientId,
                    err.localizedMessage ?: "فشل إرسال الرسالة."
                )
            }
        }
    }

    private fun markLocalMessageFailed(localClientId: String, reason: String) {
        _uiState.update { state ->
            state.copy(
                pendingOrFailedMessages = state.pendingOrFailedMessages.map {
                    if (it.localClientId == localClientId) {
                        it.copy(deliveryStatus = DeliveryStatus.FAILED.name)
                    } else it
                },
                errorMessage = reason
            )
        }
    }

    fun sendMediaUri(uri: Uri, isVideo: Boolean) {
        val senderUid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingMedia = true, errorMessage = null) }
            mediaRepository.uploadChatMedia(
                chatId = activeChatId,
                senderUid = senderUid,
                uri = uri,
                folder = if (isVideo) "videos" else "images",
                extension = if (isVideo) "mp4" else "jpg",
                isVideo = isVideo
            ).onSuccess { url ->
                chatRepository.sendMessage(
                    chatId = activeChatId,
                    senderId = senderUid,
                    participantIds = uiState.value.participantIds,
                    type = if (isVideo) MessageType.VIDEO else MessageType.IMAGE,
                    mediaUrl = url
                )
                _uiState.update { it.copy(isUploadingMedia = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUploadingMedia = false,
                        errorMessage = err.localizedMessage ?: "فشل رفع الملف."
                    )
                }
            }
        }
    }

    fun startVoiceRecording() {
        voiceRecorderManager.startRecording()
            .onSuccess { _uiState.update { it.copy(isRecordingVoice = true) } }
            .onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "تعذر تشغيل الميكروفون.")
                }
            }
    }

    fun stopAndSendVoiceRecording() {
        val senderUid = authRepository.currentUser?.uid ?: return
        _uiState.update { it.copy(isRecordingVoice = false) }
        val result = voiceRecorderManager.stopRecording() ?: return
        val (uri, durationSec) = result

        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingMedia = true) }
            mediaRepository.uploadChatMedia(
                chatId = activeChatId,
                senderUid = senderUid,
                uri = uri,
                folder = "audio",
                extension = "m4a",
                isVideo = false
            ).onSuccess { url ->
                chatRepository.sendMessage(
                    chatId = activeChatId,
                    senderId = senderUid,
                    participantIds = uiState.value.participantIds,
                    type = MessageType.AUDIO,
                    mediaUrl = url,
                    mediaDurationSec = durationSec
                )
                _uiState.update { it.copy(isUploadingMedia = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isUploadingMedia = false, errorMessage = err.localizedMessage)
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorderManager.cancelRecording()
        _uiState.update { it.copy(isRecordingVoice = false) }
    }

    fun deleteMessageForMe(messageId: String) {
        val currentUid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            chatRepository.deleteMessageForCurrentUser(activeChatId, messageId, currentUid)
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "تعذر حذف الرسالة من حسابك.")
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
