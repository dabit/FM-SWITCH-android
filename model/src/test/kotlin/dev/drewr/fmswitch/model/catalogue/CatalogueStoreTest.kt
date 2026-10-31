package dev.drewr.fmswitch.model.catalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogueStoreTest {
    private val body =
        "{\"formatVersion\":1,\"builtAt\":\"2026-10-30T00:00:00Z\",\"entries\":[{\"id\":\"x\",\"name\":\"X\",\"listing\":0},{\"id\":\"x\",\"name\":\"X2\",\"listing\":0}],\"problems\":[]}"
            .encodeToByteArray()

    private fun store(dir: File) =
        CatalogueStore(dir, PublishedList(fetch = { body }), Dispatchers.Unconfined) {
            Instant.parse("2026-11-01T00:00:00Z")
        }.also { kotlinx.coroutines.runBlocking { it.loadCached() } }

    @Test fun dropsARepeatedIdFromASavedList() =
        runTest {
            val d = createTempDirectory().toFile()
            File(d, "catalogue.json").writeText(body.decodeToString())
            assertEquals(listOf("x"), store(d).entries.map { it.id })
        }

    @Test fun correctsTheSavedListBeforeShowingIt() =
        runTest {
            val d = createTempDirectory().toFile()
            File(d, "catalogue.json").writeText(
                "{\"formatVersion\":1,\"builtAt\":\"2026-10-30T00:00:00Z\",\"entries\":[{\"id\":\"x\",\"name\":\"X\",\"version\":\"1.2-pre\",\"beta\":true}],\"problems\":[]}",
            )
            assertEquals(true, store(d).entries.single().beta)
        }

    @Test fun sortsOfficialFirst() =
        runTest {
            val d = createTempDirectory().toFile()
            File(
                d,
                "catalogue.json",
            ).writeText(
                "{\"formatVersion\":1,\"builtAt\":\"2026-10-30T00:00:00Z\",\"entries\":[{\"id\":\"z\",\"name\":\"A\",\"listing\":0},{\"id\":\"o\",\"name\":\"Z\",\"listing\":0,\"isOfficial\":true}],\"problems\":[]}",
            )
            assertEquals("o", store(d).entries.first().id)
        }

    @Test fun keepsTheSavedListOnFailureAndSaysSoWhenOld() =
        runTest {
            val d = createTempDirectory().toFile()
            File(
                d,
                "catalogue.json",
            ).writeText(
                "{\"formatVersion\":1,\"builtAt\":\"2020-01-01T00:00:00Z\",\"entries\":[{\"id\":\"x\",\"name\":\"X\",\"listing\":0}],\"problems\":[]}",
            )
            val s =
                CatalogueStore(
                    d,
                    PublishedList(fetch = {
                        error("offline")
                    }, waits = listOf(0)),
                    Dispatchers.Unconfined,
                ) { Instant.parse("2026-11-01T00:00:00Z") }
            s.refresh()
            assertEquals("Couldn't refresh; showing the list saved earlier.", s.errorMessage)
        }

    @Test fun keepsTheSavedListOnFailureAndSaysSoWhenRecent() =
        runTest {
            val d = createTempDirectory().toFile()
            File(
                d,
                "catalogue.json",
            ).writeText(
                "{\"formatVersion\":1,\"builtAt\":\"2026-10-31T00:00:00Z\",\"entries\":[{\"id\":\"x\",\"name\":\"X\",\"listing\":0}],\"problems\":[]}",
            )
            val s =
                CatalogueStore(
                    d,
                    PublishedList(fetch = {
                        error("offline")
                    }, waits = listOf(0)),
                    Dispatchers.Unconfined,
                ) { Instant.parse("2026-11-01T00:00:00Z") }
            s.refresh()
            assertEquals(null, s.errorMessage)
        }
}
