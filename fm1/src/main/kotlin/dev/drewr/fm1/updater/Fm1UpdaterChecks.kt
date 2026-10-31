// Ported from FM1Kit 0.4.5 Updater/FM1Updater+Checks.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Capability
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.transport.MidiConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

internal fun knownBuildId(packageFile: FwscPackage): BuildId? = OfficialFirmware.matching(packageFile)?.buildId

internal suspend fun Fm1Updater.checkUnlocked(
    packageFile: FwscPackage,
    device: Fm1Updater.FoundDevice,
    buildId: BuildId?,
): InstallCheck {
    if (!packageFile.isFirmware) throw Fm1UpdateError.NotFirmware(packageFile.product)
    val capability = capability(device)
    if (device.identity.hardwareModel != packageFile.identity.model &&
        capability?.family != Fm1Capability.FM1_FAMILY
    ) {
        throw Fm1UpdateError.ModelMismatch(device.identity.text, packageFile.product)
    }
    return InstallCheck.of(device.identity, capability, packageFile, buildId)
}

internal suspend fun Fm1Updater.capability(device: Fm1Updater.FoundDevice): Fm1Capability? {
    if (!device.identity.isOpenFirmware) return null
    return Fm1Capability.parse(
        query(device.connection, Fm1Message.capabilityCommand) ?: throw Fm1UpdateError.CapabilityUnavailable,
    )
        ?: throw Fm1UpdateError.CapabilityUnavailable
}

internal suspend fun Fm1Updater.arm(
    connection: MidiConnection,
    capability: Fm1Capability,
    packageFile: FwscPackage,
    buildId: BuildId?,
) {
    if (!capability.isReady) throw Fm1UpdateError.DeviceBusy
    val id = buildId ?: BuildId.parse(packageFile.sha256)!!
    val reply =
        query(
            connection,
            Fm1Message.armCommand,
            Fm1Capability.armPayload(capability.family, packageFile.logicalImage.size, id),
        )
            ?: throw Fm1UpdateError.ArmRefused(null)
    if (!reply.contentEquals(Fm1Capability.armAccepted)) {
        throw Fm1UpdateError.ArmRefused(reply.firstOrNull()?.toInt()?.and(0xFF) ?: -1)
    }
}

internal suspend fun Fm1Updater.query(
    connection: MidiConnection,
    command: Int,
    payload: ByteArray = ByteArray(0),
): ByteArray? {
    connection.drain()
    try {
        connection.send(Fm1Message.command(command, payload))
    } catch (
        error: CancellationException,
    ) {
        throw error
    } catch (_: Exception) {
        return null
    }
    return withTimeoutOrNull(timing.commandReplyWait) {
        var result: ByteArray? = null
        while (result == null) {
            coroutineContext.ensureActive()
            val reply = connection.receive(timing.commandReplyWait) ?: break
            Fm1Message.parseCommand(reply)?.let { if (it.first == command) result = it.second }
        }
        result
    }
}
