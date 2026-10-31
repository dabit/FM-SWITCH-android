// Ported from FM-SWITCH App/Model/FirmwareBadgeDesign.swift.
package dev.drewr.fmswitch.model.catalogue

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A recognisable two-colour badge derived entirely from a firmware's name. */
data class FirmwareBadgeDesign(
    val letters: String,
    val first: UInt,
    val second: UInt,
    val split: Split,
    val ink: UInt,
) {
    enum class Tone { Pale, Deep }

    enum class Split { Across, Diagonal }

    val strongerHalf: UInt get() = if (contrastRatio(first, ink) >= contrastRatio(second, ink)) first else second

    constructor(name: String, isOfficial: Boolean = false, version: String? = null) :
        this(name, isOfficial, version, designFor(name, isOfficial))

    private constructor(name: String, isOfficial: Boolean, version: String?, design: Design) : this(
        if (isOfficial) version?.takeIf { it.length <= 4 } ?: letters(name) else letters(name),
        design.first,
        design.second,
        design.split,
        design.ink,
    )

    companion object {
        private val commonWords = setOf("firmware", "official", "the", "for", "of", "and", "fm", "fm1", "fm-1")
        private const val HUE_COUNT = 16

        private fun designFor(
            name: String,
            official: Boolean,
        ): Design {
            if (official) return Design(0x1C1C1Eu, 0x1C1C1Eu, Split.Across, 0xFFFFFFu)
            val seed = seed(name)
            val lightHue = (seed % HUE_COUNT.toUInt()).toInt()
            val darkHue = (lightHue + 5 + ((seed shr 8) % 7u).toInt()) % HUE_COUNT
            val tone = if ((seed shr 16) and 1u == 0u) Tone.Pale else Tone.Deep
            val colours = colours(lightHue, darkHue, tone)
            val lightFirst = (seed shr 20) and 1u == 0u
            return Design(
                if (lightFirst) colours.light else colours.dark,
                if (lightFirst) colours.dark else colours.light,
                if ((seed shr 24) and 1u == 0u) Split.Across else Split.Diagonal,
                colours.ink,
            )
        }

        private data class Design(
            val first: UInt,
            val second: UInt,
            val split: Split,
            val ink: UInt,
        )

        private data class Colours(
            val light: UInt,
            val dark: UInt,
            val ink: UInt,
        )

        private fun colours(
            lightHue: Int,
            darkHue: Int,
            tone: Tone,
        ): Colours =
            when (tone) {
                Tone.Pale -> {
                    val light = oklch(.95, .05, hue(lightHue))
                    val dark = oklch(.80, .13, hue(darkHue))
                    var lightness = .27
                    while (minOf(
                            contrastRatio(oklch(lightness, .07, hue(lightHue)), light),
                            contrastRatio(oklch(lightness, .07, hue(lightHue)), dark),
                        ) <
                        4.5
                    ) {
                        lightness -= .01
                    }
                    Colours(light, dark, oklch(lightness, .07, hue(lightHue)))
                }

                Tone.Deep -> {
                    var lightness = .60
                    while (contrastRatio(0xFFFFFFu, oklch(lightness, .15, hue(lightHue))) < 4.5) lightness -= .01
                    Colours(oklch(lightness, .15, hue(lightHue)), oklch(.33, .12, hue(darkHue)), 0xFFFFFFu)
                }
            }

        private fun hue(step: Int) = 25 + step * 360.0 / HUE_COUNT

        private fun oklch(
            lightness: Double,
            chroma: Double,
            degrees: Double,
        ): UInt {
            val angle = degrees * Math.PI / 180
            val greenRed = chroma * cos(angle)
            val blueYellow = chroma * sin(angle)
            val long = (lightness + .396_337_777_4 * greenRed + .215_803_757_3 * blueYellow).pow(3)
            val medium = (lightness - .105_561_345_8 * greenRed - .063_854_172_8 * blueYellow).pow(3)
            val short = (lightness - .089_484_177_5 * greenRed - 1.291_485_548_0 * blueYellow).pow(3)
            val linear =
                listOf(
                    4.076_741_662_1 * long - 3.307_711_591_3 * medium + .230_969_929_2 * short,
                    -1.268_438_004_6 * long + 2.609_757_401_1 * medium - .341_319_396_5 * short,
                    -.004_196_086_3 * long - .703_418_614_7 * medium + 1.707_695_658_0 * short,
                )
            return linear.fold(0u) { value, channel ->
                val clipped = channel.coerceIn(0.0, 1.0)
                val encoded = if (clipped <= .003_130_8) 12.92 * clipped else 1.055 * clipped.pow(1 / 2.4) - .055
                (value shl 8) or (encoded * 255).roundToInt().toUInt()
            }
        }

        fun letters(name: String): String {
            val allWords =
                name
                    .split(Regex("[^\\p{L}\\p{N}-]+"))
                    .filter { word -> word.any(Char::isLetterOrDigit) }
            val distinctive = allWords.filter { it.lowercase() !in commonWords }
            val words = distinctive.ifEmpty { allWords }
            val first = words.firstOrNull() ?: return "?"
            val last = words.last()
            if (words.size > 1) return (initial(first) + initial(last)).uppercase()
            val allParts = parts(first)
            val distinctiveParts = allParts.filter { it.lowercase() !in commonWords }
            val parts = distinctiveParts.ifEmpty { allParts }
            if (parts.size > 1) return (initial(parts.first()) + initial(parts.last())).uppercase()
            val characters = (parts.firstOrNull() ?: first).filter(Char::isLetterOrDigit)
            return characters.take(1).uppercase() + characters.drop(1).take(1)
        }

        fun seed(name: String): UInt =
            name.lowercase().filter(Char::isLetterOrDigit).encodeToByteArray().fold(2_166_136_261u) { hash, byte ->
                (hash xor byte.toUByte().toUInt()) * 16_777_619u
            }

        private fun initial(word: String) = word.firstOrNull(Char::isLetterOrDigit)?.toString().orEmpty()

        private fun parts(word: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var previous: Char? = null
            for (character in word) {
                if (character == '-') {
                    parts += current.toString()
                    current.clear()
                } else {
                    if (previous?.isLowerCase() == true && character.isUpperCase()) {
                        parts += current.toString()
                        current.clear()
                    }
                    current.append(character)
                }
                previous = character
            }
            parts += current.toString()
            return parts.filter { it.any(Char::isLetterOrDigit) }
        }

        private fun contrastRatio(
            first: UInt,
            second: UInt,
        ): Double {
            fun luminance(rgb: UInt): Double {
                fun channel(shift: Int): Double {
                    val value = ((rgb shr shift) and 0xFFu).toDouble() / 255
                    return if (value <= .039_28) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
                }
                return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
            }
            val one = luminance(first)
            val two = luminance(second)
            return (maxOf(one, two) + .05) / (minOf(one, two) + .05)
        }
    }
}

/** The badge belongs to the entry's display identity, never its catalogue id. */
val CatalogueEntry.badge: FirmwareBadgeDesign
    get() = FirmwareBadgeDesign(name, isOfficial, version)
