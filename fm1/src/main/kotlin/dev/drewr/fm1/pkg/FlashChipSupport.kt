// Ported from FM1Kit 0.6.0 Package/FwscPackage+FlashChip.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.Fm1FlashChip

enum class FlashChipSupport {
    BOTH_CHIPS,
    FIRST_CHIP_ONLY,
    NO_CHECK,
}

val FwscPackage.flashChipSupport: FlashChipSupport?
    get() {
        if (!isFirmware) return null
        val code = firmwareCode ?: return null
        val first = code.containsSequence(littleEndian(Fm1FlashChip.P25Q80))
        val newer = code.containsSequence(littleEndian(Fm1FlashChip.UC25HQ80))
        return when {
            newer -> FlashChipSupport.BOTH_CHIPS
            first -> FlashChipSupport.FIRST_CHIP_ONLY
            else -> FlashChipSupport.NO_CHECK
        }
    }

private fun littleEndian(value: Int): ByteArray = ByteArray(4) { (value shr (8 * it)).toByte() }
