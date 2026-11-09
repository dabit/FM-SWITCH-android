// Ported from FM1Kit 0.6.0 Tests/FM1KitTests/ProtocolTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.protocol.Fm1FlashChip
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.transport.ScriptedTransport
import dev.drewr.fm1.transport.SimulatedFm1
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ShortenedIdentityTest {
    private fun timing() =
        UpdaterTiming(
            afterUpgradeCommand = ZERO,
            handshakeWait = 1.milliseconds,
            fullIdentityWait = 1.milliseconds,
            scanInterval = 1.milliseconds,
        )

    private fun options(block: SimulatedFm1.Options.() -> Unit) =
        SimulatedFm1.Options().apply {
            requestInterval = ZERO
            rebootDuration = ZERO
            block()
        }

    @Test fun asksNoMoreOfAWholeIdentityThatCarriesTheChip() =
        runTest {
            val transport =
                ScriptedTransport(
                    Fm1Identity.of("Felucca", 1234),
                    emptyList(),
                    Fm1FlashChip.P25Q80,
                )
            Fm1Updater(transport, timing()).identify()
            assertFalse(transport.connection.sent.any { it.contentEquals(Fm1Message.FULL_IDENTITY_QUERY) })
        }

    @Test fun aFoundDeviceCarriesTheChip() =
        runTest {
            val simulator =
                SimulatedFm1(
                    options { flashChipId = Fm1FlashChip.UC25HQ80 },
                    backgroundScope,
                )
            val summary = Fm1Updater(simulator, timing()).summary()!!
            assertEquals(Fm1FlashChip.UC25HQ80, summary.flashChipId)
            assertTrue(summary.hasNewerFlashChip)
        }

    @Test fun asksForTheWholeIdentityAfterAShortenedOne() =
        runTest {
            val identity = Fm1Identity.of("FM-1", 8_000_012)
            val simulator =
                SimulatedFm1(
                    options {
                        installedIdentity = identity
                        shortensIdentity = true
                    },
                    backgroundScope,
                )
            assertEquals(identity, Fm1Updater(simulator, timing()).identify())
        }

    @Test fun asksForTheFlashChipWhenThePlainAnswerLeavesItOut() =
        runTest {
            val simulator =
                SimulatedFm1(
                    options {
                        flashChipId = Fm1FlashChip.P25Q80
                        reportsFlashChipOnlyWhenAsked = true
                    },
                    backgroundScope,
                )
            assertEquals(Fm1FlashChip.P25Q80, Fm1Updater(simulator, timing()).summary()?.flashChipId)
        }

    @Test fun keepsAThreeDigitIdentityWhenNothingMoreComes() =
        runTest {
            val identity = Fm1Identity.of("FM-1", 900)
            val transport = ScriptedTransport(identity, emptyList())
            assertEquals(identity, Fm1Updater(transport, timing()).identify())
            assertTrue(transport.connection.sent.any { it.contentEquals(Fm1Message.FULL_IDENTITY_QUERY) })
        }
}
