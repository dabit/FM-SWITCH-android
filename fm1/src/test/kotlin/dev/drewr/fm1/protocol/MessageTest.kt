package dev.drewr.fm1.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageTest {
    @Test fun handshakeQueryUnpacksToTheIdentityHeader() =
        assertContentEquals(
            bytes(0x00, 0x59, 0x11),
            SevenBit.unpack(Fm1Message.HANDSHAKE_QUERY.copyOfRange(1, Fm1Message.HANDSHAKE_QUERY.lastIndex)).copyOf(3),
        )

    @Test fun sendsTheDocumentedCommands() {
        assertContentEquals(
            bytes(0xF0, 0x00, 0x32, 0x45, 0x00, 0x00, 0x00, 0x40, 0x7F, 0xF7),
            Fm1Message.HANDSHAKE_QUERY,
        )
        assertContentEquals(bytes(0xF0, 0x22, 0x24, 0x35, 0x7F, 0xF7), Fm1Message.upgradeCommand)
    }

    @Test fun readRequestEncodesTheDocumentedBytes() =
        assertContentEquals(
            hex("F0 00 32 41 41 00 00 40 00 40 46 04 00 10 3C 00 00 38 01 F7"),
            ReadRequest(1, 0x12340, 481).encoded(),
        )

    @Test fun responseEncodesTheDocumentedBytes() =
        assertContentEquals(
            hex("F0 00 32 41 61 00 00 00 00 40 00 00 00 40 00 00 00 01 04 0C 20 10 16 F7"),
            ReadRequest(0, 0x40, 4).response(bytes(1, 2, 3, 4)),
        )

    @Test fun finishResponseIsSuccessAndANul() =
        assertContentEquals(
            hex("F0 00 32 41 01 01 00 00 00 00 00 00 00 0F 01 00 00 73 6A 0D 1B 56 6C 5C 39 00 1C 00 F7"),
            ReadRequest(0, 0xF0000000L, 8).finishResponse(),
        )

    @Test fun readRequestRoundTrips() {
        val request = ReadRequest(1, 0x12340, 481)
        assertEquals(request, ReadRequest.parse(request.encoded()))
    }

    @Test fun readRequestWithBadChecksumIsIgnored() {
        val body = Fm1Message.unframe(ReadRequest(address = 0x100, length = 16).encoded())!!
        body[14] = (Fm1Message.u(body[14]) xor 1).toByte()
        assertNull(ReadRequest.parse(Fm1Message.frame(body)))
    }

    @Test fun responseDeclaresTheFullLength() {
        val data = ByteArray(481) { it.toByte() }
        val parsed = assertNotNull(ReadRequest.parseResponse(ReadRequest(address = 0x40, length = 481).response(data)))
        assertEquals(0x40, parsed.first.address)
        assertContentEquals(data, parsed.second)
    }

    @Test fun identityRoundTripsAndRecognisesTheLoader() {
        val identity = assertNotNull(Fm1Identity.parse("ota-FM-1_009"))
        assertTrue(identity.isLoader)
        assertEquals("FM-1", identity.hardwareModel)
        assertEquals(9, identity.version)
        assertEquals(identity, Fm1Identity.fromHandshakeReply(identity.handshakeReply()))
        assertEquals(91035, Fm1Identity.parse("FM-1_91035")!!.version)
        assertNull(Fm1Identity.parse("nonsense"))
    }

    @Test fun splitsAtTheLastUnderscore() {
        assertEquals("FM1_COMMUNITY", Fm1Identity.parse("FM1_COMMUNITY_5")!!.model)
        assertNull(Fm1Identity.parse("FM1_OFFICIAL_OPEN"))
    }

    @Test fun rejectsUnreadableIdentities() {
        assertNotNull(Fm1Identity.parse("FM-1_5"))
        listOf("_5", "FM-1_", "FM-1_12a", "FM-1", "", "FM-1_１２", "FM-1_-5", "FM-1_+5", "FM 1_5", "FM-1_5\n").forEach {
            assertNull(Fm1Identity.parse(it))
        }
    }

    @Test fun equalityIsOnTheText() = assertNotEquals(Fm1Identity.parse("FM-1_015"), Fm1Identity.of("FM-1", 15))

    @Test fun openFirmwareIsFm1KitsPredicate() {
        assertTrue(Fm1Identity.parse("FM-1_8000012")!!.isOpenFirmware)
        assertTrue(Fm1Identity.parse("FM1_COMMUNITY_3")!!.isOpenFirmware)
        assertFalse(Fm1Identity.parse("FM-1_8010300")!!.isOpenFirmware)
    }

    @Test fun armedLoaderParses() {
        val identity = assertNotNull(Fm1Identity.parse("ota-FM1C_DB1642B2B6FA5C2C"))
        assertEquals("db1642b2b6fa5c2c", identity.armedBuildPrefix)
        assertEquals("FM-1", identity.hardwareModel)
        assertNull(Fm1Identity.parse("ota-FM1C_DB1642B2B6FA5C"))
    }

    @Test fun capabilityLayoutAndCommandsRoundTrip() {
        val id = BuildId(ByteArray(32) { it.toByte() })
        val capability = Fm1Capability(state = 2, buildId = id, errorCode = 5)
        assertEquals(capability, Fm1Capability.parse(capability.payload()))
        val bad = capability.payload()
        bad[0] = 'X'.code.toByte()
        assertNull(Fm1Capability.parse(bad))
        val message = Fm1Message.command(Fm1Message.armCommand, bytes(1, 2, 3))
        val parsed = assertNotNull(Fm1Message.parseCommand(message))
        assertEquals(Fm1Message.armCommand, parsed.first)
        assertContentEquals(bytes(1, 2, 3), parsed.second)
        assertContentEquals(bytes(1, 7, 0, 0, 0, 0) + id.bytes, Fm1Capability.armPayload(7, 0, id))
    }
}

private fun bytes(vararg values: Int): ByteArray = Fm1Message.bytes(*values)

private fun hex(text: String): ByteArray = text.split(' ').map { it.toInt(16).toByte() }.toByteArray()
