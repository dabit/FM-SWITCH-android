// Ported from FM1Kit 0.4.5 Package/FirmwareContainer.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

object Crc16 {
    fun xModem(bytes: ByteArray): Int {
        var value = 0
        for (byte in bytes) {
            value = value xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                value =
                    if (value and 0x8000 == 0) (value shl 1) and 0xFFFF else ((value shl 1) xor 0x1021) and 0xFFFF
            }
        }
        return value
    }
}
