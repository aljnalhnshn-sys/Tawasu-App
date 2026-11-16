package com.tawasul.app.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawasul.app.models.CallSession
import com.tawasul.app.models.CallStatus
import com.tawasul.app.models.CallType
import com.tawasul.app.models.UserProfile
import com.tawasul.app.repositories.AuthRepository
import com.tawasul.app.repositories.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

data class ActiveCallUiState(
    val session: CallSession? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isEnded: Boolean = false
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val callRepository: CallRepository,
    val webRtcCallManager: WebRtcCallManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveCallUiState())
    val uiState: StateFlow<ActiveCallUiState> = _uiState.asStateFlow()

    private var activeCallId: String? = null
    private var answerApplied = false

    fun startOutgoingCall(
        caller: UserProfile,
        callee: UserProfile,
        type: CallType,
        localRenderer: SurfaceViewRenderer?,
        remoteRenderer: SurfaceViewRenderer?
    ) {
        val currentUid = authRepository.currentUser?.uid ?: return
        webRtcCallManager.startPeerConnection(
            isVideo = type == CallType.VIDEO,
            localRenderer = localRenderer,
            remoteRenderer = remoteRenderer,
            currentUid = currentUid,
            onIceCandidateGenerated = { candidate ->
                activeCallId?.let { id ->
                    viewModelScope.launch { callRepository.addIceCandidate(id, candidate) }
                }
            }
        )

        webRtcCallManager.createOffer { offerSdp ->
            viewModelScope.launch {
                callRepository.createOutgoingCall(caller, callee, type, offerSdp)
                    .onSuccess { session ->
                        activeCallId = session.id
                        _uiState.update { it.copy(session = session) }
                        observeSessionAndIce(session.id, currentUid, isCaller = true)
                    }
            }
        }
    }

    fun acceptIncomingCall(
        callId: String,
        localRenderer: SurfaceViewRenderer?,
        remoteRenderer: SurfaceViewRenderer?
    ) {
        val currentUid = authRepository.currentUser?.uid ?: return
        activeCallId = callId
        viewModelScope.launch {
            callRepository.observeCallSession(callId).collect { session ->
                if (session == null) return@collect
                _uiState.update { it.copy(session = session) }
                if (!answerApplied && session.offerSdp.isNotBlank()) {
                    answerApplied = true
                    webRtcCallManager.startPeerConnection(
                        isVideo = session.type == CallType.VIDEO.name,
                        localRenderer = localRenderer,
                        remoteRenderer = remoteRenderer,
                        currentUid = currentUid,
                        onIceCandidateGenerated = { candidate ->
                            viewModelScope.launch { callRepository.addIceCandidate(callId, candidate) }
                        }
                    )
                    webRtcCallManager.acceptOfferAndCreateAnswer(session.offerSdp) { answerSdp ->
                        viewModelScope.launch { callRepository.answerCall(callId, answerSdp) }
                    }
                    observeIceCandidates(callId, currentUid)
                }
                if (session.status in listOf(CallStatus.ENDED.name, CallStatus.DECLINED.name, CallStatus.MISSED.name)) {
                    endLocalSession()
                }
            }
        }
    }

    private fun observeSessionAndIce(callId: String, currentUid: String, isCaller: Boolean) {
        viewModelScope.launch {
            callRepository.observeCallSession(callId).collect { session ->
                if (session == null) return@collect
                _uiState.update { it.copy(session = session) }
                if (isCaller && !answerApplied && session.answerSdp.isNotBlank()) {
                    answerApplied = true
                    webRtcCallManager.setRemoteAnswer(session.answerSdp)
                }
                if (session.status in listOf(CallStatus.ENDED.name, CallStatus.DECLINED.name, CallStatus.MISSED.name)) {
                    endLocalSession()
                }
            }
        }
        observeIceCandidates(callId, currentUid)
    }

    private fun observeIceCandidates(callId: String, currentUid: String) {
        viewModelScope.launch {
            callRepository.observeIceCandidates(callId, currentUid).collect { candidate ->
                webRtcCallManager.addRemoteIceCandidate(candidate)
            }
        }
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        webRtcCallManager.toggleMute(next)
        _uiState.update { it.copy(isMuted = next) }
    }

    fun switchCamera() {
        webRtcCallManager.switchCamera()
    }

    fun hangUp() {
        val callId = activeCallId ?: return
        viewModelScope.launch {
            callRepository.updateCallStatus(callId, CallStatus.ENDED)
            endLocalSession()
        }
    }

    fun declineCall(callId: String) {
        viewModelScope.launch {
            callRepository.updateCallStatus(callId, CallStatus.DECLINED)
            endLocalSession()
        }
    }

    private fun endLocalSession() {
        webRtcCallManager.closeSession()
        _uiState.update { it.copy(isEnded = true) }
    }
}
