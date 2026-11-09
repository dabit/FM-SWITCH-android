// Ported from FM-SWITCH App/Model/DeviceController.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.PortDescriptor
import dev.drewr.fm1.transport.SimulatedFm1
import dev.drewr.fm1.updater.Fm1Updater
import dev.drewr.fm1.updater.InstallCheck
import dev.drewr.fm1.updater.InstallOptions
import dev.drewr.fm1.updater.UpdateEvent
import dev.drewr.fmswitch.model.history.FlashHistory
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.AppSettings
import dev.drewr.fmswitch.model.settings.TransportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class DeviceController(
    private val library: FirmwareLibrary,
    private val history: FlashHistory,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    private val transportFactory: (TransportKind, SimulatedFm1.Options) -> MidiTransport,
) {
    private val _identity = MutableStateFlow<Fm1Identity?>(null)
    val identity: StateFlow<Fm1Identity?> = _identity.asStateFlow()
    private val _flashChipId = MutableStateFlow<Int?>(null)
    val flashChipId: StateFlow<Int?> = _flashChipId.asStateFlow()
    private val _ports = MutableStateFlow<List<PortDescriptor>>(emptyList())
    val ports: StateFlow<List<PortDescriptor>> = _ports.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _job = MutableStateFlow<FlashJob?>(null)
    val job: StateFlow<FlashJob?> = _job.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()
    private val _transportError = MutableStateFlow<String?>(null)
    val transportError: StateFlow<String?> = _transportError.asStateFlow()

    private var updater: Fm1Updater? = null
    private var transport: MidiTransport? = null
    private var watch: kotlinx.coroutines.Job? = null
    val isFlashing: Boolean get() = _job.value?.isRunning == true

    val runningGuide
        get() =
            SwitchingGuide.running(
                identity.value,
                identity.value?.text?.let { history.lastInstall(it)?.guide?.toGuide() },
                settings.startUp,
            )

    fun appendLog(line: String) {
        _log.update { it + line }
    }

    fun configure(kind: TransportKind) {
        if (isFlashing) return
        _transportError.value = null
        try {
            watch?.cancel()
            val simulatorOptions =
                SimulatedFm1.Options().also { options ->
                    options.startsInRecoveryMode = settings.simulatedRecoveryMode
                    options.unplugDuringWriteAt = if (settings.simulatedCablePull) .45 else null
                    options.writesBackwards = settings.simulatedReadsBackwards
                    options.checksAfterWriting = settings.simulatedVerifies
                    options.bootloaderVersion = if (settings.simulatedMBootloader) "1.2" else null
                    options.comparesBootArea = settings.simulatedMBootloader
                    if (settings.simulatedOpenFirmware) options.installedIdentity = Fm1Identity.of("FM-1", 8_000_012)
                }
            transport = transportFactory(kind, simulatorOptions)
            updater = Fm1Updater(requireNotNull(transport))
            _identity.value = null
            _flashChipId.value = null
            _ports.value = emptyList()
            watch =
                scope.launch {
                    scan()
                    repeat(5) {
                        if (identity.value != null) return@repeat
                        delay(1.seconds)
                        scan()
                    }
                    requireNotNull(transport).changes().collect {
                        delay(400)
                        while (isFlashing) delay(1.seconds)
                        scan()
                    }
                }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            updater = null
            transport = null
            _identity.value = null
            _flashChipId.value = null
            _ports.value = emptyList()
            _transportError.value = error.message ?: error::class.simpleName.orEmpty()
        }
    }

    suspend fun scan() {
        val currentUpdater = updater ?: return
        if (_isScanning.value || isFlashing) return
        _isScanning.value = true
        try {
            _ports.value = currentUpdater.transport.ports()
            currentUpdater.summary()?.let { summary ->
                _identity.value = summary.identity
                _flashChipId.value = summary.flashChipId
            } ?: run {
                _identity.value = null
                _flashChipId.value = null
            }
            repeat(3) {
                if (_identity.value != null || _ports.value.isEmpty()) return@repeat
                delay(1.seconds)
                _ports.value = currentUpdater.transport.ports()
                currentUpdater.summary()?.let { summary ->
                    _identity.value = summary.identity
                    _flashChipId.value = summary.flashChipId
                } ?: run {
                    _identity.value = null
                    _flashChipId.value = null
                }
            }
        } finally {
            _isScanning.value = false
        }
    }

    fun plan(
        target: StoredFirmware,
        requiresOfficial: Boolean,
        alwaysViaOfficial: Boolean,
    ): List<StoredFirmware> {
        val official = library.official
        return if (
            target.source != StoredFirmware.Source.Official &&
            (requiresOfficial || alwaysViaOfficial) &&
            official != null &&
            _identity.value?.text != official.product
        ) {
            listOf(official, target)
        } else {
            listOf(target)
        }
    }

    suspend fun check(item: StoredFirmware): InstallCheck =
        requireNotNull(updater) { "Configure a transport first." }.check(library.loadPackage(item))

    fun flash(
        steps: List<StoredFirmware>,
        confirmsSwitchingFirmware: Boolean = false,
        confirmsSameIdentity: Boolean = false,
        confirmsReinstallingOfficial: Boolean = false,
        startedOnV16: Boolean = false,
    ) {
        if (steps.isEmpty()) return
        val next =
            FlashJob(
                steps = steps,
                confirmsSwitchingFirmware = confirmsSwitchingFirmware,
                confirmsSameIdentity = confirmsSameIdentity,
                confirmsReinstallingOfficial = confirmsReinstallingOfficial,
                guide = runningGuide,
                startedOnV16 = startedOnV16 || identity.value == Fm1Identity.officialV16,
            )
        while (true) {
            val current = _job.value
            if (current?.isRunning == true) return
            if (!_job.compareAndSet(current, next)) continue
            break
        }
        _log.value = emptyList()
        if (updater == null) {
            updateJob {
                it.copy(
                    phase = FlashPhase.Failed(_transportError.value ?: "MIDI isn't available.", false),
                )
            }
            return
        }
        scope.launch { run(0) }
    }

    fun resume() {
        while (true) {
            val current = _job.value ?: return
            if (current.isRunning) return
            val resumed = current.copy(phase = FlashPhase.Preparing, needsAStepFirst = false, mightBeRefused = false)
            if (_job.compareAndSet(current, resumed)) {
                scope.launch { run(current.index) }
                return
            }
        }
    }

    fun dismissFlash() {
        while (true) {
            val current = _job.value ?: return
            if (current.isRunning) return
            if (!_job.compareAndSet(current, null)) continue
            break
        }
        scope.launch { scan() }
    }

    fun installFirst(fixedFirmware: StoredFirmware) {
        val current = _job.value ?: return
        if (current.isRunning || !current.confirmsSwitchingFirmware) return
        flash(
            steps = listOf(fixedFirmware) + current.steps.drop(current.index),
            confirmsSwitchingFirmware = true,
            confirmsReinstallingOfficial = current.confirmsReinstallingOfficial,
            startedOnV16 = current.startedOnV16,
        )
    }

    private suspend fun run(startIndex: Int) {
        val steps = _job.value?.steps ?: return
        val currentUpdater = updater ?: return
        val options =
            _job.value?.let {
                InstallOptions(
                    confirmsSwitchingFirmware = it.confirmsSwitchingFirmware,
                    confirmsSameIdentity = it.confirmsSameIdentity,
                    confirmsReinstallingOfficial = it.confirmsReinstallingOfficial,
                )
            } ?: return
        for (index in startIndex until steps.size) {
            val item = steps[index]
            updateJob { it.copy(index = index, phase = FlashPhase.Preparing, writtenFraction = 0.0) }
            appendLog("▸ ${item.name} ${item.version} (${item.product})")
            try {
                val packageFile = library.loadPackage(item)
                (transport as? SimulatedFm1)?.prepare(packageFile)
                val before = currentUpdater.identify()
                val installed =
                    try {
                        if (before?.isLoader == true) {
                            currentUpdater.resume(packageFile, ::apply)
                        } else {
                            currentUpdater.install(packageFile, options, ::apply)
                        }
                    } catch (error: Throwable) {
                        confirmed(error, packageFile, before == packageFile.identity)
                    }
                _identity.value = installed
                withContext(NonCancellable) {
                    try {
                        record(item, packageFile)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        appendLog("Couldn't save this install in history: ${e.message.orEmpty()}")
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        updateJob {
                            it.copy(
                                phase = FlashPhase.Failed("The install was interrupted.", true),
                            )
                        }
                    }
                    throw error
                }
                _job.value?.writtenFraction?.takeIf { it > 0 }?.let { written ->
                    appendLog("Written: ${(written * 100).toInt()}% of it.")
                }
                appendLog("✕ ${error.message.orEmpty()}")
                val needsAStepFirst =
                    FlashJob.needsAStepFirst(error, item, _job.value?.guide, settings.startUp)
                val running = identity.value?.text?.let { history.lastInstall(it)?.name }
                updateJob {
                    it.copy(
                        phase =
                            FlashPhase.Failed(
                                FlashJob.failureMessage(error, item, running, needsAStepFirst),
                                FlashJob.canResume(error),
                            ),
                        needsAStepFirst = needsAStepFirst,
                        mightBeRefused = FlashJob.mightBeRefused(error),
                    )
                }
                return
            }
        }
        updateJob { it.copy(phase = FlashPhase.Succeeded(_identity.value?.text.orEmpty())) }
        appendLog("✓ Finished.")
    }

    private fun apply(event: UpdateEvent) {
        when (event) {
            is UpdateEvent.Connected -> {
                appendLog("Connected: ${event.identity.text}")
            }

            is UpdateEvent.Staging -> {
                updateJob { it.copy(phase = FlashPhase.Staging(event.requests)) }
            }

            UpdateEvent.WaitingForLoader -> {
                updateJob { it.copy(phase = FlashPhase.WaitingForLoader) }
                appendLog("Package accepted; rebooting into the update loader.")
            }

            is UpdateEvent.LoaderFound -> {
                appendLog("Loader: ${event.identity.text}")
            }

            is UpdateEvent.Writing -> {
                updateJob {
                    it.copy(
                        phase = FlashPhase.Writing(event.fraction),
                        writtenFraction = maxOf(it.writtenFraction, event.fraction),
                    )
                }
            }

            is UpdateEvent.Checking -> {
                if (_job.value?.phase !is FlashPhase.SecondPass) {
                    appendLog(
                        "Written once; the loader is going over it again.",
                    )
                }
                updateJob {
                    it.copy(phase = FlashPhase.SecondPass(event.fraction), writtenFraction = 1.0)
                }
            }

            UpdateEvent.Rebooting -> {
                updateJob { it.copy(phase = FlashPhase.Rebooting) }
                appendLog("Rebooting into the new firmware.")
            }

            is UpdateEvent.Finished -> {
                appendLog("Device reports ${event.identity.text}.")
            }
        }
    }

    private fun updateJob(change: (FlashJob) -> FlashJob) {
        _job.update { current -> current?.let(change) }
    }

    private suspend fun confirmed(
        error: Throwable,
        packageFile: FwscPackage,
        reinstalling: Boolean,
    ): Fm1Identity {
        val written = _job.value?.writtenFraction ?: 0.0
        if (!FlashJob.mightHaveFinished(error, written, reinstalling)) throw error
        return returned(packageFile) ?: throw error
    }

    private suspend fun returned(packageFile: FwscPackage): Fm1Identity? {
        updateJob { it.copy(phase = FlashPhase.Rebooting) }
        appendLog("Looking for the FM-1, in case it restarted into the new firmware.")
        val found: Fm1Identity? =
            withTimeoutOrNull<Fm1Identity?>(FlashJob.RETURN_SEARCH) {
                var returned: Fm1Identity? = null
                while (returned == null) {
                    val identity = updater?.identify()
                    if (identity != null) {
                        appendLog("Device reports ${identity.text}.")
                        if (identity == packageFile.identity) {
                            returned = identity
                        } else {
                            return@withTimeoutOrNull null
                        }
                    } else {
                        delay(2.seconds)
                    }
                }
                returned
            }
        if (found == null) appendLog("No FM-1 answered.")
        return found
    }

    private suspend fun record(
        item: StoredFirmware,
        packageFile: FwscPackage,
    ) {
        val installed = SwitchingGuide.installing(packageFile, item.name, _job.value?.guide, settings.startUp)
        history.record(item, installed.first)
        installed.second?.let { settings.startUp = it }
        installed.first?.let { guide -> updateJob { it.copy(guide = guide) } }
    }
}
