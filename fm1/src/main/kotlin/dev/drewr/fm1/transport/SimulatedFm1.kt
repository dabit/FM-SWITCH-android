// Ported from FM1Kit 0.6.0 Transport/SimulatedFM1.swift, SimulatedFM1+Transport.swift and SimulatedFM1+Commands.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Capability
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SimulatedFm1(
    val options: Options = Options(),
    private val scope: CoroutineScope,
) : MidiTransport {
    class Options {
        var installedIdentity: Fm1Identity = Fm1Identity.of("FM-1", 900)
        var startsInRecoveryMode: Boolean = false
        var refusesRunningFirmware: Boolean = true
        var stagingRequestCount: Int = 74
        var writeChunkLength: Int = 512
        var writesBackwards: Boolean = false
        var checksAfterWriting: Boolean = false
        var requestInterval: Duration = 4.milliseconds
        var rebootDuration: Duration = 1500.milliseconds
        var responseTimeout: Duration = 5.seconds
        var unplugDuringWriteAt: Double? = null
        var installedBuildId: BuildId = BuildId(ByteArray(BuildId.BYTE_COUNT))
        var capabilityState: Int = 0
        var armReply: Int = 0
        var serialNumber: String? = "SIMULATED-FM-1"
        var bootloaderVersion: String? = null
        var comparesBootArea: Boolean = false
        var refusesAfterHeader: Boolean = false
        var flashChipId: Int = 0
        var shortensIdentity: Boolean = false
        var reportsFlashChipOnlyWhenAsked: Boolean = false
        var restartsWithoutFinishing: Boolean = false
        var keepsConnectionThroughRestart: Boolean = false
    }

    private enum class Mode { FIRMWARE, LOADER, OFFLINE }

    private val lock = Any()
    private var mode = if (options.startsInRecoveryMode) Mode.LOADER else Mode.FIRMWARE
    private var firmwareIdentity = options.installedIdentity
    private var generation = 0
    private var expected: FwscPackage? = null
    private var session: Job? = null
    private var hasUnpluggedOnce = false
    private var buildId = options.installedBuildId
    private var armedBuildId: BuildId? = null
    private val eventsInternal = mutableListOf<String>()
    private val changesInternal = MutableSharedFlow<Unit>(extraBufferCapacity = 2)

    val events: List<String>
        get() = synchronized(lock) { eventsInternal.toList() }

    val currentBuildId: BuildId
        get() = synchronized(lock) { buildId }

    val currentIdentity: Fm1Identity?
        get() =
            synchronized(lock) {
                when (mode) {
                    Mode.FIRMWARE -> {
                        firmwareIdentity
                    }

                    Mode.LOADER -> {
                        armedBuildId?.let { Fm1Identity.parse("ota-FM1C_${it.hex.take(16)}") } ?: loaderIdentity
                    }

                    Mode.OFFLINE -> {
                        null
                    }
                }
            }

    fun prepare(packageToInstall: FwscPackage) {
        synchronized(lock) { expected = packageToInstall }
    }

    override suspend fun ports(): List<PortDescriptor> =
        synchronized(lock) {
            if (mode == Mode.OFFLINE) {
                emptyList()
            } else {
                listOf(PortDescriptor("simulated-$generation", listOf(PORT_NAME), options.serialNumber))
            }
        }

    override suspend fun open(port: PortDescriptor): MidiConnection {
        val connection =
            synchronized(lock) {
                if (mode == Mode.OFFLINE || port.id != "simulated-$generation") throw MidiTransportException.PortGone
                SimulatedConnection(this, port, generation)
            }
        return connection
    }

    override fun changes(): Flow<Unit> = changesInternal.asSharedFlow()

    internal fun isCurrent(connectionGeneration: Int): Boolean =
        synchronized(lock) { generation == connectionGeneration && mode != Mode.OFFLINE }

    internal fun handle(
        sysex: ByteArray,
        connection: SimulatedConnection,
    ) {
        when {
            sysex.contentEquals(Fm1Message.HANDSHAKE_QUERY) -> {
                answerHandshake(asksForFullIdentity = false, connection)
            }

            sysex.contentEquals(Fm1Message.upgradeCommand) -> {
                if (currentIdentity?.isOpenFirmware == true && synchronized(lock) { armedBuildId } == null) {
                    log("Open firmware ignores the upgrade command until it's armed.")
                } else {
                    startSession(connection)
                }
            }

            else -> {
                Fm1Message.parseCommand(sysex)?.let { (command, payload) ->
                    when (command) {
                        Fm1Message.identityCommand -> {
                            if (payload.contentEquals(byteArrayOf(1))) {
                                answerHandshake(asksForFullIdentity = true, connection)
                            } else {
                                connection.fromHost.deliver(sysex)
                            }
                        }

                        Fm1Message.capabilityCommand,
                        Fm1Message.armCommand,
                        -> {
                            handleOpenFirmwareCommand(command, payload, connection)
                        }

                        else -> {
                            connection.fromHost.deliver(sysex)
                        }
                    }
                } ?: connection.fromHost.deliver(sysex)
            }
        }
    }

    private fun answerHandshake(
        asksForFullIdentity: Boolean,
        connection: SimulatedConnection,
    ) {
        val identity = currentIdentity ?: return
        val answer =
            if (options.shortensIdentity && !asksForFullIdentity) {
                Fm1Identity.parse(
                    identity.text.substringBeforeLast('_') + "_" + identity.text.substringAfterLast('_').take(3),
                )
                    ?: identity
            } else {
                identity
            }
        val flashChipId = if (options.reportsFlashChipOnlyWhenAsked && !asksForFullIdentity) 0 else options.flashChipId
        connection.toHost.deliver(answer.handshakeReply(flashChipId))
    }

    private fun startSession(connection: SimulatedConnection) {
        synchronized(lock) {
            val packageToInstall = expected ?: return
            if (session != null) return
            val sessionGeneration = generation
            session =
                scope.launch {
                    if (synchronized(lock) { mode } == Mode.LOADER) {
                        write(packageToInstall, connection)
                    } else {
                        stage(packageToInstall, connection)
                    }
                    synchronized(lock) {
                        if (generation == sessionGeneration) session = null
                    }
                }
        }
    }

    private suspend fun stage(
        packageToInstall: FwscPackage,
        connection: SimulatedConnection,
    ) {
        val image = packageToInstall.logicalImage
        val length = min(Fm1Message.maximumReadLength, image.size)
        if (length <= 0) return
        val bootAddress =
            packageToInstall.container.flash
                ?.offset
                ?.toLong()
        if (options.refusesAfterHeader || (options.comparesBootArea && options.bootloaderVersion != null)) {
            val addresses =
                listOf(0L, length.toLong()) +
                    if (options.refusesAfterHeader) emptyList() else listOfNotNull(bootAddress)
            for (address in addresses) {
                val offset = address.toInt()
                if (
                    !exchange(
                        ReadRequest(address = address, length = length),
                        image.copyOfRange(offset, offset + length),
                        connection,
                    )
                ) {
                    return
                }
            }
            log(
                if (options.refusesAfterHeader) {
                    "The package is refused after its header."
                } else {
                    "The package's bootloader isn't the FM-1's."
                },
            )
            return
        }
        repeat(options.stagingRequestCount) { index ->
            val address = ((index.toLong() * length) % maxOf(image.size - length, 1).toLong()).toInt()
            if (
                !exchange(
                    ReadRequest(address = address.toLong(), length = length),
                    image.copyOfRange(address, address + length),
                    connection,
                )
            ) {
                return
            }
        }
        if (
            options.refusesRunningFirmware && currentIdentity == packageToInstall.identity &&
            currentIdentity?.isOfficial == true
        ) {
            log("${packageToInstall.product} is already running, so the package is refused.")
            return
        }
        if (
            !exchange(
                ReadRequest(address = Fm1Message.stageFinishAddress, length = 8),
                Fm1Message.finishPayload,
                connection,
            )
        ) {
            return
        }
        if (!packageToInstall.isFirmware) return
        log("Staged ${packageToInstall.product}; rebooting into the loader.")
        reboot(Mode.LOADER)
    }

    private suspend fun write(
        packageToInstall: FwscPackage,
        connection: SimulatedConnection,
    ) {
        val image = packageToInstall.logicalImage
        require(options.writeChunkLength > 0) { "writeChunkLength must be positive." }
        val addresses = (0 until image.size step options.writeChunkLength).toMutableList()
        if (options.writesBackwards) addresses.reverse()
        repeat(if (options.checksAfterWriting) 2 else 1) {
            addresses.forEachIndexed { index, address ->
                if (options.unplugDuringWriteAt?.let { index.toDouble() / addresses.size >= it } == true &&
                    synchronized(lock) { !hasUnpluggedOnce }
                ) {
                    synchronized(lock) { hasUnpluggedOnce = true }
                    log("Cable pulled during writing; the update can be resumed once it's reconnected.")
                    reboot(Mode.LOADER)
                    return
                }
                val length = min(options.writeChunkLength, image.size - address)
                if (
                    !exchange(
                        ReadRequest(address = address.toLong(), length = length),
                        image.copyOfRange(address, address + length),
                        connection,
                    )
                ) {
                    return
                }
            }
        }
        if (!options.restartsWithoutFinishing) {
            if (
                !exchange(
                    ReadRequest(address = Fm1Message.writeFinishAddress, length = 8),
                    Fm1Message.finishPayload,
                    connection,
                )
            ) {
                return
            }
        }
        synchronized(lock) {
            buildId = armedBuildId ?: BuildId.parse(packageToInstall.sha256)!!
            firmwareIdentity = packageToInstall.identity
        }
        log("Wrote ${packageToInstall.product}; rebooting.")
        reboot(Mode.FIRMWARE)
    }

    private suspend fun exchange(
        request: ReadRequest,
        expectedData: ByteArray,
        connection: SimulatedConnection,
    ): Boolean {
        delay(options.requestInterval)
        if (!isCurrent(connection.generation)) return false
        connection.toHost.deliver(request.encoded())
        while (true) {
            val reply = connection.fromHost.next(options.responseTimeout) ?: break
            val parsed = ReadRequest.parseResponse(reply) ?: continue
            if (parsed.first.address != request.address || !parsed.second.contentEquals(expectedData)) {
                log("Bad response at 0x${request.address.toString(16)}; the device stops.")
                return false
            }
            return true
        }
        log("No response at 0x${request.address.toString(16)}; the device stops.")
        return false
    }

    private suspend fun reboot(nextMode: Mode) {
        synchronized(lock) {
            mode = Mode.OFFLINE
            generation += 1
            if (nextMode == Mode.FIRMWARE) armedBuildId = null
        }
        changesInternal.tryEmit(Unit)
        delay(options.rebootDuration)
        synchronized(lock) {
            mode = nextMode
            generation += 1
            session = null
        }
        changesInternal.tryEmit(Unit)
    }

    private fun handleOpenFirmwareCommand(
        command: Int,
        payload: ByteArray,
        connection: SimulatedConnection,
    ) {
        if (currentIdentity?.isOpenFirmware != true) return
        if (command == Fm1Message.capabilityCommand) {
            connection.toHost.deliver(
                Fm1Message.command(
                    command,
                    Fm1Capability(state = options.capabilityState, buildId = currentBuildId).payload(),
                ),
            )
            return
        }
        val expectedLength = synchronized(lock) { expected?.logicalImage?.size }
        val valid =
            payload.size == 6 + BuildId.BYTE_COUNT && payload[0] == 1.toByte() &&
                Fm1Message.u(payload[1]) == Fm1Capability.FM1_FAMILY &&
                Fm1Message.readLittleEndian(payload, 2, 4).toLong() == expectedLength?.toLong()
        if (options.armReply != 0 || !valid) {
            log("Arming refused.")
            connection.toHost.deliver(Fm1Message.command(command, byteArrayOf(maxOf(options.armReply, 1).toByte())))
            return
        }
        val armed = BuildId(payload.copyOfRange(6, payload.size))
        synchronized(lock) { armedBuildId = armed }
        log("Armed for build ${armed.hex.take(16)}.")
        connection.toHost.deliver(Fm1Message.command(command, Fm1Capability.armAccepted))
    }

    private fun log(message: String) {
        synchronized(lock) { eventsInternal += message }
    }

    companion object {
        const val PORT_NAME = "FM-1 Midi (simulated)"
        val loaderIdentity = Fm1Identity.of("ota-FM-1", 9)
    }
}

internal class SimulatedConnection(
    private val device: SimulatedFm1,
    override val port: PortDescriptor,
    val generation: Int,
) : MidiConnection {
    internal val toHost = SysexInbox()
    internal val fromHost = SysexInbox()
    private var closed = false

    override val isLost: Boolean
        get() =
            synchronized(this) {
                closed || (!device.options.keepsConnectionThroughRestart && !device.isCurrent(generation))
            }

    override fun send(sysex: ByteArray) {
        if (isLost) throw MidiTransportException.PortGone
        device.handle(sysex.copyOf(), this)
    }

    override suspend fun receive(timeout: Duration): ByteArray? = toHost.next(timeout)

    override fun drain() = toHost.drain()

    override fun close() {
        synchronized(this) { closed = true }
        toHost.close()
        fromHost.close()
    }
}
