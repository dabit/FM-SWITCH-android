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
    val confirmsNotOfficial: Boolean = false,
) {
    val isTicked: Boolean
        get() =
            hasDataCable && staysPluggedIn && (!isSwitching || hasSaved) &&
                (!isUntested || acceptsUntested)

    val confirmsSwitchingFirmware: Boolean get() = isSwitching && hasSaved
    val confirmsSameIdentity: Boolean get() = isSameIdentity

    /**
     * A person who says an apparently official FM-1 is a look-alike is switching firmware too,
     * so its saved-work confirmation belongs in the same checklist rule.
     */
    fun isSwitching(readiness: InstallReadiness) =
        isSwitching || (readiness.canReinstallOfficial && confirmsNotOfficial)

    fun isTicked(readiness: InstallReadiness) =
        hasDataCable && staysPluggedIn && (!isSwitching(readiness) || hasSaved) && (!isUntested || acceptsUntested)

    fun confirmsSwitchingFirmware(readiness: InstallReadiness) = isSwitching(readiness) && hasSaved

    fun confirmsReinstallingOfficial(readiness: InstallReadiness) =
        readiness.knowsItIsntOfficial || (readiness.canReinstallOfficial && confirmsNotOfficial)

    fun isEnabled(readiness: InstallReadiness): Boolean =
        isTicked(readiness) && readiness.canStart(hasSaved, acceptsUntested, confirmsNotOfficial)

    fun buttonTitle(readiness: InstallReadiness): String =
        when {
            readiness.needsStartUp -> "Choose your FM-1's start-up"
            isTicked(readiness) -> "Install $targetName"
            else -> "Tick each one to install"
        }
}
