// Ported from FM-SWITCH App/Views/SwitchingWords.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.LeavingDestination
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.LeavingRoute
import dev.drewr.fm1.protocol.Fm1Identity

object SwitchingWords {
    val warningTitle = "Switching away later"
    val decline = "Don't install"
    val notReallyOfficial = "It's really running another firmware"
    const val BEFORE_V16_WARNING =
        "Going back to V15 later can't be fully done from this phone. V15 goes back on over USB, but after V16 it plays one note at a time until its saved data is cleared, which needs a computer: FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. V16 isn't included with this app; it's downloaded from M-VAVE."
    const val AFTER_V16_WARNING =
        "After V16, V15 plays one note at a time, as V16 leaves data behind that V15 misreads. This phone can't clear it: that needs FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. Some of V15's sounds may also be wrong or silent, and this app can't put back the factory sounds yet."
    const val STILL_MONO =
        "V15 is on, but after V16 it plays one note at a time until its saved data is cleared, which can't be done from this phone. On a Mac, install V15 again in FM-SWITCH's Backup & rescue, with Start the new firmware fresh ticked. Some of its sounds may also be wrong or silent, and this app can't put back the factory sounds yet."

    fun warnings(guide: LeavingGuide): List<String> =
        buildList {
            val refused =
                listOf(LeavingDestination.V15 to "V15", LeavingDestination.V16 to "V16")
                    .filter { guide.route(it.first) == LeavingRoute.THROUGH_FIXED_FIRMWARE }
                    .map { it.second }
            if (refused.isNotEmpty()) {
                add(
                    "Once ${guide.firmwareName} is installed, it can't install ${refused.joinToString(
                        " or ",
                    )} straight away. " +
                        "FM-SWITCH puts MIDIMON on first when you go back, so it takes a little longer. On a Mac, " +
                        "download mode can do it in one.",
                )
            }
            if (SwitchingGuide.reportsOfficialIdentity(guide)) {
                add(
                    "${guide.firmwareName} reports itself as ${officialName(
                        guide.identity,
                    )}, so apps can't tell them apart. " +
                        "When you switch away, FM-SWITCH may ask whether it's really running another firmware.",
                )
            }
        }

    fun firstStep(
        firstStep: String,
        running: String,
        next: String,
    ) = "$firstStep goes on first: $running can't install $next directly, but it can install $firstStep, which can."

    fun missingFirstStep(
        running: String,
        next: String,
        problem: String?,
    ) = listOf(
        "$running can't install $next directly, so MIDIMON has to go on first.",
        problem,
    ).filterNotNull().joinToString(" ")

    fun alreadyOfficial(identity: Fm1Identity) =
        "Your FM-1 says it's running ${officialName(
            identity,
        )}, so there's nothing to install, unless it's really running a firmware that reports itself as ${officialName(
            identity,
        )}."

    fun notReallyOfficialDetail(identity: Fm1Identity) =
        "Such as TransitFM1, which reports itself as ${officialName(
            identity,
        )}. Tick this to install ${officialName(identity)} over it."

    fun replacesLookAlike(
        running: String,
        reporting: Fm1Identity,
    ) = "Your FM-1 is running $running, which reports itself as ${officialName(
        reporting,
    )}. This installs ${officialName(reporting)} over it."

    fun installFirst(firstStep: String) = "Install $firstStep first"

    fun installFirstDetail(
        firstStep: String,
        then: String,
    ) = "$firstStep goes on, then $then from it. Your FM-1 restarts on its own in between."

    private fun officialName(identity: Fm1Identity) = if (identity == Fm1Identity.officialV16) "V16" else "V15"
}
