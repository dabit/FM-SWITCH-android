// Ported from FM1Kit 0.4.5 Transport/MIDITransport.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

import kotlin.time.Duration

interface MidiConnection {
    val port: PortDescriptor
    val isLost: Boolean

    fun send(sysex: ByteArray)

    suspend fun receive(timeout: Duration): ByteArray?

    fun drain()

    fun close()
}
