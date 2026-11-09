// Ported from FM-SWITCH App/Catalogue/HubFlashChip.swift.
package dev.drewr.fmswitch.model.catalogue

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HubFlashChip(
    val savesOn: String,
) {
    @SerialName("old")
    Old("Older FM-1s only"),

    @SerialName("new")
    New("All FM-1s"),

    @SerialName("any")
    Any("All FM-1s (doesn't check the chip)"),
}
