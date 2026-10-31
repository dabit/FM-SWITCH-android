package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.MidiConnection
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.MidiTransportException
import dev.drewr.fm1.transport.PortDescriptor
import dev.drewr.fm1.transport.SimulatedFm1
import dev.drewr.fm1.updater.Fm1UpdateError
import dev.drewr.fmswitch.model.history.FlashHistory
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.InMemoryAppSettings
import dev.drewr.fmswitch.model.settings.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FlashingTest {
    private class ControllableTransport(
        private val simulated: SimulatedFm1,
    ) : MidiTransport {
        private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private var isPlugged = true

        override suspend fun ports(): List<PortDescriptor> = if (isPlugged) simulated.ports() else emptyList()

        override suspend fun open(port: PortDescriptor): MidiConnection =
            if (isPlugged) simulated.open(port) else throw MidiTransportException.PortGone

        override fun changes(): Flow<Unit> = changes.asSharedFlow()

        fun unplug() {
            isPlugged = false
            changes.tryEmit(Unit)
        }

        fun plug() {
            isPlugged = true
            changes.tryEmit(Unit)
        }
    }

    private val item =
        StoredFirmware(
            "one",
            "Felucca",
            "1",
            null,
            "FM-1_916",
            "one",
            1,
            "one.fwsc",
            StoredFirmware.Source.Imported,
            null,
            null,
            null,
            Instant.EPOCH,
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun aFlashWithoutATransportStopsImmediately() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> error("MIDI unavailable") }
            controller.configure(TransportKind.UsbMidi)
            controller.flash(listOf(saved))
            assertTrue(controller.identity.value == null)
            assertTrue(controller.ports.value.isEmpty())
            assertTrue(controller.job.value?.phase is FlashPhase.Failed)
            assertFalse(controller.job.value?.isRunning ?: true)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun configureAppliesEverySimulatorSetting() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val settings =
                InMemoryAppSettings().also {
                    it.simulatedRecoveryMode = true
                    it.simulatedCablePull = true
                    it.simulatedReadsBackwards = true
                    it.simulatedVerifies = true
                    it.simulatedMBootloader = true
                    it.simulatedOpenFirmware = true
                }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    settings,
                    backgroundScope,
                ) { _, options ->
                    SimulatedFm1(options, backgroundScope)
                }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(controller.identity.value?.isLoader == true)
            settings.simulatedRecoveryMode = false
            settings.simulatedOpenFirmware = true
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(controller.identity.value?.isOpenFirmware == true)
            settings.simulatedOpenFirmware = false
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(controller.identity.value?.text == "FM-1_900")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun watchLoopFollowsPlugAndUnplug() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val transport = ControllableTransport(SimulatedFm1(scope = backgroundScope))
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) {
                        Instant.EPOCH
                    },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> transport }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(controller.identity.value != null)
            transport.unplug()
            testScheduler.advanceTimeBy(400)
            runCurrent()
            assertTrue(controller.identity.value == null)
            transport.plug()
            testScheduler.advanceTimeBy(400)
            runCurrent()
            assertTrue(controller.identity.value != null)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun aLoaderAppearingAfterAResumableFailureIsSeen() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val target = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val settings = InMemoryAppSettings().also { it.simulatedCablePull = true }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    settings,
                    backgroundScope,
                ) { _, options ->
                    SimulatedFm1(options, backgroundScope)
                }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(target), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60.seconds)
            runCurrent()
            assertTrue((controller.job.value?.phase as? FlashPhase.Failed)?.canResume == true)
            assertTrue(controller.identity.value?.isLoader == true)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cancellingAFlashLeavesAResumableFailure() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val target = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val options = SimulatedFm1.Options().also { it.requestInterval = 1.seconds }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    scope,
                ) { _, _ ->
                    SimulatedFm1(options, scope)
                }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(target), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(80.seconds)
            runCurrent()
            assertTrue(controller.job.value?.phase is FlashPhase.Writing)
            scope.cancel()
            runCurrent()
            assertTrue((controller.job.value?.phase as? FlashPhase.Failed)?.canResume == true)
            assertFalse(controller.isFlashing)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun aHistoryWriteFailureDoesNotFailTheInstall() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val target = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val notADirectory = createTempFile().toFile()
            val controller =
                DeviceController(
                    library,
                    FlashHistory(notADirectory, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, options -> SimulatedFm1(options, backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(target), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60.seconds)
            runCurrent()
            assertTrue(controller.job.value?.phase is FlashPhase.Succeeded)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun flashesAFirmwareAndRecordsIt() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val simulated = SimulatedFm1(scope = backgroundScope)
            val history = FlashHistory(null, dispatcher) { Instant.EPOCH }
            val controller =
                DeviceController(library, history, InMemoryAppSettings(), backgroundScope) { _, _ -> simulated }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            assertTrue(controller.job.value?.phase is FlashPhase.Succeeded)
            assertTrue(controller.identity.value?.text == "FM-1_916")
            assertTrue(history.last?.product == "FM-1_916")
            assertTrue(controller.log.value.lastOrNull() == "✓ Finished.")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun flashesThroughALoaderThatReadsBackwardsAndVerifies() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val options =
                SimulatedFm1.Options().also {
                    it.writesBackwards = true
                    it.checksAfterWriting = true
                }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(options, backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            val verifying = controller.log.value.indexOfFirst { it.contains("reading it back") }
            val rebooting = controller.log.value.indexOfFirst { it.contains("Rebooting") }
            assertTrue(controller.job.value?.phase is FlashPhase.Succeeded)
            assertTrue(verifying >= 0 && verifying < rebooting)
            assertTrue(controller.log.value.count { it.contains("reading it back") } == 1)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun resumesAfterTheCableIsPulled() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val options = SimulatedFm1.Options().also { it.unplugDuringWriteAt = .45 }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(options, backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            assertTrue((controller.job.value?.phase as? FlashPhase.Failed)?.canResume == true)
            testScheduler.advanceTimeBy(2_000)
            controller.resume()
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            assertTrue(controller.job.value?.phase is FlashPhase.Succeeded)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun refusesTheSameIdentityUnconfirmed() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_900"), "Felucca")
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(scope = backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(controller.check(saved).kind == dev.drewr.fm1.updater.InstallKind.SAME_IDENTITY)
            controller.flash(listOf(saved), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            val failure = controller.job.value?.phase as? FlashPhase.Failed
            assertTrue(failure?.message?.contains("already reports FM-1_900") == true)
            assertFalse(failure?.canResume ?: true)
            assertTrue(controller.identity.value?.text == "FM-1_900")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun installsTheSameIdentityOnceConfirmed() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_900"), "Felucca")
            val history = FlashHistory(null, dispatcher) { Instant.EPOCH }
            val controller =
                DeviceController(library, history, InMemoryAppSettings(), backgroundScope) { _, _ ->
                    SimulatedFm1(scope = backgroundScope)
                }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved), confirmsSameIdentity = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            assertTrue(controller.job.value?.phase is FlashPhase.Succeeded)
            assertTrue(history.last?.product == "FM-1_900")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun refusesToSwitchUnconfirmed() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(scope = backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(controller.check(saved).kind == dev.drewr.fm1.updater.InstallKind.SWITCHING_FIRMWARE)
            controller.flash(listOf(saved))
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            val failure = controller.job.value?.phase as? FlashPhase.Failed
            assertTrue(failure?.message?.contains("confirm the switch") == true)
            assertFalse(failure?.canResume ?: true)
            assertTrue(controller.identity.value?.text == "FM-1_900")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun eachStepResumesWhenALoaderAnswers() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val simulated =
                SimulatedFm1(SimulatedFm1.Options().also { it.startsInRecoveryMode = true }, backgroundScope)
            val history = FlashHistory(null, dispatcher) { Instant.EPOCH }
            val controller =
                DeviceController(library, history, InMemoryAppSettings(), backgroundScope) { _, _ -> simulated }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved))
            runCurrent()
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            assertTrue(
                controller.job.value?.phase is FlashPhase.Succeeded,
                "${controller.job.value?.phase}: ${controller.log.value}",
            )
            assertTrue(controller.log.value.none { it.startsWith("Connected:") })
        }

    @Test fun loaderNotFoundIsResumable() {
        assertTrue(FlashJob.canResume(Fm1UpdateError.LoaderNotFound))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun planGoesViaOfficialUnlessAlreadyOnIt() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val target = library.add(SamplePackage.file("FM-1_916"), "Felucca")
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(scope = backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(
                controller.plan(target, requiresOfficial = true, alwaysViaOfficial = false) ==
                    listOf(library.official, target),
            )
            val alreadyOnV15 =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ ->
                    SimulatedFm1(
                        SimulatedFm1.Options().also {
                            it.installedIdentity = requireNotNull(Fm1Identity.parse("FM-1_015"))
                        },
                        backgroundScope,
                    )
                }
            alreadyOnV15.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertTrue(
                alreadyOnV15.plan(target, requiresOfficial = true, alwaysViaOfficial = false) == listOf(target),
            )
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun aRefusedInstallIsExplainedPlainly() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_015"), "V15")
            val options =
                SimulatedFm1.Options().also {
                    it.bootloaderVersion = "1.2"
                    it.comparesBootArea = true
                }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    InMemoryAppSettings(),
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(options, backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            val failed = controller.job.value?.phase as? FlashPhase.Failed
            assertTrue(failed?.message?.contains("Install MIDIMON 0.4 or LOOM-8 first") == true)
            assertTrue(controller.job.value?.needsAStepFirst == true)
            assertTrue(controller.identity.value?.text == "FM-1_900")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun aRefusalWithMVavesStartUpSuggestsV15First() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library =
                FirmwareLibrary(createTempDirectory().toFile(), {
                    FileInputStream(File(System.getProperty("fm1.v15")))
                }, dispatcher) { Instant.EPOCH }
            library.load()
            val saved = library.add(SamplePackage.file("FM-1_8000012"), "Open")
            val options =
                SimulatedFm1.Options().also {
                    it.bootloaderVersion = "1.2"
                    it.comparesBootArea = true
                }
            val settings = InMemoryAppSettings().also { it.startUp = dev.drewr.fmswitch.model.settings.StartUp.MVave }
            val controller =
                DeviceController(
                    library,
                    FlashHistory(null, dispatcher) { Instant.EPOCH },
                    settings,
                    backgroundScope,
                ) { _, _ -> SimulatedFm1(options, backgroundScope) }
            controller.configure(TransportKind.Simulated)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            controller.flash(listOf(saved), confirmsSwitchingFirmware = true)
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            val failed = controller.job.value?.phase as? FlashPhase.Failed
            assertTrue(failed?.message?.contains("Install V15 first") == true)
            assertFalse(controller.job.value?.needsAStepFirst ?: true)
        }

    @Test fun aStallBeforeTheInstallStartsMightBeARefusal() {
        assertTrue(FlashJob.mightBeRefused(Fm1UpdateError.Stalled(Fm1UpdateError.Phase.STAGING, 2)))
        assertFalse(FlashJob.mightBeRefused(Fm1UpdateError.Stalled(Fm1UpdateError.Phase.WRITING, 2)))
        assertFalse(FlashJob.mightBeRefused(Fm1UpdateError.BootloaderMismatch(3)))
    }

    @Test fun bothRefusalsAreRecognised() {
        assertTrue(FlashJob.isRefusal(Fm1UpdateError.BootloaderMismatch(3)))
        assertTrue(FlashJob.isRefusal(Fm1UpdateError.PackageRefused(2)))
        assertFalse(FlashJob.isRefusal(Fm1UpdateError.Stalled(Fm1UpdateError.Phase.STAGING, 2)))
    }
}
