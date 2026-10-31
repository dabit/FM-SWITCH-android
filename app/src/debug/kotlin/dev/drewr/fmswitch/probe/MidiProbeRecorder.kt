package dev.drewr.fmswitch.probe

import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.protocol.Fm1Message
import dev.drewr.fm1.transport.PortDescriptor
import dev.drewr.fm1.transport.PortMatching
import dev.drewr.fm1.transport.SysexFramer
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object MidiProbeRecorder {
    private const val LOG_TAG = "FM1Probe"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val entries = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val sessions = ConcurrentHashMap<Int, CloseableSession>()
    private lateinit var midiManager: MidiManager
    private var started = false

    @Suppress("DEPRECATION")
    fun start(context: Context) {
        if (started) return
        started = true
        midiManager = context.applicationContext.getSystemService(MidiManager::class.java)
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        record("Probe started")
        midiManager.devices.forEach { recordDevice("Present", it) }
    }

    fun log(): List<String> = entries.toList()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun sendHandshake(deviceInfo: MidiDeviceInfo) {
        if (!PortMatching.isProbeable(descriptor(deviceInfo))) {
            record("Skipped id=${deviceInfo.id}: not a matching MIDI device")
            return
        }
        closeSession(deviceInfo.id, "before reopening")
        record("Opening id=${deviceInfo.id} to send handshake")
        midiManager.openDevice(deviceInfo, { device ->
            if (device == null) {
                record("Open failed id=${deviceInfo.id}; check whether Android requested permission")
            } else {
                openHandshakeSession(deviceInfo, device)
            }
        }, mainHandler)
    }

    private fun openHandshakeSession(
        deviceInfo: MidiDeviceInfo,
        device: MidiDevice,
    ) {
        if (deviceInfo.inputPortCount == 0) {
            record("Cannot send id=${deviceInfo.id}: no input ports")
            closeQuietly(device)
            return
        }
        var input: MidiInputPort? = null
        try {
            input = device.openInputPort(0)
            if (input == null) {
                record("Cannot send id=${deviceInfo.id}: input port 0 unavailable")
                closeQuietly(device)
                return
            }
            val output =
                if (deviceInfo.outputPortCount > 0) {
                    device.openOutputPort(0)?.also { port -> port.connect(ProbeReceiver(deviceInfo.id)) }
                } else {
                    null
                }
            val session = CloseableSession(input, output, device)
            sessions.put(deviceInfo.id, session)?.let { previous -> closeSession(previous, deviceInfo.id, "replaced") }
            input.send(Fm1Message.HANDSHAKE_QUERY, 0, Fm1Message.HANDSHAKE_QUERY.size)
            record("Sent handshake id=${deviceInfo.id} input=0")
            if (output == null) record("No output port to receive replies id=${deviceInfo.id}")
        } catch (error: IOException) {
            record("Handshake failed id=${deviceInfo.id}: ${error.message.orEmpty()}")
            closeSession(deviceInfo.id, "after handshake error")
            input?.let(::closeQuietly)
            closeQuietly(device)
        }
    }

    @Suppress("DEPRECATION")
    private fun recordDevice(
        event: String,
        deviceInfo: MidiDeviceInfo,
    ) {
        val properties = deviceInfo.properties
        val ports =
            deviceInfo.ports.joinToString { port ->
                "${port.portNumber}:${portType(port.type)}:${port.name.orEmpty()}"
            }
        val values = properties.keySet().sorted().joinToString { key -> "$key=${properties.get(key)}" }
        val descriptor = descriptor(deviceInfo)
        record(
            "$event id=${deviceInfo.id} type=${deviceInfo.type} inputs=${deviceInfo.inputPortCount} " +
                "outputs=${deviceInfo.outputPortCount} vid=${descriptor.usbVendorId} " +
                "pid=${descriptor.usbProductId} ports=[$ports] properties=[$values] " +
                "probeable=${PortMatching.isProbeable(descriptor)}",
        )
    }

    internal fun descriptor(deviceInfo: MidiDeviceInfo): PortDescriptor {
        val properties = deviceInfo.properties
        val names =
            listOf(
                MidiDeviceInfo.PROPERTY_NAME,
                MidiDeviceInfo.PROPERTY_PRODUCT,
                MidiDeviceInfo.PROPERTY_MANUFACTURER,
            ).mapNotNull(properties::getString) + deviceInfo.ports.mapNotNull { it.name }
        return PortDescriptor(
            id = deviceInfo.id.toString(),
            names = names,
            serialNumber = properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER),
            usbVendorId = usbDevice(properties)?.vendorId,
            usbProductId = usbDevice(properties)?.productId,
        )
    }

    @Suppress("DEPRECATION")
    private fun usbDevice(properties: Bundle): UsbDevice? =
        properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE) as? UsbDevice

    private fun portType(type: Int): String =
        when (type) {
            MidiDeviceInfo.PortInfo.TYPE_INPUT -> "input"
            MidiDeviceInfo.PortInfo.TYPE_OUTPUT -> "output"
            else -> "unknown($type)"
        }

    private fun record(message: String) {
        val line = String.format(Locale.US, "%012d %s", System.nanoTime(), message)
        entries += line
        Log.i(LOG_TAG, line)
        mainHandler.post { listeners.forEach { it() } }
    }

    private val deviceCallback =
        object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) = recordDevice("Added", device)

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                closeSession(device.id, "device removed")
                recordDevice("Removed", device)
            }
        }

    private class ProbeReceiver(
        private val deviceId: Int,
    ) : MidiReceiver() {
        private val framer = SysexFramer()

        override fun onSend(
            data: ByteArray,
            offset: Int,
            count: Int,
            timestamp: Long,
        ) {
            record("Received raw id=$deviceId bytes=$count timestamp=$timestamp")
            framer.feed(data.copyOfRange(offset, offset + count)).forEach { message ->
                val identity = Fm1Identity.fromHandshakeReply(message)
                record("Received frame id=$deviceId bytes=${message.size} identity=${identity ?: "unparsed"}")
            }
        }
    }

    private class CloseableSession(
        private val input: MidiInputPort,
        private val output: MidiOutputPort?,
        private val device: MidiDevice,
    ) : AutoCloseable {
        override fun close() {
            closeQuietly(input)
            output?.let(::closeQuietly)
            closeQuietly(device)
        }
    }

    private fun closeQuietly(closeable: AutoCloseable) {
        try {
            closeable.close()
        } catch (_: IOException) {
            record("Close failed")
        }
    }

    private fun closeSession(
        deviceId: Int,
        reason: String,
    ) {
        sessions.remove(deviceId)?.let { session -> closeSession(session, deviceId, reason) }
    }

    private fun closeSession(
        session: CloseableSession,
        deviceId: Int,
        reason: String,
    ) {
        session.close()
        record("Closed session id=$deviceId: $reason")
    }
}
