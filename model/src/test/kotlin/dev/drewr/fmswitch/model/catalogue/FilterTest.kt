package dev.drewr.fmswitch.model.catalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterTest {
    private val entries =
        listOf(
            CatalogueEntry("a", "A", kinds = listOf("Synth"), listing = Listing.HUB),
            CatalogueEntry("b", "B", kinds = listOf("synth", "Drums"), listing = Listing.RESOURCE_HUB),
        )

    @Test fun listsEachKindOnceTheMostCommonFirst() {
        assertEquals(listOf("synth", "drums"), FirmwareFilter.kinds(entries))
    }

    @Test fun matchesAKindWhateverItsCase() {
        assertTrue(FirmwareFilter.isKind("SYNTH", entries.first()))
    }

    @Test fun keepsTheNameOrder() {
        assertEquals(entries, FirmwareOrder.ByName.sorted(entries))
    }

    @Test fun putsTheNewestReleaseFirstAndUndatedLast() {
        assertEquals(
            "a",
            FirmwareOrder.Updated
                .sorted(
                    listOf(entries[1].copy(date = null), entries[0].copy(date = "2026-01-01")),
                ).first()
                .id,
        )
    }

    @Test fun countsTheFirmwareHubAndTheOfficialFirmwareAsTested() {
        assertTrue(entries.first().isHardwareTested)
    }

    @Test fun marksFirmwaresOnlyInTheResourceHubAsUntested() {
        assertTrue(!entries[1].isHardwareTested)
    }

    @Test fun dropsTheHubsFlagFromAPlainVersion() {
        val fixed =
            CatalogueStore.correctingTestFlags(
                MergedCatalogue(
                    1,
                    java.time.Instant.EPOCH,
                    entries =
                        listOf(
                            CatalogueEntry("x", "X", version = "0.3.7", beta = true, packageOrigin = PackageOrigin.Hub),
                        ),
                ),
            )
        assertTrue(!fixed.entries.single().beta)
    }

    @Test fun marksAnUntaggedBuildAsATest() {
        val fixed =
            CatalogueStore.correctingTestFlags(
                MergedCatalogue(
                    1,
                    java.time.Instant.EPOCH,
                    entries =
                        listOf(
                            CatalogueEntry(
                                "x",
                                "X",
                                version = "047d21f6154ff6f86c7d3dc4b90f8d5e3320c300",
                                packageOrigin = PackageOrigin.Hub,
                            ),
                        ),
                ),
            )
        assertEquals("047d21f", fixed.entries.single().version)
        assertTrue(fixed.entries.single().beta)
    }

    @Test fun correctsVersionsWithoutAPackageOrigin() {
        val versions = listOf("20261020", "1.2-pre", "v2-dev", "1.0-b3", "1.2-3")
        val corrected =
            CatalogueStore.correctingTestFlags(
                MergedCatalogue(
                    1,
                    java.time.Instant.EPOCH,
                    entries = versions.map { CatalogueEntry(it, it, version = it, beta = true) },
                ),
            )
        assertEquals(listOf(false, true, true, true, false), corrected.entries.map { it.beta })
    }

    @Test fun namesAllOfTheHubsKinds() {
        assertEquals("Synths", FirmwareFilter.name("synth"))
        assertEquals("Trackers", FirmwareFilter.name("tracker"))
        assertEquals("For devs", FirmwareFilter.name("dev"))
        assertEquals("Games", FirmwareFilter.name("game"))
    }

    @Test fun isBuiltFromSourceNeedsTheSameSha() {
        assertTrue(!entries.first().isBuiltFromSource)
    }

    @Test fun filtersBetaSearchAndLinksOnlyEntries() {
        val filter = FirmwareFilter("a")
        val hidden = CatalogueEntry("h", "Alpha", beta = true, listing = Listing.HUB)
        val shown = CatalogueEntry("s", "Alpha", packageURL = "https://example.test/a", listing = Listing.HUB)
        assertEquals(
            listOf(shown),
            filter.visible(listOf(hidden, shown), showBeta = false, showLinksOnly = false, saved = emptyList()),
        )
    }

    @Test fun scopesAndDescriptionsFollowTheCatalogue() {
        val entry = CatalogueEntry("file-abc", "File", listing = Listing.HUB or Listing.EDITOR)
        assertEquals("None: added from a file", entry.listedOn)
        assertTrue(FirmwareScope.Kind("synth").includes(entries.first()))
        assertEquals("Grooveboxes", FirmwareFilter.name("groove"))
    }
}
