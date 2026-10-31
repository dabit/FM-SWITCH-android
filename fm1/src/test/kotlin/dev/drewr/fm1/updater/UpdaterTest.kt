// Ported from FM1Kit 0.4.5 Tests/FM1KitTests/UpdaterTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.MidiConnection
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.PortDescriptor
import dev.drewr.fm1.transport.SimulatedFm1
import dev.drewr.fm1.transport.SysexInbox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterTest {
    private fun packageFile(product: String = "FM-1_901") = FwscPackage(SamplePackage.file(product))

    private fun fastTiming() =
        UpdaterTiming(
            loading(),
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

    private fun loading() = ZERO

    private suspend fun simulator(
        test: kotlinx.coroutines.test.TestScope,
        options: SimulatedFm1.Options.() -> Unit = {
        },
    ): SimulatedFm1 =
        SimulatedFm1(
            SimulatedFm1.Options().apply {
                requestInterval =
                    ZERO
                ; rebootDuration = ZERO
                options()
            },
            test.backgroundScope,
        )

    @Test fun identifiesTheDevice() =
        runTest {
            assertEquals(Fm1Identity.of("FM-1", 900), Fm1Updater(simulator(this), fastTiming()).identify())
        }

    @Test fun installsAndVerifiesTheIdentity() =
        runTest {
            val sim = simulator(this)
            val file = packageFile()
            sim.prepare(file)
            assertEquals(
                file.identity,
                Fm1Updater(sim, fastTiming()).install(file, InstallOptions(confirmsSwitchingFirmware = true)),
            )
        }

    @Test fun resumesAfterTheCableIsPulledMidWrite() =
        runTest {
            val sim = simulator(this) { unplugDuringWriteAt = .45 }
            val file = packageFile()
            sim.prepare(file)
            val updater = Fm1Updater(sim, fastTiming())
            assertFailsWith<Fm1UpdateError.Disconnected> {
                updater.install(file, InstallOptions(confirmsSwitchingFirmware = true))
            }
            assertEquals(file.identity, updater.resume(file))
        }

    @Test fun finishesFromRecoveryMode() =
        runTest {
            val sim = simulator(this) { startsInRecoveryMode = true }
            val file = packageFile()
            sim.prepare(file)
            assertEquals(file.identity, Fm1Updater(sim, fastTiming()).resume(file))
        }

    @Test fun refusesARequestBeyondTheImage() =
        runTest {
            val script =
                dev.drewr.fm1.transport.ScriptedTransport(
                    Fm1Identity.of("FM-1", 900),
                    listOf(ReadRequest(address = Long.MAX_VALUE, length = 1).encoded()),
                )
            val file = packageFile()
            assertFailsWith<Fm1UpdateError.BadRequest> {
                Fm1Updater(script, fastTiming()).install(file, InstallOptions(confirmsSwitchingFirmware = true))
            }
        }

    @Test fun reportsEachStepInOrder() =
        runTest {
            val sim = simulator(this)
            val file = packageFile()
            sim.prepare(file)
            val events = mutableListOf<UpdateEvent>()
            Fm1Updater(sim, fastTiming()).install(file, InstallOptions(confirmsSwitchingFirmware = true)) {
                events +=
                    it
            }
            assertTrue(events.first() is UpdateEvent.Connected)
            assertTrue(events.any { it is UpdateEvent.Staging })
            assertTrue(events.any { it is UpdateEvent.WaitingForLoader })
            assertTrue(events.any { it is UpdateEvent.LoaderFound })
            assertTrue(events.any { it is UpdateEvent.Writing })
            assertTrue(events.any { it is UpdateEvent.Rebooting })
            assertTrue(events.last() is UpdateEvent.Finished)
        }

    @Test fun resumeNeedsTheLoader() =
        runTest {
            assertFailsWith<Fm1UpdateError.LoaderNotFound> {
                Fm1Updater(
                    simulator(this),
                    fastTiming(),
                ).resume(packageFile())
            }
        }

    @Test fun v15IsNotReinstalled() =
        runTest {
            val sim = simulator(this) { installedIdentity = Fm1Identity.officialV15 }
            val file = packageFile("FM-1_015")
            sim.prepare(file)
            assertFailsWith<Fm1UpdateError.AlreadyInstalled> { Fm1Updater(sim, fastTiming()).install(file) }
        }

    @Test fun refusesAPackageForAnotherModel() =
        runTest {
            val sim = simulator(this)
            val file = packageFile()
            assertNull(
                Fm1Updater(sim, fastTiming()).identify()?.takeIf {
                    it.hardwareModel !=
                        "FM-1"
                },
            )
        }

    @Test fun rejectsPackagesForOtherModels() = runTest { assertEquals("FM-1", packageFile().identity.model) }

    @Test fun standardTimingsAddNoPauses() {
        assertEquals(ZERO, UpdaterTiming.standard.afterUpgradeCommand)
        assertEquals(ZERO, UpdaterTiming.standard.responseDelay)
    }

    @Test fun neverSendsToOtherGear() =
        runTest {
            val fm = RecordingTransport(PortDescriptor("fm", listOf("FM-1")), Fm1Identity.of("FM-1", 900))
            val keyStep = RecordingTransport(PortDescriptor("key-step", listOf("Arturia KeyStep")), null)
            val transport = MultiPortTransport(fm, keyStep)
            val found = Fm1Updater(transport, fastTiming()).findAll()
            found.forEach { it.connection.close() }
            assertEquals(1, found.size)
            assertEquals(setOf("fm"), transport.opened)
            assertTrue(transport.sent.getValue("fm").any { it.contentEquals(Fm1Message.HANDSHAKE_QUERY) })
            assertTrue("key-step" !in transport.opened)
            assertTrue(transport.sent["key-step"].isNullOrEmpty())
        }

    @Test fun healthyWritingMayOutlastItsIdleLimit() =
        runTest {
            val file = packageFile()
            val sim = simulator(this) { requestInterval = 2.milliseconds }
            sim.prepare(file)
            val timing = fastTiming().copy(writeIdleLimit = 10.milliseconds)
            assertEquals(
                file.identity,
                Fm1Updater(sim, timing).install(file, InstallOptions(confirmsSwitchingFirmware = true)),
            )
        }

    @Test fun cancellationDuringHandshakeClosesTheConnection() =
        runTest {
            val transport = BlockingTransport()
            val job = backgroundScope.launch { Fm1Updater(transport, fastTiming()).identify() }
            runCurrent()
            job.cancelAndJoin()
            assertTrue(transport.connection.closed)
        }

    @Test fun buildCapabilityTransportFailureDoesNotFailAnInstall() =
        runTest {
            val file = packageFile("FM-1_8000001")
            val sim = simulator(this)
            sim.prepare(file)
            val transport = ThrowingCapabilityTransport(sim)
            assertEquals(
                file.identity,
                Fm1Updater(transport, fastTiming()).install(
                    file,
                    InstallOptions(
                        confirmsSwitchingFirmware = true,
                        buildId =
                            dev.drewr.fm1.protocol
                                .BuildId(ByteArray(32)),
                    ),
                ),
            )
        }

    private class RecordingTransport(
        private val port: PortDescriptor,
        private val identity: Fm1Identity?,
    ) : MidiTransport {
        override suspend fun ports() = listOf(port)

        override suspend fun open(port: PortDescriptor): MidiConnection =
            object : MidiConnection {
                private val inbox = SysexInbox()
                override val port = this@RecordingTransport.port
                override val isLost = false

                override fun send(sysex: ByteArray) {
                    if (sysex.contentEquals(
                            Fm1Message.HANDSHAKE_QUERY,
                        )
                    ) {
                        identity?.let { inbox.deliver(it.handshakeReply()) }
                    }
                }

                override suspend fun receive(timeout: kotlin.time.Duration) = inbox.next(timeout)

                override fun drain() = inbox.drain()

                override fun close() = inbox.close()
            }

        override fun changes(): Flow<Unit> = emptyFlow()
    }

    private class BlockingTransport : MidiTransport {
        val connection = BlockingConnection()

        override suspend fun ports() = listOf(connection.port)

        override suspend fun open(port: PortDescriptor) = connection

        override fun changes(): Flow<Unit> = emptyFlow()
    }

    private class BlockingConnection : MidiConnection {
        override val port = PortDescriptor("blocking", listOf("FM-1"))
        override val isLost = false
        var closed = false

        override fun send(sysex: ByteArray) = Unit

        override suspend fun receive(timeout: kotlin.time.Duration): ByteArray? = awaitCancellation()

        override fun drain() = Unit

        override fun close() {
            closed = true
        }
    }

    private class ThrowingCapabilityTransport(
        private val delegate: MidiTransport,
    ) : MidiTransport {
        override suspend fun ports() = delegate.ports()

        override suspend fun open(port: PortDescriptor): MidiConnection {
            val connection = delegate.open(port)
            return object : MidiConnection by connection {
                private var capabilityQuery = false

                override fun send(sysex: ByteArray) {
                    if (Fm1Message.parseCommand(sysex)?.first == Fm1Message.capabilityCommand) {
                        capabilityQuery = true
                    }
                    connection.send(sysex)
                }

                override suspend fun receive(timeout: kotlin.time.Duration): ByteArray? {
                    if (capabilityQuery) {
                        throw dev.drewr.fm1.transport.MidiTransportException
                            .SendFailed("test")
                    }
                    return connection.receive(timeout)
                }
            }
        }

        override fun changes(): Flow<Unit> = delegate.changes()
    }
}
