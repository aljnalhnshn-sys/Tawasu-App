package com.tawasul.app

import com.google.firebase.auth.FirebaseUser
import com.tawasul.app.data.local.ConnectivityObserver
import com.tawasul.app.messaging.ChatViewModel
import com.tawasul.app.messaging.VoiceRecorderManager
import com.tawasul.app.models.DeliveryStatus
import com.tawasul.app.models.Message
import com.tawasul.app.models.MessageType
import com.tawasul.app.repositories.AuthRepository
import com.tawasul.app.repositories.ChatRepository
import com.tawasul.app.repositories.MediaRepository
import com.tawasul.app.repositories.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        onlineFlow: MutableStateFlow<Boolean>,
        chatRepo: ChatRepository = mockk(relaxed = true) {
            every { observeLatestMessages(any(), any()) } returns emptyFlow()
        }
    ): ChatViewModel {
        val firebaseUser = mockk<FirebaseUser> { every { uid } returns "uid_me" }
        val authRepo = mockk<AuthRepository> {
            every { currentUser } returns firebaseUser
            every { authStateFlow } returns emptyFlow()
        }
        val userRepo = mockk<UserRepository>(relaxed = true) {
            every { observeUserProfile(any()) } returns emptyFlow()
            coEvery { getUserProfile(any()) } returns null
        }
        val mediaRepo = mockk<MediaRepository>(relaxed = true)
        val voiceMgr = mockk<VoiceRecorderManager>(relaxed = true)
        val connectivity = mockk<ConnectivityObserver> {
            every { isOnline } returns onlineFlow
        }
        return ChatViewModel(authRepo, chatRepo, userRepo, mediaRepo, voiceMgr, connectivity)
    }

    @Test
    fun `sending message while offline marks message as FAILED for retry`() = runTest(testDispatcher) {
        val onlineFlow = MutableStateFlow(false)
        val vm = buildViewModel(onlineFlow)
        vm.openChat("chat_1", listOf("uid_me", "uid_peer"), false, "Peer", "uid_peer")
        advanceUntilIdle()

        vm.sendTextMessage("مرحبا بك")
        advanceUntilIdle()

        val pending = vm.uiState.value.pendingOrFailedMessages
        assertEquals(1, pending.size)
        assertEquals(DeliveryStatus.FAILED.name, pending.first().deliveryStatus)
    }

    @Test
    fun `sending message while online dispatches to repository and clears pending list`() = runTest(testDispatcher) {
        val onlineFlow = MutableStateFlow(true)
        val chatRepo = mockk<ChatRepository>(relaxed = true) {
            every { observeLatestMessages(any(), any()) } returns emptyFlow()
            coEvery {
                sendMessage(
                    chatId = "chat_1",
                    senderId = "uid_me",
                    participantIds = listOf("uid_me", "uid_peer"),
                    type = MessageType.TEXT,
                    text = "أهلاً",
                    mediaUrl = "",
                    mediaDurationSec = 0,
                    localClientId = any()
                )
            } returns Result.success(
                Message(id = "msg_1", chatId = "chat_1", senderId = "uid_me", text = "أهلاً")
            )
        }
        val vm = buildViewModel(onlineFlow, chatRepo)
        vm.openChat("chat_1", listOf("uid_me", "uid_peer"), false, "Peer", "uid_peer")
        advanceUntilIdle()

        vm.sendTextMessage("  أهلاً  ")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingOrFailedMessages.isEmpty())
        coVerify(exactly = 1) {
            chatRepo.sendMessage(
                chatId = "chat_1",
                senderId = "uid_me",
                participantIds = listOf("uid_me", "uid_peer"),
                type = MessageType.TEXT,
                text = "أهلاً",
                mediaUrl = "",
                mediaDurationSec = 0,
                localClientId = any()
            )
        }
    }

    @Test
    fun `retrying failed message succeeds when connectivity returns`() = runTest(testDispatcher) {
        val onlineFlow = MutableStateFlow(false)
        val chatRepo = mockk<ChatRepository>(relaxed = true) {
            every { observeLatestMessages(any(), any()) } returns emptyFlow()
            coEvery {
                sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Result.success(Message(id = "msg_retry", text = "إعادة الإرسال"))
        }
        val vm = buildViewModel(onlineFlow, chatRepo)
        vm.openChat("chat_1", listOf("uid_me", "uid_peer"), false, "Peer", "uid_peer")
        advanceUntilIdle()

        vm.sendTextMessage("إعادة الإرسال")
        advanceUntilIdle()
        val failed = vm.uiState.value.pendingOrFailedMessages.single()
        assertEquals(DeliveryStatus.FAILED.name, failed.deliveryStatus)

        onlineFlow.value = true
        advanceUntilIdle()
        vm.retryFailedMessage(failed)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingOrFailedMessages.isEmpty())
    }

    @Test
    fun `blank text message is ignored`() = runTest(testDispatcher) {
        val chatRepo = mockk<ChatRepository>(relaxed = true) {
            every { observeLatestMessages(any(), any()) } returns emptyFlow()
        }
        val vm = buildViewModel(MutableStateFlow(true), chatRepo)
        vm.openChat("chat_1", listOf("uid_me", "uid_peer"), false, "Peer", "uid_peer")
        advanceUntilIdle()

        vm.sendTextMessage("    ")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingOrFailedMessages.isEmpty())
        coVerify(exactly = 0) { chatRepo.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
