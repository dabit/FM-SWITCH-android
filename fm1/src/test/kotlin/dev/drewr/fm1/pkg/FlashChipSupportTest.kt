// Ported from FM1Kit 0.6.0 Tests/FM1RescueTests/FlashChipSupportTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.Fm1FlashChip
import dev.drewr.fm1.testing.SamplePackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlashChipSupportTest {
    @Test fun readsWhichChipsTheCodeChecksFor() {
        assertEquals(
            FlashChipSupport.BOTH_CHIPS,
            packageFile(Fm1FlashChip.P25Q80, Fm1FlashChip.UC25HQ80).flashChipSupport,
        )
        assertEquals(FlashChipSupport.FIRST_CHIP_ONLY, packageFile(Fm1FlashChip.P25Q80).flashChipSupport)
        assertEquals(FlashChipSupport.NO_CHECK, packageFile().flashChipSupport)
    }

    @Test fun saysNothingForAnInstaller() {
        val installer =
            FwscPackage(
                SamplePackage.file(SamplePackage.image(listOf(SamplePackage.Part.loader())), "FM-1_916"),
            )
        assertNull(installer.flashChipSupport)
    }

    private fun packageFile(vararg constants: Int): FwscPackage {
        val code = ByteArray(0x3000) { 0x5A }
        constants.forEachIndexed { index, value ->
            ByteArray(4) { (value shr (8 * it)).toByte() }.copyInto(code, 0x100 + 8 * index)
        }
        return FwscPackage(SamplePackage.firmwareWithCode(code))
    }
}
