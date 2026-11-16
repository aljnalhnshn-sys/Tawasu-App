package com.tawasul.app

import com.tawasul.app.data.remote.FirestorePaths
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatIdDeterminismTest {

    @Test
    fun `directChatId produces identical id regardless of participant order`() {
        val uidA = "uid_alpha_123"
        val uidB = "uid_zeta_999"

        val id1 = FirestorePaths.directChatId(uidA, uidB)
        val id2 = FirestorePaths.directChatId(uidB, uidA)

        assertEquals(id1, id2)
        assertEquals("uid_alpha_123_uid_zeta_999", id1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `directChatId rejects self chat`() {
        FirestorePaths.directChatId("same_uid", "same_uid")
    }
}
