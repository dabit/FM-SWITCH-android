// Ported from FM-SWITCH AppTests/ContrastTests.swift.
package dev.drewr.fmswitch.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {
    @Test
    fun textPairsPassFourAndAHalf() {
        val pairs =
            listOf(
                Palette.INK_LIGHT to Palette.GROUND_LIGHT,
                Palette.INK_DARK to Palette.GROUND_DARK,
                Palette.INK_LIGHT to Palette.CARD_LIGHT,
                Palette.INK_DARK to Palette.CARD_DARK,
                Palette.INK_LIGHT to Palette.CONTROL_LIGHT,
                Palette.INK_DARK to Palette.CONTROL_DARK,
                Palette.SECONDARY_INK_LIGHT to Palette.GROUND_LIGHT,
                Palette.SECONDARY_INK_DARK to Palette.GROUND_DARK,
                Palette.SECONDARY_INK_LIGHT to Palette.CARD_LIGHT,
                Palette.SECONDARY_INK_DARK to Palette.CARD_DARK,
                Palette.SECONDARY_INK_LIGHT to Palette.CONTROL_LIGHT,
                Palette.SECONDARY_INK_DARK to Palette.CONTROL_DARK,
                Palette.ACCENT_TEXT_LIGHT to Palette.GROUND_LIGHT,
                Palette.ACCENT_TEXT_DARK to Palette.GROUND_DARK,
                Palette.ACCENT_TEXT_LIGHT to Palette.CARD_LIGHT,
                Palette.ACCENT_TEXT_DARK to Palette.CARD_DARK,
                Palette.CARD_LIGHT to Palette.INK_LIGHT,
                Palette.CARD_DARK to Palette.INK_DARK,
                Palette.GOOD_INK_LIGHT to Palette.GOOD_BACKGROUND_LIGHT,
                Palette.GOOD_INK_DARK to Palette.GOOD_BACKGROUND_DARK,
                Palette.WARNING_INK_LIGHT to Palette.WARNING_BACKGROUND_LIGHT,
                Palette.WARNING_INK_DARK to Palette.WARNING_BACKGROUND_DARK,
                Palette.DANGER_LIGHT to Palette.CARD_LIGHT,
                Palette.DANGER_DARK to Palette.CARD_DARK,
                0xFFFFFFFF to Palette.ACCENT,
                0xFFFFFFFF to Palette.FIRMWARES,
                0xFFFFFFFF to Palette.SAVE,
            )
        pairs.forEachIndexed {
            index,
            (text, background),
            ->
            assertTrue(contrastRatio(text, background) >= 4.5, "text pair $index")
        }
    }

    @Test
    fun graphicPairsPassThree() {
        val pairs =
            listOf(
                0xFFFFFFFF to Palette.GOOD_LIGHT,
                0xFFFFFFFF to Palette.GOOD_DARK,
                Palette.GOOD_LIGHT to Palette.GROUND_LIGHT,
                Palette.GOOD_DARK to Palette.GROUND_DARK,
                Palette.GOOD_LIGHT to Palette.CARD_LIGHT,
                Palette.GOOD_DARK to Palette.CARD_DARK,
                Palette.FIRMWARES_TINT_LIGHT to Palette.CARD_LIGHT,
                Palette.FIRMWARES_TINT_DARK to Palette.CARD_DARK,
                Palette.FIRMWARES_TINT_LIGHT to Palette.GROUND_LIGHT,
                Palette.FIRMWARES_TINT_DARK to Palette.GROUND_DARK,
                Palette.ACCENT to Palette.CARD_LIGHT,
                Palette.ACCENT to Palette.CARD_DARK,
            )
        pairs.forEachIndexed {
            index,
            (graphic, background),
            ->
            assertTrue(contrastRatio(graphic, background) >= 3.0, "graphic pair $index")
        }
    }

    @Test
    fun ratioMatchesKnownValues() {
        assertTrue(kotlin.math.abs(contrastRatio(0xFF000000, 0xFFFFFFFF) - 21.0) < .01, "black on white")
        assertTrue(kotlin.math.abs(contrastRatio(0xFF767676, 0xFFFFFFFF) - 4.54) < .01, "known grey on white")
    }
}
