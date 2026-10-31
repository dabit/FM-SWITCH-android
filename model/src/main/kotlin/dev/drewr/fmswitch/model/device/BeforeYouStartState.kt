// Ported from FM-SWITCH App/Standard/Firmwares/BeforeYouStartView.swift.
package dev.drewr.fmswitch.model.device

data class BeforeYouStartState(
    val isSwitching: Boolean,
    val isSameIdentity: Boolean,
    val isUntested: Boolean,
    val hasDataCable: Boolean,
    val staysPluggedIn: Boolean,
    val hasSaved: Boolean,
    val acceptsUntested: Boolean,
    val targetName: String = "firmware",
) {
    val isTicked: Boolean
        get() =
            hasDataCable && staysPluggedIn && (!isSwitching || hasSaved) &&
                (!isUntested || acceptsUntested)

    val confirmsSwitchingFirmware: Boolean get() = isSwitching && hasSaved
    val confirmsSameIdentity: Boolean get() = isSameIdentity

    fun isEnabled(readiness: InstallReadiness): Boolean = isTicked && readiness.canStart(hasSaved, acceptsUntested)

    fun buttonTitle(readiness: InstallReadiness): String =
        when {
            readiness.needsStartUp -> "Choose your FM-1's start-up"
            isTicked -> "Install $targetName"
            else -> "Tick each one to install"
        }
}
