package dev.drewr.fmswitch.model.history
import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.UpdateCode
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fmswitch.model.library.StoredFirmware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class FlashHistoryTest {
    private fun item(i: Int) =
        StoredFirmware(
            "$i",
            "N$i",
            "V",
            product = "P$i",
            sha256 = "$i",
            size = 0,
            fileName = "$i",
            source = StoredFirmware.Source.Imported,
            savedAt = Instant.EPOCH,
        )

    @Test fun historyKeepsThirtyNewestFirst() {
        val h = FlashHistory(null, Dispatchers.Unconfined) { Instant.EPOCH }
        runBlocking { (1..31).forEach { h.record(item(it)) } }
        assertEquals(30, h.records.size)
        assertEquals("P31", h.last!!.product)
    }

    @Test fun lastInstallOnlyIfNewestMatches() {
        val h = FlashHistory(null, Dispatchers.Unconfined) { Instant.EPOCH }
        runBlocking { h.record(item(1)) }
        assertEquals(null, h.lastInstall("other"))
        assertEquals("P1", h.lastInstall("P1")?.product)
    }

    @Test fun historySurvivesRestart() {
        val directory = createTempDirectory().toFile()
        val item = item(7)
        runBlocking { FlashHistory(directory, Dispatchers.Unconfined) { Instant.EPOCH }.record(item) }
        val reloaded = FlashHistory(directory, Dispatchers.Unconfined) { Instant.EPOCH }
        runBlocking { reloaded.load() }
        assertEquals("P7", reloaded.lastInstall("P7")?.product)
    }

    @Test fun canBeSavedWithTheFM1() {
        val guide = LeavingGuide("TransitFM1", Fm1Identity.officialV15, UpdateCode.OWN_LOADER, Fm1BootArea.M_BOOTLOADER)
        assertEquals(guide, SavedGuide.from(guide).toGuide())
    }

    @Test fun readsHistorySavedBeforeGuides() {
        val directory = createTempDirectory().toFile()
        java.io
            .File(
                directory,
                "flash-history.json",
            ).writeText("[{\"name\":\"N\",\"version\":\"V\",\"product\":\"P\",\"date\":\"1970-01-01T00:00:00Z\"}]")
        val history = FlashHistory(directory, Dispatchers.Unconfined) { Instant.EPOCH }
        runBlocking { history.load() }
        val record = requireNotNull(history.last)
        assertEquals("N", record.name)
        assertEquals(null, record.guide)
    }

    @Test fun forgetsAGuideItCannotRead() =
        assertEquals(
            null,
            SavedGuide("bad", "FM-1_015", "notKnown", null).toGuide(),
        )
}
