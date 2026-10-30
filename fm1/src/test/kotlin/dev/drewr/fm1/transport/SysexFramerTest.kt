package dev.drewr.fm1.transport

import dev.drewr.fm1.protocol.ReadRequest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SysexFramerTest {
    @Test fun framerReassemblesAcrossChunks() {
        val message = ReadRequest(address = 0, length = 512).response(ByteArray(512) { 0xA5.toByte() })
        for (chunkSize in listOf(1, 7, 64)) {
            val framer = SysexFramer()
            val result = ArrayList<ByteArray>()
            message.asList().chunked(chunkSize).forEach { result += framer.feed(it.toByteArray()) }
            assertEquals(1, result.size)
            assertContentEquals(message, result.single())
        }
    }

    @Test fun framerSkipsRealTimeBytes() {
        val result = SysexFramer().feed(byteArrayOf(0xF0.toByte(), 1, 0xF8.toByte(), 2, 0xFE.toByte(), 0xF7.toByte()))
        assertContentEquals(byteArrayOf(0xF0.toByte(), 1, 2, 0xF7.toByte()), result.single())
    }

    @Test fun framerAbortsOnOtherStatus() {
        val framer = SysexFramer()
        assertTrue(framer.feed(byteArrayOf(0xF0.toByte(), 1, 0x90.toByte(), 2, 0xF7.toByte())).isEmpty())
        assertContentEquals(
            byteArrayOf(0xF0.toByte(), 3, 0xF7.toByte()),
            framer.feed(byteArrayOf(0xF0.toByte(), 3, 0xF7.toByte())).single(),
        )
    }

    @Test fun newStartDiscardsUnfinished() {
        val result = SysexFramer().feed(byteArrayOf(0xF0.toByte(), 1, 0xF0.toByte(), 2, 0xF7.toByte()))
        assertContentEquals(byteArrayOf(0xF0.toByte(), 2, 0xF7.toByte()), result.single())
    }

    @Test fun dataWithoutStartIsIgnored() {
        val result = SysexFramer().feed(byteArrayOf(1, 2, 0xF7.toByte(), 0xF0.toByte(), 3, 0xF7.toByte()))
        assertEquals(1, result.size)
    }

    @Test fun dropsOver64KiB() {
        val framer = SysexFramer()
        assertTrue(
            framer
                .feed(
                    byteArrayOf(0xF0.toByte()) +
                        ByteArray(SysexFramer.MAXIMUM_MESSAGE_LENGTH) +
                        byteArrayOf(0xF7.toByte()),
                ).isEmpty(),
        )
        assertEquals(1, framer.feed(byteArrayOf(0xF0.toByte(), 1, 0xF7.toByte())).size)
    }
}
