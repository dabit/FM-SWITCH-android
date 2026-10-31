package dev.drewr.fmswitch.model.library
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryTest {
    private fun add(
        library: FirmwareLibrary,
        bytes: ByteArray,
        name: String,
    ) = kotlinx.coroutines.runBlocking { library.add(bytes, name) }

    private fun finishers(
        library: FirmwareLibrary,
        loader: Fm1Identity,
    ) = kotlinx.coroutines.runBlocking { library.packagesThatCanFinish(loader) }

    private fun library(): FirmwareLibrary {
        val f = File(System.getProperty("fm1.v15"))
        return FirmwareLibrary(createTempDirectory().toFile(), {
            FileInputStream(f)
        }, Dispatchers.Unconfined, { Instant.EPOCH }).also { kotlinx.coroutines.runBlocking { it.load() } }
    }

    private fun libraryWithStockLoader(): FirmwareLibrary {
        val v15 = File(System.getProperty("fm1.v15"))
        val directory = createTempDirectory().toFile()
        v15.copyTo(File(directory, "stock.fwsc"))
        val hash = "db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a"
        val details = File(directory, ".details").also { it.mkdirs() }
        File(details, "$hash.json").writeText(
            """{"id":"$hash","name":"Stock","version":"V15","product":"FM-1_900","sha256":"$hash","size":699956,"fileName":"stock.fwsc","source":"Imported","savedAt":"1970-01-01T00:00:00Z"}""",
        )
        return FirmwareLibrary(directory, {
            FileInputStream(v15)
        }, Dispatchers.Unconfined, { Instant.EPOCH }).also { kotlinx.coroutines.runBlocking { it.load() } }
    }

    @Test fun comesWithTheOfficialFirmwareAndKeepsItWhenCleared() {
        val l = library()
        kotlinx.coroutines.runBlocking { l.clear() }
        assertEquals("Official", l.official!!.version)
    }

    @Test fun readsTheBuiltInCopyRatherThanSavingIt() {
        assertTrue(library().items.size == 1)
    }

    @Test fun loadsSavedAndBuiltInPackages() {
        val library = library()
        val saved = add(library, SamplePackage.file("FM-1_916"), "Felucca")
        val savedPackage = kotlinx.coroutines.runBlocking { library.loadPackage(saved) }
        val officialPackage = kotlinx.coroutines.runBlocking { library.loadPackage(library.official!!) }
        assertEquals(saved.sha256, savedPackage.sha256)
        assertEquals(library.official!!.sha256, officialPackage.sha256)
    }

    @Test fun cannotRemoveTheBuiltInCopy() {
        val l = library()
        assertTrue(!l.canRemove(l.official!!))
    }

    @Test fun savesPackagesUnderTheirNamesWithTheirDetailsBeside() {
        val library = library()
        val item = add(library, SamplePackage.file("FM-1_916"), "Felucca")
        assertTrue(item.fileName.endsWith(".fwsc"))
        assertTrue(File(libraryDir(library), ".details/${item.sha256}.json").isFile)
        val collision = add(library, SamplePackage.file("FM-1_917", seed = 14), "Felucca")
        assertTrue(collision.fileName.contains(collision.sha256.take(8)))
        kotlinx.coroutines.runBlocking { library.remove(item) }
        assertTrue(!File(libraryDir(library), ".details/${item.sha256}.json").exists())
    }

    @Test fun reloadPicksUpFilesWrittenElsewhere() {
        val directory = createTempDirectory().toFile()
        val v15 = File(System.getProperty("fm1.v15"))
        val first = FirmwareLibrary(directory, { FileInputStream(v15) }, Dispatchers.Unconfined, { Instant.EPOCH })
        val second = FirmwareLibrary(directory, { FileInputStream(v15) }, Dispatchers.Unconfined, { Instant.EPOCH })
        val item = add(second, SamplePackage.file("FM-1_916"), "Other")
        assertTrue(first.items.none { it.id == item.id })
        kotlinx.coroutines.runBlocking { first.reload() }
        assertTrue(first.items.any { it.id == item.id })
    }

    @Test fun picksThePackagesThatCanFinishALoadersUpdate() {
        val library = library()
        val item = add(library, SamplePackage.file("FM-1_916"), "Felucca")
        assertEquals(listOf(item), finishers(library, Fm1Identity.parse("ota-FM-1_916")!!))
    }

    @Test fun anyPackageCarryingTheStockLoaderCanFinishIt() {
        val library = libraryWithStockLoader()
        val loader = Fm1Identity.parse("ota-FM-1_015")!!
        assertEquals(1, finishers(library, loader).size)
    }

    @Test fun picksThePackageAnArmedLoaderIsWaitingFor() {
        val library = library()
        assertEquals(1, finishers(library, Fm1Identity.parse("ota-FM1C_db1642b2b6fa5c2c")!!).size)
    }

    @Test fun armedLoaderMatchesAnImportBySha() {
        val library = library()
        val item = add(library, SamplePackage.file("FM-1_916"), "Import")
        val loader = Fm1Identity.parse("ota-FM1C_${item.sha256.take(16)}")!!
        assertEquals(listOf(item), finishers(library, loader))
    }

    @Test fun refusesAWrongHash() {
        val library = library()
        val bytes = SamplePackage.file("FM-1_916")
        val entry =
            dev.drewr.fmswitch.model.catalogue.CatalogueEntry(
                "x",
                "X",
                packageURL = "x",
                sha256 = "00",
                size = bytes.size,
            )
        assertTrue(
            kotlin
                .runCatching {
                    kotlinx.coroutines.runBlocking {
                        library.download(entry) { bytes }
                    }
                }.exceptionOrNull() is FirmwareLibrary.Failure.HashMismatch,
        )
    }

    @Test fun refusesAWrongProduct() {
        val library = library()
        val bytes = SamplePackage.file("FM-1_916")
        val entry =
            dev.drewr.fmswitch.model.catalogue.CatalogueEntry(
                "x",
                "X",
                packageURL = "x",
                product = "FM-1_999",
                size = bytes.size,
            )
        assertTrue(
            kotlin
                .runCatching {
                    kotlinx.coroutines.runBlocking {
                        library.download(entry) { bytes }
                    }
                }.exceptionOrNull() is FirmwareLibrary.Failure.ProductMismatch,
        )
    }

    @Test fun refusesAnOfficialEntryThatIsntV15() {
        val library = library()
        val bytes = SamplePackage.file("FM-1_916")
        val entry =
            dev.drewr.fmswitch.model.catalogue.CatalogueEntry(
                "x",
                "X",
                packageURL = "x",
                isOfficial = true,
                size = bytes.size,
            )
        assertTrue(
            kotlin
                .runCatching {
                    kotlinx.coroutines.runBlocking {
                        library.download(entry) { bytes }
                    }
                }.exceptionOrNull() is FirmwareLibrary.Failure.NotOfficial,
        )
    }

    @Test fun savesAndReplacesAnOlderRelease() {
        val library = library()
        val firstBytes = SamplePackage.file("FM-1_916")
        val secondBytes = SamplePackage.file("FM-1_917", seed = 14)
        val first =
            kotlinx.coroutines.runBlocking {
                library.download(
                    dev.drewr.fmswitch.model.catalogue.CatalogueEntry(
                        "same",
                        "Felucca",
                        packageURL = "one",
                        size = firstBytes.size,
                    ),
                ) { firstBytes }
            }
        val second =
            kotlinx.coroutines.runBlocking {
                library.download(
                    dev.drewr.fmswitch.model.catalogue.CatalogueEntry(
                        "same",
                        "Felucca",
                        packageURL = "two",
                        size = secondBytes.size,
                    ),
                ) { secondBytes }
            }
        assertTrue(first.sha256 != second.sha256)
        assertTrue(library.items.any { it.id == second.id })
        assertTrue(library.items.none { it.id == first.id })
    }

    @Test fun savingUsesTheInjectedClock() {
        val v15 = File(System.getProperty("fm1.v15"))
        val instant = Instant.parse("2026-10-31T00:00:00Z")
        val library =
            FirmwareLibrary(
                createTempDirectory().toFile(),
                { FileInputStream(v15) },
                Dispatchers.Unconfined,
            ) { instant }
        assertEquals(instant, add(library, SamplePackage.file("FM-1_916"), "Clock").savedAt)
    }

    @Test fun downloadFailuresKeepTheirReason() {
        val library = library()
        val entry =
            dev.drewr.fmswitch.model.catalogue
                .CatalogueEntry("x", "X", packageURL = "x")
        val failure =
            kotlin
                .runCatching {
                    kotlinx.coroutines.runBlocking { library.download(entry) { error("offline") } }
                }.exceptionOrNull()
        assertEquals("Download failed: offline", failure?.message)
    }

    @Test fun cancellingADownloadIsNotAFailure() {
        val library = library()
        val entry =
            dev.drewr.fmswitch.model.catalogue
                .CatalogueEntry("x", "X", packageURL = "x")
        val result =
            kotlin
                .runCatching {
                    kotlinx.coroutines.runBlocking {
                        library.download(entry) { throw kotlinx.coroutines.CancellationException("stopped") }
                    }
                }.exceptionOrNull()
        assertTrue(result is kotlinx.coroutines.CancellationException)
    }

    @Test fun aSavedCopyNewerThanTheListingIsNotOutdated() {
        assertTrue(!ReleaseVersion.isNewer("1.2", "1.3"))
    }

    private fun libraryDir(library: FirmwareLibrary): File =
        FirmwareLibrary::class.java.getDeclaredField("directory").let {
            it.isAccessible = true
            it.get(library) as File
        }
}
