package dev.drewr.fmswitch.model.catalogue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HubFlashChipTest {
    @Test fun savesOnSaysWhichFm1sKeepChanges() {
        assertEquals("Older FM-1s only", HubFlashChip.Old.savesOn)
        assertEquals("All FM-1s", HubFlashChip.New.savesOn)
        assertEquals("All FM-1s (doesn't check the chip)", HubFlashChip.Any.savesOn)
    }

    @Test fun leavesTheChipUnknownWhenTheFeedSaysNothingUseful() =
        runTest {
            val list =
                PublishedList(fetch = { _ ->
                    """
                    {"formatVersion":1,"builtAt":"2026-11-09T00:00:00Z","entries":[
                      {"id":"absent","name":"Absent"},
                      {"id":"future","name":"Future","flashChip":"future"}
                    ]}
                    """.trimIndent().encodeToByteArray()
                }).load()
            assertNull(list.entries[0].flashChip)
            assertNull(list.entries[1].flashChip)
        }

    @Test fun keepsTheChipWhenTheListIsSaved() =
        runTest {
            val directory = createTempDirectory().toFile()
            val body =
                """
                {"formatVersion":1,"builtAt":"2026-11-09T00:00:00Z","entries":[
                  {"id":"old","name":"Old","flashChip":"old"},
                  {"id":"new","name":"New","flashChip":"new"},
                  {"id":"any","name":"Any","flashChip":"any"}
                ]}
                """.trimIndent().encodeToByteArray()
            val store =
                CatalogueStore(directory, PublishedList(fetch = { _ -> body }), Dispatchers.Unconfined) {
                    Instant.parse("2026-11-10T00:00:00Z")
                }
            store.refresh()
            val saved =
                CatalogueStore.json.decodeFromString<MergedCatalogue>(
                    File(directory, "catalogue.json").readText(),
                )
            assertEquals(
                listOf(HubFlashChip.Old, HubFlashChip.New, HubFlashChip.Any),
                saved.entries.map { it.flashChip },
            )
        }
}
