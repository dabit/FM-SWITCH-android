// Ported from FM-SWITCH App/Model/DeviceController+States.swift.
package dev.drewr.fmswitch.model.device

sealed class FlashPhase {
    data object Preparing : FlashPhase()

    data class Staging(
        val requests: Int,
    ) : FlashPhase()

    data object WaitingForLoader : FlashPhase()

    data class Writing(
        val fraction: Double,
    ) : FlashPhase()

    data class SecondPass(
        val fraction: Double,
    ) : FlashPhase()

    data object Rebooting : FlashPhase()

    data class Succeeded(
        val identity: String,
    ) : FlashPhase()

    data class Failed(
        val message: String,
        val canResume: Boolean,
    ) : FlashPhase()

    val label: String
        get() =
            when (this) {
                Preparing -> "CONNECTING"
                is Staging -> "CHECKING PACKAGE"
                WaitingForLoader -> "WAITING FOR LOADER"
                is Writing -> "WRITING"
                is SecondPass -> "WRITING PASS 2"
                Rebooting -> "REBOOTING"
                is Succeeded -> "DONE"
                is Failed -> "STOPPED"
            }
}
