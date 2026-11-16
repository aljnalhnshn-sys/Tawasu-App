package com.tawasul.app.messaging

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawasul.app.data.local.ConnectivityObserver
import com.tawasul.app.data.local.PreferencesManager
import com.tawasul.app.models.BlockedUser
import com.tawasul.app.models.CallSession
import com.tawasul.app.models.Chat
import com.tawasul.app.models.GroupPrivacySetting
import com.tawasul.app.models.StatusItem
import com.tawasul.app.models.StatusMediaType
import com.tawasul.app.models.UserProfile
import com.tawasul.app.repositories.AuthRepository
import com.tawasul.app.repositories.CallRepository
import com.tawasul.app.repositories.ChatRepository
import com.tawasul.app.repositories.MediaRepository
import com.tawasul.app.repositories.StatusRepository
import com.tawasul.app.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainHubUiState(
    val currentUser: UserProfile? = null,
    val chats: List<Chat> = emptyList(),
    val peerProfiles: Map<String, UserProfile> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<UserProfile> = emptyList(),
    val isSearching: Boolean = false,
    val statuses: List<StatusItem> = emptyList(),
    val callHistory: List<CallSession> = emptyList(),
    val incomingCall: CallSession? = null,
    val blockedUsers: List<BlockedUser> = emptyList(),
    val isOnline: Boolean = true,
    val isDarkMode: Boolean? = null,
    val isLoadingChats: Boolean = true,
    val actionMessage: String? = null
)

