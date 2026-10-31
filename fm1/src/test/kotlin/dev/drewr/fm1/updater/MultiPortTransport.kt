// Ported from FM1Kit 0.4.5 Tests/FM1KitTests/UpdaterTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.transport.MidiConnection
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.PortDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class MultiPortTransport(
    private vararg val transports: MidiTransport,
) : MidiTransport {
    val opened = mutableSetOf<String>()
    val sent = mutableMapOf<String, MutableList<ByteArray>>()

    override suspend fun ports(): List<PortDescriptor> = transports.flatMap { it.ports() }

    override suspend fun open(port: PortDescriptor): MidiConnection {
        val connection = transports.first { transport -> transport.ports().any { it.id == port.id } }.open(port)
        opened += port.id
        return object : MidiConnection by connection {
            override fun send(sysex: ByteArray) {
                sent.getOrPut(port.id) { mutableListOf() } += sysex.copyOf()
                connection.send(sysex)
            }
        }
    }

    override fun changes(): Flow<Unit> = emptyFlow()
}
