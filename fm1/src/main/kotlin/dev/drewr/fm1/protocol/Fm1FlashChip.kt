// Ported from FM1Kit 0.6.0 Protocol/FM1FlashChip.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

object Fm1FlashChip {
    const val P25Q80 = 0x856014
    const val UC25HQ80 = 0xB36014

    fun name(id: Int): String? =
        when (id) {
            P25Q80 -> "P25Q80"
            UC25HQ80 -> "UC25HQ80"
            else -> null
        }
}
