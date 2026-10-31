// Ported from FM-SWITCH App/Model/AppSettings.swift.

package dev.drewr.fmswitch.model.settings

import dev.drewr.fmswitch.model.catalogue.FirmwareOrder

enum class Appearance { System, Light, Dark }

enum class TransportKind { UsbMidi, Simulated }

enum class StartUp(
    val label: String,
) {
    MVave(
        "As it came from M-VAVE",
    ),
    MBootloader("m-bootloader"),
    OpenFirmware("Changed by Open V12 or V14"),
    NotSure("I'm not sure"),
}

val StartUp.detail: String? get() =
    when (this) {
        StartUp.MVave -> "Most FM-1s. Holding REC at switch-on does nothing special."
        StartUp.MBootloader -> "Holding REC at switch-on starts its recovery mode."
        StartUp.OpenFirmware -> "M-VAVE's Open V12 or V14 has been installed on it."
        StartUp.NotSure -> null
    }
const val START_UP_QUESTION = "Which start-up does your FM-1 have?"
const val START_UP_QUESTION_DETAIL =
    "We can't tell from here, and it changes what you can undo from this phone or tablet."
const val START_UP_CHANGE_LATER = "You can change this later in Settings."

object FeluccaUpdateCode {
    val known =
        mapOf(
            "amb" to "AMB-1",
            "dub" to "DUB-1",
            "felucca" to "Felucca",
            "hyp1" to "HYP-1",
            "melodee" to "Melodee",
            "resource-hub-lof-1" to "LOF-1",
        )
    val wayRound = "midimon"

    fun isKnown(id: String?) = id != null && known.containsKey(id)

    data class Warning(
        val body: String,
        val title: String = "Coming back from this takes two installs",
        val why: String =
            "Once the FM-1's bootloader has been changed, by m-bootloader or by Open V12 or V14, these firmwares only accept an update that brings the same boot area the FM-1 already has. V15 brings M-VAVE's, so it's refused, and so are most firmwares not built on Felucca. MIDIMON and LOOM-8 are built on Felucca, so they're let in, and their updated check takes V15 and the others.",
        val decline: String = "Don't install",
    ) {
        constructor(installing: String?, named: String, startUp: StartUp) : this(
            (if (startUp == StartUp.NotSure) "If your FM-1's bootloader has been changed, once" else "Once") +
                " $named is on your FM-1, you can still switch between firmwares built like it (" +
                known
                    .filterKeys {
                        it != installing
                    }.values
                    .sorted()
                    .joinToString(
                        ", ",
                    ).replace(
                        ", ${known.filterKeys { it != installing }.values.sorted().last()}",
                        " and ${known.filterKeys { it != installing }.values.sorted().last()}",
                    ) +
                ") right here.\n\nBut to install V15, Open or most firmwares not built on Felucca, install MIDIMON 0.4 or LOOM-8 0.8 first, then the one you want.",
        )
    }
}

interface AppSettings {
    var appearance: Appearance
    var transportKind: TransportKind
    var showBeta: Boolean
    var showLinksOnly: Boolean
    var alwaysViaOfficial: Boolean
    var startUp: StartUp?
    var firmwareOrder: FirmwareOrder
    var simulatedRecoveryMode: Boolean
    var simulatedCablePull: Boolean
    var simulatedReadsBackwards: Boolean
    var simulatedVerifies: Boolean
    var simulatedMBootloader: Boolean
    var simulatedOpenFirmware: Boolean
    var acknowledgedNoticeVersion: Int
}

class InMemoryAppSettings : AppSettings {
    override var appearance = Appearance.System
    override var transportKind = TransportKind.UsbMidi
    override var showBeta = true
    override var showLinksOnly = false
    override var alwaysViaOfficial = false
    override var startUp: StartUp? = null
    override var firmwareOrder = FirmwareOrder.ByName
    override var simulatedRecoveryMode = false
    override var simulatedCablePull = false
    override var simulatedReadsBackwards = false
    override var simulatedVerifies = false
    override var simulatedMBootloader = false
    override var simulatedOpenFirmware = false
    override var acknowledgedNoticeVersion = 0
}
