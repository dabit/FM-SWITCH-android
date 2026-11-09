// Ported from FM1Kit 0.6.0 Updater/WriteProgress.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.protocol.ReadRequest
import java.util.BitSet
import kotlin.math.max
import kotlin.math.min

class WriteProgress(
    private val region: IntRange,
) {
    private val regionStart = region.first
    private val regionEnd = if (region.isEmpty()) region.first else region.last + 1
    private val written = BitSet(max(0, regionEnd - regionStart))
    private val checked = BitSet(max(0, regionEnd - regionStart))
    private val rereads = mutableListOf<IntRange>()
    private var requests = 0
    private var isChecking = false
    private var lastEvent: UpdateEvent? = null

    fun record(request: ReadRequest): UpdateEvent {
        requests += 1
        val requestedStart = request.address.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val requestedEndLong = request.address + request.length.toLong()
        val requestedEnd = requestedEndLong.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val start = max(requestedStart, regionStart)
        val end = min(requestedEnd, regionEnd)
        if (start >= end) return repeatedEvent()
        val relativeStart = start - regionStart
        val relativeEnd = end - regionStart
        val isNew = written.nextClearBit(relativeStart) < relativeEnd
        if (isChecking && isNew) {
            isChecking = false
            checked.clear()
            requests = 1
        }
        if (isChecking) {
            checked.set(relativeStart, relativeEnd)
            return report(checkingEvent())
        }
        val mightBeChecking =
            !isNew && request.length == Fm1Message.maximumReadLength && writtenFraction() >= CHECKING_THRESHOLD
        if (mightBeChecking) rereads += start until end else rereads.clear()
        if (rereads.size >= REREADS_BEFORE_CHECKING) {
            isChecking = true
            requests = rereads.size
            rereads.forEach { checked.set(it.first - regionStart, it.last + 1 - regionStart) }
            rereads.clear()
            return report(checkingEvent())
        }
        written.set(relativeStart, relativeEnd)
        return report(UpdateEvent.Writing(writtenFraction(), requests))
    }

    private fun writtenFraction(): Double =
        if (regionStart >=
            regionEnd
        ) {
            1.0
        } else {
            min(written.cardinality().toDouble() / (regionEnd - regionStart), 1.0)
        }

    private fun checkingEvent() =
        UpdateEvent.Checking(
            min(checked.cardinality().toDouble() / written.cardinality().coerceAtLeast(1), 1.0),
            requests,
        )

    /** True when nearly all of the pass that writes flash has been read. */
    val isNearlyWritten: Boolean
        get() = (if (isChecking) checkedFraction() else writtenFraction()) >= NEARLY_WRITTEN_THRESHOLD

    private fun checkedFraction(): Double =
        if (written.isEmpty) 0.0 else min(checked.cardinality().toDouble() / written.cardinality(), 1.0)

    private fun repeatedEvent(): UpdateEvent =
        when (val event = lastEvent) {
            is UpdateEvent.Checking -> UpdateEvent.Checking(event.fraction, requests)
            is UpdateEvent.Writing -> UpdateEvent.Writing(event.fraction, requests)
            else -> UpdateEvent.Writing(0.0, requests)
        }

    private fun report(event: UpdateEvent): UpdateEvent {
        lastEvent = event
        return event
    }

    companion object {
        const val CHECKING_THRESHOLD = 0.5
        const val REREADS_BEFORE_CHECKING = 3
        const val NEARLY_WRITTEN_THRESHOLD = 0.95
    }
}
