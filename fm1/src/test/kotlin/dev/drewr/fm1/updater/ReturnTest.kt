// Ported from FM1Kit 0.6.0 Tests/FM1KitTests/ReturnTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.SimulatedFm1
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReturnTest {
    private fun timing() =
        UpdaterTiming(
            afterUpgradeCommand = ZERO,
            loaderBoot = ZERO,
            firmwareBoot = ZERO,
            handshakeWait = 1.milliseconds,
            fullIdentityWait = 1.milliseconds,
            scanInterval = 1.milliseconds,
            returnCheckAfter = 1.milliseconds,
            writeIdleLimit = 1.seconds,
            returnSearch = 1.seconds,
        )

    private fun simulator(
        scope: TestScope,
        block: SimulatedFm1.Options.() -> Unit,
    ) = SimulatedFm1(
        SimulatedFm1.Options().apply {
            requestInterval = ZERO
            rebootDuration = ZERO
            block()
        },
        scope.backgroundScope,
    )

    private val file get() = FwscPackage(SamplePackage.file("FM-1_901"))

    @Test fun finishesWhenTheFirmwareAnswersOnAConnectionThatNeverShowsAsLost() =
        runTest {
            val simulator =
                simulator(this) {
                    restartsWithoutFinishing = true
                    keepsConnectionThroughRestart = true
                }
            simulator.prepare(file)
            assertEquals(
                file.identity,
                Fm1Updater(simulator, timing()).install(file, InstallOptions(confirmsSwitchingFirmware = true)),
            )
        }

    @Test fun finishesWhenTheDeviceRestartsWithoutAskingToFinish() =
        runTest {
            val simulator = simulator(this) { restartsWithoutFinishing = true }
            simulator.prepare(file)
            assertEquals(
                file.identity,
                Fm1Updater(simulator, timing()).install(file, InstallOptions(confirmsSwitchingFirmware = true)),
            )
        }

    @Test fun aCableCutNearTheEndStopsWhenTheLoaderComesBack() =
        runTest {
            val simulator = simulator(this) { unplugDuringWriteAt = .96 }
            simulator.prepare(file)
            assertFailsWith<Fm1UpdateError.Disconnected> {
                Fm1Updater(simulator, timing()).install(file, InstallOptions(confirmsSwitchingFirmware = true))
            }
        }
}
