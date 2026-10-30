// Ported from FM1Kit 0.4.5 Transport/MIDITransport.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

import kotlinx.coroutines.flow.Flow

interface MidiTransport {
    suspend fun ports(): List<PortDescriptor>

    suspend fun open(port: PortDescriptor): MidiConnection

    fun changes(): Flow<Unit>
}

sealed class MidiTransportException(
    message: String,
) : IllegalStateException(message) {
    data object PortGone : MidiTransportException("The MIDI port has gone.")

    data class Unavailable(
        val reason: String,
    ) : MidiTransportException("MIDI isn't available: $reason.")

    data class SendFailed(
        val reason: String,
    ) : MidiTransportException("Sending MIDI failed: $reason.")
}
