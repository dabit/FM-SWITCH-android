package dev.drewr.fmswitch.model.catalogue

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PublishedListTest {
    private val sample = javaClass.classLoader.getResource("firmwares-sample.json")!!.readBytes()
    private val live = javaClass.classLoader.getResource("firmwares-live-trimmed.json")!!.readBytes()

    @Test fun everyFieldTheLiveListCarriesIsKept() {
        val source = Json.parseToJsonElement(live.decodeToString()).jsonObject
        val entries = source.getValue("entries").jsonArray
        assertTrue(entries.any { it.jsonObject.containsKey("installs") })
        assertTrue(entries.any { it.jsonObject.containsKey("flashChip") })
        val fields =
            (0 until CatalogueEntry.serializer().descriptor.elementsCount)
                .map(CatalogueEntry.serializer().descriptor::getElementName)
        entries.forEach { entry ->
            assertTrue(entry.jsonObject.keys.all { it in fields }, "Unknown fields: ${entry.jsonObject.keys - fields}")
        }
    }

    @Test fun reEncodingKeepsEveryValue() =
        runTest {
            val source = Json.parseToJsonElement(live.decodeToString()).jsonObject
            val decoded = PublishedList(fetch = { _ -> live }).load()
            val encoder = Json(PublishedList.json) { encodeDefaults = true }
            val encoded =
                encoder
                    .parseToJsonElement(encoder.encodeToString(MergedCatalogue.serializer(), decoded))
                    .jsonObject
            valuesIn(source, encoded)
        }

    @Test fun unknownValuesBecomeNull() =
        runTest {
            val list =
                PublishedList(fetch = { _ ->
                    """
                    {"formatVersion":1,"builtAt":"2026-11-09T00:00:00Z","entries":[
                      {"id":"future","name":"Future","flashChip":"future","packageOrigin":"zzz","whatSource":"x",
                       "otherRelease":{"packageOrigin":"zzz"}},
                      {"id":"normal","name":"Normal","flashChip":"old","packageOrigin":"hub"}
                    ]}
                    """.trimIndent().encodeToByteArray()
                }).load()
            val future = list.entries.first()
            assertEquals(null, future.flashChip)
            assertEquals(null, future.packageOrigin)
            assertEquals(null, future.whatSource)
            assertEquals(null, future.otherRelease?.packageOrigin)
            assertEquals(HubFlashChip.Old, list.entries.last().flashChip)
        }

    @Test fun decodesThePublishedShape() =
        runTest {
            assertEquals(
                (1..7).toList(),
                PublishedList(fetch = { sample })
                    .load()
                    .entries
                    .map { it.listing }
                    .sorted(),
            )
        }

    @Test fun triesAgainAfterTheNetworkFails() =
        runTest {
            var calls = 0
            PublishedList({
                if (calls++ ==
                    0
                ) {
                    error("offline")
                } else {
                    sample
                }
            }, listOf(0)).load()
            assertEquals(2, calls)
        }

    @Test fun givesUpAfterItsLastWait() =
        runTest {
            assertFailsWith<PublishedList.Failure.Unreachable> { PublishedList({ error("offline") }, listOf(0)).load() }
        }

    @Test fun doesNotTryAgainWhenTheListIsMissing() =
        runTest {
            assertFailsWith<PublishedList.Failure.Unusable> { PublishedList({ throw BadStatus(404) }).load() }
        }

    @Test fun aCancelledDownloadIsNotAFailure() =
        runTest {
            assertFailsWith<CancellationException> { PublishedList({ throw CancellationException() }).load() }
        }

    @Test fun refusesANewerFormat() =
        runTest {
            assertFailsWith<PublishedList.Failure.Unusable> {
                PublishedList({
                    sample.decodeToString().replace("\"formatVersion\":1", "\"formatVersion\":2").encodeToByteArray()
                }).load()
            }
        }

    @Test fun refusesAnEmptyList() =
        runTest {
            assertFailsWith<PublishedList.Failure.Unusable> {
                PublishedList({
                    "{\"formatVersion\":1,\"builtAt\":\"2026-10-30T00:00:00Z\",\"entries\":[]}".encodeToByteArray()
                }).load()
            }
        }

    @Test fun refusesAGarbageBodyWithoutRetrying() =
        runTest {
            var calls = 0
            assertFailsWith<PublishedList.Failure.Unusable> {
                PublishedList(fetch = {
                    calls++
                    "not json".encodeToByteArray()
                }, waits = listOf(0)).load()
            }
            assertEquals(1, calls)
        }

    private fun valuesIn(
        source: JsonObject,
        encoded: JsonObject,
    ) {
        val encodedEntries = encoded.getValue("entries").jsonArray.associateBy { it.jsonObject.getValue("id") }
        source.getValue("entries").jsonArray.forEach { sourceEntry ->
            val entry = sourceEntry.jsonObject
            val reEncoded = encodedEntries.getValue(entry.getValue("id")).jsonObject
            entry.forEach { (key, value) -> assertEquals(value, reEncoded[key], "${entry["id"]}: $key") }
        }
    }
}
