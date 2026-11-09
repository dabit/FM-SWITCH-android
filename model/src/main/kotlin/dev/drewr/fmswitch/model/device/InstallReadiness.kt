// Ported from FM-SWITCH App/Model/InstallReadiness.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.FlashChipSupport
import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.LeavingDestination
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.LeavingRoute
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.pkg.flashChipSupport
import dev.drewr.fm1.protocol.Fm1FlashChip
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.updater.InstallKind
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.catalogue.HubFlashChip
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.StartUp
import kotlinx.coroutines.CancellationException

val HubFlashChip.support: FlashChipSupport
    get() =
        when (this) {
            HubFlashChip.Old -> FlashChipSupport.FIRST_CHIP_ONLY
            HubFlashChip.New -> FlashChipSupport.BOTH_CHIPS
            HubFlashChip.Any -> FlashChipSupport.NO_CHECK
        }

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

data class SwitchingCheck(
    val isRead: Boolean = false,
    val running: LeavingGuide? = null,
    val installed: LeavingGuide? = null,
    val route: LeavingRoute? = null,
    val destination: LeavingDestination? = null,
    val firstStep: StoredFirmware? = null,
    val firstStepProblem: String? = null,
    val dependsOnStartUp: Boolean = false,
    val followsV16: Boolean = false,
    val installsV16: Boolean = false,
    val knowsOnlyFirstChip: List<String> = emptyList(),
    val hasNewerFlashChip: Boolean = false,
) {
    val needsFirstStep get() = route == LeavingRoute.THROUGH_FIXED_FIRMWARE

    val warnings: List<String>
        get() {
            val guide = installed ?: return emptyList()
            val warnings = SwitchingWords.warnings(guide)
            if (guide.bootArea != null || !SwitchingGuide.dependsOnBootArea(guide.updateCode)) return warnings
            val changed = guide.copy(bootArea = Fm1BootArea.M_BOOTLOADER)
            return warnings + SwitchingWords.warnings(changed).filterNot(warnings::contains).map { "$IF_CHANGED $it" }
        }

    companion object {
        const val IF_CHANGED = "If your FM-1's bootloader isn't the one it came with:"

        fun from(
            planned: List<Pair<StoredFirmware, FwscPackage>>,
            identity: Fm1Identity?,
            flashChipId: Int?,
            saved: LeavingGuide?,
            startUp: StartUp?,
            listedChip: (StoredFirmware) -> HubFlashChip?,
            firstStep: Pair<StoredFirmware, FwscPackage>?,
            firstStepProblem: String?,
            officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official,
        ): SwitchingCheck {
            val running = SwitchingGuide.running(identity, saved, startUp)
            val destination = planned.firstOrNull()?.second?.let { destinationOf(it, officialOf(it)) }
            val route = destination?.let { running?.route(it) }
            val allPlanned = listOfNotNull(firstStep) + planned

            fun installed(
                starting: LeavingGuide?,
                selectedStartUp: StartUp?,
            ): LeavingGuide? {
                var previous = starting
                var last: LeavingGuide? = null
                allPlanned.forEach { (item, packageFile) ->
                    last = SwitchingGuide.guide(packageFile, item.name, previous, selectedStartUp)
                    previous = last ?: previous
                }
                return last
            }
            return SwitchingCheck(
                isRead = true,
                running = running,
                installed = installed(running, startUp),
                route = route,
                destination = destination,
                firstStep = firstStep?.first,
                firstStepProblem = firstStepProblem,
                dependsOnStartUp =
                    installed(SwitchingGuide.running(identity, saved, null), null)?.let {
                        it.bootArea == null && SwitchingGuide.dependsOnBootArea(it.updateCode)
                    } ?: false,
                followsV16 =
                    planned.any {
                        destinationOf(it.second, officialOf(it.second)) == LeavingDestination.V15
                    } && (running?.identity == Fm1Identity.officialV16 || running?.bootArea == Fm1BootArea.V16),
                installsV16 = planned.any { destinationOf(it.second, officialOf(it.second)) == LeavingDestination.V16 },
                knowsOnlyFirstChip =
                    if (flashChipId == Fm1FlashChip.P25Q80) {
                        emptyList()
                    } else {
                        planned
                            .filter { (item, packageFile) ->
                                (packageFile.flashChipSupport ?: listedChip(item)?.support) ==
                                    FlashChipSupport.FIRST_CHIP_ONLY
                            }.map { it.first.name }
                    },
                hasNewerFlashChip = flashChipId == Fm1FlashChip.UC25HQ80,
            )
        }

        suspend fun read(
            target: StoredFirmware,
            requiresOfficial: Boolean,
            alwaysViaOfficial: Boolean,
            device: DeviceController,
            library: FirmwareLibrary,
            savedGuide: LeavingGuide?,
            flashChipId: Int?,
            startUp: StartUp?,
            entries: List<CatalogueEntry>,
            fetch: suspend (String) -> ByteArray,
        ): SwitchingCheck {
            val steps = device.plan(target, requiresOfficial, alwaysViaOfficial)
            val packages =
                try {
                    steps.map { library.loadPackage(it) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    return SwitchingCheck(isRead = true)
                }
            val planned = steps.zip(packages)
            val listedChip = { item: StoredFirmware -> entries.firstOrNull { it.id == item.catalogueID }?.flashChip }
            var check = from(planned, device.identity.value, flashChipId, savedGuide, startUp, listedChip, null, null)
            if (check.needsFirstStep) {
                var first: Pair<StoredFirmware, FwscPackage>? = null
                var problem: String? = null
                try {
                    val item = FixedFirmware.find(library, entries, fetch)
                    first = item to library.loadPackage(item)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    problem = error.message
                }
                check =
                    from(
                        planned,
                        device.identity.value,
                        flashChipId,
                        savedGuide,
                        startUp,
                        listedChip,
                        first,
                        problem,
                    )
            }
            return check
        }
    }
}

data class InstallReadiness(
    val steps: List<StoredFirmware>,
    val checked: InstallCheckState,
    val identity: Fm1Identity?,
    val isMissingOfficial: Boolean = false,
    val isUntested: Boolean = false,
    val switching: SwitchingCheck = SwitchingCheck(),
    val startUp: StartUp? = null,
) {
    val needsStartUp get() = switching.dependsOnStartUp && startUp == null
    val isMissingFirstStep get() = switching.needsFirstStep && switching.firstStep == null
    val isSwitching
        get() =
            steps.size > 1 ||
                (checked as? InstallCheckState.Found)?.kind == InstallKind.SWITCHING_FIRMWARE ||
                reinstallsOfficial
    val isSameIdentity get() = !isSwitching && (checked as? InstallCheckState.Found)?.kind == InstallKind.SAME_IDENTITY
    val canReinstallOfficial
        get() =
            (checked as? InstallCheckState.Found)?.kind == InstallKind.REINSTALL &&
                steps.size == 1 &&
                identity?.isOfficial == true
    val knowsItIsntOfficial
        get() =
            canReinstallOfficial && switching.running?.let(SwitchingGuide::reportsOfficialIdentity) == true
    val reinstallsOfficial get() = knowsItIsntOfficial
    val isAlreadyInstalled
        get() =
            (checked as? InstallCheckState.Found)?.kind == InstallKind.REINSTALL &&
                steps.size == 1 &&
                !knowsItIsntOfficial

    fun canStart(
        confirmsSwitch: Boolean,
        acceptsUntested: Boolean,
        confirmsNotOfficial: Boolean = false,
    ): Boolean {
        if (
            identity == null ||
            !switching.isRead ||
            isMissingOfficial ||
            isMissingFirstStep ||
            (isUntested && !acceptsUntested) ||
            needsStartUp
        ) {
            return false
        }
        return when (checked) {
            InstallCheckState.FinishingUpdate -> {
                true
            }

            is InstallCheckState.Found -> {
                val confirmsReinstall = canReinstallOfficial && confirmsNotOfficial
                val isSwitch = isSwitching || confirmsReinstall
                (!isAlreadyInstalled || confirmsReinstall) && (!isSwitch || confirmsSwitch)
            }

            InstallCheckState.Checking, is InstallCheckState.Failed -> {
                false
            }
        }
    }

    companion object {
        const val UNTESTED_WARNING =
            "This firmware is only listed on aroum's FM1 Resource Hub or mutant.cloud, not on The Firmware Hub or fm1-editor.com, so it may not have been well tested on a real FM-1. It may not install, or may not let another firmware install after it. Recovery might need a computer, check this app's settings for further information."

        fun fromTarget(
            target: StoredFirmware,
            requiresOfficial: Boolean,
            checked: InstallCheckState,
            switching: SwitchingCheck,
            isUntested: Boolean,
            startUp: StartUp?,
            device: DeviceController,
            library: FirmwareLibrary,
            alwaysViaOfficial: Boolean,
        ) = InstallReadiness(
            steps = listOfNotNull(switching.firstStep) + device.plan(target, requiresOfficial, alwaysViaOfficial),
            checked = checked,
            identity = device.identity.value,
            isMissingOfficial =
                (requiresOfficial || alwaysViaOfficial) &&
                    target.source != StoredFirmware.Source.Official &&
                    library.official == null,
            isUntested = isUntested,
            switching = switching,
            startUp = startUp,
        )

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
    }
}
