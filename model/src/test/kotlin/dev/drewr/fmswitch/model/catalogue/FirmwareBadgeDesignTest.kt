package dev.drewr.fmswitch.model.catalogue

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FirmwareBadgeDesignTest {
    @Test fun badgeLettersComeFromTheName() {
        assertEquals("Fe", FirmwareBadgeDesign.letters("Felucca"))
        assertEquals("SB", FirmwareBadgeDesign.letters("SLOOP DX7 Banks"))
        assertEquals("MV", FirmwareBadgeDesign.letters("M-VAVE official firmware"))
        assertEquals("A1", FirmwareBadgeDesign.letters("AMB-1"))
        assertEquals("AE", FirmwareBadgeDesign.letters("AMB-1 Evolved"))
        assertEquals("AW", FirmwareBadgeDesign.letters("AmbWave"))
        assertEquals("38", FirmwareBadgeDesign.letters("386DX"))
        assertEquals("SL", FirmwareBadgeDesign.letters("SLOOP"))
        assertEquals("Md", FirmwareBadgeDesign.letters("fm1-mdx"))
        assertEquals("RO", FirmwareBadgeDesign.letters("fm1-rocket-os"))
        assertEquals("?", FirmwareBadgeDesign.letters(""))
    }

    @Test fun badgesKeepToTheirNames() {
        assertEquals(4_087_215_988u, FirmwareBadgeDesign.seed("Felucca"))
        assertEquals(FirmwareBadgeDesign("felucca!"), FirmwareBadgeDesign("Felucca"))
        val felucca = FirmwareBadgeDesign("Felucca")
        val felucca2 = FirmwareBadgeDesign("Felucca 2")
        assertNotEquals(listOf(felucca.first, felucca.second), listOf(felucca2.first, felucca2.second))
    }

    @Test fun badgesComeIn896Designs() {
        val designs = (0 until 20_000).map { FirmwareBadgeDesign("Firmware $it") }
        assertEquals(896, designs.map { "${it.first} ${it.second} ${it.split}" }.toSet().size)
    }

    @Test fun aBadgesHalvesContrast() {
        for (index in 0 until 2_000) {
            val design = FirmwareBadgeDesign("Firmware $index")
            assertTrue(contrast(design.first, design.second) >= 1.4)
        }
    }

    @Test fun theOfficialBadgeShowsItsVersion() {
        assertEquals("V15", FirmwareBadgeDesign("M-VAVE official firmware", true, "V15").letters)
        assertEquals("V16", FirmwareBadgeDesign("M-VAVE official firmware", true, "V16").letters)
        assertEquals("MV", FirmwareBadgeDesign("M-VAVE official firmware", true).letters)
    }

    @Test fun badgeColoursMatchTheSwiftGoldenValues() {
        val felucca = FirmwareBadgeDesign("Felucca")
        assertEquals(0x3C236Bu, felucca.first)
        assertEquals(0x737D00u, felucca.second)
        assertEquals(FirmwareBadgeDesign.Split.Diagonal, felucca.split)
        assertEquals(0xFFFFFFu, felucca.ink)
        val official = FirmwareBadgeDesign("M-VAVE official firmware", true, "V15")
        assertEquals("V15", official.letters)
        assertEquals(0x1C1C1Eu, official.first)
        assertEquals(0x1C1C1Eu, official.second)
    }

    @Test fun catalogueEntryBadgeUsesTheNameNotItsId() {
        val entry = CatalogueEntry(id = "not-the-name", name = "Felucca")
        assertEquals(FirmwareBadgeDesign("Felucca"), entry.badge)
        assertNotEquals(FirmwareBadgeDesign(entry.id), entry.badge)
    }

    private fun contrast(
        first: UInt,
        second: UInt,
    ): Double {
        fun luminance(rgb: UInt): Double {
            fun channel(shift: Int): Double {
                val value = ((rgb shr shift) and 0xFFu).toDouble() / 255
                return if (value <= .03928) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        return (maxOf(luminance(first), luminance(second)) + .05) / (minOf(luminance(first), luminance(second)) + .05)
    }
}
