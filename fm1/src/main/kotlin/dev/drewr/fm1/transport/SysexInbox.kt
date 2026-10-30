// Ported from FM1Kit 0.4.5 Transport/MIDITransport.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

class SysexInbox {
    private val messages = Channel<ByteArray>(CAPACITY, BufferOverflow.DROP_OLDEST)

    fun deliver(sysex: ByteArray) {
        messages.trySend(sysex.copyOf())
    }

    suspend fun next(timeout: Duration): ByteArray? =
        withTimeoutOrNull(timeout) { messages.receiveCatching().getOrNull() }

    fun drain() {
        while (messages.tryReceive().isSuccess) Unit
    }

    fun close() {
        messages.cancel()
    }

    companion object {
        const val CAPACITY = 256
    }
}
