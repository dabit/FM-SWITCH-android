package dev.drewr.fmswitch.ui.install

import dev.drewr.fm1.updater.InstallKind
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.device.DeviceController
import dev.drewr.fmswitch.model.device.FlashJob
import dev.drewr.fmswitch.model.device.FlashPhase
import dev.drewr.fmswitch.model.device.InstallCheckState
import dev.drewr.fmswitch.model.device.InstallProgress
import dev.drewr.fmswitch.model.device.InstallReadiness
import dev.drewr.fmswitch.model.device.SwitchingCheck
import dev.drewr.fmswitch.model.history.FlashHistory
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.InMemoryAppSettings
import dev.drewr.fmswitch.model.settings.StartUp
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstallPresentationTest {
    @Test
    fun `resumable problem carries on and does not close`() {
        val presentation = problemPresentation(true, false, true, true)

        assertEquals(ProblemAction.RESUME, presentation.action)
        assertEquals(R.string.install_interrupted_body, presentation.bodyRes)
        assertEquals(false, presentation.showsClose)
    }

    @Test
    fun `step first problem uses its job message and never retries`() {
        val withMidiMon = problemPresentation(false, true, false, true)
        val withoutMidiMon = problemPresentation(false, true, false, false)

        assertEquals(true, withMidiMon.usesJobMessage)
        assertEquals(ProblemAction.MIDIMON, withMidiMon.action)
        assertEquals(ProblemAction.NONE, withoutMidiMon.action)
        assertEquals(true, withMidiMon.showsClose)
        assertEquals(true, withoutMidiMon.showsClose)
    }

    @Test
    fun `nonresumable retry explains possible refusal and closes`() {
        val presentation = problemPresentation(false, false, true, false)

        assertEquals(ProblemAction.RETRY, presentation.action)
        assertEquals(R.string.install_try_again_might_refuse, presentation.bodyRes)
        assertEquals(true, presentation.showsClose)
    }

    @Test
    fun `plain retry omits the might-be-refused sentence`() {
        val presentation = problemPresentation(false, false, false, false)

        assertEquals(R.string.install_try_again, presentation.bodyRes)
        assertEquals(ProblemAction.RETRY, presentation.action)
    }

    @Test
    fun `status notices follow connection check and identity state`() {
        assertEquals(
            InstallStatusNotice.PLUG_IN,
            installStatusNotice(false, false, InstallCheckState.Checking, false, false),
        )
        assertEquals(
            InstallStatusNotice.WAITING,
            installStatusNotice(true, true, InstallCheckState.FinishingUpdate, false, false),
        )
        assertEquals(
            InstallStatusNotice.CHECKING,
            installStatusNotice(true, false, InstallCheckState.Checking, false, false),
        )
        assertEquals(
            InstallStatusNotice.FAILED,
            installStatusNotice(true, false, InstallCheckState.Failed("no reply"), false, false),
        )
        assertEquals(
            InstallStatusNotice.ALREADY_RUNNING,
            installStatusNotice(true, false, InstallCheckState.Found(InstallKind.REINSTALL), true, false),
        )
        assertEquals(
            InstallStatusNotice.SAME_IDENTITY,
            installStatusNotice(true, false, InstallCheckState.Found(InstallKind.SAME_IDENTITY), false, true),
        )
    }

    @Test
    fun `drawing progress is the forward tracker value only while running`() {
        assertEquals(0.72f, drawingProgress(true, 0.72))
        assertNull(drawingProgress(false, 0.72))
    }

    @Test
    fun `start-up warning requires a non-M-VAVE answer`() {
        assertEquals(false, showsStartUpWarning(false, null))
        StartUp.entries.forEach { startUp ->
            assertEquals(startUp != StartUp.MVave, showsStartUpWarning(true, startUp))
        }
    }

    @Test
    fun `tracker reaches restarting on the rebooting update`() {
        val item = testFirmware()
        val tracker = InstallProgress.Tracker()

        tracker.update(FlashJob(listOf(item), phase = FlashPhase.Rebooting))

        assertEquals(InstallProgress.Stage.Restarting, tracker.current.stage)
    }

    @Test
    fun `readiness from target uses official first and is switching`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(
                    createTempDirectory().toFile(),
                    {
                        FileInputStream(
                            File(
                                System.getProperty("fm1.v15")
                                    ?: File("src/main/assets/firmware/FM-1_015.fwsc").absolutePath,
                            ),
                        )
                    },
                    dispatcher,
                    { Instant.EPOCH },
                )
            library.load()
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> error("MIDI unavailable") }
            val target = testFirmware()

            val readiness =
                InstallReadiness.fromTarget(
                    target,
                    requiresOfficial = true,
                    checked = InstallCheckState.Checking,
                    switching = SwitchingCheck(),
                    isUntested = false,
                    startUp = StartUp.MVave,
                    device = controller,
                    library = library,
                    alwaysViaOfficial = false,
                )

            assertEquals(2, readiness.steps.size)
            assertEquals("Felucca", readiness.steps.last().name)
            assertEquals(true, readiness.isSwitching)
        }

    private fun testFirmware() =
        StoredFirmware(
            "test",
            "Felucca",
            "1.0",
            product = "Felucca",
            sha256 = "test",
            size = 1,
            fileName = "test.fwsc",
            source = StoredFirmware.Source.Catalogue,
            savedAt = Instant.EPOCH,
        )
}
