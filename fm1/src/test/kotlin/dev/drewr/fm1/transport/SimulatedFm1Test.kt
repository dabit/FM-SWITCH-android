package dev.drewr.fm1.transport

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Capability
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import dev.drewr.fm1.testing.SamplePackage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
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

    private fun options() =
        SimulatedFm1.Options().apply {
            requestInterval = ZERO
            rebootDuration = ZERO
        }
}
