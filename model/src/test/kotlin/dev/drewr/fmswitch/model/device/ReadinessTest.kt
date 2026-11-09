package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.LeavingRoute
import dev.drewr.fm1.pkg.UpdateCode
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.updater.InstallKind
import dev.drewr.fmswitch.model.library.StoredFirmware
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessTest {
    private val item =
        StoredFirmware(
            "one",
            "One",
            "1",
            product = "FM-1_901",
            sha256 = "one",
            size = 1,
            fileName = "one.fwsc",
            source = StoredFirmware.Source.Imported,
            savedAt = Instant.EPOCH,
        )
    private val other = item.copy(id = "two", product = "FM-1_902")

    private fun readiness(
        kind: InstallKind,
        steps: List<StoredFirmware> = listOf(item),
        switching: SwitchingCheck = SwitchingCheck(isRead = true),
    ) = InstallReadiness(steps, InstallCheckState.Found(kind), Fm1Identity.parse("FM-1_900"), switching = switching)

    @Test fun aChainIsAlwaysSwitching() = assertTrue(readiness(InstallKind.UPDATE, listOf(item, other)).isSwitching)

    @Test fun alreadyInstalledBlocksStart() = assertFalse(readiness(InstallKind.REINSTALL).canStart(true, true))

    @Test fun finishingUpdateCanStart() =
        assertTrue(
            InstallReadiness(
                listOf(item),
                InstallCheckState.FinishingUpdate,
                Fm1Identity.parse("ota-FM-1_015"),
                switching = SwitchingCheck(isRead = true),
            ).canStart(false, false),
        )

    @Test
    fun onlyTheFirstStepIsChecked() =
        runTest {
            var checked: StoredFirmware? = null
            InstallReadiness.check(listOf(item, other), Fm1Identity.parse("FM-1_900")) {
                checked = it
                InstallKind.UPDATE
            }
            kotlin.test.assertEquals(item, checked)
        }

    @Test fun startUpMustBeChosenFirst() =
        assertFalse(
            readiness(
                InstallKind.UPDATE,
                switching = SwitchingCheck(isRead = true, dependsOnStartUp = true),
            ).canStart(true, true),
        )

    @Test fun canReinstallOfficialOnlyForOneOfficialReinstall() =
        assertTrue(
            InstallReadiness(
                listOf(item),
                InstallCheckState.Found(InstallKind.REINSTALL),
                Fm1Identity.officialV15,
                switching = SwitchingCheck(isRead = true),
            ).canReinstallOfficial,
        )

    @Test
    fun theNotOfficialTickMakesItASwitch() {
        val readiness =
            InstallReadiness(
                listOf(item),
                InstallCheckState.Found(InstallKind.REINSTALL),
                Fm1Identity.officialV15,
                switching = SwitchingCheck(isRead = true),
            )
        assertFalse(readiness.canStart(confirmsSwitch = false, acceptsUntested = true, confirmsNotOfficial = true))
        assertTrue(readiness.canStart(confirmsSwitch = true, acceptsUntested = true, confirmsNotOfficial = true))
    }

    @Test fun aSavedTransitGuideKnowsItIsntOfficial() =
        assertTrue(
            InstallReadiness(
                listOf(item),
                InstallCheckState.Found(InstallKind.REINSTALL),
                Fm1Identity.officialV15,
                switching = SwitchingCheck(isRead = true, running = transit()),
            ).knowsItIsntOfficial,
        )

    @Test fun aMissingFirstStepBlocksStart() =
        assertFalse(
            readiness(
                InstallKind.UPDATE,
                switching = SwitchingCheck(isRead = true, route = LeavingRoute.THROUGH_FIXED_FIRMWARE),
            ).canStart(true, true),
        )

    @Test
    fun theNotOfficialTickTurnsAReinstallIntoASwitch() {
        val readiness =
            InstallReadiness(
                listOf(item),
                InstallCheckState.Found(InstallKind.REINSTALL),
                Fm1Identity.officialV15,
                switching = SwitchingCheck(isRead = true),
            )
        assertFalse(readiness.isSwitching)
        assertFalse(readiness.canStart(confirmsSwitch = false, acceptsUntested = true, confirmsNotOfficial = true))
    }

    private fun transit() =
        LeavingGuide("TransitFM1", Fm1Identity.officialV15, UpdateCode.OWN_LOADER, Fm1BootArea.M_BOOTLOADER)
}
