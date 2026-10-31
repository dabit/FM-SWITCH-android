package dev.drewr.fmswitch.model.history
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
}
