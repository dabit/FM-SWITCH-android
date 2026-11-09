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
    V16("Changed by V16"),
    MBootloader("m-bootloader"),
    OpenFirmware("Changed by Open V12 or V14"),
    NotSure("I'm not sure"),
}

val StartUp.detail: String? get() =
    when (this) {
        StartUp.MVave -> "Most FM-1s. Holding REC at switch-on does nothing special."
        StartUp.V16 -> "M-VAVE's V16 has been installed on it."
        StartUp.MBootloader -> "Holding REC at switch-on starts its recovery mode."
        StartUp.OpenFirmware -> "M-VAVE's Open V12 or V14 has been installed on it."
        StartUp.NotSure -> null
    }
const val START_UP_QUESTION = "Which start-up does your FM-1 have?"
const val START_UP_QUESTION_DETAIL =
    "We can't tell from here, and it changes what you can undo from this phone or tablet."
const val START_UP_CHANGE_LATER = "You can change this later in Settings."

interface AppSettings {
    var appearance: Appearance
    var transportKind: TransportKind
    var showBeta: Boolean
    var showLinksOnly: Boolean
    var alwaysViaOfficial: Boolean
    var startUp: StartUp?
    var firmwareOrder: FirmwareOrder
    var firmwareScope: String?
    var myFirmwaresOrder: FirmwareOrder
    var myFirmwaresScope: String?
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
    override var firmwareScope: String? = null
    override var myFirmwaresOrder = FirmwareOrder.ByName
    override var myFirmwaresScope: String? = null
    override var simulatedRecoveryMode = false
    override var simulatedCablePull = false
    override var simulatedReadsBackwards = false
    override var simulatedVerifies = false
    override var simulatedMBootloader = false
    override var simulatedOpenFirmware = false
    override var acknowledgedNoticeVersion = 0
}
