// Ported from FM1Kit 0.6.0 Updater/FM1Updater.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1FlashChip
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
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

data class Fm1DeviceSummary(
    val identity: Fm1Identity,
    val flashChipId: Int?,
) {
    val hasNewerFlashChip: Boolean get() = flashChipId == Fm1FlashChip.UC25HQ80
}

class Fm1Updater(
    val transport: MidiTransport,
    val timing: UpdaterTiming = UpdaterTiming.standard,
) {
    data class FoundDevice(
        val connection: MidiConnection,
        val identity: Fm1Identity,
        val flashChipId: Int? = null,
    )

    private data class ServeOutcome(
        var requests: Int = 0,
        var finished: Boolean = false,
        var lost: Boolean = false,
        var lastRead: IntRange? = null,
        var returned: FoundDevice? = null,
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

    suspend fun summary(): Fm1DeviceSummary? =
        mutex.withLock {
            findUnlocked()?.let { device ->
                device.connection.close()
                Fm1DeviceSummary(device.identity, device.flashChipId)
            }
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
                val answer = handshakeAnswer(connection)
                if (answer != null && filter(answer.identity)) {
                    found = true
                    return FoundDevice(connection, answer.identity, answer.flashChipId)
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
                    val answer = handshakeAnswer(connection)
                    if (answer != null && filter(answer.identity)) {
                        found += FoundDevice(connection, answer.identity, answer.flashChipId)
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

    private data class HandshakeAnswer(
        val identity: Fm1Identity,
        val flashChipId: Int?,
    )

    private suspend fun handshakeAnswer(connection: MidiConnection): HandshakeAnswer? {
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
                    var result: HandshakeAnswer? = null
                    while (result == null) {
                        coroutineContext.ensureActive()
                        val reply = connection.receive(timing.handshakeWait) ?: break
                        val identity = Fm1Identity.fromHandshakeReply(reply) ?: continue
                        result = HandshakeAnswer(identity, Fm1Identity.flashChipId(reply))
                    }
                    result
                }
            if (found != null) {
                if (!found.identity.mayTellMore(found.flashChipId)) return found
                val full = fullIdentity(connection, found.identity)
                return full?.let { HandshakeAnswer(it.identity, it.flashChipId ?: found.flashChipId) } ?: found
            }
        }
        return null
    }

    private suspend fun fullIdentity(
        connection: MidiConnection,
        start: Fm1Identity,
    ): HandshakeAnswer? {
        try {
            connection.send(Fm1Message.FULL_IDENTITY_QUERY)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        return withTimeoutOrNull(timing.fullIdentityWait) {
            var result: HandshakeAnswer? = null
            while (result == null) {
                coroutineContext.ensureActive()
                val reply = connection.receive(timing.fullIdentityWait) ?: break
                val identity = Fm1Identity.fromHandshakeReply(reply) ?: continue
                if (identity.text.startsWith(start.text)) {
                    result = HandshakeAnswer(identity, Fm1Identity.flashChipId(reply))
                }
            }
            result
        }
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
                        if (!(check.canReinstallOfficial && options.confirmsReinstallingOfficial)) {
                            throw Fm1UpdateError.AlreadyInstalled(device.identity.text)
                        }
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
                    connection = device.connection,
                    image = packageFile.logicalImage,
                    phase = Fm1UpdateError.Phase.STAGING,
                    idleLimit = timing.stagingIdleLimit,
                    progress = { _, requests -> events(UpdateEvent.Staging(requests)) },
                )
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
        val writing = serveLoader(packageFile, loader, events)
        val returned = returnedDevice(writing, events)
        try {
            if (returned.identity != packageFile.identity) {
                throw Fm1UpdateError.IdentityMismatch(packageFile.product, returned.identity.text)
            }
            if (buildId != null) {
                val capability =
                    try {
                        capability(returned)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                if (capability != null && capability.buildId != buildId) {
                    throw Fm1UpdateError.BuildMismatch(buildId, capability.buildId)
                }
            }
            events(UpdateEvent.Finished(returned.identity))
            return returned.identity
        } finally {
            returned.connection.close()
        }
    }

    private suspend fun serveLoader(
        packageFile: FwscPackage,
        loader: FoundDevice,
        events: (UpdateEvent) -> Unit,
    ): ServeOutcome {
        val progress = WriteProgress(packageFile.container.applicationRange)
        val outcome =
            try {
                events(UpdateEvent.LoaderFound(loader.identity))
                loader.connection.send(Fm1Message.upgradeCommand)
                pause(timing.afterUpgradeCommand)
                serve(
                    connection = loader.connection,
                    image = packageFile.logicalImage,
                    phase = Fm1UpdateError.Phase.WRITING,
                    idleLimit = timing.writeIdleLimit,
                    lookingAfterQuiet = timing.returnCheckAfter,
                    progress = { request, _ -> events(progress.record(request)) },
                    lookForReturn = {
                        if (!progress.isNearlyWritten) {
                            null
                        } else {
                            try {
                                findOnlyUnlocked { it == packageFile.identity }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                null
                            }
                        }
                    },
                )
            } finally {
                loader.connection.close()
            }
        if (outcome.lost && progress.isNearlyWritten) {
            val returned =
                waitFor(timing.returnSearch) {
                    it == loader.identity || it == packageFile.identity
                }
            if (returned?.identity == packageFile.identity) {
                outcome.returned = returned
            } else {
                returned?.connection?.close()
            }
        }
        return outcome
    }

    private suspend fun returnedDevice(
        writing: ServeOutcome,
        events: (UpdateEvent) -> Unit,
    ): FoundDevice {
        writing.returned?.let {
            events(UpdateEvent.Rebooting)
            return it
        }
        if (!writing.finished) {
            throw if (writing.lost) {
                Fm1UpdateError.Disconnected(Fm1UpdateError.Phase.WRITING, writing.requests)
            } else {
                Fm1UpdateError.Stalled(Fm1UpdateError.Phase.WRITING, writing.requests)
            }
        }
        events(UpdateEvent.Rebooting)
        pause(timing.firmwareBoot)
        return waitFor(timing.returnSearch) { !it.isLoader } ?: throw Fm1UpdateError.DeviceDidNotReturn
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
        lookingAfterQuiet: Duration? = null,
        progress: (ReadRequest, Int) -> Unit,
        lookForReturn: suspend () -> FoundDevice? = { null },
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
        var quiet = ZERO
        while (finishRequest == null && outcome.returned == null) {
            val window = minOf(lookingAfterQuiet ?: idleLimit, idleLimit - quiet)
            val request =
                withTimeoutOrNull(window) {
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
            if (request == null) {
                if (connection.isLost) outcome.lost = true
                if (outcome.lost) break
                quiet += window
                if (lookingAfterQuiet != null) {
                    outcome.returned = lookForReturn()
                    if (outcome.returned != null) break
                }
                if (quiet >= idleLimit) break
                continue
            }
            if (request.isFinish) {
                finishRequest = request
                continue
            }
            quiet = ZERO
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