@HiltViewModel
class MainHubViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val statusRepository: StatusRepository,
    private val callRepository: CallRepository,
    private val mediaRepository: MediaRepository,
    private val preferencesManager: PreferencesManager,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainHubUiState())
    val uiState: StateFlow<MainHubUiState> = _uiState.asStateFlow()

    init {
        val currentUid = authRepository.currentUser?.uid
        if (currentUid != null) {
            observeAllData(currentUid)
        }

        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }

        viewModelScope.launch {
            preferencesManager.isDarkModeFlow.collect { dark ->
                _uiState.update { it.copy(isDarkMode = dark) }
            }
        }
    }

    private fun observeAllData(uid: String) {
        viewModelScope.launch {
            userRepository.observeUserProfile(uid).collect { profile ->
                _uiState.update { it.copy(currentUser = profile) }
            }
        }
        viewModelScope.launch {
            chatRepository.observeUserChats(uid).collect { chats ->
                _uiState.update { it.copy(chats = chats, isLoadingChats = false) }
                val missingPeerIds = chats
                    .filter { !it.isGroup }
                    .flatMap { it.participantIds }
                    .filter { it != uid && it !in uiState.value.peerProfiles }
                    .distinct()

                missingPeerIds.forEach { peerId ->
                    val peer = userRepository.getUserProfile(peerId)
                    if (peer != null) {
                        _uiState.update { state ->
                            state.copy(peerProfiles = state.peerProfiles + (peerId to peer))
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userRepository.observeBlockedUsers(uid).collect { blocked ->
                _uiState.update { it.copy(blockedUsers = blocked) }
            }
        }
        viewModelScope.launch {
            statusRepository.observeActiveStatuses(emptySet()).collect { items ->
                val blockedIds = uiState.value.blockedUsers.map { it.blockedUid }.toSet()
                _uiState.update { state ->
                    state.copy(statuses = items.filter { it.authorUid !in blockedIds })
                }
            }
        }
        viewModelScope.launch {
            callRepository.observeCallHistory(uid).collect { history ->
                _uiState.update { it.copy(callHistory = history) }
            }
        }
        viewModelScope.launch {
            callRepository.observeIncomingCalls(uid).collect { incoming ->
                _uiState.update { it.copy(incomingCall = incoming) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val uid = authRepository.currentUser?.uid ?: return
        if (query.trim().isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val blocked = uiState.value.blockedUsers.map { it.blockedUid }.toSet()
            userRepository.searchUsersByUsername(query, uid, blocked)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSearching = false, actionMessage = err.localizedMessage)
                    }
                }
        }
    }

    fun startOrOpenDirectChat(otherUser: UserProfile, onReady: (Chat) -> Unit) {
        val currentUid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            chatRepository.getOrCreateDirectChat(currentUid, otherUser.uid)
                .onSuccess { chat ->
                    _uiState.update {
                        it.copy(peerProfiles = it.peerProfiles + (otherUser.uid to otherUser))
                    }
                    onReady(chat)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(actionMessage = err.localizedMessage) }
                }
        }
    }

    fun createGroup(title: String, memberUids: List<String>, onCreated: (Chat) -> Unit) {
        val currentUid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            chatRepository.createGroupChat(currentUid, title, memberUids)
                .onSuccess(onCreated)
                .onFailure { err ->
                    _uiState.update { it.copy(actionMessage = err.localizedMessage) }
                }
        }
    }

    fun publishTextStatus(text: String, colorHex: String) {
        val me = uiState.value.currentUser ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            statusRepository.publishStatus(
                author = me,
                type = StatusMediaType.TEXT,
                textContent = text,
                backgroundColorHex = colorHex
            ).onSuccess {
                _uiState.update { it.copy(actionMessage = "تم نشر حالتك لمدة 24 ساعة.") }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.localizedMessage) }
            }
        }
    }

    fun publishMediaStatus(uri: Uri, isVideo: Boolean) {
        val me = uiState.value.currentUser ?: return
        viewModelScope.launch {
            mediaRepository.uploadStatusMedia(
                uid = me.uid,
                uri = uri,
                extension = if (isVideo) "mp4" else "jpg",
                isVideo = isVideo
            ).onSuccess { url ->
                statusRepository.publishStatus(
                    author = me,
                    type = if (isVideo) StatusMediaType.VIDEO else StatusMediaType.IMAGE,
                    mediaUrl = url
                )
                _uiState.update { it.copy(actionMessage = "تم نشر الحالة بنجاح.") }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.localizedMessage) }
            }
        }
    }

    fun markStatusViewed(statusId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            statusRepository.markStatusViewed(statusId, uid)
        }
    }

    fun updateProfileDetails(displayName: String, bio: String, newUsername: String?, avatarUri: Uri?) {
        val me = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val finalAvatar = if (avatarUri != null) {
                mediaRepository.uploadAvatar(me.uid, avatarUri).getOrElse {
                    _uiState.update { state -> state.copy(actionMessage = it.localizedMessage) }
                    return@launch
                }
            } else me.avatarUrl

            userRepository.updateProfile(
                uid = me.uid,
                displayName = displayName,
                bio = bio,
                avatarUrl = finalAvatar,
                currentUsernameLower = me.usernameLower,
                newRawUsername = newUsername
            ).onSuccess {
                _uiState.update { it.copy(actionMessage = "تم تحديث الملف الشخصي.") }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.localizedMessage) }
            }
        }
    }

    fun updatePrivacy(
        showOnlineStatus: Boolean,
        showReadReceipts: Boolean,
        whoCanAddToGroups: GroupPrivacySetting
    ) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            userRepository.updatePrivacySettings(uid, showOnlineStatus, showReadReceipts, whoCanAddToGroups)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "تم حفظ إعدادات الخصوصية.") }
                }
        }
    }

    fun blockUser(target: UserProfile) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            userRepository.blockUser(uid, target)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "تم حظر @${target.username}.") }
                }
        }
    }

    fun unblockUser(targetUid: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            userRepository.unblockUser(uid, targetUid)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "تم إلغاء الحظر.") }
                }
        }
    }

    fun setThemePreference(isDark: Boolean?) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(isDark)
        }
    }

    fun declineIncomingCall(callId: String) {
        viewModelScope.launch {
            callRepository.updateCallStatus(callId, com.tawasul.app.models.CallStatus.DECLINED)
            _uiState.update { it.copy(incomingCall = null) }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(actionMessage = message) }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
