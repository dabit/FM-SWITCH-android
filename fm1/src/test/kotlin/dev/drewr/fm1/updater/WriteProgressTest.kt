// Ported from FM1Kit 0.6.0 Tests/FM1KitTests/WriteProgressTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.protocol.ReadRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WriteProgressTest {
    private fun progress() = WriteProgress(0 until 64 * 512)

    private fun read(
        block: Int,
        length: Int = 512,
    ) = ReadRequest(address = (block * 512).toLong(), length = length)

    private fun fraction(event: UpdateEvent) =
        when (event) {
            is UpdateEvent.Writing -> event.fraction
            is UpdateEvent.Checking -> event.fraction
            else -> error("progress")
        }

    @Test fun aForwardsPassRisesSteadilyToTheEnd() {
        val p = progress()
        var last = 0.0
        repeat(64) {
            val now = fraction(p.record(read(it)))
            assertTrue(now >= last)
            last = now
        }
        assertEquals(1.0, last)
    }

    @Test fun aBackwardsPassRisesSteadilyRatherThanJumping() {
        val p = progress()
        var last = 0.0
        for (i in 63 downTo 0) {
            val now = fraction(p.record(read(i)))
            assertTrue(now >= last)
            last = now
        }
        assertEquals(1.0, last)
    }

    @Test fun headerRereadsAtTheStartAreStillWriting() {
        val p = progress()
        repeat(31) { p.record(read(it)) }
        repeat(3) { p.record(read(0)) }
        assertTrue(p.record(read(1)) is UpdateEvent.Writing)
    }

    @Test fun shortRereadsAtTheEndAreStillWriting() {
        val p = progress()
        repeat(64) { p.record(read(it)) }
        repeat(3) { p.record(read(63, 12)) }
        assertTrue(p.record(read(63, 12)) is UpdateEvent.Writing)
    }

    @Test fun aSecondPassIsReportedAsChecking() {
        val p = progress()
        repeat(64) { p.record(read(it)) }
        repeat(3) { p.record(read(it)) }
        assertTrue(p.record(read(3)) is UpdateEvent.Checking)
    }

    @Test fun aRereadAtTheEndOfAPassIsStillWriting() {
        val p = progress()
        repeat(64) { p.record(read(it)) }
        assertTrue(p.record(read(63)) is UpdateEvent.Writing)
    }

    @Test fun newGroundDuringCheckingGoesBackToWriting() {
        val p = progress()
        repeat(63) { p.record(read(it)) }
        repeat(3) { p.record(read(it)) }
        assertTrue(p.record(read(63)) is UpdateEvent.Writing)
    }

    @Test fun aLoaderThatSkipsPartOfTheImageEndsShortOfOne() {
        val p = progress()
        repeat(32) { p.record(read(it)) }
        assertTrue(fraction(p.record(read(31))) < 1.0)
    }

    @Test fun aShortFinalReadIsCounted() {
        val p = WriteProgress(0 until 513)
        p.record(ReadRequest(address = 0, length = 512))
        assertEquals(1.0, fraction(p.record(ReadRequest(address = 512, length = 1))))
    }

    @Test fun countsNearlyAllOfTheApplicationAreaAsNearlyWritten() {
        val p = progress()
        repeat(60) { p.record(read(it)) }
        assertTrue(!p.isNearlyWritten)
        p.record(read(60))
        assertTrue(p.isNearlyWritten)
    }

    @Test fun aSecondPassCountsOnlyOnceItIsNearlyDone() {
        val p = progress()
        repeat(64) { p.record(read(it)) }
        repeat(3) { p.record(read(it)) }
        repeat(57) { p.record(read(it + 3)) }
        assertTrue(!p.isNearlyWritten)
        p.record(read(60))
        assertTrue(p.isNearlyWritten)
    }

    @Test fun aBackwardsLoaderReportsSteadyProgress() = aBackwardsPassRisesSteadilyRatherThanJumping()

    @Test fun aCheckingLoaderReportsCheckingAfterWriting() = aSecondPassIsReportedAsChecking()

    @Test fun readsOutsideTheRegionLeaveProgressUnchanged() {
        val p = WriteProgress(100 until 200)
        p.record(ReadRequest(address = 100, length = 100))
        assertEquals(1.0, fraction(p.record(ReadRequest(address = 0, length = 50))))
    }

    @Test fun readsAcrossTheRegionCountOnlyTheirInsidePart() {
        val p = WriteProgress(100 until 200)
        assertEquals(.5, fraction(p.record(ReadRequest(address = 50, length = 100))))
    }
}
