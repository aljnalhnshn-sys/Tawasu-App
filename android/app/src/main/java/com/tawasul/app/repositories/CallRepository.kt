package com.tawasul.app.repositories

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tawasul.app.data.remote.FirestorePaths
import com.tawasul.app.models.CallSession
import com.tawasul.app.models.CallStatus
import com.tawasul.app.models.CallType
import com.tawasul.app.models.IceCandidateModel
import com.tawasul.app.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val callsCol get() = firestore.collection(FirestorePaths.CALLS)

    fun observeIncomingCalls(currentUid: String): Flow<CallSession?> = callbackFlow {
        val reg = callsCol
            .whereEqualTo("calleeUid", currentUid)
            .whereEqualTo("status", CallStatus.RINGING.name)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val incoming = snap?.documents?.firstOrNull()?.let { doc ->
                    doc.toObject(CallSession::class.java)?.copy(id = doc.id)
                }
                trySend(incoming)
            }
        awaitClose { reg.remove() }
    }

    fun observeCallHistory(currentUid: String): Flow<List<CallSession>> = callbackFlow {
        val reg = callsCol
            .whereArrayContains("participantUids", currentUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(CallSession::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = callbackFlow {
        val reg = callsCol.document(callId).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(CallSession::class.java)?.copy(id = callId))
        }
        awaitClose { reg.remove() }
    }

    suspend fun createOutgoingCall(
        caller: UserProfile,
        callee: UserProfile,
        type: CallType,
        offerSdp: String
    ): Result<CallSession> = runCatching {
        val ref = callsCol.document()
        val participants = listOf(caller.uid, callee.uid)
        val payload = mapOf(
            "id" to ref.id,
            "callerUid" to caller.uid,
            "callerName" to caller.displayName,
            "callerAvatarUrl" to caller.avatarUrl,
            "calleeUid" to callee.uid,
            "calleeName" to callee.displayName,
            "calleeAvatarUrl" to callee.avatarUrl,
            "participantUids" to participants,
            "type" to type.name,
            "status" to CallStatus.RINGING.name,
            "offerSdp" to offerSdp,
            "answerSdp" to "",
            "createdAt" to FieldValue.serverTimestamp()
        )
        ref.set(payload).await()
        CallSession(
            id = ref.id,
            callerUid = caller.uid,
            callerName = caller.displayName,
            calleeUid = callee.uid,
            calleeName = callee.displayName,
            participantUids = participants,
            type = type.name,
            status = CallStatus.RINGING.name,
            offerSdp = offerSdp
        )
    }

    suspend fun answerCall(callId: String, answerSdp: String): Result<Unit> = runCatching {
        callsCol.document(callId).update(
            mapOf(
                "answerSdp" to answerSdp,
                "status" to CallStatus.CONNECTED.name
            )
        ).await()
    }

    suspend fun updateCallStatus(callId: String, status: CallStatus) {
        runCatching {
            val updates = mutableMapOf<String, Any>("status" to status.name)
            if (status == CallStatus.ENDED || status == CallStatus.DECLINED || status == CallStatus.MISSED) {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            callsCol.document(callId).update(updates).await()
        }
    }

    suspend fun addIceCandidate(callId: String, candidate: IceCandidateModel) {
        runCatching {
            callsCol.document(callId)
                .collection(FirestorePaths.ICE_CANDIDATES_SUBCOLLECTION)
                .add(
                    mapOf(
                        "senderUid" to candidate.senderUid,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "sdpCandidate" to candidate.sdpCandidate
                    )
                ).await()
        }
    }

    fun observeIceCandidates(callId: String, currentUid: String): Flow<IceCandidateModel> = callbackFlow {
        val reg = callsCol.document(callId)
            .collection(FirestorePaths.ICE_CANDIDATES_SUBCOLLECTION)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    val model = change.document.toObject(IceCandidateModel::class.java)
                    if (model.senderUid != currentUid) {
                        trySend(model)
                    }
                }
            }
        awaitClose { reg.remove() }
    }
}
