// Ported from FM1Kit 0.4.5 Tests/FM1KitTests/PackageCheckTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.ScriptedTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class FinishTest {
    private fun timing() =
        UpdaterTiming(
            afterUpgradeCommand = ZERO,
            stagingIdleLimit = 10.milliseconds,
            finishSettle = ZERO,
            handshakeWait = 1.milliseconds,
            scanInterval = 1.milliseconds,
        )

    private fun packageFile() = FwscPackage(SamplePackage.file("FM-1_901"))

    @Test fun answersARepeatedFinishBeforeClosing() =
        runTest {
            val finish = ReadRequest(address = Fm1Message.stageFinishAddress, length = 8).encoded()
            val t = ScriptedTransport(Fm1Identity.of("FM-1", 900), listOf(finish, finish))
            assertFailsWith<Fm1UpdateError.LoaderNotFound> {
                Fm1Updater(t, timing()).install(packageFile(), InstallOptions(confirmsSwitchingFirmware = true))
            }
        }

    @Test fun finishOfTheOtherPhaseStops() =
        runTest {
            val t =
                ScriptedTransport(
                    Fm1Identity.of("FM-1", 900),
                    listOf(ReadRequest(address = Fm1Message.writeFinishAddress, length = 8).encoded()),
                )
            assertFailsWith<Fm1UpdateError.UnexpectedFinish> {
                Fm1Updater(t, timing()).install(packageFile(), InstallOptions(confirmsSwitchingFirmware = true))
            }
        }

    @Test fun finishWithLengthOtherThan8Stops() =
        runTest {
            val t =
                ScriptedTransport(
                    Fm1Identity.of("FM-1", 900),
                    listOf(ReadRequest(address = Fm1Message.stageFinishAddress, length = 7).encoded()),
                )
            assertFailsWith<Fm1UpdateError.UnexpectedFinish> {
                Fm1Updater(t, timing()).install(packageFile(), InstallOptions(confirmsSwitchingFirmware = true))
            }
        }
}
