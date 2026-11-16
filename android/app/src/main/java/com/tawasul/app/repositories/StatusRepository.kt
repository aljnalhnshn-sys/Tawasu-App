package com.tawasul.app.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tawasul.app.data.remote.FirestorePaths
import com.tawasul.app.models.StatusItem
import com.tawasul.app.models.StatusMediaType
import com.tawasul.app.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val statusesCol get() = firestore.collection(FirestorePaths.STATUSES)

    fun observeActiveStatuses(blockedUids: Set<String>): Flow<List<StatusItem>> = callbackFlow {
        val reg = statusesCol
            .whereGreaterThan("expiresAt", Timestamp.now())
            .orderBy("expiresAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(StatusItem::class.java)?.copy(id = doc.id)
                }.orEmpty().filter { it.authorUid !in blockedUids }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun publishStatus(
        author: UserProfile,
        type: StatusMediaType,
        textContent: String = "",
        mediaUrl: String = "",
        backgroundColorHex: String = "#065F46"
    ): Result<Unit> = runCatching {
        val nowMillis = System.currentTimeMillis()
        val expiresAt = Timestamp(Date(nowMillis + FirestorePaths.STATUS_TTL_MILLIS))
        val docRef = statusesCol.document()
        docRef.set(
            mapOf(
                "id" to docRef.id,
                "authorUid" to author.uid,
                "authorName" to author.displayName,
                "authorUsername" to author.username,
                "authorAvatarUrl" to author.avatarUrl,
                "type" to type.name,
                "textContent" to textContent.trim().take(500),
                "backgroundColorHex" to backgroundColorHex,
                "mediaUrl" to mediaUrl,
                "viewerUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt
            )
        ).await()
    }

    suspend fun markStatusViewed(statusId: String, viewerUid: String) {
        runCatching {
            statusesCol.document(statusId)
                .update("viewerUids", FieldValue.arrayUnion(viewerUid))
                .await()
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> = runCatching {
        statusesCol.document(statusId).delete().await()
    }
}
