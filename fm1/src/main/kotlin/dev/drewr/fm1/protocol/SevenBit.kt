// Ported from FM1Kit 0.4.5 Protocol/SevenBit.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

object SevenBit {
    fun pack(bytes: ByteArray): ByteArray {
        val packed = ArrayList<Byte>(bytes.size * 8 / 7 + 1)
        var accumulator = 0L
        var bitCount = 0
        for (byte in bytes) {
            accumulator = accumulator or ((byte.toInt() and 0xFF).toLong() shl bitCount)
            bitCount += 8
            while (bitCount >= 7) {
                packed += (accumulator and 0x7F).toByte()
                accumulator = accumulator shr 7
                bitCount -= 7
            }
        }
        if (bitCount > 0) packed += (accumulator and 0x7F).toByte()
        return packed.toByteArray()
    }

    fun unpack(septets: ByteArray): ByteArray {
        val unpacked = ArrayList<Byte>(septets.size * 7 / 8)
        var accumulator = 0L
        var bitCount = 0
        for (septet in septets) {
            accumulator = accumulator or (((septet.toInt() and 0x7F).toLong()) shl bitCount)
            bitCount += 7
            while (bitCount >= 8) {
                unpacked += (accumulator and 0xFF).toByte()
                accumulator = accumulator shr 8
                bitCount -= 8
            }
        }
        return unpacked.toByteArray()
    }
}
