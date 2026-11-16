package com.tawasul.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tawasul.app.data.local.ConnectivityObserver
import com.tawasul.app.messaging.ChatViewModel
import com.tawasul.app.messaging.VoiceRecorderManager
import com.tawasul.app.models.DeliveryStatus
import com.tawasul.app.repositories.AuthRepository
import com.tawasul.app.repositories.ChatRepository
import com.tawasul.app.repositories.MediaRepository
import com.tawasul.app.repositories.UserRepository
import io.mockk.coEvery
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

    @Test
    fun `sending message while offline marks message as FAILED for retry`() = runTest(testDispatcher) {
        val firebaseUser = mockk<FirebaseUser> { every { uid } returns "uid_me" }
        val authRepo = mockk<AuthRepository> {
            every { currentUser } returns firebaseUser
            every { authStateFlow } returns emptyFlow()
        }
        val chatRepo = mockk<ChatRepository>(relaxed = true) {
            every { observeLatestMessages(any(), any()) } returns emptyFlow()
        }
        val userRepo = mockk<UserRepository>(relaxed = true) {
            every { observeUserProfile(any()) } returns emptyFlow()
            coEvery { getUserProfile(any()) } returns null
        }
        val mediaRepo = mockk<MediaRepository>(relaxed = true)
        val voiceMgr = mockk<VoiceRecorderManager>(relaxed = true)
        val connectivity = mockk<ConnectivityObserver> {
            every { isOnline } returns MutableStateFlow(false)
        }

        val vm = ChatViewModel(authRepo, chatRepo, userRepo, mediaRepo, voiceMgr, connectivity)
        vm.openChat("chat_1", listOf("uid_me", "uid_peer"), false, "Peer", "uid_peer")
        advanceUntilIdle()

        vm.sendTextMessage("مرحبا بك")
        advanceUntilIdle()

        val pending = vm.uiState.value.pendingOrFailedMessages
        assertEquals(1, pending.size)
        assertEquals(DeliveryStatus.FAILED.name, pending.first().deliveryStatus)
    }
}
