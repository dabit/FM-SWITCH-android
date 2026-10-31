package dev.drewr.fmswitch.probe

import android.content.Context
import android.content.Intent

object ProbeHooks {
    fun start(context: Context) {
        MidiProbeRecorder.start(context)
    }

    fun probeIntent(context: Context): Intent = Intent(context, ProbeActivity::class.java)
}
