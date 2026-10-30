// Ported from FM1Kit 0.4.5 Protocol/FM1Capability.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

data class Fm1Capability(
    val family: Int = FM1_FAMILY,
    val state: Int = 0,
    val buildId: BuildId,
    val errorCode: Int = 0,
) {
    init {
        require(family in 0..MAXIMUM_FAMILY) { "An update family is 0 to 7." }
    }

    val isReady: Boolean get() = state == 0

    fun payload(): ByteArray =
        magic + byteArrayOf(VERSION, family.toByte(), state.toByte(), LOADER_KIND) +
            Fm1Message.littleEndian(Fm1Message.maximumReadLength.toLong(), 2) +
            Fm1Message.littleEndian(FLASH_LENGTH, 4) + buildId.bytes + byteArrayOf(errorCode.toByte())

    companion object {
        const val FM1_FAMILY = 7
        private const val MAXIMUM_FAMILY = 7
        private const val VERSION: Byte = 1
        private const val LOADER_KIND: Byte = 1
        private const val FLASH_LENGTH = 1_048_576L
        private const val PAYLOAD_LENGTH = 47
        private val magic = "FMC1".encodeToByteArray()
        val armAccepted = byteArrayOf(0)

        fun parse(payload: ByteArray): Fm1Capability? {
            if (payload.size != PAYLOAD_LENGTH || !payload.copyOfRange(0, 4).contentEquals(magic) ||
                payload[4] != VERSION || Fm1Message.u(payload[5]) > MAXIMUM_FAMILY || payload[7] != LOADER_KIND ||
                Fm1Message.readLittleEndian(payload, 8, 2) != Fm1Message.maximumReadLength ||
                Fm1Message.readLittleEndian(payload, 10, 4).toLong() != FLASH_LENGTH
            ) {
                return null
            }
            return Fm1Capability(
                Fm1Message.u(payload[5]),
                Fm1Message.u(payload[6]),
                BuildId(payload.copyOfRange(14, 46)),
                Fm1Message.u(payload[46]),
            )
        }

        fun armPayload(
            family: Int,
            imageLength: Int,
            buildId: BuildId,
        ): ByteArray =
            byteArrayOf(1, family.toByte()) + Fm1Message.littleEndian(imageLength.toLong(), 4) + buildId.bytes
    }
}
