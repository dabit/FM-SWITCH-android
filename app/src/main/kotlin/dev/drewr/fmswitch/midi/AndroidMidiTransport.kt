package dev.drewr.fmswitch.midi

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.drewr.fm1.transport.MidiConnection
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.MidiTransportException
import dev.drewr.fm1.transport.PortDescriptor
import dev.drewr.fm1.transport.PortMatching
import dev.drewr.fm1.transport.SysexFramer
import dev.drewr.fm1.transport.SysexInbox
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

class AndroidMidiTransport(
    private val context: Context,
) : MidiTransport {
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _deviceLog = MutableSharedFlow<String>(extraBufferCapacity = DEVICE_LOG_BUFFER)

    val deviceLog: Flow<String> = _deviceLog

    override suspend fun ports(): List<PortDescriptor> =
        managerOrNull()
            ?.let(::devices)
            .orEmpty()
            .asSequence()
            .filter(::isUsbDeviceWithPorts)
            .map(::descriptor)
            .filter(PortMatching::isProbeable)
            .toList()

    override suspend fun open(port: PortDescriptor): MidiConnection {
        val manager = managerOrThrow()
        val info =
            devices(manager).firstOrNull { it.id.toString() == port.id }
                ?: throw MidiTransportException.PortGone
        if (!PortMatching.isProbeable(descriptor(info))) throw MidiTransportException.PortGone

        val device = openDevice(manager, info)
        val input =
            device.openInputPort(0) ?: run {
                device.close()
                throw MidiTransportException.PortGone
            }
        val output =
            device.openOutputPort(0) ?: run {
                input.close()
                device.close()
                throw MidiTransportException.PortGone
            }
        return AndroidMidiConnection(manager, device, input, output, port, mainHandler, ::log)
    }

    override fun changes(): Flow<Unit> =
        callbackFlow {
            val manager = managerOrNull()
            if (manager == null) {
                close()
                return@callbackFlow
            }
            val callback =
                object : MidiManager.DeviceCallback() {
                    override fun onDeviceAdded(device: MidiDeviceInfo) {
                        logDeviceEvent("added", device)
                        trySend(Unit)
                    }

                    override fun onDeviceRemoved(device: MidiDeviceInfo) {
                        logDeviceEvent("removed", device)
                        trySend(Unit)
                    }

                    override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
                        val line = "MIDI device status changed: ${status.deviceInfo.id}"
                        log(line)
                        trySend(Unit)
                    }
                }
            registerDeviceCallback(manager, callback, mainHandler)
            awaitClose { manager.unregisterDeviceCallback(callback) }
        }

    private suspend fun openDevice(
        manager: MidiManager,
        info: MidiDeviceInfo,
    ): MidiDevice =
        suspendCancellableCoroutine { continuation ->
            manager.openDevice(
                info,
                { device ->
                    if (device == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(MidiTransportException.PortGone)
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(device)
                    } else {
                        device.close()
                    }
                },
                mainHandler,
            )
        }

    private fun descriptor(info: MidiDeviceInfo): PortDescriptor {
        val properties = info.properties
        val usbDevice = usbDevice(properties)
        return PortDescriptor(
            id = info.id.toString(),
            names =
                buildList {
                    listOf(
                        MidiDeviceInfo.PROPERTY_NAME,
                        MidiDeviceInfo.PROPERTY_PRODUCT,
                        MidiDeviceInfo.PROPERTY_MANUFACTURER,
                    ).mapNotNullTo(this) { properties.getString(it) }
                    info.ports.mapTo(this) { it.name }
                },
            serialNumber = properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER),
            usbVendorId = usbDevice?.vendorId,
            usbProductId = usbDevice?.productId,
        )
    }

    private fun isUsbDeviceWithPorts(info: MidiDeviceInfo) =
        info.type == MidiDeviceInfo.TYPE_USB && info.inputPortCount > 0 && info.outputPortCount > 0

    private fun managerOrNull(): MidiManager? =
        midiManager.takeIf { context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI) }

    private fun managerOrThrow(): MidiManager =
        managerOrNull()
            ?: throw MidiTransportException.Unavailable("This Android device does not support MIDI.")

    @Suppress("DEPRECATION") // The transport-specific lookup is API 33; minSdk is 26.
    private fun devices(manager: MidiManager): List<MidiDeviceInfo> = manager.devices.toList()

    private fun usbDevice(properties: Bundle): UsbDevice? =
        if (SDK_INT >= 33) {
            properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE, UsbDevice::class.java)
        } else {
            usbDeviceBeforeApi33(properties)
        }

    @Suppress("DEPRECATION") // The typed Bundle overload is API 33; minSdk is 26.
    private fun usbDeviceBeforeApi33(properties: Bundle): UsbDevice? =
        properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE)

    private fun logDeviceEvent(
        event: String,
        device: MidiDeviceInfo,
    ) = log("MIDI device $event: ${descriptor(device).id} (${descriptor(device).names.joinToString()})")

    private fun log(line: String) {
        Log.d(LOG_TAG, line)
        _deviceLog.tryEmit(line)
    }

    companion object {
        private const val LOG_TAG = "FM1Midi"
        private const val DEVICE_LOG_BUFFER = 64

        fun create(context: Context): MidiTransport =
            if (
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI) &&
                context.getSystemService(Context.MIDI_SERVICE) is MidiManager
            ) {
                AndroidMidiTransport(context)
            } else {
                UnavailableTransport("This Android device does not support MIDI.")
            }

        @Suppress("DEPRECATION") // The executor overload is API 33; minSdk is 26.
        fun registerDeviceCallback(
            manager: MidiManager,
            callback: MidiManager.DeviceCallback,
            handler: Handler,
        ) {
            manager.registerDeviceCallback(callback, handler)
        }
    }
}

