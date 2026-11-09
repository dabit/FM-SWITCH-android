// Ported from FM1Kit 0.6.0 Tests/FM1KitTests/OfficialReleaseTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.SimulatedFm1
import dev.drewr.fm1.updater.Fm1UpdateError
import dev.drewr.fm1.updater.Fm1Updater
import dev.drewr.fm1.updater.InstallKind
import dev.drewr.fm1.updater.InstallOptions
import dev.drewr.fm1.updater.UpdaterTiming
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class OfficialReleaseTest {
    @Test fun onlyV15MayBeIncludedInAnApp() {
        assertEquals(listOf(OfficialFirmware.V15, OfficialFirmware.V16), OfficialFirmware.official)
        assertEquals(listOf(OfficialFirmware.V15), OfficialFirmware.known.filter { it.isRedistributable })
        assertEquals(listOf("FM-1_015", "FM-1_016"), OfficialFirmware.official.map { it.identity.text })
        assertTrue(OfficialFirmware.official.all { it.identity.isOfficial })
        assertFalse(OfficialFirmware.OPEN_V12.identity.isOfficial)
    }

    @Test fun v16IsTreatedAsTheOfficialFirmwareToo() =
        runTest {
            val packageFile = FwscPackage(SamplePackage.file("FM-1_016"))
            val simulator =
                SimulatedFm1(
                    SimulatedFm1.Options().apply {
                        installedIdentity = Fm1Identity.officialV16
                        requestInterval = ZERO
                        rebootDuration = ZERO
                    },
                    backgroundScope,
                )
            simulator.prepare(packageFile)
            val updater = Fm1Updater(simulator, timing())

            val check = updater.check(packageFile)
            assertEquals(InstallKind.REINSTALL, check.kind)
            assertTrue(check.canReinstallOfficial)
            val error =
                assertFailsWith<Fm1UpdateError.AlreadyInstalled> {
                    updater.install(packageFile, InstallOptions(true, true))
                }
            assertEquals("FM-1_016", error.identity)
            assertTrue(error.message!!.contains("TransitFM1"))
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
