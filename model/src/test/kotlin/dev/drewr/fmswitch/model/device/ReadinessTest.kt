package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.updater.InstallKind
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessTest {
    private val item =
        StoredFirmware(
            "one",
            "One",
            "1",
            null,
            "FM-1_901",
            "one",
            1,
            "one.fwsc",
            StoredFirmware.Source.Imported,
            null,
            null,
            null,
            java.time.Instant.EPOCH,
        )
    private val other = item.copy(id = "two", product = "FM-1_902")

    private fun readiness(
        kind: InstallKind,
        steps: List<StoredFirmware> = listOf(item),
        startup: Boolean = false,
    ) = InstallReadiness(
        steps,
        InstallCheckState.Found(kind),
        Fm1Identity.parse("FM-1_900"),
        refusesV15OverOtherBootloaders = startup,
    )

    @Test fun aChainIsAlwaysSwitching() = assertTrue(readiness(InstallKind.UPDATE, listOf(item, other)).isSwitching)

    @Test
    fun onlyTheFirstStepIsChecked() =
        runTest {
            val v15 = item.copy(id = "v15", product = "FM-1_015", source = StoredFirmware.Source.Official)
            val asked = mutableListOf<StoredFirmware>()
            InstallReadiness.check(listOf(v15, other), Fm1Identity.parse("FM-1_900")) {
                asked += it
                InstallKind.UPDATE
            }
            assertEquals(listOf(v15), asked)
        }

    @Test fun alreadyInstalledBlocksStart() = assertFalse(readiness(InstallKind.REINSTALL).canStart(true, true))

    @Test
    fun finishingUpdateCanStart() =
        runTest {
            val loader = Fm1Identity.parse("ota-FM-1_015")
            val state =
                InstallReadiness.check(listOf(item), loader) {
                    error("A loader should resume without checking a package")
                }
            assertEquals(InstallCheckState.FinishingUpdate, state)
            assertTrue(InstallReadiness(listOf(item), state, loader).canStart(false, false))
        }

    @Test fun startUpMustBeChosenFirst() =
        assertFalse(
            readiness(InstallKind.UPDATE, startup = true).canStart(true, true),
        )

    @Test fun aPackageThatAcceptsV15IsntFlagged() =
        runTest {
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, StandardTestDispatcher(testScheduler)) { Instant.EPOCH }
            library.load()
            val target = library.add(SamplePackage.file("FM-1_015"), "V15")
            assertFalse(InstallReadiness.refusesV15OverOtherBootloaders(target, library))
        }
}
