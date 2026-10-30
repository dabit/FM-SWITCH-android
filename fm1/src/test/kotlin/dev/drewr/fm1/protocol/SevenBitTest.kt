package dev.drewr.fm1.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SevenBitTest {
    @Test fun packsLeastSignificantBitFirst() {
        assertContentEquals(Fm1Message.bytes(0x7F, 0x01), SevenBit.pack(Fm1Message.bytes(0xFF)))
        assertContentEquals(Fm1Message.bytes(0x00, 0x32, 0x41, 0x01), SevenBit.pack(Fm1Message.bytes(0x00, 0x59, 0x30)))
    }

    @Test fun roundTripsEveryLength() {
        for (length in 0..40) {
            val input = ByteArray(length) { ((it * 37 + 11) and 0xFF).toByte() }
            val packed = SevenBit.pack(input)
            assertContentEquals(input, SevenBit.unpack(packed).copyOf(length))
            assertTrue(packed.all { Fm1Message.u(it) < 0x80 })
        }
    }
}
