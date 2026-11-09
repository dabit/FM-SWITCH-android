// Ported from FM1Kit 0.6.0 Tests/FM1RescueTests/LeavingGuideTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeavingGuideTest {
    @Test fun readsHowAFirmwareInstallsOthers() {
        assertEquals(UpdateCode.FELUCCA_ORIGINAL_CHECK, firmware("FELUCCA-LOADER-1").updateCode)
        assertEquals(UpdateCode.FELUCCA_FIXED_CHECK, firmware("FELUCCA-LOADER-1..AC79BLID").updateCode)
        assertEquals(UpdateCode.OWN_LOADER, firmware("FELUCCA-LOADER-1..LOADER NOT STAGED").updateCode)
        assertEquals(UpdateCode.UNKNOWN, firmware("BAUD").updateCode)
        assertEquals(UpdateCode.M_VAVE, firmware("", "FM-1_015").updateCode)
        val installer =
            FwscPackage(
                SamplePackage.file(SamplePackage.image(listOf(SamplePackage.Part.loader())), "FM-1_916"),
            )
        assertNull(installer.updateCode)
        assertNull(LeavingGuide.from(installer, bootArea = Fm1BootArea.V15))
    }

    @Test fun sendsPeopleThroughAFixedFirmwareFromTheOriginalCheckOnM_Bootloader() {
        val guide =
            requireNotNull(
                LeavingGuide.from(
                    firmware("FELUCCA-LOADER-1"),
                    name = "Felucca 1.6.1",
                    bootArea = Fm1BootArea.M_BOOTLOADER,
                ),
            )
        assertEquals(Fm1BootArea.M_BOOTLOADER, guide.bootArea)
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.V15))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.V16))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.M_BOOTLOADER))
        assertEquals(LeavingRoute.DIRECT, guide.route(LeavingDestination.FELUCCA_PLATFORM))
        assertEquals(1, guide.warnings.size)
        assertTrue(guide.warnings.first().contains("can't install V15 or V16 straight away"))
        assertEquals(
            listOf(
                "Felucca 1.6.1 can't install V15 directly: its update check refuses it while the FM-1 has " +
                    "m-bootloader.",
                "Install MIDIMON 0.4 or LOOM-8 first: Felucca 1.6.1 can install either.",
                "Then install V15 from that.",
                "On a Mac, installing V15 in download mode works whatever firmware is running.",
            ),
            guide.instructions(LeavingDestination.V15),
        )
    }

    @Test fun letsTheOriginalCheckTakeOnlyTheBootloaderTheFM1Has() {
        val guide = requireNotNull(LeavingGuide.from(firmware("FELUCCA-LOADER-1"), bootArea = Fm1BootArea.V15))
        assertEquals(LeavingRoute.DIRECT, guide.route(LeavingDestination.V15))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.V16))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.OPEN_FIRMWARE))
        assertEquals(
            "${guide.firmwareName} can't install V16 directly.",
            guide.instructions(LeavingDestination.V16).first(),
        )
        val overV16 = requireNotNull(LeavingGuide.from(firmware("FELUCCA-LOADER-1"), bootArea = Fm1BootArea.V16))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, overV16.route(LeavingDestination.V15))
        assertEquals(LeavingRoute.UNCERTAIN, overV16.route(LeavingDestination.V16))
        val unknown = requireNotNull(LeavingGuide.from(firmware("FELUCCA-LOADER-1"), bootArea = null))
        assertEquals(LeavingRoute.UNCERTAIN, unknown.route(LeavingDestination.V15))
        assertTrue(unknown.warnings.isEmpty())
    }

    @Test fun letsAFixedFirmwareGoAnywhere() {
        val guide =
            requireNotNull(
                LeavingGuide.from(firmware("FELUCCA-LOADER-1..AC79BLID"), bootArea = Fm1BootArea.M_BOOTLOADER),
            )
        assertTrue(LeavingDestination.entries.all { guide.route(it) == LeavingRoute.DIRECT })
        assertTrue(guide.warnings.isEmpty())
    }

    @Test fun warnsThatTransitFM1ReportsV15sIdentityAndKeepsTheBootloader() {
        val guide =
            requireNotNull(
                LeavingGuide.from(
                    firmware("FELUCCA-LOADER-1..LOADER NOT STAGED", "FM-1_015"),
                    name = "TransitFM1",
                    bootArea = Fm1BootArea.V16,
                ),
            )
        assertEquals(LeavingRoute.DIRECT, guide.route(LeavingDestination.V15))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.OPEN_FIRMWARE))
        assertEquals(LeavingRoute.THROUGH_FIXED_FIRMWARE, guide.route(LeavingDestination.V16))
        assertEquals(2, guide.warnings.size)
        assertTrue(guide.warnings.first().contains("can't install V16 straight away"))
        assertTrue(guide.warnings.last().startsWith("TransitFM1 reports itself as V15"))
        val steps = guide.instructions(LeavingDestination.V15)
        assertTrue(steps.contains("TransitFM1 reports itself as V15: confirm it isn't really V15 when asked."))
        assertTrue(steps.any { it.contains("keeps the bootloader it has") })
        val toV16 = guide.instructions(LeavingDestination.V16)
        assertEquals(
            "TransitFM1 can't install V16 directly: it only takes firmware images the size of V15's.",
            toV16.first(),
        )
        assertTrue(toV16.none { it.contains("keeps the bootloader it has") || it.contains("isn't really V15") })
    }

    @Test fun keepsTheBootloaderWhenAFirmwareComesWithItsOwnLoader() {
        val packageFile = firmware("FELUCCA-LOADER-1")
        assertEquals(
            Fm1BootArea.V16,
            LeavingGuide.from(packageFile, bootArea = Fm1BootArea.V16, installedBy = UpdateCode.M_VAVE)?.bootArea,
        )
        val v15 = firmware("", "FM-1_015")
        assertEquals(Fm1BootArea.M_BOOTLOADER, LeavingGuide.from(v15, bootArea = Fm1BootArea.M_BOOTLOADER)?.bootArea)
    }

    @Test fun letsV15InstallAnything() {
        val guide = LeavingGuide("V15", Fm1Identity.officialV15, UpdateCode.M_VAVE, Fm1BootArea.V15)
        assertTrue(LeavingDestination.entries.all { guide.route(it) == LeavingRoute.DIRECT })
        val v16 = LeavingGuide("V16", Fm1Identity.officialV16, UpdateCode.M_VAVE, Fm1BootArea.V16)
        assertEquals(LeavingRoute.UNCERTAIN, v16.route(LeavingDestination.M_BOOTLOADER))
        assertTrue(v16.warnings.isEmpty())
    }

    private fun firmware(
        markers: String,
        product: String = "FM-1_916",
    ): FwscPackage {
        val code = "..$product..$markers..".encodeToByteArray() + ByteArray(0x3000) { 0x5A }
        return FwscPackage(SamplePackage.firmwareWithCode(code, product))
    }
}
