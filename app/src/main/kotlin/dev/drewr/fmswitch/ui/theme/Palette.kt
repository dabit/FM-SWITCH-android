// Ported from FM-SWITCH App/Standard/Theme/Palette.swift.
package dev.drewr.fmswitch.ui.theme

/** Standard look colours, kept as ARGB numbers so their contrast is JVM-testable. */
object Palette {
    const val GROUND_LIGHT = 0xFFEEE8DD
    const val GROUND_DARK = 0xFF141413
    const val CARD_LIGHT = 0xFFFFFFFF
    const val CARD_DARK = 0xFF232321
    const val CONTROL_LIGHT = 0xFFE2DBCD
    const val CONTROL_DARK = 0xFF2C2B29
    const val LINE_LIGHT = 0xFFE8E2D7
    const val LINE_DARK = 0xFF33322F
    const val INK_LIGHT = 0xFF1C1C1E
    const val INK_DARK = 0xFFF2F1EE
    const val SECONDARY_INK_LIGHT = 0xFF5E5E63
    const val SECONDARY_INK_DARK = 0xFFA8A6A1
    const val ACCENT = 0xFFC2410C
    const val ACCENT_TEXT_LIGHT = 0xFF5B21B6
    const val ACCENT_TEXT_DARK = 0xFFFF9259
    const val FIRMWARES = 0xFF6D28D9
    const val FIRMWARES_TINT_LIGHT = 0xFF6D28D9
    const val FIRMWARES_TINT_DARK = 0xFFA78BFA
    const val SAVE = 0xFF2563EB
    const val GOOD_LIGHT = 0xFF1F8A45
    const val GOOD_DARK = 0xFF2E9E57
    const val GOOD_INK_LIGHT = 0xFF17602F
    const val GOOD_INK_DARK = 0xFF86D9A2
    const val GOOD_BACKGROUND_LIGHT = 0xFFE3F1E7
    const val GOOD_BACKGROUND_DARK = 0xFF173322
    const val WARNING_INK_LIGHT = 0xFF7A3E00
    const val WARNING_INK_DARK = 0xFFFFC58A
    const val WARNING_BACKGROUND_LIGHT = 0xFFFFF4E5
    const val WARNING_BACKGROUND_DARK = 0xFF3A2610
}

internal fun contrastRatio(
    first: Long,
    second: Long,
): Double {
    fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val normalized = ((color shr shift) and 0xFF) / 255.0
            return if (normalized <= 0.04045) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    val light = maxOf(luminance(first), luminance(second))
    val dark = minOf(luminance(first), luminance(second))
    return (light + 0.05) / (dark + 0.05)
}
