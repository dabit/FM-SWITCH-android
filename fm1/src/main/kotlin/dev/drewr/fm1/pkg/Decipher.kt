// Ported from FM1Kit 0.4.5 Package/FirmwareContainer.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

object Decipher {
    fun apply(
        bytes: ByteArray,
        startKey: Int = 0xFFFF,
    ): ByteArray {
        var key = startKey and 0xFFFF
        return ByteArray(bytes.size) { index ->
            val output = ((bytes[index].toInt() and 0xFF) xor (key and 0xFF)).toByte()
            key = if (key and 0x8000 == 0) (key shl 1) and 0xFFFF else ((key shl 1) xor 0x1021) and 0xFFFF
            output
        }
    }
}
