package dev.drewr.fm1.transport

class SysexFramer {
    private var message: ArrayList<Byte>? = null

    fun feed(chunk: ByteArray): List<ByteArray> {
        val output = ArrayList<ByteArray>()
        for (byte in chunk.copyOf()) {
            val value = byte.toInt() and 0xFF
            when {
                value == 0xF0 -> {
                    message = arrayListOf(byte)
                }

                value == 0xF7 -> {
                    val current = message
                    if (current != null) {
                        current += byte
                        output += current.toByteArray()
                    }
                    message = null
                }

                value in 0xF8..0xFF -> {
                    Unit
                }

                value >= 0x80 -> {
                    message = null
                }

                else -> {
                    val current = message ?: continue
                    current += byte
                    if (current.size > MAXIMUM_MESSAGE_LENGTH) message = null
                }
            }
        }
        return output
    }

    companion object {
        const val MAXIMUM_MESSAGE_LENGTH = 64 * 1024
    }
}
