package dev.drewr.fmswitch

import android.app.Application
import dev.drewr.fmswitch.probe.ProbeHooks

class FmSwitchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProbeHooks.start(this)
    }
}
