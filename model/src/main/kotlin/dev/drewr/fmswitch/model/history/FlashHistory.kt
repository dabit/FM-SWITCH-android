// Ported from FM-SWITCH App/Model/FlashHistory.swift.
package dev.drewr.fmswitch.model.history

import dev.drewr.fmswitch.model.catalogue.InstantSerializer
import dev.drewr.fmswitch.model.library.StoredFirmware
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

@Serializable
data class FlashRecord(
    val name: String,
    val version: String,
    val product: String,
    @Serializable(with = InstantSerializer::class) val date: Instant,
)

class FlashHistory(
    private val directory: File?,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant,
) {
    var records: List<FlashRecord> = emptyList()
        private set
    val last get() = records.firstOrNull()

    suspend fun load() =
        withContext(ioDispatcher) {
            records =
                directory
                    ?.let { dir ->
                        runCatching {
                            Json.decodeFromString<List<FlashRecord>>(File(dir, "flash-history.json").readText())
                        }.getOrDefault(emptyList())
                    }.orEmpty()
        }

    fun lastInstall(product: String) = last?.takeIf { it.product == product }

    suspend fun record(item: StoredFirmware) =
        withContext(ioDispatcher) {
            records =
                (listOf(FlashRecord(item.name, item.version, item.product, now())) + records).take(30)
            directory?.let { dir ->
                dir.mkdirs()
                runCatching {
                    atomicWrite(File(dir, "flash-history.json"), Json.encodeToString(records).encodeToByteArray())
                }
            }
        }

    private fun atomicWrite(
        file: File,
        bytes: ByteArray,
    ) {
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw java.io.IOException("Couldn't save ${file.name}")
        }
    }
}
