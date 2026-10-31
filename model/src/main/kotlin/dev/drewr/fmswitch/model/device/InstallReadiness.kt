// Ported from FM-SWITCH App/Model/InstallReadiness.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.refusesV15OverOtherBootloaders
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.updater.InstallKind
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.FeluccaUpdateCode
import dev.drewr.fmswitch.model.settings.StartUp
import kotlinx.coroutines.CancellationException

sealed class InstallCheckState {
    data object Checking : InstallCheckState()

    data class Found(
        val kind: InstallKind,
    ) : InstallCheckState()

    data object FinishingUpdate : InstallCheckState()

    data class Failed(
        val message: String,
    ) : InstallCheckState()
}

data class InstallReadiness(
    val steps: List<StoredFirmware>,
    val checked: InstallCheckState,
    val identity: Fm1Identity?,
    val isMissingOfficial: Boolean = false,
    val isUntested: Boolean = false,
    val refusesV15OverOtherBootloaders: Boolean = false,
    val startUp: StartUp? = null,
) {
    val needsStartUp: Boolean get() = refusesV15OverOtherBootloaders && startUp == null
    val isSwitching: Boolean
        get() = steps.size > 1 || (checked as? InstallCheckState.Found)?.kind == InstallKind.SWITCHING_FIRMWARE

    val isSameIdentity: Boolean
        get() = !isSwitching && (checked as? InstallCheckState.Found)?.kind == InstallKind.SAME_IDENTITY

    val isAlreadyInstalled: Boolean
        get() = steps.size == 1 && (checked as? InstallCheckState.Found)?.kind == InstallKind.REINSTALL

    fun canStart(
        confirmsSwitch: Boolean,
        acceptsUntested: Boolean,
    ): Boolean {
        if (identity == null || isMissingOfficial || (isUntested && !acceptsUntested) || needsStartUp) return false
        return when (checked) {
            InstallCheckState.FinishingUpdate -> true

            is InstallCheckState.Found -> !isAlreadyInstalled && (!isSwitching || confirmsSwitch)

            InstallCheckState.Checking,
            is InstallCheckState.Failed,
            -> false
        }
    }

    companion object {
        fun fromTarget(
            target: StoredFirmware,
            requiresOfficial: Boolean,
            checked: InstallCheckState,
            refusesV15OverOtherBootloaders: Boolean,
            isUntested: Boolean,
            startUp: StartUp?,
            device: DeviceController,
            library: FirmwareLibrary,
            alwaysViaOfficial: Boolean,
        ): InstallReadiness {
            val viaOfficial = requiresOfficial || alwaysViaOfficial
            val steps = device.plan(target, requiresOfficial, alwaysViaOfficial)
            // Android has no Mac-only warning action; the UI explains recovery separately.
            return InstallReadiness(
                steps = steps,
                checked = checked,
                identity = device.identity.value,
                isMissingOfficial =
                    viaOfficial && target.source != StoredFirmware.Source.Official && library.official == null,
                isUntested = isUntested,
                refusesV15OverOtherBootloaders = refusesV15OverOtherBootloaders,
                startUp = startUp,
            )
        }

        suspend fun check(
            first: StoredFirmware?,
            device: DeviceController,
        ): InstallCheckState = check(listOfNotNull(first), device.identity.value) { device.check(it).kind }

        suspend fun check(
            steps: List<StoredFirmware>,
            identity: Fm1Identity?,
            checker: suspend (StoredFirmware) -> InstallKind,
        ): InstallCheckState {
            val current = identity ?: return InstallCheckState.Checking
            val first = steps.firstOrNull() ?: return InstallCheckState.Checking
            if (current.isLoader) return InstallCheckState.FinishingUpdate
            return try {
                InstallCheckState.Found(checker(first))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                InstallCheckState.Failed(error.message.orEmpty())
            }
        }

        suspend fun refusesV15OverOtherBootloaders(
            target: StoredFirmware,
            library: FirmwareLibrary,
        ): Boolean =
            try {
                library.loadPackage(target).refusesV15OverOtherBootloaders
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                FeluccaUpdateCode.isKnown(target.catalogueID)
            }
    }
}
