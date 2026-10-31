// Ported from FM1Kit 0.4.5 Updater/FM1Updater.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import dev.drewr.fm1.transport.MidiConnection
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.MidiTransportException
import dev.drewr.fm1.transport.PortMatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Fm1Updater(
    val transport: MidiTransport,
    val timing: UpdaterTiming = UpdaterTiming.standard,
) {
    data class FoundDevice(
        val connection: MidiConnection,
        val identity: Fm1Identity,
    )

    private data class ServeOutcome(
        var requests: Int = 0,
        var finished: Boolean = false,
        var lost: Boolean = false,
        var lastRead: IntRange? = null,
    )

    private val mutex = Mutex()

    suspend fun findAll(
        filter: (Fm1Identity) -> Boolean = {
            true
        },
    ): List<FoundDevice> = mutex.withLock { findAllUnlocked(filter) }

    suspend fun identify(): Fm1Identity? =
        mutex.withLock {
            findUnlocked()?.also { it.connection.close() }?.identity
        }

    suspend fun check(
        packageFile: FwscPackage,
        buildId: BuildId? = null,
    ): InstallCheck =
        mutex.withLock {
            val device = findOnlyUnlocked { !it.isLoader } ?: throw Fm1UpdateError.DeviceNotFound
            try {
                checkUnlocked(packageFile, device, buildId ?: knownBuildId(packageFile))
            } finally {
                device.connection.close()
            }
        }

    suspend fun install(
        packageFile: FwscPackage,
        options: InstallOptions = InstallOptions(),
        events: (UpdateEvent) -> Unit = {
        },
    ): Fm1Identity =
        mutex.withLock {
            installUnlocked(packageFile, options, events)
        }

    suspend fun resume(
        packageFile: FwscPackage,
        events: (UpdateEvent) -> Unit = {},
    ): Fm1Identity =
        mutex.withLock {
            if (!packageFile.isFirmware) throw Fm1UpdateError.NotFirmware(packageFile.product)
            val loader =
                findOnlyUnlocked { it.isLoaderFor(packageFile.identity.model) } ?: throw Fm1UpdateError.LoaderNotFound
            writeUnlocked(packageFile, loader, knownBuildId(packageFile), events)
        }

    private suspend fun findUnlocked(filter: (Fm1Identity) -> Boolean = { true }): FoundDevice? {
        for (port in transport.ports()) {
            if (!PortMatching.isProbeable(port)) continue
            val connection =
                try {
                    transport.open(port)
                } catch (_: MidiTransportException) {
                    continue
                }
            var found = false
            try {
                val identity = handshake(connection)
                if (identity != null && filter(identity)) {
                    found = true
                    return FoundDevice(connection, identity)
                }
            } finally {
                if (!found) connection.close()
            }
        }
        return null
    }

    private suspend fun findAllUnlocked(filter: (Fm1Identity) -> Boolean): List<FoundDevice> {
        val found = mutableListOf<FoundDevice>()
        try {
            for (port in transport.ports()) {
                if (!PortMatching.isProbeable(port)) continue
                val connection =
                    try {
                        transport.open(port)
                    } catch (_: MidiTransportException) {
                        continue
                    }
                var retained = false
                try {
                    val identity = handshake(connection)
                    if (identity != null && filter(identity)) {
                        found += FoundDevice(connection, identity)
                        retained = true
                    }
                } finally {
                    if (!retained) connection.close()
                }
            }
        } catch (error: CancellationException) {
            found.forEach { it.connection.close() }
            throw error
        } catch (error: Exception) {
            found.forEach { it.connection.close() }
            throw error
        }
        return found
    }

    private suspend fun findOnlyUnlocked(filter: (Fm1Identity) -> Boolean): FoundDevice? {
        val found = findAllUnlocked(filter)
        if (found.size >
            1
        ) {
            found.forEach { it.connection.close() }
            throw Fm1UpdateError.MultipleDevices(found.size)
        }
        return found.singleOrNull()
    }

    private suspend fun handshake(connection: MidiConnection): Fm1Identity? {
        connection.drain()
        repeat(timing.handshakeAttempts) {
            try {
                connection.send(Fm1Message.HANDSHAKE_QUERY)
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (_: Exception) {
                return null
            }
            val found =
                withTimeoutOrNull(timing.handshakeWait) {
                    var result: Fm1Identity? = null
                    while (result == null) {
                        coroutineContext.ensureActive()
                        val reply = connection.receive(timing.handshakeWait) ?: break
                        result = Fm1Identity.fromHandshakeReply(reply)
                    }
                    result
                }
            if (found != null) return found
        }
        return null
    }

    private suspend fun installUnlocked(
        packageFile: FwscPackage,
        options: InstallOptions,
        events: (UpdateEvent) -> Unit,
    ): Fm1Identity {
        val device = findOnlyUnlocked { !it.isLoader } ?: throw Fm1UpdateError.DeviceNotFound
        val buildId = options.buildId ?: knownBuildId(packageFile)
        val staging =
            try {
                val check = checkUnlocked(packageFile, device, buildId)
                when (check.kind) {
                    InstallKind.REINSTALL -> {
                        throw Fm1UpdateError.AlreadyInstalled(device.identity.text)
                    }

                    InstallKind.SWITCHING_FIRMWARE -> {
                        if (!options.confirmsSwitchingFirmware) {
                            throw Fm1UpdateError
                                .SwitchNotConfirmed(
                                    device.identity.text,
                                    packageFile.product,
                                )
                        }
                    }

                    InstallKind.SAME_IDENTITY -> {
                        if (!options.confirmsSameIdentity) {
                            throw Fm1UpdateError
                                .SameIdentityNotConfirmed(
                                    device.identity.text,
                                )
                        }
                    }

                    InstallKind.UPDATE -> {
                        Unit
                    }
                }
                events(UpdateEvent.Connected(device.identity))
                check.capability?.let { arm(device.connection, it, packageFile, buildId) }
                device.connection.send(Fm1Message.upgradeCommand)
                pause(timing.afterUpgradeCommand)
                serve(
                    device.connection,
                    packageFile.logicalImage,
                    Fm1UpdateError.Phase.STAGING,
                    timing.stagingIdleLimit,
                ) { _, requests -> events(UpdateEvent.Staging(requests)) }
            } finally {
                device.connection.close()
            }
        if (!staging.finished) {
            if (refusedBootloader(staging, packageFile)) throw Fm1UpdateError.BootloaderMismatch(staging.requests)
            if (refusedPackage(staging, packageFile)) throw Fm1UpdateError.PackageRefused(staging.requests)
            throw if (staging.lost) {
                Fm1UpdateError.Disconnected(
                    Fm1UpdateError.Phase.STAGING,
                    staging.requests,
                )
            } else {
                Fm1UpdateError.Stalled(Fm1UpdateError.Phase.STAGING, staging.requests)
            }
        }
        events(UpdateEvent.WaitingForLoader)
        pause(timing.loaderBoot)
        val armedPrefix = (buildId ?: BuildId.parse(packageFile.sha256)!!).hex.take(16)
        val loader =
            waitFor(timing.loaderSearch) {
                it.isLoaderFor(packageFile.identity.model) && (it.armedBuildPrefix ?: armedPrefix) == armedPrefix
            } ?: throw Fm1UpdateError.LoaderNotFound
        return writeUnlocked(packageFile, loader, buildId, events)
    }

    private suspend fun writeUnlocked(
        packageFile: FwscPackage,
        loader: FoundDevice,
        buildId: BuildId?,
        events: (UpdateEvent) -> Unit,
    ): Fm1Identity {
        val writing =
            try {
                events(UpdateEvent.LoaderFound(loader.identity))
                loader.connection.send(Fm1Message.upgradeCommand)
                pause(timing.afterUpgradeCommand)
                val progress = WriteProgress(packageFile.container.applicationRange)
                serve(
                    loader.connection,
                    packageFile.logicalImage,
                    Fm1UpdateError.Phase.WRITING,
                    timing.writeIdleLimit,
                ) { request, _ -> events(progress.record(request)) }
            } finally {
                loader.connection.close()
            }
        if (!writing.finished) {
            throw if (writing.lost) {
                Fm1UpdateError.Disconnected(
                    Fm1UpdateError.Phase.WRITING,
                    writing.requests,
                )
            } else {
                Fm1UpdateError.Stalled(Fm1UpdateError.Phase.WRITING, writing.requests)
            }
        }
        events(UpdateEvent.Rebooting)
        pause(timing.firmwareBoot)
        val returned = waitFor(timing.returnSearch) { !it.isLoader } ?: throw Fm1UpdateError.DeviceDidNotReturn
        try {
            if (returned.identity !=
                packageFile.identity
            ) {
                throw Fm1UpdateError.IdentityMismatch(packageFile.product, returned.identity.text)
            }
            if (buildId != null) {
                val capability =
                    try {
                        capability(returned)
                    } catch (
                        error: CancellationException,
                    ) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                if (capability != null &&
                    capability.buildId != buildId
                ) {
                    throw Fm1UpdateError.BuildMismatch(buildId, capability.buildId)
                }
            }
            events(UpdateEvent.Finished(returned.identity))
            return returned.identity
        } finally {
            returned.connection.close()
        }
    }

    private suspend fun waitFor(
        limit: Duration,
        filter: (Fm1Identity) -> Boolean,
    ): FoundDevice? =
        withTimeoutOrNull(limit) {
            var result: FoundDevice? = null
            while (result == null) {
                result = findOnlyUnlocked(filter)
                if (result == null) pause(timing.scanInterval)
            }
            result
        }

    private suspend fun serve(
        connection: MidiConnection,
        image: ByteArray,
        phase: Fm1UpdateError.Phase,
        idleLimit: Duration,
        progress: (ReadRequest, Int) -> Unit,
    ): ServeOutcome {
        val outcome = ServeOutcome()
        val finishAddress =
            if (phase ==
                Fm1UpdateError.Phase.STAGING
            ) {
                Fm1Message.stageFinishAddress
            } else {
                Fm1Message.writeFinishAddress
            }
        var finishRequest: ReadRequest? = null
        while (finishRequest == null) {
            val request =
                withTimeoutOrNull(idleLimit) {
                    var next: ReadRequest? = null
                    while (next == null && !outcome.lost) {
                        coroutineContext.ensureActive()
                        val message = connection.receive(1.seconds)
                        if (message == null) {
                            if (connection.isLost) {
                                outcome.lost = true
                            }
                            continue
                        }
                        next = ReadRequest.parse(message)
                    }
                    next
                }
            if (request == null) break
            if (request.isFinish) {
                finishRequest = request
                continue
            }
            val end = request.address + request.length.toLong()
            if (request.length > Fm1Message.maximumReadLength || request.length < 0 ||
                request.address > image.size.toLong() ||
                end < request.address ||
                end > image.size.toLong()
            ) {
                throw Fm1UpdateError.BadRequest(request.address, request.length)
            }
            pause(timing.responseDelay)
            try {
                connection.send(request.response(image.copyOfRange(request.address.toInt(), end.toInt())))
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (_: Exception) {
                outcome.lost = true
                break
            }
            outcome.requests++
            outcome.lastRead = request.address.toInt() until end.toInt()
            progress(request, outcome.requests)
        }
        finishRequest?.let { request ->
            if (!isExpectedFinish(request, finishAddress)) throw Fm1UpdateError.UnexpectedFinish(phase)
            try {
                connection.send(request.finishResponse())
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (_: Exception) {
            }
            settle(connection, request)
            outcome.finished = true
        }
        return outcome
    }

    private suspend fun settle(
        connection: MidiConnection,
        finish: ReadRequest,
    ) {
        withTimeoutOrNull(timing.finishSettle) {
            while (!connection.isLost) {
                coroutineContext.ensureActive()
                val message =
                    connection.receive(timing.finishSettle) ?: continue
                if (ReadRequest.parse(message) ==
                    finish
                ) {
                    try {
                        connection.send(finish.finishResponse())
                    } catch (
                        error: CancellationException,
                    ) {
                        throw error
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private suspend fun pause(duration: Duration) {
        if (duration > Duration.ZERO) delay(duration)
    }

    private fun isExpectedFinish(
        request: ReadRequest,
        address: Long,
    ): Boolean = request.address == address && request.length == Fm1Message.finishPayload.size

    private fun refusedBootloader(
        outcome: ServeOutcome,
        packageFile: FwscPackage,
    ): Boolean =
        packageFile.container.flash?.let { flash ->
            outcome.lastRead?.first in
                (flash.offset until flash.offset + 0x4000)
        } ==
            true

    private fun refusedPackage(
        outcome: ServeOutcome,
        packageFile: FwscPackage,
    ): Boolean =
        !outcome.lost && outcome.lastRead?.first?.let { address ->
            val firstPart =
                listOfNotNull(packageFile.container.flash?.offset, packageFile.container.loader.offset).minOrNull() ?: 0
            address < firstPart
        } == true
}
