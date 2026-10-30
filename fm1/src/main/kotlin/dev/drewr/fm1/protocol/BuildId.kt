// Ported from FM1Kit 0.4.5 Protocol/FM1Capability.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

class BuildId(
    bytes: ByteArray,
) {
    val bytes = bytes.copyOf()

    init {
        require(this.bytes.size == BYTE_COUNT) { "A build ID is 32 bytes." }
    }

    val hex: String get() = bytes.joinToString("") { "%02x".format(Fm1Message.u(it)) }

    override fun equals(other: Any?): Boolean = other is BuildId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = hex

    companion object {
        const val BYTE_COUNT = 32

        fun parse(hex: String): BuildId? {
            if (hex.length != BYTE_COUNT * 2) return null
            val output = ByteArray(BYTE_COUNT)
            for (index in output.indices) {
                val high = hex[index * 2].digitToIntOrNull(16) ?: return null
                val low = hex[index * 2 + 1].digitToIntOrNull(16) ?: return null
                output[index] = ((high shl 4) or low).toByte()
            }
            return BuildId(output)
        }
    }
}