private class AndroidMidiConnection(
    private val midiManager: MidiManager,
    private val device: MidiDevice,
    private val input: MidiInputPort,
    private val output: MidiOutputPort,
    override val port: PortDescriptor,
    handler: Handler,
    private val log: (String) -> Unit,
) : MidiConnection {
    private val inbox = SysexInbox()
    private val framer = SysexFramer()

    @Volatile
    private var lost = false
    private val callback =
        object : MidiManager.DeviceCallback() {
            override fun onDeviceRemoved(removed: MidiDeviceInfo) {
                if (removed.id.toString() == port.id) {
                    lost = true
                    log("MIDI device removed: ${removed.id}")
                }
            }
        }
    private val receiver =
        object : MidiReceiver() {
            override fun onSend(
                message: ByteArray,
                offset: Int,
                count: Int,
                timestamp: Long,
            ) {
                message.copyOfRange(offset, offset + count).let(framer::feed).forEach(inbox::deliver)
            }
        }

    init {
        output.connect(receiver)
        AndroidMidiTransport.registerDeviceCallback(midiManager, callback, handler)
    }

    override val isLost: Boolean
        get() = lost || devices().none { it.id.toString() == port.id }

    override fun send(sysex: ByteArray) {
        if (isLost) throw MidiTransportException.PortGone
        try {
            input.send(sysex, 0, sysex.size)
        } catch (error: Exception) {
            throw MidiTransportException.SendFailed(error.message ?: "MIDI send failed.")
        }
    }

    override suspend fun receive(timeout: Duration): ByteArray? = inbox.next(timeout)

    override fun drain() = inbox.drain()

    override fun close() {
        quietly("MIDI callback") { midiManager.unregisterDeviceCallback(callback) }
        inbox.close()
        quietly("MIDI output disconnect") { output.disconnect(receiver) }
        quietly("MIDI output port") { output.close() }
        quietly("MIDI input port") { input.close() }
        quietly("MIDI device") { device.close() }
    }

    @Suppress("DEPRECATION") // The transport-specific lookup is API 33; minSdk is 26.
    private fun devices(): List<MidiDeviceInfo> = midiManager.devices.toList()

    private fun quietly(
        resource: String,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (error: IOException) {
            log("Couldn't close $resource: ${error.message}")
        } catch (error: IllegalStateException) {
            log("Couldn't close $resource: ${error.message}")
        }
    }
}
