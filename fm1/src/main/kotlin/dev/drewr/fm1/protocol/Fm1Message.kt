// Ported from FM1Kit 0.4.5 Protocol/FM1Message.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

object Fm1Message {
    val HANDSHAKE_QUERY = bytes(0xF0, 0x00, 0x32, 0x45, 0x00, 0x00, 0x00, 0x40, 0x7F, 0xF7)
    val upgradeCommand = bytes(0xF0, 0x22, 0x24, 0x35, 0x7F, 0xF7)
    val stageFinishAddress: Long get() = 0xE0000000L
    val writeFinishAddress: Long get() = 0xF0000000L
    val finishPayload = "success\u0000".encodeToByteArray()
    val maximumReadLength: Int get() = 512
    val prefix = bytes(0x00, 0x59)
    val readCommand: Int get() = 0x30
    val identityCommand: Int get() = 0x11
    val capabilityCommand: Int get() = 0x31
    val armCommand: Int get() = 0x32
    val fixedFieldsLength: Int get() = 8
    val headerLength: Int get() = 6
    val commandOffset: Int get() = 2
    val bodyLengthOffset: Int get() = 3
    val flashTypeOffset: Int get() = 6
    val addressOffset: Int get() = 7
    val lengthOffset: Int get() = 11
    val dataOffset: Int get() = 14

    fun frame(unpacked: ByteArray): ByteArray =
        byteArrayOf(0xF0.toByte()) + SevenBit.pack(unpacked) + byteArrayOf(0xF7.toByte())

    fun unframe(sysex: ByteArray): ByteArray? {
        if (sysex.size < 2 || u(sysex.first()) != 0xF0 || u(sysex.last()) != 0xF7) return null
        return SevenBit.unpack(sysex.copyOfRange(1, sysex.lastIndex))
    }

    fun checksum(
        bytes: ByteArray,
        start: Int = 0,
        endExclusive: Int = bytes.size,
    ): Byte {
        var sum = 0
        for (index in start until endExclusive) sum = (sum + u(bytes[index])) and 0xFF
        return sum.inv().toByte()
    }

    fun command(
        command: Int,
        payload: ByteArray = ByteArray(0),
    ): ByteArray = frame(header(command, payload.size) + payload + byteArrayOf(checksum(payload)))

    fun parseCommand(sysex: ByteArray): Pair<Int, ByteArray>? {
        val body = unframe(sysex) ?: return null
        if (body.size <= headerLength || !body.startsWith(prefix)) return null
        if (readLittleEndian(body, bodyLengthOffset, 3) != body.size - headerLength - 1) return null
        if (checksum(body, headerLength, body.lastIndex) != body.last()) return null
        return u(body[commandOffset]) to body.copyOfRange(headerLength, body.lastIndex)
    }

    fun header(
        command: Int,
        bodyLength: Int,
    ): ByteArray = prefix + byteArrayOf(command.toByte()) + littleEndian(bodyLength.toLong(), 3)

    fun littleEndian(
        value: Long,
        byteCount: Int,
    ): ByteArray = ByteArray(byteCount) { index -> ((value shr (8 * index)) and 0xFF).toByte() }

    fun readLittleEndian(
        bytes: ByteArray,
        offset: Int,
        byteCount: Int,
    ): Int {
        var value = 0
        for (index in 0 until byteCount) value = value or (u(bytes[offset + index]) shl (8 * index))
        return value
    }

    fun u(byte: Byte): Int = byte.toInt() and 0xFF

    fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun ByteArray.startsWith(other: ByteArray): Boolean =
        size >= other.size && copyOfRange(0, other.size).contentEquals(other)
}
