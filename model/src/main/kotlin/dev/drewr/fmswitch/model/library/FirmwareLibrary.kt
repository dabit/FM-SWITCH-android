// Ported from FM-SWITCH App/Model/FirmwareLibrary.swift.
package dev.drewr.fmswitch.model.library

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.catalogue.InstantSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class StoredFirmware(
    val id: String,
    val name: String,
    val version: String,
    val author: String? = null,
    val product: String,
    val sha256: String,
    val size: Int,
    val fileName: String,
    val source: Source,
    val catalogueID: String? = null,
    val packageURL: String? = null,
    val build: String? = null,
    @Serializable(with = InstantSerializer::class) val savedAt: Instant,
) {
    @Serializable enum class Source { Catalogue, Official, Imported }
}

class FirmwareLibrary(
    private val directory: File,
    private val bundledOfficial: () -> InputStream,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant,
    private val officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official,
) {
    constructor(
        directory: File,
        bundledOfficial: () -> InputStream,
        ioDispatcher: CoroutineDispatcher,
        now: () -> Instant,
    ) : this(directory, bundledOfficial, ioDispatcher, now, OfficialFirmware::official)

    private val mutableItems = MutableStateFlow<List<StoredFirmware>>(emptyList())
    val itemsFlow: StateFlow<List<StoredFirmware>> = mutableItems.asStateFlow()
    val items: List<StoredFirmware> get() = mutableItems.value
    private val loaderHashes = mutableMapOf<String, String?>()
    private val officialBySha = ConcurrentHashMap<String, OfficialFirmware>()
    private val mutationMutex = Mutex()

    @Volatile private var builtIn: StoredFirmware? = null

    suspend fun load() =
        withContext(ioDispatcher) {
            builtIn =
                bundledOfficial()
                    .use { stream ->
                        runCatching { FwscPackage(stream.readBytes()) }.getOrNull()
                    }?.takeIf { OfficialFirmware.V15.matches(it) }
                    ?.let { p ->
                        StoredFirmware(
                            p.sha256,
                            OfficialFirmware.V15.name,
                            "V15",
                            "M-VAVE",
                            p.product,
                            p.sha256,
                            p.fileBytes.size,
                            "FM-1_015.fwsc",
                            StoredFirmware.Source.Official,
                            savedAt = Instant.EPOCH,
                        )
                    }
            directory.mkdirs()
            reloadFromDisk()
        }

    private val details get() = File(directory, ".details")
    val official get() =
        items.firstOrNull {
            it.source == StoredFirmware.Source.Official &&
                it.sha256 == OfficialFirmware.V15.sha256
        }
    val sortedItems get() =
        items.sortedWith(
            compareByDescending<StoredFirmware> {
                it.source ==
                    StoredFirmware.Source.Official
            }.thenBy { it.name.lowercase() },
        )

    fun url(item: StoredFirmware) = if (item == builtIn) null else File(directory, item.fileName)

    suspend fun loadPackage(item: StoredFirmware): FwscPackage =
        withContext(ioDispatcher) {
            val bytes =
                if (item == builtIn) {
                    bundledOfficial().use { it.readBytes() }
                } else {
                    requireNotNull(url(item)).readBytes()
                }
            FwscPackage(bytes)
        }

    fun stored(
        entry: CatalogueEntry,
        from: List<StoredFirmware> = items,
    ) = entry.sha256?.let { sha -> from.firstOrNull { it.sha256.equals(sha, true) } }
        ?: entry.packageURL?.let { address ->
            from.firstOrNull { it.catalogueID == entry.id && it.packageURL == address }
        }

    fun outdated(entry: CatalogueEntry) =
        if (stored(entry) != null) {
            null
        } else {
            items.firstOrNull {
                it.catalogueID == entry.id &&
                    (entry.version?.let { version -> ReleaseVersion.isNewer(version, it.version) } ?: true)
            }
        }

    fun otherBuild(entry: CatalogueEntry) =
        if (stored(entry) != null) {
            null
        } else {
            items.firstOrNull {
                it.catalogueID == entry.id && !ReleaseVersion.differs(it.version, entry.version)
            }
        }

    fun savedCopies(
        entry: CatalogueEntry,
        from: List<StoredFirmware> = items,
    ) = from.filter { it.catalogueID == entry.id }

    /** The copies an entry's page can install, including the bundled V15 for M-VAVE's entry. */
    fun pageCopies(
        entry: CatalogueEntry,
        from: List<StoredFirmware> = items,
    ): List<StoredFirmware> {
        val saved = savedCopies(entry, from)
        val bundled =
            builtIn?.takeIf { item ->
                entry.isOfficial && saved.none { it.sha256.equals(item.sha256, ignoreCase = true) }
            }
        return saved + listOfNotNull(bundled)
    }

    fun pageVersionLabel(
        entry: CatalogueEntry,
        copy: StoredFirmware?,
    ): String? =
        copy
            ?.sha256
            ?.let { officialBySha[it.lowercase()] }
            ?.name
            ?.removePrefix("FM-1 ")
            ?: copy?.version
            ?: entry.version

    fun isSaved(entry: CatalogueEntry) = savedCopies(entry).isNotEmpty() && outdated(entry) == null

    val totalBytes get() = items.sumOf { it.size }

    fun canRemove(item: StoredFirmware) = item != builtIn

    suspend fun remove(item: StoredFirmware) =
        mutationMutex.withLock {
            withContext(ioDispatcher) {
                if (!canRemove(item)) return@withContext
                url(item)?.delete()
                File(details, "${item.sha256}.json").delete()
                reloadFromDisk()
            }
        }

    suspend fun clear() =
        mutationMutex.withLock {
            withContext(ioDispatcher) {
                items.filter { it.source != StoredFirmware.Source.Official }.forEach { item ->
                    url(item)?.delete()
                    File(details, "${item.sha256}.json").delete()
                }
                reloadFromDisk()
            }
        }

    suspend fun reload() = withContext(ioDispatcher) { reloadFromDisk() }

    private fun reloadFromDisk() {
        val disk =
            details
                .listFiles()
                ?.mapNotNull {
                    runCatching { Json.decodeFromString(StoredFirmware.serializer(), it.readText()) }.getOrNull()
                }?.filter { File(directory, it.fileName).isFile }
                .orEmpty()
        val loaded = disk + listOfNotNull(builtIn).filter { item -> disk.none { it.sha256 == item.sha256 } }
        officialBySha.clear()
        loaded.filter { it.source == StoredFirmware.Source.Official }.forEach { item ->
            runCatching {
                val bytes =
                    if (item == builtIn) {
                        bundledOfficial().use { it.readBytes() }
                    } else {
                        File(directory, item.fileName).readBytes()
                    }
                officialOf(FwscPackage(bytes))
            }.getOrNull()
                ?.let { official -> officialBySha[item.sha256.lowercase()] = official }
        }
        mutableItems.value = loaded
        loaderHashes.keys.retainAll(items.map { it.id }.toSet())
    }

    suspend fun packagesThatCanFinish(loader: Fm1Identity): List<StoredFirmware> =
        withContext(ioDispatcher) {
            sortedItems.filter { item ->
                when {
                    loader.armedBuildPrefix?.let { buildId(item).startsWith(it) } == true -> true
                    Fm1Identity.LOADER_PREFIX + item.product == loader.text -> true
                    isStockLoader(loader) -> loaderSha256(item) == OfficialFirmware.STOCK_LOADER_SHA256
                    else -> false
                }
            }
        }

    private fun isStockLoader(loader: Fm1Identity) =
        loader.isLoader &&
            loader.armedBuildPrefix == null &&
            loader.hardwareModel == "FM-1" &&
            OfficialFirmware.official.any { it.identity.version == loader.version }

    private fun buildId(item: StoredFirmware) =
        OfficialFirmware.known
            .firstOrNull { it.sha256 == item.sha256 }
            ?.buildId
            ?.hex ?: item.sha256

    @Synchronized
    private fun loaderSha256(item: StoredFirmware): String? {
        if (loaderHashes.containsKey(item.id)) return loaderHashes[item.id]
        val value = url(item)?.let { runCatching { FwscPackage(it.readBytes()).loaderSha256 }.getOrNull() }
        loaderHashes[item.id] = value
        return value
    }

    suspend fun download(
        entry: CatalogueEntry,
        fetch: suspend (String) -> ByteArray,
    ): StoredFirmware =
        mutationMutex.withLock {
            withContext(ioDispatcher) {
                val address = entry.packageURL ?: throw Failure.NoPackage
                val bytes =
                    try {
                        fetch(address)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        throw Failure.Download(error.message ?: error::class.simpleName.orEmpty())
                    }
                if (
                    !entry.isOfficial &&
                    (
                        (entry.size != null && entry.size != bytes.size) ||
                            (entry.sha256 != null && !FwscPackage.sha256Hex(bytes).equals(entry.sha256, true))
                    )
                ) {
                    throw Failure.HashMismatch
                }
                val packageFile = FwscPackage(bytes)
                if (
                    !entry.isOfficial &&
                    entry.product != null &&
                    entry.product != packageFile.product
                ) {
                    throw Failure.ProductMismatch(entry.product, packageFile.product)
                }
                officialOf(packageFile)?.let { official ->
                    return@withContext saveOfficial(
                        bytes,
                        packageFile,
                        official,
                        entry.id,
                        address,
                    )
                }
                if (entry.isOfficial) throw Failure.NotOfficial
                save(
                    bytes,
                    packageFile,
                    entry.name,
                    entry.version ?: packageFile.product,
                    entry.author,
                    StoredFirmware.Source.Catalogue,
                    entry.id,
                    address,
                    entry.build,
                )
            }
        }

    suspend fun add(
        bytes: ByteArray,
        fallbackName: String,
    ): StoredFirmware =
        mutationMutex.withLock {
            withContext(ioDispatcher) {
                val packageFile = FwscPackage(bytes)
                items.firstOrNull { it.sha256 == packageFile.sha256 }?.let { return@withContext it }
                officialOf(packageFile)?.let { official ->
                    return@withContext saveOfficial(
                        bytes,
                        packageFile,
                        official,
                        null,
                        null,
                    )
                }
                val known = OfficialFirmware.matching(packageFile)
                save(
                    bytes,
                    packageFile,
                    known?.name ?: fallbackName,
                    packageFile.product,
                    known?.let {
                        "M-VAVE"
                    },
                    StoredFirmware.Source.Imported,
                    null,
                    null,
                    null,
                )
            }
        }

    private suspend fun saveOfficial(
        bytes: ByteArray,
        packageFile: FwscPackage,
        official: OfficialFirmware,
        catalogueID: String?,
        packageURL: String?,
    ): StoredFirmware {
        officialBySha[packageFile.sha256.lowercase()] = official
        return save(
            bytes,
            packageFile,
            official.name,
            "Official",
            "M-VAVE",
            StoredFirmware.Source.Official,
            catalogueID,
            packageURL,
            null,
        )
    }

    private suspend fun save(
        bytes: ByteArray,
        packageFile: FwscPackage,
        name: String,
        version: String,
        author: String?,
        source: StoredFirmware.Source,
        catalogueID: String?,
        packageURL: String?,
        build: String?,
    ): StoredFirmware {
        items.firstOrNull { it.sha256 == packageFile.sha256 }?.let { return it }
        directory.mkdirs()
        details.mkdirs()
        val safe = (
            listOf(name, version.takeIf { it != packageFile.product }, build)
                .filterNotNull()
                .joinToString(" ")
                .map {
                    if (it in
                        "/:\\"
                    ) {
                        '-'
                    } else {
                        it
                    }
                }.joinToString("") +
                ".fwsc"
        )
        val requested = File(directory, safe)
        val file =
            if (requested.exists()) {
                File(
                    directory,
                    safe.removeSuffix(".fwsc") + " ${packageFile.sha256.take(8)}.fwsc",
                )
            } else {
                requested
            }
        atomicWrite(file, bytes)
        val item =
            StoredFirmware(
                packageFile.sha256,
                name,
                version,
                author,
                packageFile.product,
                packageFile.sha256,
                bytes.size,
                file.name,
                source,
                catalogueID,
                packageURL,
                build,
                now(),
            )
        atomicWrite(
            File(details, "${item.sha256}.json"),
            Json.encodeToString(StoredFirmware.serializer(), item).encodeToByteArray(),
        )
        reloadFromDisk()
        return item
    }

    sealed class Failure(
        message: String,
    ) : IllegalArgumentException(message) {
        data class Download(
            val reason: String,
        ) : Failure("Download failed: $reason")

        data object OfficialNotFound : Failure(
            "Couldn't find FM-1.fwsc on M-VAVE's downloads page. Download it in your browser and import it from your files.",
        )

        data object NoPackage : Failure(
            "No downloadable file is known for this firmware. Get it from the project's own links.",
        )

        data object HashMismatch : Failure(
            "The downloaded file doesn't match its published size or checksum, so it was not saved. Refresh the list and try again.",
        )

        data class ProductMismatch(
            val expected: String,
            val found: String,
        ) : Failure("The file reports $found, but the catalogue says $expected. Not saved.")

        data object NotOfficial : Failure(
            "That file isn't a recognised official FM-1 firmware (only an unmodified V15 or V16 FM-1.fwsc is accepted).",
        )
    }

    companion object {
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
    }
}

/** Release ordering equivalent to the catalogue's semantic version comparison. */
object ReleaseVersion {
    fun differs(
        candidate: String,
        listed: String?,
    ): Boolean {
        if (listed == null) return true
        return isNewer(candidate, listed) || isNewer(listed, candidate)
    }

    fun isNewer(
        candidate: String,
        than: String,
    ): Boolean {
        val candidateParts = candidate.filter { it.isDigit() || it == '.' }.split('.').mapNotNull(String::toIntOrNull)
        val currentParts = than.filter { it.isDigit() || it == '.' }.split('.').mapNotNull(String::toIntOrNull)
        val length = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until length) {
            val comparison = (candidateParts.getOrElse(index) { 0 }).compareTo(currentParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}
