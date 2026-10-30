package dev.drewr.fm1.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordedDeviceTest {
    private val reply = hex("F0 00 32 45 58 01 00 00 23 4D 5A 44 79 05 06 4C 1C") + ByteArray(21) + hex("40 05 F7")

    @Test fun readsARealDevicesHandshakeReply() {
        val identity = Fm1Identity.fromHandshakeReply(reply)!!
        assertEquals("FM-1_009", identity.text)
        kotlin.test.assertContentEquals(reply, identity.handshakeReply())
    }

    @Test fun rejectsAReplyWithABadChecksum() {
        val corrupted = reply.copyOf()
        corrupted[corrupted.lastIndex - 1] =
            (Fm1Message.u(corrupted[corrupted.lastIndex - 1]) xor 1).toByte()
        assertNull(Fm1Identity.fromHandshakeReply(corrupted))
    }
}

private fun hex(text: String): ByteArray = text.split(' ').map { it.toInt(16).toByte() }.toByteArray()
