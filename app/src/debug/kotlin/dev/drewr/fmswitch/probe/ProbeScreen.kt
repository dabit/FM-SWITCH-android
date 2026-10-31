package dev.drewr.fmswitch.probe

import android.content.ClipData
import android.content.ClipboardManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.drewr.fm1.transport.PortMatching

@Suppress("DEPRECATION", "FunctionName", "LongMethod")
@androidx.compose.runtime.Composable
fun ProbeScreen() {
    val context = LocalContext.current
    var log by remember { mutableStateOf(MidiProbeRecorder.log()) }
    val manager = remember { context.getSystemService(MidiManager::class.java) }
    DisposableEffect(Unit) {
        val listener = { log = MidiProbeRecorder.log() }
        MidiProbeRecorder.addListener(listener)
        onDispose { MidiProbeRecorder.removeListener(listener) }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("USB MIDI probe", style = MaterialTheme.typography.headlineSmall)
        Text("This sends one handshake only to matching devices. Copy the log after testing.")
        manager.devices.filter { PortMatching.isProbeable(MidiProbeRecorder.descriptor(it)) }.forEach { device ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Device ${device.id}: ${device.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty()}")
                Button(onClick = { MidiProbeRecorder.sendHandshake(device) }) { Text("Send handshake") }
            }
        }
        Button(
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("FM-1 probe log", log.joinToString("\n")))
            },
        ) { Text("Copy log") }
        Text(
            log.joinToString("\n"),
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
