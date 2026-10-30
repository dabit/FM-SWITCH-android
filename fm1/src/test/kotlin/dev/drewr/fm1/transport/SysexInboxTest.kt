package dev.drewr.fm1.transport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SysexInboxTest {
    @Test fun deliversInOrder() =
        runTest {
            val inbox = SysexInbox()
            inbox.deliver(byteArrayOf(1))
            inbox.deliver(byteArrayOf(2))
            assertContentEquals(byteArrayOf(1), inbox.next(kotlin.time.Duration.parse("1s")))
            assertContentEquals(byteArrayOf(2), inbox.next(kotlin.time.Duration.parse("1s")))
        }

    @Test fun nextTimesOutAfterOneSecond() =
        runTest {
            assertNull(SysexInbox().next(kotlin.time.Duration.parse("1s")))
            assertEquals(1000, currentTime)
        }

    @Test fun aWaiterWakesOnArrival() =
        runTest {
            val inbox = SysexInbox()
            val waiting = async { inbox.next(kotlin.time.Duration.parse("5s")) }
            runCurrent()
            inbox.deliver(byteArrayOf(4))
            assertContentEquals(byteArrayOf(4), waiting.await())
        }

    @Test fun closeWakesTheWaiterAndIgnoresLaterSends() =
        runTest {
            val inbox = SysexInbox()
            val waiting = async { inbox.next(kotlin.time.Duration.parse("5s")) }
            runCurrent()
            inbox.close()
            assertNull(waiting.await())
            inbox.deliver(byteArrayOf(5))
            assertNull(inbox.next(kotlin.time.Duration.parse("1s")))
            val queued = SysexInbox()
            queued.deliver(byteArrayOf(6))
            queued.close()
            assertNull(queued.next(kotlin.time.Duration.parse("1s")))
        }

    @Test fun drainEmptiesTheInbox() =
        runTest {
            val inbox = SysexInbox()
            inbox.deliver(byteArrayOf(6))
            inbox.drain()
            assertNull(inbox.next(kotlin.time.Duration.parse("1s")))
        }

    @Test fun cancellingEndsTheWait() =
        runTest {
            val inbox = SysexInbox()
            var returned = false
            val waiting =
                launch {
                    inbox.next(kotlin.time.Duration.parse("10s"))
                    returned = true
                }
            runCurrent()
            waiting.cancelAndJoin()
            assertFalse(returned)
            assertTrue(waiting.isCancelled)
        }

    @Test fun aWaitCancelledBeforeItStartsEndsAtOnce() =
        runTest {
            val inbox = SysexInbox()
            val waiting = launch { inbox.next(kotlin.time.Duration.parse("10s")) }
            waiting.cancel()
            waiting.join()
            assertFalse(waiting.isActive)
        }

    @Test fun keepsTheNewest256() =
        runTest {
            val inbox = SysexInbox()
            repeat(SysexInbox.CAPACITY + 44) { inbox.deliver(byteArrayOf(it.toByte())) }
            assertEquals(44.toByte(), inbox.next(kotlin.time.Duration.parse("1s"))!!.single())
        }
}
