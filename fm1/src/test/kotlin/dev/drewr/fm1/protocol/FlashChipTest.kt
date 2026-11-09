// Ported from FM1Kit 0.6.0 Tests/FM1KitTests/ProtocolTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlashChipTest {
    @Test fun readsTheChipAFirmwareReportsAfterItsIdentity() {
        val reply = Fm1Identity.of("Felucca", 1234).handshakeReply(Fm1FlashChip.UC25HQ80)
        assertEquals(Fm1FlashChip.UC25HQ80, Fm1Identity.flashChipId(reply))
    }

    @Test fun findsNoChipInAReplyWithoutOne() =
        assertNull(Fm1Identity.flashChipId(Fm1Identity.of("Felucca", 1234).handshakeReply()))

    @Test fun readsNoChipFromAnIdentityThatRunsIntoIt() {
        val identity = Fm1Identity.parse("ota-FM1C_1234567890123456")!!
        assertNull(Fm1Identity.flashChipId(identity.handshakeReply(Fm1FlashChip.UC25HQ80)))
    }

    @Test fun theQueriesAreTheDocumentedOnes() {
        val query = Fm1Message.parseCommand(Fm1Message.FULL_IDENTITY_QUERY)!!
        assertEquals(Fm1Message.identityCommand, query.first)
        assertContentEquals(byteArrayOf(1), query.second)
    }
}
