package dev.drewr.fmswitch.model.library

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun download(
        library: FirmwareLibrary,
        entry: CatalogueEntry,
        bytes: ByteArray,
    ) = kotlinx.coroutines.runBlocking { library.download(entry) { bytes } }

    private fun library(officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official): FirmwareLibrary {
        val f = File(System.getProperty("fm1.v15"))
        return FirmwareLibrary(
            createTempDirectory().toFile(),
            { FileInputStream(f) },
            Dispatchers.Unconfined,
            now = { Instant.EPOCH },
            officialOf = officialOf,
        ).also { kotlinx.coroutines.runBlocking { it.load() } }
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
        assertEquals("V15", l.official!!.version)
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

    @Test fun refusesAnOfficialEntryThatIsntV15OrV16() {
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
        val failure =
            kotlin
                .runCatching {
                    kotlinx.coroutines.runBlocking { library.download(entry) { bytes } }
                }.exceptionOrNull()
        assertTrue(failure is FirmwareLibrary.Failure.NotOfficial)
        assertTrue(failure?.message?.contains("V15 or V16") == true)
    }

    @Test fun keepsEachReleaseItSaves() {
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
        assertTrue(library.items.any { it.id == first.id })
        assertEquals(
            2,
            library
                .savedCopies(
                    CatalogueEntry("same", "Felucca", packageURL = "two"),
                ).size,
        )
    }

    @Test fun otherBuildNeedsTheSameVersion() {
        val oldLibrary = library()
        val old = SamplePackage.file("FM-1_916")
        download(
            oldLibrary,
            CatalogueEntry("same", "Felucca", version = "1.0", packageURL = "one", size = old.size),
            old,
        )
        val newer = CatalogueEntry("same", "Felucca", version = "1.1", packageURL = "two")
        assertTrue(oldLibrary.outdated(newer) != null)
        assertEquals(null, oldLibrary.otherBuild(newer))
        assertFalse(oldLibrary.isSaved(newer))

        val currentLibrary = library()
        val current = SamplePackage.file("FM-1_917", seed = 14)
        download(
            currentLibrary,
            CatalogueEntry("same", "Felucca", version = "1.1", packageURL = "one", size = current.size),
            current,
        )
        assertTrue(currentLibrary.otherBuild(newer) != null)
        assertTrue(currentLibrary.isSaved(newer))
    }

    @Test fun downloadsTheOfficialEntryCheckingItsHashNotTheList() {
        val bytes = SamplePackage.file("FM-1_016")
        val library =
            library { packageFile ->
                if (packageFile.product == "FM-1_016") OfficialFirmware.V16 else OfficialFirmware.official(packageFile)
            }
        val item =
            download(
                library,
                CatalogueEntry(
                    "official",
                    "M-VAVE",
                    packageURL = "v16",
                    product = "FM-1_999",
                    size = bytes.size + 1,
                    sha256 = "00",
                    isOfficial = true,
                ),
                bytes,
            )
        assertEquals(StoredFirmware.Source.Official, item.source)
        assertEquals("FM-1 V16", item.name)
        assertEquals("Official", item.version)
        assertEquals(FwscPackage(bytes).sha256, item.sha256)
        assertTrue(File(libraryDir(library), item.fileName).isFile)
        assertEquals(OfficialFirmware.V15.sha256, library.official!!.sha256)
    }

    @Test fun downloadingV15FromTheListReturnsTheBuiltInCopy() {
        val library = library()
        val bytes = File(System.getProperty("fm1.v15")).readBytes()
        val item =
            download(
                library,
                CatalogueEntry("official", "M-VAVE", packageURL = "v15", isOfficial = true),
                bytes,
            )
        assertEquals(library.official, item)
        assertTrue(libraryDir(library).listFiles()?.none { it.extension == "fwsc" } == true)
    }

    @Test fun officialPageOffersBundledV15WithoutCountingItAsSaved() {
        val library = library()
        val official =
            CatalogueEntry(
                "mvave-official",
                "M-VAVE official firmware",
                version = "V16",
                isOfficial = true,
            )

        assertEquals(listOf(library.official), library.pageCopies(official))
        assertEquals("V15", library.pageCopies(official).single().version)
        assertTrue(library.savedCopies(official).isEmpty())
        assertFalse(library.isSaved(official))
    }

    @Test fun officialPageVersionLabelsNameBothOfficialReleases() {
        val entry =
            CatalogueEntry(
                "mvave-official",
                "M-VAVE official firmware",
                version = "V16",
                packageURL = "v16",
                isOfficial = true,
            )
        val v15 = library()
        assertEquals("V15", v15.pageVersionLabel(entry, v15.official))

        val v16 =
            library { packageFile ->
                if (packageFile.product == "FM-1_016") OfficialFirmware.V16 else OfficialFirmware.official(packageFile)
            }
        val savedV16 = download(v16, entry, SamplePackage.file("FM-1_016"))
        assertEquals("Official", savedV16.version)
        assertEquals("V16", v16.pageVersionLabel(entry, savedV16))
        kotlinx.coroutines.runBlocking { v16.reload() }
        assertEquals("V16", v16.pageVersionLabel(entry, v16.stored(entry)))
        assertEquals("V16", v16.pageVersionLabel(entry, null))
    }

    @Test fun pageQueriesUseTheProvidedItemsSnapshot() {
        val library = library()
        val entry = CatalogueEntry("same", "Felucca", packageURL = "one")
        val saved = download(library, entry, SamplePackage.file("FM-1_916"))

        assertEquals(saved, library.stored(entry, listOf(saved)))
        assertEquals(listOf(saved), library.savedCopies(entry, listOf(saved)))
        assertEquals(null, library.stored(entry, emptyList()))
        assertTrue(library.savedCopies(entry, emptyList()).isEmpty())
    }

    @Test fun addRecognisesV16ByHash() {
        val bytes = SamplePackage.file("FM-1_016")
        val library =
            library { packageFile ->
                if (packageFile.product == "FM-1_016") OfficialFirmware.V16 else OfficialFirmware.official(packageFile)
            }
        val item = add(library, bytes, "Not used")
        assertEquals(StoredFirmware.Source.Official, item.source)
        assertEquals("FM-1 V16", item.name)
        assertTrue(File(libraryDir(library), item.fileName).isFile)
    }

    @Test fun aStockLoaderReportingV16CanBeFinishedByV15() {
        val library = libraryWithStockLoader()
        val loader = Fm1Identity.parse("ota-FM-1_016")!!
        assertEquals(1, finishers(library, loader).size)
    }

    @Test fun aCustomLoaderStillMatchesOnlyItsOwnPackage() {
        val library = libraryWithStockLoader()
        val item = add(library, SamplePackage.file("FM-1_916"), "Felucca")
        val loader = Fm1Identity.parse("ota-FM-1_916")!!
        assertEquals(listOf(item), finishers(library, loader))
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
