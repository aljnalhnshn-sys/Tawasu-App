package com.tawasul.app.messaging

import android.content.Context
import com.tawasul.app.models.IceCandidateModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcCallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    fun initializeFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startPeerConnection(
        isVideo: Boolean,
        localRenderer: SurfaceViewRenderer?,
        remoteRenderer: SurfaceViewRenderer?,
        onIceCandidateGenerated: (IceCandidateModel) -> Unit,
        currentUid: String
    ) {
        initializeFactory()
        val peerFactory = factory ?: return

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    onIceCandidateGenerated(
                        IceCandidateModel(
                            senderUid = currentUid,
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            sdpCandidate = candidate.sdp
                        )
                    )
                }

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack && remoteRenderer != null) {
                        track.addSink(remoteRenderer)
                    }
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            }
        )

        val audioSource = peerFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerFactory.createAudioTrack("TAWASUL_AUDIO", audioSource).also {
            peerConnection?.addTrack(it, listOf("TAWASUL_STREAM"))
        }

        if (isVideo) {
            val enumerator = Camera2Enumerator(context)
            val frontCamera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
            if (frontCamera != null) {
                videoCapturer = enumerator.createCapturer(frontCamera, null)
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                val videoSource = peerFactory.createVideoSource(false)
                videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                videoCapturer?.startCapture(720, 1280, 30)
                localVideoTrack = peerFactory.createVideoTrack("TAWASUL_VIDEO", videoSource).also { track ->
                    if (localRenderer != null) track.addSink(localRenderer)
                    peerConnection?.addTrack(track, listOf("TAWASUL_STREAM"))
                }
            }
        }
    }

    fun createOffer(onOfferCreated: (String) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onOfferCreated(sdp.description)
            }
        }, constraints)
    }

    fun acceptOfferAndCreateAnswer(offerSdp: String, onAnswerCreated: (String) -> Unit) {
        val pc = peerConnection ?: return
        pc.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        )
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onAnswerCreated(sdp.description)
            }
        }, MediaConstraints())
    }

    fun setRemoteAnswer(answerSdp: String) {
        peerConnection?.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        )
    }

    fun addRemoteIceCandidate(candidate: IceCandidateModel) {
        peerConnection?.addIceCandidate(
            IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdpCandidate)
        )
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun closeSession() {
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { peerConnection?.close() }
        videoCapturer = null
        surfaceTextureHelper = null
        peerConnection = null
        localAudioTrack = null
        localVideoTrack = null
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
