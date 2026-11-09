// Ported from FM1Kit 0.6.0 Tests/FM1KitTests/PackageCheckTests.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import kotlin.time.Duration

class ScriptedTransport(
    identity: Fm1Identity,
    private val script: List<ByteArray>,
    flashChipId: Int = 0,
) : MidiTransport {
    val connection = ScriptedConnection(identity, script, flashChipId)

    override suspend fun ports(): List<PortDescriptor> = listOf(connection.port)

    override suspend fun open(port: PortDescriptor): MidiConnection = connection

    override fun changes() = kotlinx.coroutines.flow.emptyFlow<Unit>()
}

class ScriptedConnection(
    private val identity: Fm1Identity,
    private val script: List<ByteArray>,
    private val flashChipId: Int,
) : MidiConnection {
    override val port = PortDescriptor("scripted", listOf("FM-1 Midi"))
    private val inbox = SysexInbox()
    val sent = mutableListOf<ByteArray>()
    override val isLost = false

    override fun send(sysex: ByteArray) {
        sent += sysex.copyOf()
        when {
            sysex.contentEquals(Fm1Message.HANDSHAKE_QUERY) -> inbox.deliver(identity.handshakeReply(flashChipId))
            sysex.contentEquals(Fm1Message.upgradeCommand) -> script.forEach { inbox.deliver(it) }
        }
    }

    override suspend fun receive(timeout: Duration): ByteArray? = inbox.next(timeout)

    override fun drain() = inbox.drain()

    override fun close() = inbox.close()
}
