package dev.drewr.fmswitch.model.catalogue

import kotlin.test.Test
import kotlin.test.assertTrue

class FlashChipWordsTest {
    @Test fun namesOneFirmwareThatKnowsOnlyTheOlderChip() {
        val warning = FlashChipWords.warning(listOf("ZEN-1"), hasNewerFlashChip = false)
        assertTrue(warning.startsWith("ZEN-1 only knows the older FM-1 flash chip."))
        assertTrue(warning.contains("it usually keeps changes"))
    }

    @Test fun namesSeveralFirmwaresAsAList() {
        val warning = FlashChipWords.warning(listOf("ZEN-1", "DUB-1", "AMB-1"), hasNewerFlashChip = false)
        assertTrue(warning.startsWith("ZEN-1, DUB-1, and AMB-1 only know the older"))
        assertTrue(warning.contains("they usually keep changes"))
    }

    @Test fun saysWhenTheFm1HasTheNewerChip() {
        val warning = FlashChipWords.warning(listOf("ZEN-1"), hasNewerFlashChip = true)
        assertTrue(warning.startsWith("Your FM-1 has the newer flash chip"))
    }
}
