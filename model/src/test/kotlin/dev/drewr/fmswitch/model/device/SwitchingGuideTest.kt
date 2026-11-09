package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.LeavingDestination
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.pkg.UpdateCode
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fmswitch.model.settings.StartUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwitchingGuideTest {
    private val transit =
        LeavingGuide("TransitFM1", Fm1Identity.officialV15, UpdateCode.OWN_LOADER, Fm1BootArea.M_BOOTLOADER)
    private val felucca = LeavingGuide("Felucca", Fm1Identity.of("FM-1", 916), UpdateCode.FELUCCA_ORIGINAL_CHECK, null)

    @Test fun mVavesFirmwaresAreKnownByTheirIdentity() {
        assertEquals(UpdateCode.M_VAVE, SwitchingGuide.running(Fm1Identity.officialV15, null, null)?.updateCode)
        assertEquals(Fm1BootArea.V16, SwitchingGuide.running(Fm1Identity.officialV16, null, StartUp.MVave)?.bootArea)
    }

    @Test fun aSavedGuideTellsTransitFM1FromV15() {
        val running = SwitchingGuide.running(Fm1Identity.officialV15, transit, StartUp.MVave)
        assertEquals(transit, running)
        assertTrue(SwitchingGuide.reportsOfficialIdentity(requireNotNull(running)))
    }

    @Test fun aSavedGuideForAnotherIdentityIsIgnored() =
        assertNull(
            SwitchingGuide.running(felucca.identity, transit, null),
        )

    @Test fun theStartUpFillsInABootloaderTheGuideDoesntKnow() =
        assertEquals(
            Fm1BootArea.M_BOOTLOADER,
            SwitchingGuide.running(felucca.identity, felucca, StartUp.MBootloader)?.bootArea,
        )

    @Test fun nothingIsKnownAboutAnUpdateLoaderOrAnUnknownFirmware() {
        assertNull(SwitchingGuide.running(requireNotNull(Fm1Identity.parse("ota-FM-1_015")), null, StartUp.MVave))
        assertNull(SwitchingGuide.running(felucca.identity, null, StartUp.MVave))
    }

    @Test fun startUpsAndBootloadersMatch() =
        StartUp.entries
            .filter {
                it.bootArea != null
            }.forEach { assertEquals(it, startUpOf(requireNotNull(it.bootArea))) }

    @Test fun v15IsADestinationOfItsOwn() {
        val file = FwscPackage(SamplePackage.file("FM-1_015"))
        assertEquals(LeavingDestination.V15, destinationOf(file, official = OfficialFirmware.V15))
    }

    @Test fun aV16PackageIsADestinationOfItsOwn() {
        val file = FwscPackage(SamplePackage.file("FM-1_016"))
        assertEquals(LeavingDestination.V16, destinationOf(file, official = OfficialFirmware.V16))
    }

    @Test fun v15ThroughTransitFM1KeepsTheBootloader() {
        val file = FwscPackage(SamplePackage.file("FM-1_015"))
        val installed = SwitchingGuide.installing(file, "V15", transit, StartUp.MBootloader)
        assertEquals(Fm1BootArea.M_BOOTLOADER, installed.first?.bootArea)
        assertNull(installed.second)
    }
}
