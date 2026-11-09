// Ported from FM1Kit 0.6.0 Transport/SimulatedFM1.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Capability
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.updater.Fm1UpdateError
import dev.drewr.fm1.updater.Fm1Updater
import dev.drewr.fm1.updater.InstallOptions
import dev.drewr.fm1.updater.UpdaterTiming
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedFm1Test {
    @Test fun simulatorAnswersTheHandshake() =
        runTest {
            val simulator = SimulatedFm1(scope = this)
            val connection = simulator.open(simulator.ports().single())
            connection.send(Fm1Message.HANDSHAKE_QUERY)
            assertEquals(Fm1Identity.of("FM-1", 900), Fm1Identity.fromHandshakeReply(connection.receive(1.seconds)!!))
        }

    @Test fun connectionsDieOnReboot() =
        runTest {
            val simulator = SimulatedFm1(options().apply { stagingRequestCount = 0 }, this)
            simulator.prepare(FwscPackage(SamplePackage.file("FM-1_901")))
            val connection = simulator.open(simulator.ports().single())
            connection.send(Fm1Message.upgradeCommand)
            runCurrent()
            val finish = ReadRequest.parse(connection.receive(1.seconds)!!)
            connection.send(finish!!.finishResponse())
            runCurrent()
            assertTrue(connection.isLost)
        }

    @Test fun armedLoaderIsNamedAfterTheBuild() =
        runTest {
            val build = BuildId(ByteArray(BuildId.BYTE_COUNT) { (it + 1).toByte() })
            val simulator =
                SimulatedFm1(
                    options().apply {
                        installedIdentity = Fm1Identity.of("FM-1", 8_000_012)
                        stagingRequestCount = 1
                    },
                    this,
                )
            val packageToInstall = FwscPackage(SamplePackage.file("FM-1_8000013"))
            simulator.prepare(packageToInstall)
            val connection = simulator.open(simulator.ports().single())
            connection.send(
                Fm1Message.command(
                    Fm1Message.armCommand,
                    Fm1Capability.armPayload(7, packageToInstall.logicalImage.size, build),
                ),
            )
            assertContentEquals(
                Fm1Capability.armAccepted,
                Fm1Message.parseCommand(connection.receive(1.seconds)!!)?.second,
            )
            connection.send(Fm1Message.upgradeCommand)
            runCurrent()
            val first = ReadRequest.parse(connection.receive(1.seconds)!!)
            connection.send(first!!.response(packageToInstall.logicalImage.copyOfRange(0, first.length)))
            runCurrent()
            val finish = ReadRequest.parse(connection.receive(1.seconds)!!)
            connection.send(finish!!.finishResponse())
            runCurrent()
            val loader = simulator.open(simulator.ports().single())
            loader.send(Fm1Message.HANDSHAKE_QUERY)
            assertEquals(
                "ota-FM1C_${build.hex.take(16)}",
                Fm1Identity.fromHandshakeReply(loader.receive(1.seconds)!!)?.text,
            )
        }

    @Test fun v16RefusesItsOwnIdentity() =
        runTest {
            val packageFile = FwscPackage(SamplePackage.file("FM-1_016"))
            val simulator =
                SimulatedFm1(
                    options().apply { installedIdentity = Fm1Identity.officialV16 },
                    this,
                )
            simulator.prepare(packageFile)
            val events = mutableListOf<dev.drewr.fm1.updater.UpdateEvent>()
            assertFailsWith<Fm1UpdateError> {
                Fm1Updater(simulator, timing()).install(
                    packageFile,
                    InstallOptions(confirmsReinstallingOfficial = true),
                ) { events += it }
            }
            assertTrue(events.none { it is dev.drewr.fm1.updater.UpdateEvent.WaitingForLoader })
            assertTrue(simulator.events.any { it.contains("already running") })
        }

    @Test fun cablePullSaysTheUpdateCanBeResumed() =
        runTest {
            val simulator = SimulatedFm1(options().apply { unplugDuringWriteAt = 0.0 }, this)
            val packageFile = FwscPackage(SamplePackage.file("FM-1_901"))
            simulator.prepare(packageFile)

            assertFailsWith<Fm1UpdateError.Disconnected> {
                Fm1Updater(simulator, timing()).install(
                    packageFile,
                    InstallOptions(confirmsSwitchingFirmware = true),
                )
            }

            assertTrue(
                "Cable pulled during writing; the update can be resumed once it's reconnected." in simulator.events,
            )
        }

    private fun options() =
        SimulatedFm1.Options().apply {
            requestInterval = ZERO
            rebootDuration = ZERO
        }

    private fun timing() =
        UpdaterTiming(
            ZERO,
            2.seconds,
            ZERO,
            2.seconds,
            2.seconds,
            ZERO,
            2.seconds,
            ZERO,
            ZERO,
            1.milliseconds,
            1.milliseconds,
            1.milliseconds,
            handshakeAttempts = 2,
        )
}
