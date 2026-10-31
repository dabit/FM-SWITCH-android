// Ported from FM-SWITCH App/FMSwitchApp.swift.
package dev.drewr.fmswitch

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import dev.drewr.fm1.transport.MidiTransport
import dev.drewr.fm1.transport.SimulatedFm1
import dev.drewr.fmswitch.data.DataStoreAppSettings
import dev.drewr.fmswitch.data.HttpFetch
import dev.drewr.fmswitch.midi.AndroidMidiTransport
import dev.drewr.fmswitch.model.catalogue.CatalogueStore
import dev.drewr.fmswitch.model.catalogue.PublishedList
import dev.drewr.fmswitch.model.device.DeviceController
import dev.drewr.fmswitch.model.history.FlashHistory
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.settings.TransportKind
import dev.drewr.fmswitch.service.FlashService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

val LocalAppModel =
    staticCompositionLocalOf<AppModel> {
        error("AppModel was not provided.")
    }

sealed interface RequestedPage {
    data class Firmware(
        val id: String,
    ) : RequestedPage

    data object Settings : RequestedPage
}

class AppModel private constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    val settings: DataStoreAppSettings,
) {
    val catalogue =
        CatalogueStore(
            File(context.filesDir, "catalogue"),
            PublishedList(HttpFetch(Dispatchers.IO)::invoke),
            Dispatchers.IO,
            Instant::now,
        )
    val library =
        FirmwareLibrary(
            File(context.filesDir, "firmware"),
            { context.assets.open("firmware/FM-1_015.fwsc") },
            Dispatchers.IO,
            Instant::now,
        )
    val history = FlashHistory(File(context.filesDir, "history"), Dispatchers.IO, Instant::now)
    val device = DeviceController(library, history, settings, scope, ::createTransport)

    private val mutableRequestedPage = MutableStateFlow<RequestedPage?>(null)
    val requestedPage: StateFlow<RequestedPage?> = mutableRequestedPage.asStateFlow()
    private var deviceLogCollector: Job? = null

    init {
        scope.launch {
            library.load()
            catalogue.loadCached()
            history.load()
            device.configure(settings.transportKind)
        }
        scope.launch {
            var serviceRunning = false
            device.job.collect { job ->
                val isRunning = job?.isRunning == true
                if (isRunning && !serviceRunning) {
                    FlashService.start(context, requireNotNull(job).current.name)
                } else if (!isRunning && serviceRunning) {
                    FlashService.stop(context)
                }
                serviceRunning = isRunning
            }
        }
    }

    fun requestPage(page: RequestedPage) {
        mutableRequestedPage.value = page
    }

    fun clearRequestedPage() {
        mutableRequestedPage.value = null
    }

    private fun createTransport(
        kind: TransportKind,
        options: SimulatedFm1.Options,
    ): MidiTransport {
        deviceLogCollector?.cancel()
        return when (kind) {
            TransportKind.UsbMidi -> {
                AndroidMidiTransport.create(context).also { transport ->
                    (transport as? AndroidMidiTransport)?.let { androidTransport ->
                        deviceLogCollector =
                            scope.launch {
                                androidTransport.deviceLog.collect(device::appendLog)
                            }
                    }
                }
            }

            TransportKind.Simulated -> {
                SimulatedFm1(options, scope)
            }
        }
    }

    companion object {
        suspend fun load(
            context: Context,
            scope: CoroutineScope,
        ): AppModel {
            val appContext = context.applicationContext
            return AppModel(appContext, scope, DataStoreAppSettings.load(appContext, scope))
        }
    }
}
