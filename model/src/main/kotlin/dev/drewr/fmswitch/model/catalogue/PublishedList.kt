// Ported from FM-SWITCH App/Model/PublishedList.swift.
package dev.drewr.fmswitch.model.catalogue

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.concurrent.CancellationException

class BadStatus(
    val code: Int,
) : Exception("HTTP $code")

class PublishedList(
    private val fetch: suspend (String) -> ByteArray,
    private val waits: List<Long> = listOf(2_000, 4_000, 8_000),
) {
    sealed class Failure : Exception() {
        data object Unreachable : Failure()

        data object Unusable : Failure()
    }

    suspend fun load(): MergedCatalogue {
        for (attempt in 0..waits.size) {
            val data =
                try {
                    fetch(URL)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: BadStatus) {
                    if (error.code < 500) throw Failure.Unusable
                    if (attempt < waits.size) delay(waits[attempt])
                    continue
                } catch (_: Throwable) {
                    if (attempt < waits.size) delay(waits[attempt])
                    continue
                }
            try {
                val result = json.decodeFromString<MergedCatalogue>(data.decodeToString())
                if (!result.isReadable || result.entries.isEmpty()) throw Failure.Unusable
                return result
            } catch (error: Failure) {
                throw error
            } catch (_: Throwable) {
                throw Failure.Unusable
            }
        }
        throw Failure.Unreachable
    }

    companion object {
        const val URL = "https://fm-1.drewr.dev/firmwares.json"
        val json =
            Json {
                ignoreUnknownKeys =
                    true
            }
    }
}
