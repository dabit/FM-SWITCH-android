// Ported from FM-SWITCH App/Model/UnavailableTransport.swift.
package dev.drewr.fmswitch.midi

import dev.drewr.fm1.transport.MidiConnection
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.MidiTransportException
import dev.drewr.fm1.transport.PortDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class UnavailableTransport(
    private val reason: String,
) : MidiTransport {
    override suspend fun ports(): List<PortDescriptor> = emptyList()

    override suspend fun open(port: PortDescriptor): MidiConnection = throw MidiTransportException.Unavailable(reason)

    override fun changes(): Flow<Unit> = emptyFlow()
}
