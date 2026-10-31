package dev.drewr.fmswitch

import android.app.Application
import dev.drewr.fmswitch.probe.ProbeHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FmSwitchApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableAppModel = MutableStateFlow<AppModel?>(null)
    val appModel: StateFlow<AppModel?> = mutableAppModel.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        ProbeHooks.start(this)
        applicationScope.launch {
            mutableAppModel.value = AppModel.load(this@FmSwitchApplication, this)
        }
    }
}
