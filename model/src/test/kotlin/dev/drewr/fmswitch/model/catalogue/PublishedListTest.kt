package dev.drewr.fmswitch.model.catalogue

import kotlinx.coroutines.test.runTest
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublishedListTest {
    private val sample = javaClass.classLoader.getResource("firmwares-sample.json")!!.readBytes()

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
}
