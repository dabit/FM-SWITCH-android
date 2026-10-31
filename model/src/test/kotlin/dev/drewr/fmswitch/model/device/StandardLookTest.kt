package dev.drewr.fmswitch.model.device

import dev.drewr.fmswitch.model.library.StoredFirmware
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardLookTest {
    private val item =
        StoredFirmware(
            "one",
            "One",
            "1",
            null,
            "FM-1_901",
            "one",
            1,
            "one.fwsc",
            StoredFirmware.Source.Imported,
            null,
            null,
            null,
            Instant.EPOCH,
        )

    private fun job(
        phase: FlashPhase,
        index: Int = 0,
        count: Int = 1,
    ) = FlashJob(
        List(count) {
            item
        },
        index = index,
        phase = phase,
    )

    @Test fun progressRunsThroughTheStagesInOrder() {
        val seen =
            listOf(
                FlashPhase.Preparing,
                FlashPhase.Staging(40),
                FlashPhase.WaitingForLoader,
                FlashPhase.Writing(.5),
                FlashPhase.Verifying(.5),
                FlashPhase.Rebooting,
                FlashPhase.Succeeded("FM-1_901"),
            ).map {
                InstallProgress(job(it))
            }
        assertEquals(seen.map { it.fraction }.sorted(), seen.map { it.fraction })
        assertEquals(
            listOf(
                InstallProgress.Stage.GettingReady,
                InstallProgress.Stage.GettingReady,
                InstallProgress.Stage.GettingReady,
                InstallProgress.Stage.Installing,
                InstallProgress.Stage.Checking,
                InstallProgress.Stage.Restarting,
                InstallProgress.Stage.Restarting,
            ),
            seen.map {
                it.stage
            },
        )
    }

    @Test fun progressSpreadsAcrossTheSteps() {
        assertEquals(.5, InstallProgress(job(FlashPhase.Succeeded(""), count = 2)).fraction)
        assertTrue(InstallProgress(job(FlashPhase.Writing(0.0), 1, 2)).fraction > .5)
    }

    @Test fun trackerNeverGoesBackwards() {
        val tracker = InstallProgress.Tracker()
        tracker.update(job(FlashPhase.Writing(.6)))
        val reached = tracker.current.fraction
        tracker.update(job(FlashPhase.Preparing))
        tracker.update(job(FlashPhase.Writing(.1)))
        tracker.update(job(FlashPhase.Failed("Unplugged", true)))
        assertEquals(reached, tracker.current.fraction)
    }

    @Test fun summarySaysWhatTheFm1IsDoing() {
        assertEquals("Not connected", DeviceSummary.from(null).statusLine)
        assertEquals(
            "Connected, running SLOOP 2.7.1",
            DeviceSummary
                .from(
                    dev.drewr.fm1.protocol.Fm1Identity
                        .parse("FM-1_900"),
                    "SLOOP 2.7.1",
                ).statusLine,
        )
        assertEquals(
            "Connected, reporting FM-1_900",
            DeviceSummary
                .from(
                    dev.drewr.fm1.protocol.Fm1Identity
                        .parse("FM-1_900"),
                ).statusLine,
        )
        assertEquals(
            "V15",
            DeviceSummary
                .from(
                    dev.drewr.fm1.protocol.Fm1Identity.officialV15,
                    "the official firmware (V15)",
                ).screenTitle,
        )
        val identity =
            dev.drewr.fm1.protocol.Fm1Identity
                .parse("FM-1_900")!!
        assertEquals("Last 2", DeviceSummary.resolvedName(identity, "Last" to "2", listOf("Listed" to "1")))
        assertEquals("Listed 1", DeviceSummary.resolvedName(identity, catalogueMatches = listOf("Listed" to "1")))
        assertEquals(null, DeviceSummary.resolvedName(identity, catalogueMatches = listOf("One" to "1", "Two" to "2")))
        assertEquals(
            "the official firmware (V15)",
            DeviceSummary.resolvedName(dev.drewr.fm1.protocol.Fm1Identity.officialV15),
        )
    }

    @Test
    fun theHomeScreenNamesTheFirmwareFromHistoryThenTheList() {
        val identity =
            dev.drewr.fm1.protocol.Fm1Identity
                .parse("FM-1_901")!!
        val history =
            dev.drewr.fmswitch.model.history.FlashHistory(null, kotlinx.coroutines.Dispatchers.Unconfined) {
                Instant.EPOCH
            }
        val listed =
            listOf(
                dev.drewr.fmswitch.model.catalogue
                    .CatalogueEntry("a", "Listed", version = "3", product = "FM-1_901"),
            )
        assertEquals("Connected, running Listed 3", DeviceSummary.from(identity, history, listed).statusLine)
        kotlinx.coroutines.runBlocking { history.record(item) }
        assertEquals("Connected, running One 1", DeviceSummary.from(identity, history, listed).statusLine)
        assertEquals(
            "Connected, reporting FM-1_901",
            DeviceSummary
                .from(
                    identity,
                    dev.drewr.fmswitch.model.history.FlashHistory(null, kotlinx.coroutines.Dispatchers.Unconfined) {
                        Instant.EPOCH
                    },
                    listed + listed.single().copy(id = "b", name = "Other"),
                ).statusLine,
        )
    }

    @Test
    fun onlyOneFirmwareIsMarkedAsOnTheFm1() {
        val identity =
            dev.drewr.fm1.protocol.Fm1Identity
                .parse("FM-1_901")!!
        val history =
            dev.drewr.fmswitch.model.history.FlashHistory(null, kotlinx.coroutines.Dispatchers.Unconfined) {
                Instant.EPOCH
            }
        val a =
            dev.drewr.fmswitch.model.catalogue
                .CatalogueEntry("a", "One", product = "FM-1_901")
        val b = a.copy(id = "b", name = "Two")
        assertTrue(DeviceSummary.isOnFm1(a, identity, history, listOf(a)))
        assertEquals(false, DeviceSummary.isOnFm1(a, identity, history, listOf(a, b)))
        assertEquals(false, DeviceSummary.isOnFm1(b, identity, history, listOf(a, b)))
        kotlinx.coroutines.runBlocking { history.record(item) }
        assertTrue(DeviceSummary.isOnFm1(a, identity, history, listOf(a, b)))
        assertEquals(false, DeviceSummary.isOnFm1(b, identity, history, listOf(a, b)))
        assertEquals(false, DeviceSummary.isOnFm1(a, null, history, listOf(a)))
    }
}
