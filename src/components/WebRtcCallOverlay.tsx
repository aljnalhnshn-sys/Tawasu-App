import React, { useEffect, useRef, useState } from 'react';
import { Mic, MicOff, PhoneOff, Video } from 'lucide-react';
import {
  addIceCandidate,
  answerCall,
  createOutgoingCall,
  subscribeCallSession,
  subscribeIceCandidates,
  updateCallStatus,
} from '../services/tawasulService';
import type { CallSession, CallType, UserPublicProfile } from '../types/tawasul';

interface WebRtcCallOverlayProps {
  currentUser: UserPublicProfile;
  peerUser?: UserPublicProfile | null;
  incomingSession?: CallSession | null;
  callType: CallType;
  onClose: () => void;
}

const RTC_CONFIG: RTCConfiguration = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
  ],
};

export const WebRtcCallOverlay: React.FC<WebRtcCallOverlayProps> = ({
  currentUser,
  peerUser,
  incomingSession,
  callType,
  onClose,
}) => {
  const [session, setSession] = useState<CallSession | null>(incomingSession || null);
  const [muted, setMuted] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const callIdRef = useRef<string | null>(incomingSession?.id || null);
  const answerSetRef = useRef(false);

  const isVideo = (incomingSession?.type || callType) === 'VIDEO';
  const peerName = incomingSession
    ? incomingSession.callerName
    : peerUser?.displayName || 'مستخدم';

  useEffect(() => {
    let active = true;
    let unsubSession: (() => void) | null = null;
    let unsubIce: (() => void) | null = null;

    const cleanupMedia = () => {
      localStreamRef.current?.getTracks().forEach((t) => t.stop());
      pcRef.current?.close();
      pcRef.current = null;
    };

    const startCall = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: isVideo,
        });
        if (!active) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        localStreamRef.current = stream;
        if (localVideoRef.current && isVideo) {
          localVideoRef.current.srcObject = stream;
        }

        const pc = new RTCPeerConnection(RTC_CONFIG);
        pcRef.current = pc;
        stream.getTracks().forEach((track) => pc.addTrack(track, stream));

        pc.ontrack = (event) => {
          const [remoteStream] = event.streams;
          if (!remoteStream) return;
          if (isVideo && remoteVideoRef.current) {
            remoteVideoRef.current.srcObject = remoteStream;
          }
          if (remoteAudioRef.current) {
            remoteAudioRef.current.srcObject = remoteStream;
          }
        };

        pc.onicecandidate = (event) => {
          if (event.candidate && callIdRef.current) {
            addIceCandidate(callIdRef.current, {
              senderUid: currentUser.uid,
              sdpMid: event.candidate.sdpMid || '',
              sdpMLineIndex: event.candidate.sdpMLineIndex ?? 0,
              sdpCandidate: event.candidate.candidate,
            });
          }
        };

        if (incomingSession) {
          callIdRef.current = incomingSession.id;
          await pc.setRemoteDescription({ type: 'offer', sdp: incomingSession.offerSdp });
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          await answerCall(incomingSession.id, answer.sdp || '');
        } else if (peerUser) {
          const offer = await pc.createOffer();
          await pc.setLocalDescription(offer);
          const created = await createOutgoingCall({
            caller: currentUser,
            callee: peerUser,
            type: callType,
            offerSdp: offer.sdp || '',
          });
          callIdRef.current = created.id;
          setSession(created);
        }

        if (callIdRef.current) {
          const cid = callIdRef.current;
          unsubSession = subscribeCallSession(cid, async (latest) => {
            if (!latest) return;
            setSession(latest);
            if (!incomingSession && !answerSetRef.current && latest.answerSdp) {
              answerSetRef.current = true;
              await pc.setRemoteDescription({ type: 'answer', sdp: latest.answerSdp });
            }
            if (['ENDED', 'DECLINED', 'MISSED'].includes(latest.status)) {
              cleanupMedia();
              onClose();
            }
          });
          unsubIce = subscribeIceCandidates(cid, currentUser.uid, async (ice) => {
            try {
              await pc.addIceCandidate({
                sdpMid: ice.sdpMid,
                sdpMLineIndex: ice.sdpMLineIndex,
                candidate: ice.sdpCandidate,
              });
            } catch {
              // Ignore stale ICE candidates
            }
          });
        }
      } catch (err) {
        setErrorMsg(
          err instanceof Error
            ? err.message
            : 'تعذر الوصول إلى الميكروفون أو الكاميرا.'
        );
      }
    };

    startCall();

    return () => {
      active = false;
      unsubSession?.();
      unsubIce?.();
      cleanupMedia();
    };
  }, []);

  const handleToggleMute = () => {
    const next = !muted;
    localStreamRef.current?.getAudioTracks().forEach((t) => {
      t.enabled = !next;
    });
    setMuted(next);
  };

  const handleHangUp = async () => {
    if (callIdRef.current) {
      await updateCallStatus(callIdRef.current, 'ENDED');
    }
    localStreamRef.current?.getTracks().forEach((t) => t.stop());
    pcRef.current?.close();
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-emerald-950 text-white flex flex-col justify-between p-6">
      <audio ref={remoteAudioRef} autoPlay playsInline className="hidden" />

      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs text-emerald-300">
            {isVideo ? 'مكالمة مرئية (WebRTC)' : 'مكالمة صوتية (WebRTC)'}
          </p>
          <h2 className="text-xl font-bold font-display">{peerName}</h2>
          <p className="text-xs text-emerald-200/80 mt-0.5">
            {session?.status === 'CONNECTED'
              ? 'متصل الآن'
              : session?.status === 'RINGING'
              ? 'جاري الاتصال...'
              : 'جاري التهيئة...'}
          </p>
        </div>
        {isVideo && (
          <video
            ref={localVideoRef}
            autoPlay
            muted
            playsInline
            className="w-28 h-40 rounded-2xl object-cover bg-slate-900 border border-white/20"
          />
        )}
      </div>

      <div className="flex-1 flex items-center justify-center my-4">
        {errorMsg ? (
          <div className="bg-red-950/60 border border-red-400/30 rounded-2xl p-4 text-sm text-center max-w-sm">
            {errorMsg}
          </div>
        ) : isVideo ? (
          <video
            ref={remoteVideoRef}
            autoPlay
            playsInline
            className="w-full max-w-2xl max-h-[60vh] rounded-3xl object-cover bg-black/40"
          />
        ) : (
          <div className="w-28 h-28 rounded-full bg-emerald-800 flex items-center justify-center text-3xl font-bold">
            {peerName.charAt(0)}
          </div>
        )}
      </div>

      <div className="flex items-center justify-center gap-6 pb-4">
        <button
          type="button"
          onClick={handleToggleMute}
          className="w-14 h-14 rounded-full bg-white/15 hover:bg-white/25 flex items-center justify-center transition-colors"
          aria-label="كتم الميكروفون"
        >
          {muted ? <MicOff className="w-6 h-6" /> : <Mic className="w-6 h-6" />}
        </button>

        <button
          type="button"
          onClick={handleHangUp}
          className="w-16 h-16 rounded-full bg-red-600 hover:bg-red-500 flex items-center justify-center transition-colors"
          aria-label="إنهاء المكالمة"
        >
          <PhoneOff className="w-7 h-7" />
        </button>

        {isVideo && (
          <div className="w-14 h-14 rounded-full bg-white/10 flex items-center justify-center opacity-80">
            <Video className="w-6 h-6" />
          </div>
        )}
      </div>
    </div>
  );
};
