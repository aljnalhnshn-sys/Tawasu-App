package com.tawasul.app

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.tawasul.app.models.CallStatus
import com.tawasul.app.models.CallType
import com.tawasul.app.models.UserProfile
import com.tawasul.app.repositories.CallRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRepositoryTest {

    @Test
    fun `createOutgoingCall writes RINGING call session without participantUids field`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val callsCol = mockk<CollectionReference>(relaxed = true)
        val callRef = mockk<DocumentReference>(relaxed = true)
        val payloadSlot = slot<Map<String, Any>>()

        every { firestore.collection("calls") } returns callsCol
        every { callsCol.document() } returns callRef
        every { callRef.id } returns "call_99"
        every { callRef.set(capture(payloadSlot)) } returns completedTask(null)

        val repo = CallRepository(firestore)
        val caller = UserProfile(uid = "uid_caller", displayName = "سالم", username = "salem")
        val callee = UserProfile(uid = "uid_callee", displayName = "خالد", username = "khaled")

        val result = repo.createOutgoingCall(caller, callee, CallType.VIDEO, "v=0...")

        assertTrue(result.isSuccess)
        assertEquals("call_99", result.getOrThrow().id)
        assertEquals(CallStatus.RINGING.name, payloadSlot.captured["status"])
        assertEquals(CallType.VIDEO.name, payloadSlot.captured["type"])
        assertFalse(payloadSlot.captured.containsKey("participantUids"))
    }

    @Test
    fun `answerCall updates answerSdp and transitions status to CONNECTED`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val callsCol = mockk<CollectionReference>(relaxed = true)
        val callRef = mockk<DocumentReference>(relaxed = true)
        val updateSlot = slot<Map<String, Any>>()

        every { firestore.collection("calls") } returns callsCol
        every { callsCol.document("call_99") } returns callRef
        every { callRef.update(capture(updateSlot)) } returns completedTask(null)

        val repo = CallRepository(firestore)
        val result = repo.answerCall("call_99", "answer_sdp")

        assertTrue(result.isSuccess)
        assertEquals("answer_sdp", updateSlot.captured["answerSdp"])
        assertEquals(CallStatus.CONNECTED.name, updateSlot.captured["status"])
    }

    @Test
    fun `updateCallStatus adds endedAt only for terminal states`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val callsCol = mockk<CollectionReference>(relaxed = true)
        val callRef = mockk<DocumentReference>(relaxed = true)
        val updates = mutableListOf<Map<String, Any>>()

        every { firestore.collection("calls") } returns callsCol
        every { callsCol.document("call_99") } returns callRef
        every { callRef.update(capture(updates)) } returns completedTask(null)

        val repo = CallRepository(firestore)
        repo.updateCallStatus("call_99", CallStatus.ENDED)

        verify(exactly = 1) { callRef.update(any<Map<String, Any>>()) }
        assertEquals(CallStatus.ENDED.name, updates.single()["status"])
        assertTrue(updates.single().containsKey("endedAt"))
    }
}
