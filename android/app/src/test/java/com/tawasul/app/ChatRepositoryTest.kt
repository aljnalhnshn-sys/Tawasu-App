package com.tawasul.app

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.tawasul.app.models.Chat
import com.tawasul.app.models.ChatType
import com.tawasul.app.models.MessageType
import com.tawasul.app.repositories.ChatRepository
import com.tawasul.app.repositories.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    @Test
    fun `getOrCreateDirectChat fails when block exists between users`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val userRepository = mockk<UserRepository> {
            coEvery { isBlockedBetween("uid_a", "uid_b") } returns true
        }
        val repo = ChatRepository(firestore, userRepository)

        val result = repo.getOrCreateDirectChat("uid_a", "uid_b")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `getOrCreateDirectChat creates deterministic chat and both member docs atomically`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val chatsCol = mockk<CollectionReference>(relaxed = true)
        val chatRef = mockk<DocumentReference>(relaxed = true)
        val membersCol = mockk<CollectionReference>(relaxed = true)
        val myMemberRef = mockk<DocumentReference>(relaxed = true)
        val otherMemberRef = mockk<DocumentReference>(relaxed = true)
        val memberSnap = mockk<DocumentSnapshot>(relaxed = true)
        val batch = mockk<WriteBatch>(relaxed = true)

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.document("uid_a_uid_b") } returns chatRef
        every { chatRef.collection("members") } returns membersCol
        every { membersCol.document("uid_a") } returns myMemberRef
        every { membersCol.document("uid_b") } returns otherMemberRef
        every { memberSnap.exists() } returns false
        every { myMemberRef.get() } returns completedTask(memberSnap)
        every { firestore.batch() } returns batch
        every { batch.commit() } returns completedTask(null)

        val userRepository = mockk<UserRepository> {
            coEvery { isBlockedBetween("uid_b", "uid_a") } returns false
        }

        val repo = ChatRepository(firestore, userRepository)
        val result = repo.getOrCreateDirectChat("uid_b", "uid_a")

        assertTrue(result.isSuccess)
        val chat = result.getOrThrow()
        assertEquals("uid_a_uid_b", chat.id)
        assertEquals(ChatType.DIRECT.name, chat.type)
        assertEquals(listOf("uid_a", "uid_b"), chat.participantIds)
        verify(exactly = 1) { batch.set(chatRef, any()) }
        verify(exactly = 1) { batch.set(myMemberRef, any()) }
        verify(exactly = 1) { batch.set(otherMemberRef, any()) }
        verify(exactly = 1) { batch.commit() }
    }

    @Test
    fun `getOrCreateDirectChat returns existing chat when member doc exists`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val chatsCol = mockk<CollectionReference>(relaxed = true)
        val chatRef = mockk<DocumentReference>(relaxed = true)
        val membersCol = mockk<CollectionReference>(relaxed = true)
        val myMemberRef = mockk<DocumentReference>(relaxed = true)
        val memberSnap = mockk<DocumentSnapshot>(relaxed = true)
        val chatSnap = mockk<DocumentSnapshot>(relaxed = true)

        val existingChat = Chat(
            id = "uid_a_uid_b",
            type = ChatType.DIRECT.name,
            participantIds = listOf("uid_a", "uid_b")
        )

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.document("uid_a_uid_b") } returns chatRef
        every { chatRef.collection("members") } returns membersCol
        every { membersCol.document("uid_a") } returns myMemberRef
        every { memberSnap.exists() } returns true
        every { myMemberRef.get() } returns completedTask(memberSnap)
        every { chatSnap.exists() } returns true
        every { chatSnap.toObject(Chat::class.java) } returns existingChat
        every { chatRef.get() } returns completedTask(chatSnap)

        val userRepository = mockk<UserRepository> {
            coEvery { isBlockedBetween("uid_a", "uid_b") } returns false
        }

        val repo = ChatRepository(firestore, userRepository)
        val result = repo.getOrCreateDirectChat("uid_a", "uid_b")

        assertTrue(result.isSuccess)
        assertEquals("uid_a_uid_b", result.getOrThrow().id)
        verify(exactly = 0) { firestore.batch() }
    }

    @Test
    fun `sendMessage validates empty text and writes batch with unread increment`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val chatsCol = mockk<CollectionReference>(relaxed = true)
        val chatRef = mockk<DocumentReference>(relaxed = true)
        val messagesCol = mockk<CollectionReference>(relaxed = true)
        val msgRef = mockk<DocumentReference>(relaxed = true)
        val batch = mockk<WriteBatch>(relaxed = true)

        every { firestore.collection("chats") } returns chatsCol
        every { chatsCol.document("chat_1") } returns chatRef
        every { chatRef.collection("messages") } returns messagesCol
        every { messagesCol.document() } returns msgRef
        every { msgRef.id } returns "msg_123"
        every { firestore.batch() } returns batch
        every { batch.commit() } returns completedTask(null)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val repo = ChatRepository(firestore, userRepository)

        val invalidResult = repo.sendMessage(
            chatId = "chat_1",
            senderId = "uid_a",
            participantIds = listOf("uid_a", "uid_b"),
            type = MessageType.TEXT,
            text = "   "
        )
        assertTrue(invalidResult.isFailure)

        val validResult = repo.sendMessage(
            chatId = "chat_1",
            senderId = "uid_a",
            participantIds = listOf("uid_a", "uid_b"),
            type = MessageType.TEXT,
            text = "السلام عليكم"
        )
        assertTrue(validResult.isSuccess)
        assertEquals("msg_123", validResult.getOrThrow().id)
        assertEquals("السلام عليكم", validResult.getOrThrow().text)
        verify(exactly = 1) { batch.set(msgRef, any()) }
        verify(exactly = 1) { batch.update(chatRef, any<Map<String, Any>>()) }
        verify(exactly = 1) { batch.commit() }
    }
}
