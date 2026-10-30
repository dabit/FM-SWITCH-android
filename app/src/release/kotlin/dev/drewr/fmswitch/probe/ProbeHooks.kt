package dev.drewr.fmswitch.probe

import android.content.Context
import android.content.Intent

object ProbeHooks {
    fun start(context: Context) {}

    fun probeIntent(context: Context): Intent? = null
}
