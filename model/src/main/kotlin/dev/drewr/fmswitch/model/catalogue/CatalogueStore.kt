// Ported from FM-SWITCH CatalogueStore.swift.
package dev.drewr.fmswitch.model.catalogue

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant

class CatalogueStore(
    private val directory: File,
    private val list: PublishedList,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant,
) {
    private val refreshingState = MutableStateFlow(false)
    val isRefreshingFlow: StateFlow<Boolean> = refreshingState.asStateFlow()
    val isRefreshing get() = refreshingState.value
    private val catalogueState = MutableStateFlow<MergedCatalogue?>(null)
    val catalogueFlow: StateFlow<MergedCatalogue?> = catalogueState.asStateFlow()
    val catalogue get() = catalogueState.value
    private var didLoadCached = false
    private val errorState = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = errorState.asStateFlow()
    val errorMessage get() = errorState.value
    private val refreshMutex = Mutex()
    val entries get() =
        catalogue?.entries.orEmpty().sortedWith(
            compareByDescending<CatalogueEntry> {
                it.isOfficial
            }.thenBy { it.name.lowercase() },
        )

    suspend fun loadCached() =
        withContext(ioDispatcher) {
            if (didLoadCached) return@withContext
            catalogueState.value =
                runCatching {
                    val cache = File(directory, "catalogue.json")
                    val cached = json.decodeFromString<MergedCatalogue>(cache.readText())
                    correctingTestFlags(cached.copy(entries = cached.entries.distinctBy { it.id }))
                }.getOrNull()
            didLoadCached = true
        }

    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        refreshingState.value = true
        try {
            loadCached()
            val fresh = list.load()
            catalogueState.value = correctingTestFlags(fresh)
            withContext(ioDispatcher) {
                directory.mkdirs()
                runCatching {
                    atomicWrite(
                        File(directory, "catalogue.json"),
                        json.encodeToString(MergedCatalogue.serializer(), fresh).encodeToByteArray(),
                    )
                }
            }
            errorState.value = null
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (_: Throwable) {
            errorState.value =
                when {
                    catalogue?.builtAt?.isBefore(now().minus(Duration.ofDays(1))) == true -> {
                        "Couldn't refresh; showing the list saved earlier."
                    }

                    catalogue == null -> {
                        "Couldn't fetch the firmware list. Check your connection and try again."
                    }

                    else -> {
                        null
                    }
                }
        } finally {
            refreshingState.value = false
            refreshMutex.unlock()
        }
    }

    companion object {
        internal val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        private fun atomicWrite(
            file: File,
            bytes: ByteArray,
        ) {
            val temp = File(file.parentFile, ".${file.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                temp.delete()
                throw java.io.IOException("Couldn't save ${file.name}")
            }
        }

        fun correctingTestFlags(catalogue: MergedCatalogue): MergedCatalogue =
            catalogue.copy(
                entries =
                    catalogue.entries.map { entry ->
                        val version = entry.version.orEmpty()
                        when {
                            version.matches(Regex("[0-9a-fA-F]{7,40}")) &&
                                version.any(Char::isLetter) -> {
                                entry.copy(version = version.take(7), beta = true)
                            }

                            entry.beta -> {
                                entry.copy(
                                    beta =
                                        Regex("alpha|beta|rc|pre|test|dev|-b\\d", RegexOption.IGNORE_CASE)
                                            .containsMatchIn(version),
                                )
                            }

                            else -> {
                                entry
                            }
                        }
                    },
            )
    }
}
