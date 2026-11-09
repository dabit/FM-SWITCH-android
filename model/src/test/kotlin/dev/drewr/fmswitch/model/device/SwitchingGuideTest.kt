package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.LeavingDestination
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.pkg.UpdateCode
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.SimulatedFm1
import dev.drewr.fm1.updater.Fm1UpdateError
import dev.drewr.fmswitch.model.history.FlashHistory
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.InMemoryAppSettings
import dev.drewr.fmswitch.model.settings.StartUp
import dev.drewr.fmswitch.model.settings.TransportKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SwitchingGuideTest {
    private val transit =
        LeavingGuide("TransitFM1", Fm1Identity.officialV15, UpdateCode.OWN_LOADER, Fm1BootArea.M_BOOTLOADER)
    private val felucca = LeavingGuide("Felucca", Fm1Identity.of("FM-1", 916), UpdateCode.FELUCCA_ORIGINAL_CHECK, null)

    @Test fun mVavesFirmwaresAreKnownByTheirIdentity() {
        assertEquals(UpdateCode.M_VAVE, SwitchingGuide.running(Fm1Identity.officialV15, null, null)?.updateCode)
        assertEquals(Fm1BootArea.V16, SwitchingGuide.running(Fm1Identity.officialV16, null, StartUp.MVave)?.bootArea)
    }

    @Test fun aSavedGuideTellsTransitFM1FromV15() {
        val running = SwitchingGuide.running(Fm1Identity.officialV15, transit, StartUp.MVave)
        assertEquals(transit, running)
        assertTrue(SwitchingGuide.reportsOfficialIdentity(requireNotNull(running)))
    }

    @Test fun aSavedGuideForAnotherIdentityIsIgnored() =
        assertNull(
            SwitchingGuide.running(felucca.identity, transit, null),
        )

    @Test fun theStartUpFillsInABootloaderTheGuideDoesntKnow() =
        assertEquals(
            Fm1BootArea.M_BOOTLOADER,
            SwitchingGuide.running(felucca.identity, felucca, StartUp.MBootloader)?.bootArea,
        )

    @Test fun nothingIsKnownAboutAnUpdateLoaderOrAnUnknownFirmware() {
        assertNull(SwitchingGuide.running(requireNotNull(Fm1Identity.parse("ota-FM-1_015")), null, StartUp.MVave))
        assertNull(SwitchingGuide.running(felucca.identity, null, StartUp.MVave))
    }

    @Test fun startUpsAndBootloadersMatch() =
        StartUp.entries
            .filter {
                it.bootArea != null
            }.forEach { assertEquals(it, startUpOf(requireNotNull(it.bootArea))) }

    @Test fun v15IsADestinationOfItsOwn() {
        val file = FwscPackage(SamplePackage.file("FM-1_015"))
        assertEquals(LeavingDestination.V15, destinationOf(file, official = OfficialFirmware.V15))
    }

    @Test fun aV16PackageIsADestinationOfItsOwn() {
        val file = FwscPackage(SamplePackage.file("FM-1_016"))
        assertEquals(LeavingDestination.V16, destinationOf(file, official = OfficialFirmware.V16))
    }

    @Test fun v15ThroughTransitFM1KeepsTheBootloader() {
        val file = FwscPackage(SamplePackage.file("FM-1_015"))
        val installed = SwitchingGuide.installing(file, "V15", transit, StartUp.MBootloader)
        assertEquals(Fm1BootArea.M_BOOTLOADER, installed.first?.bootArea)
        assertNull(installed.second)
    }

    @Test fun aRefusalNeedsMIDIMONUnlessTheFirmwareRunningTakesV15() {
        val item =
            StoredFirmware(
                "a",
                "LOOM",
                "1",
                null,
                "FM-1_8000012",
                "a",
                1,
                "a.fwsc",
                StoredFirmware.Source.Catalogue,
                null,
                null,
                null,
                Instant.EPOCH,
            )
        val refused = Fm1UpdateError.BootloaderMismatch(3)
        val fromFelucca = SwitchingGuide.filling(felucca, StartUp.MBootloader)
        assertTrue(FlashJob.needsAStepFirst(refused, item, fromFelucca, StartUp.MVave))
        assertFalse(FlashJob.needsAStepFirst(refused, item, transit, null))
        assertFalse(FlashJob.needsAStepFirst(refused, item, null, StartUp.MVave))
        val message = FlashJob.failureMessage(refused, item, "Felucca", needsAStepFirst = true)
        assertTrue(
            message.startsWith("Your FM-1 is fine. It's still running Felucca, which won't take LOOM from here."),
        )
        assertTrue(message.endsWith("Install MIDIMON 0.4 or LOOM-8 first, then LOOM."))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun anInstallSavesItsGuide() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val item = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val history = FlashHistory(null, dispatcher) { Instant.EPOCH }
            val controller =
                DeviceController(library, history, InMemoryAppSettings(), backgroundScope) { _, options ->
                    SimulatedFm1(options, backgroundScope)
                }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(301.milliseconds)
            runCurrent()
            controller.flash(listOf(item), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60.seconds)
            runCurrent()
            val guide = history.last?.guide?.toGuide()
            assertTrue(controller.job.value?.phase is FlashPhase.Succeeded)
            assertEquals(Fm1Identity.of("FM-1", 916), guide?.identity)
            assertEquals(item.name, guide?.firmwareName)
            assertEquals(guide, controller.runningGuide)
        }
}
