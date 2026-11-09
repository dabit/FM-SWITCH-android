// Ported from FM-SWITCH App/Model/FlashChipWords.swift.
package dev.drewr.fmswitch.model.catalogue

object FlashChipWords {
    const val HOW_TO_TELL_QUESTION = "How would I know?"
    const val HOW_TO_TELL =
        "FM-1s sold since autumn 2026 have the newer chip. The easiest way to tell so far is the M-VAVE logo: on newer FM-1s it's on the front, under the screen. On older ones it's on the top, near the USB and MIDI sockets."

    fun warning(
        names: List<String>,
        hasNewerFlashChip: Boolean,
    ): String {
        val name = joinNames(names)
        return if (hasNewerFlashChip) {
            "Your FM-1 has the newer flash chip, which $name doesn't know. On it, $name usually keeps changes only until it's switched off, and it often can't install another firmware."
        } else {
            val one = names.size == 1
            "$name only ${if (one) "knows" else "know"} the older FM-1 flash chip. On an FM-1 with the newer chip, ${if (one) "it usually keeps" else "they usually keep"} changes only until it's switched off, and ${if (one) "it often can't" else "they often can't"} install another firmware."
        }
    }

    private fun joinNames(names: List<String>) =
        when (names.size) {
            0 -> ""
            1 -> names.single()
            2 -> names.joinToString(" and ")
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }
}
