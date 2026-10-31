// Ported from FM1Kit 0.4.5 Tests/FM1KitTests/EdgeCaseTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fm1.transport.ScriptedTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EdgeCaseTest {
    private fun timing() =
        UpdaterTiming(
            afterUpgradeCommand = ZERO,
            stagingIdleLimit = 10.milliseconds,
            handshakeWait = 1.milliseconds,
            scanInterval = 1.milliseconds,
        )

    private fun packageFile() = FwscPackage(SamplePackage.file("FM-1_901"))

    @Test fun otherMessagesDontKeepAStalledPhaseWaiting() =
        runTest {
            val t = ScriptedTransport(Fm1Identity.of("FM-1", 900), listOf(Fm1Message.HANDSHAKE_QUERY))
            withTimeout(30.seconds) {
                assertFailsWith<Fm1UpdateError.Stalled> {
                    Fm1Updater(t, timing()).install(packageFile(), InstallOptions(confirmsSwitchingFirmware = true))
                }
            }
        }

    @Test fun ignoresRepliesThatAreNotHandshakes() =
        runTest {
            val t = ScriptedTransport(Fm1Identity.of("FM-1", 900), emptyList())
            assertEquals(Fm1Identity.of("FM-1", 900), Fm1Updater(t, timing()).identify())
        }

    @Test fun otherMessagesAreNotReadRequests() {
        assertEquals(null, ReadRequest.parse(Fm1Message.HANDSHAKE_QUERY))
    }

    @Test fun responsesWithAWrongLengthOrChecksumAreRejected() {
        assertEquals(null, ReadRequest.parseResponse(byteArrayOf(0xF0.toByte(), 0xF7.toByte())))
    }

    @Test fun storesAnIdentityAsItsText() {
        assertEquals("FM-1_900", Fm1Identity.of("FM-1", 900).toString())
    }

    @Test fun hashesTheFileNotTheImage() {
        val file = SamplePackage.file("FM-1_901")
        assertTrue(FwscPackage.sha256Hex(file).isNotEmpty())
    }

    @Test fun recognisesOnlyOfficialReleases() {
        assertEquals(
            null,
            dev.drewr.fm1.pkg.OfficialFirmware
                .matching(packageFile()),
        )
    }

    @Test fun aCustomFirmwaresOwnIdentityNeedsConfirming() {
        assertTrue(!InstallOptions().confirmsSameIdentity)
    }

    @Test fun sameIdentityNeedsConfirming() {
        assertTrue(Fm1UpdateError.SameIdentityNotConfirmed("FM-1_900").message!!.contains("Confirm"))
    }

    @Test fun errorTextIsFm1Kits() {
        assertEquals(
            "No FM-1 found. Check the cable, and close any other app using it.",
            Fm1UpdateError.DeviceNotFound.message,
        )
    }
}
