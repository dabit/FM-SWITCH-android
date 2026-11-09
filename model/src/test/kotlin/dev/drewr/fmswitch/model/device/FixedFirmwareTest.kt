package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixedFirmwareTest {
    private val bytes = SamplePackage.firmwareWithCode("FELUCCA-LOADER-1..AC79BLID".encodeToByteArray(), "FM-1_916")

    private fun library() =
        FirmwareLibrary(createTempDirectory().toFile(), {
            error("not used")
        }, Dispatchers.Unconfined) { Instant.EPOCH }

    private val entry = CatalogueEntry("midimon", "MIDIMON", packageURL = "https://example.test/midimon")
    private val loom = CatalogueEntry("loom", "LOOM-8", packageURL = "https://example.test/loom")

    @Test fun findsASavedFixedCopyWithoutFetching() =
        runBlocking {
            val library = library()
            val unfixed = library.download(entry) { SamplePackage.file("FM-1_916") }
            val fixed = library.download(loom) { bytes }
            val saved = FixedFirmware.find(library, listOf(entry)) { error("should not fetch") }
            assertEquals(fixed.sha256, saved.sha256)
            assertTrue(unfixed.sha256 != saved.sha256)
        }

    @Test fun downloadsMidimonWhenNoneIsSaved() =
        runBlocking {
            val library = library()
            assertEquals("midimon", FixedFirmware.find(library, listOf(entry)) { bytes }.catalogueID)
        }

    @Test fun refusesADownloadWithoutTheFixedCheck() {
        assertFailsWith<FixedFirmware.Failure.NotFixed> {
            runBlocking { FixedFirmware.find(library(), listOf(entry)) { SamplePackage.file("FM-1_916") } }
        }
    }

    @Test fun saysWhenMidimonIsntListed() {
        assertFailsWith<FixedFirmware.Failure.NotListed> {
            runBlocking { FixedFirmware.find(library(), emptyList()) { bytes } }
        }
    }
}
