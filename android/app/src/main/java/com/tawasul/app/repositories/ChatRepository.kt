package com.tawasul.app.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tawasul.app.data.remote.FirestorePaths
import com.tawasul.app.models.Chat
import com.tawasul.app.models.ChatType
import com.tawasul.app.models.DeliveryStatus
import com.tawasul.app.models.MemberRole
import com.tawasul.app.models.Message
import com.tawasul.app.models.MessageType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    private val chatsCol get() = firestore.collection(FirestorePaths.CHATS)

    fun observeUserChats(currentUid: String): Flow<List<Chat>> = callbackFlow {
        val reg = chatsCol
            .whereArrayContains("participantIds", currentUid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(chats)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Deterministically gets or creates a single 1:1 chat between two UIDs.
     * Prevents duplicate conversations between the same pair of accounts.
     */
    suspend fun getOrCreateDirectChat(currentUid: String, otherUid: String): Result<Chat> = runCatching {
        if (userRepository.isBlockedBetween(currentUid, otherUid)) {
            throw IllegalStateException("لا يمكن بدء محادثة بسبب وجود حظر بين الحسابين.")
        }

        val chatId = FirestorePaths.directChatId(currentUid, otherUid)
        val chatRef = chatsCol.document(chatId)
        val memberCheck = chatRef.collection(FirestorePaths.MEMBERS_SUBCOLLECTION)
            .document(currentUid)
            .get()
            .await()
        if (memberCheck.exists()) {
            val snapshot = chatRef.get().await()
            if (snapshot.exists()) {
                return@runCatching snapshot.toObject(Chat::class.java)?.copy(id = chatId)
                    ?: Chat(id = chatId, type = ChatType.DIRECT.name, participantIds = listOf(currentUid, otherUid))
            }
        }

        val sortedParticipants = listOf(currentUid, otherUid).sorted()
        val batch = firestore.batch()
        val chatPayload = mapOf(
            "id" to chatId,
            "type" to ChatType.DIRECT.name,
            "title" to "",
            "avatarUrl" to "",
            "createdBy" to currentUid,
            "participantIds" to sortedParticipants,
            "lastMessageText" to "",
            "lastMessageType" to MessageType.TEXT.name,
            "lastMessageSenderId" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts" to mapOf(currentUid to 0, otherUid to 0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batch.set(chatRef, chatPayload)

        sortedParticipants.forEach { memberUid ->
            val memberRef = chatRef.collection(FirestorePaths.MEMBERS_SUBCOLLECTION).document(memberUid)
            batch.set(
                memberRef,
                mapOf(
                    "uid" to memberUid,
                    "role" to MemberRole.MEMBER.name,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()

        Chat(
            id = chatId,
            type = ChatType.DIRECT.name,
            createdBy = currentUid,
            participantIds = sortedParticipants,
            unreadCounts = mapOf(currentUid to 0, otherUid to 0),
            createdAt = Timestamp.now()
        )
    }

    suspend fun createGroupChat(
        creatorUid: String,
        title: String,
        memberUids: List<String>,
        avatarUrl: String = ""
    ): Result<Chat> = runCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.length in 2..60) { "يجب أن يكون اسم المجموعة بين 2 و60 حرفًا." }

        val uniqueMembers = (memberUids + creatorUid).distinct()
        require(uniqueMembers.size in 2..FirestorePaths.MAX_GROUP_MEMBERS) {
            "عدد أعضاء المجموعة يجب أن يكون بين 2 و${FirestorePaths.MAX_GROUP_MEMBERS}."
        }

        val chatRef = chatsCol.document()
        val chatId = chatRef.id
        val unreadMap = uniqueMembers.associateWith { 0 }

        val batch = firestore.batch()
        batch.set(
            chatRef,
            mapOf(
                "id" to chatId,
                "type" to ChatType.GROUP.name,
                "title" to cleanTitle,
                "avatarUrl" to avatarUrl.trim().take(1024),
                "createdBy" to creatorUid,
                "participantIds" to uniqueMembers,
                "lastMessageText" to "تم إنشاء المجموعة",
                "lastMessageType" to MessageType.SYSTEM.name,
                "lastMessageSenderId" to creatorUid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "unreadCounts" to unreadMap,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        uniqueMembers.forEach { uid ->
            val role = if (uid == creatorUid) MemberRole.ADMIN.name else MemberRole.MEMBER.name
            batch.set(
                chatRef.collection(FirestorePaths.MEMBERS_SUBCOLLECTION).document(uid),
                mapOf(
                    "uid" to uid,
                    "role" to role,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()

        Chat(
            id = chatId,
            type = ChatType.GROUP.name,
            title = cleanTitle,
            avatarUrl = avatarUrl,
            createdBy = creatorUid,
            participantIds = uniqueMembers,
            lastMessageText = "تم إنشاء المجموعة",
            unreadCounts = unreadMap
        )
    }

    fun observeLatestMessages(chatId: String, pageSize: Long = FirestorePaths.MESSAGES_PAGE_SIZE): Flow<List<Message>> =
        callbackFlow {
            val reg = chatsCol.document(chatId)
                .collection(FirestorePaths.MESSAGES_SUBCOLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val messages = snap?.documents?.mapNotNull { doc ->
                        doc.toObject(Message::class.java)?.copy(id = doc.id)
                    }.orEmpty().reversed()
                    trySend(messages)
                }
            awaitClose { reg.remove() }
        }

    suspend fun loadOlderMessages(chatId: String, oldestCreatedAt: Timestamp): Result<List<Message>> = runCatching {
        val snap = chatsCol.document(chatId)
            .collection(FirestorePaths.MESSAGES_SUBCOLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(oldestCreatedAt)
            .limit(FirestorePaths.MESSAGES_PAGE_SIZE)
            .get()
            .await()

        snap.documents
            .mapNotNull { it.toObject(Message::class.java)?.copy(id = it.id) }
            .reversed()
    }

    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        participantIds: List<String>,
        type: MessageType,
        text: String = "",
        mediaUrl: String = "",
        mediaDurationSec: Int = 0,
        localClientId: String = UUID.randomUUID().toString()
    ): Result<Message> = runCatching {
        val trimmedText = text.trim()
        if (type == MessageType.TEXT) {
            require(trimmedText.isNotEmpty() && trimmedText.length <= FirestorePaths.MAX_MESSAGE_LENGTH) {
                "طول الرسالة يجب أن يكون بين 1 و${FirestorePaths.MAX_MESSAGE_LENGTH} حرفًا."
            }
        } else {
            require(mediaUrl.isNotBlank()) { "رابط الوسائط مطلوب." }
        }

        val chatRef = chatsCol.document(chatId)
        val msgRef = chatRef.collection(FirestorePaths.MESSAGES_SUBCOLLECTION).document()

        val summaryPreview = when (type) {
            MessageType.TEXT -> trimmedText.take(120)
            MessageType.IMAGE -> "صورة"
            MessageType.VIDEO -> "فيديو"
            MessageType.AUDIO -> "رسالة صوتية"
            MessageType.SYSTEM -> trimmedText.take(120)
        }

        val cleanMediaUrl = mediaUrl.trim().take(1048576)
        val batch = firestore.batch()
        batch.set(
            msgRef,
            mapOf(
                "id" to msgRef.id,
                "chatId" to chatId,
                "senderId" to senderId,
                "type" to type.name,
                "text" to trimmedText,
                "mediaUrl" to cleanMediaUrl,
                "mediaDurationSec" to mediaDurationSec.coerceIn(0, 3600),
                "deliveryStatus" to DeliveryStatus.SENT.name,
                "readBy" to mapOf(senderId to System.currentTimeMillis()),
                "deletedForUids" to emptyList<String>(),
                "localClientId" to localClientId,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        val chatUpdates = mutableMapOf<String, Any>(
            "lastMessageText" to summaryPreview,
            "lastMessageType" to type.name,
            "lastMessageSenderId" to senderId,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        participantIds.filter { it != senderId }.forEach { recipientUid ->
            chatUpdates["unreadCounts.$recipientUid"] = FieldValue.increment(1)
        }
        batch.update(chatRef, chatUpdates)
        batch.commit().await()

        Message(
            id = msgRef.id,
            chatId = chatId,
            senderId = senderId,
            type = type.name,
            text = trimmedText,
            mediaUrl = mediaUrl,
            mediaDurationSec = mediaDurationSec,
            deliveryStatus = DeliveryStatus.SENT.name,
            localClientId = localClientId,
            createdAt = Timestamp.now()
        )
    }

    suspend fun markChatAsRead(chatId: String, currentUid: String, sendReadReceipt: Boolean) {
        runCatching {
            val chatRef = chatsCol.document(chatId)
            chatRef.update(
                mapOf(
                    "unreadCounts.$currentUid" to 0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (sendReadReceipt) {
                val unreadSnap = chatRef.collection(FirestorePaths.MESSAGES_SUBCOLLECTION)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()
                val batch = firestore.batch()
                var hasWrites = false
                unreadSnap.documents.forEach { doc ->
                    val senderId = doc.getString("senderId")
                    if (senderId != null && senderId != currentUid) {
                        val status = doc.getString("deliveryStatus")
                        if (status != DeliveryStatus.READ.name) {
                            batch.update(
                                doc.reference,
                                mapOf(
                                    "deliveryStatus" to DeliveryStatus.READ.name,
                                    "readBy.$currentUid" to System.currentTimeMillis()
                                )
                            )
                            hasWrites = true
                        }
                    }
                }
                if (hasWrites) batch.commit().await()
            }
        }
    }

    /**
     * Per-user deletion policy: hides the message from the current user's account view only.
     * The UI explicitly explains this does not wipe cached copies on other participants' devices.
     */
    suspend fun deleteMessageForCurrentUser(
        chatId: String,
        messageId: String,
        currentUid: String
    ): Result<Unit> = runCatching {
        chatsCol.document(chatId)
            .collection(FirestorePaths.MESSAGES_SUBCOLLECTION)
            .document(messageId)
            .update("deletedForUids", FieldValue.arrayUnion(currentUid))
            .await()
    }
}
