// Ported from FM-SWITCH App/Model/DeviceController.swift.
package dev.drewr.fmswitch.model.device

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
import dev.drewr.fmswitch.model.settings.StartUp
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
            _identity.value = currentUpdater.identify()
            repeat(3) {
                if (_identity.value != null || _ports.value.isEmpty()) return@repeat
                delay(1.seconds)
                _ports.value = currentUpdater.transport.ports()
                _identity.value = currentUpdater.identify()
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
    ) {
        if (steps.isEmpty()) return
        val next = FlashJob(steps, confirmsSwitchingFirmware, confirmsSameIdentity)
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

    private suspend fun run(startIndex: Int) {
        val steps = _job.value?.steps ?: return
        val currentUpdater = updater ?: return
        val options =
            _job.value?.let { InstallOptions(it.confirmsSwitchingFirmware, it.confirmsSameIdentity) } ?: return
        for (index in startIndex until steps.size) {
            val item = steps[index]
            updateJob { it.copy(index = index, phase = FlashPhase.Preparing) }
            appendLog("▸ ${item.name} ${item.version} (${item.product})")
            try {
                val packageFile = library.loadPackage(item)
                (transport as? SimulatedFm1)?.prepare(packageFile)
                val installed =
                    if (currentUpdater.identify()?.isLoader == true) {
                        currentUpdater.resume(packageFile, ::apply)
                    } else {
                        currentUpdater.install(packageFile, options, ::apply)
                    }
                _identity.value = installed
                withContext(NonCancellable) {
                    try {
                        history.record(item)
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
                appendLog("✕ ${error.message.orEmpty()}")
                updateJob {
                    it.copy(
                        phase = FlashPhase.Failed(failureMessage(error, item), FlashJob.canResume(error)),
                        needsAStepFirst = needsAStepFirst(error, item),
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
                updateJob { it.copy(phase = FlashPhase.Writing(event.fraction)) }
            }

            is UpdateEvent.Checking -> {
                if (_job.value?.phase !is FlashPhase.Verifying) {
                    appendLog(
                        "Written; the loader is reading it back to check it.",
                    )
                }
                updateJob { it.copy(phase = FlashPhase.Verifying(event.fraction)) }
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

    private fun failureMessage(
        error: Throwable,
        item: StoredFirmware,
    ): String {
        if (!FlashJob.isRefusal(error)) return error.message.orEmpty()
        val running =
            _identity.value?.text?.let { history.lastInstall(it)?.name }
                ?: "the firmware it had"
        return if (needsAStepFirst(error, item)) {
            "Your FM-1 is fine. It's still running $running, which won't take ${item.name} from here. Install MIDIMON 0.4 or LOOM-8 first, then ${item.name}."
        } else {
            "Your FM-1 is fine. It's still running $running, which won't take ${item.name} from here. Install V15 first, then ${item.name}."
        }
    }

    private fun needsAStepFirst(
        error: Throwable,
        item: StoredFirmware,
    ): Boolean =
        FlashJob.isRefusal(error) &&
            (settings.startUp != StartUp.MVave || item.source == StoredFirmware.Source.Official)
}
