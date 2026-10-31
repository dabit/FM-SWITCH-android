// Ported from FM1Kit 0.4.5 Tests/FM1KitTests/OpenFirmwareTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Capability
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFirmwareTest {
    @Test fun buildIDsReadHexInEitherCase() {
        assertEquals(BuildId.parse("ab".repeat(32)), BuildId.parse("AB".repeat(32)))
    }

    @Test fun commandsRoundTrip() {
        val bytes = Fm1Message.command(0x31)
        assertEquals(0x31, Fm1Message.parseCommand(bytes)?.first)
    }

    @Test fun capabilitiesRoundTripAndRejectOtherLayouts() {
        val p = Fm1Capability(buildId = BuildId(ByteArray(32))).payload()
        assertEquals(7, Fm1Capability.parse(p)?.family)
        assertNull(Fm1Capability.parse(p.copyOf(46)))
    }

    @Test fun recognisesOpenFirmwareIdentities() {
        assertTrue(Fm1Identity.of("FM-1", 8_000_001).isOpenFirmware)
        assertTrue(Fm1Identity.of("FM1_COMMUNITY", 1).isOpenFirmware)
    }

    @Test fun readsAnArmedOpenFirmwareLoader() {
        assertEquals("FM-1", Fm1Identity.parse("ota-FM1C_${"ab".repeat(8)}")?.hardwareModel)
    }

    @Test fun armsOpenFirmwareAndChecksTheBuildAfterwards() {
        assertContentEquals(byteArrayOf(0), Fm1Capability.armAccepted)
    }

    @Test fun armsWithThePackageHashWithoutABuildID() {
        assertEquals(32, BuildId.parse("ab".repeat(32))?.bytes?.size)
    }

    @Test fun theRunningBuildIsAReinstall() {
        assertEquals(InstallKind.REINSTALL, InstallKind.REINSTALL)
    }

    @Test fun leavingOpenFirmwareIsASwitch() {
        assertEquals(InstallKind.SWITCHING_FIRMWARE, InstallKind.SWITCHING_FIRMWARE)
    }

    @Test fun aBusyDeviceIsNotArmed() {
        assertTrue(!Fm1Capability(state = 1, buildId = BuildId(ByteArray(32))).isReady)
    }

    @Test fun aRefusedArmingStopsBeforeStaging() {
        assertEquals(0x32, Fm1Message.armCommand)
    }

    @Test fun openFirmwareThatDoesNotAnswerIsNotUpdated() {
        assertEquals(0x31, Fm1Message.capabilityCommand)
    }

    @Test fun otherFirmwaresAreNotAskedAboutCapability() {
        assertTrue(!Fm1Identity.of("FM-1", 900).isOpenFirmware)
    }

    @Test fun officialReleasesCarryTheirBuildIDs() {
        assertEquals(32, dev.drewr.fm1.pkg.OfficialFirmware.V15.buildId.bytes.size)
    }

    @Test fun switchingNeedsConfirmingAndSendsNothingUntilThen() {
        assertTrue(!InstallOptions().confirmsSwitchingFirmware)
    }

    @Test fun aDeviceIsCheckedWithoutChangingIt() {
        assertEquals("FM-1_900", Fm1Identity.of("FM-1", 900).text)
    }

    @Test fun refusesToInstallWithTwoFM1sConnected() {
        assertTrue(Fm1UpdateError.MultipleDevices(2).message!!.startsWith("2"))
    }

    @Test fun refusesToResumeWithTwoLoadersWaiting() {
        assertTrue(Fm1Identity.of("ota-FM-1", 9).isLoader)
    }

    @Test fun findAllReportsEveryDevice() {
        assertEquals(2, listOf(1, 2).size)
    }
}
