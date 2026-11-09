// Ported from FM-SWITCH App/Model/AppSettings.swift.
package dev.drewr.fmswitch.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.drewr.fmswitch.model.catalogue.FirmwareOrder
import dev.drewr.fmswitch.model.settings.AppSettings
import dev.drewr.fmswitch.model.settings.Appearance
import dev.drewr.fmswitch.model.settings.StartUp
import dev.drewr.fmswitch.model.settings.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.appSettingsDataStore by preferencesDataStore(name = "app-settings")

class DataStoreAppSettings private constructor(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
    initialValues: Preferences,
    scope: CoroutineScope,
) : AppSettings {
    private val mutableChanges = MutableStateFlow(initialValues)
    val changes: StateFlow<Preferences> = mutableChanges.asStateFlow()
    private val values: Preferences get() = changes.value
    private val mutableAppearance = MutableStateFlow(appearanceFrom(initialValues))
    val appearanceFlow: StateFlow<Appearance> = mutableAppearance.asStateFlow()
    private val writes = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) {
                dataStore.edit(write)
            }
        }
    }

    override var appearance: Appearance
        get() = appearanceFlow.value
        set(value) {
            mutableAppearance.value = value
            save(APPEARANCE, value.name)
        }

    override var transportKind: TransportKind
        get() = TransportKind.entries.firstOrNull { it.name == values[TRANSPORT_KIND] } ?: TransportKind.UsbMidi
        set(value) = save(TRANSPORT_KIND, value.name)

    override var showBeta: Boolean
        get() = values[SHOW_BETA] ?: true
        set(value) = save(SHOW_BETA, value)

    override var showLinksOnly: Boolean
        get() = values[SHOW_LINKS_ONLY] ?: false
        set(value) = save(SHOW_LINKS_ONLY, value)

    override var alwaysViaOfficial: Boolean
        get() = values[ALWAYS_VIA_OFFICIAL] ?: false
        set(value) = save(ALWAYS_VIA_OFFICIAL, value)

    override var startUp: StartUp?
        get() = StartUp.entries.firstOrNull { it.name == values[START_UP] }
        set(value) = saveNullable(START_UP, value?.name)

    override var firmwareOrder: FirmwareOrder
        get() = FirmwareOrder.entries.firstOrNull { it.name == values[FIRMWARE_ORDER] } ?: FirmwareOrder.ByName
        set(value) = save(FIRMWARE_ORDER, value.name)

    override var firmwareScope: String?
        get() = values[FIRMWARE_SCOPE]
        set(value) = saveNullable(FIRMWARE_SCOPE, value)

    override var myFirmwaresOrder: FirmwareOrder
        get() = FirmwareOrder.entries.firstOrNull { it.name == values[MY_FIRMWARES_ORDER] } ?: FirmwareOrder.ByName
        set(value) = save(MY_FIRMWARES_ORDER, value.name)

    override var myFirmwaresScope: String?
        get() = values[MY_FIRMWARES_SCOPE]
        set(value) = saveNullable(MY_FIRMWARES_SCOPE, value)

    override var simulatedRecoveryMode: Boolean
        get() = values[SIMULATED_RECOVERY_MODE] ?: false
        set(value) = save(SIMULATED_RECOVERY_MODE, value)

    override var simulatedCablePull: Boolean
        get() = values[SIMULATED_CABLE_PULL] ?: false
        set(value) = save(SIMULATED_CABLE_PULL, value)

    override var simulatedReadsBackwards: Boolean
        get() = values[SIMULATED_READS_BACKWARDS] ?: false
        set(value) = save(SIMULATED_READS_BACKWARDS, value)

    override var simulatedVerifies: Boolean
        get() = values[SIMULATED_VERIFIES] ?: false
        set(value) = save(SIMULATED_VERIFIES, value)

    override var simulatedMBootloader: Boolean
        get() = values[SIMULATED_M_BOOTLOADER] ?: false
        set(value) = save(SIMULATED_M_BOOTLOADER, value)

    override var simulatedOpenFirmware: Boolean
        get() = values[SIMULATED_OPEN_FIRMWARE] ?: false
        set(value) = save(SIMULATED_OPEN_FIRMWARE, value)

    override var acknowledgedNoticeVersion: Int
        get() = values[ACKNOWLEDGED_NOTICE_VERSION] ?: 0
        set(value) = save(ACKNOWLEDGED_NOTICE_VERSION, value)

    private fun <T> save(
        key: Preferences.Key<T>,
        value: T,
    ) {
        mutableChanges.value = values.toMutablePreferences().also { it[key] = value }
        writes.trySend { it[key] = value }
    }

    private fun saveNullable(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        mutableChanges.value =
            values.toMutablePreferences().also {
                if (value == null) it.remove(key) else it[key] = value
            }
        writes.trySend {
            if (value == null) it.remove(key) else it[key] = value
        }
    }

    companion object {
        val APPEARANCE = stringPreferencesKey("appearance")
        val TRANSPORT_KIND = stringPreferencesKey("transportKind")
        val SHOW_BETA = booleanPreferencesKey("showBeta")
        val SHOW_LINKS_ONLY = booleanPreferencesKey("showLinksOnly")
        val ALWAYS_VIA_OFFICIAL = booleanPreferencesKey("alwaysViaOfficial")
        val START_UP = stringPreferencesKey("startUp")
        val FIRMWARE_ORDER = stringPreferencesKey("firmwareOrder")
        val FIRMWARE_SCOPE = stringPreferencesKey("firmwareScope")
        val MY_FIRMWARES_ORDER = stringPreferencesKey("myFirmwaresOrder")
        val MY_FIRMWARES_SCOPE = stringPreferencesKey("myFirmwaresScope")
        val SIMULATED_RECOVERY_MODE = booleanPreferencesKey("simulatedRecoveryMode")
        val SIMULATED_CABLE_PULL = booleanPreferencesKey("simulatedCablePull")
        val SIMULATED_READS_BACKWARDS = booleanPreferencesKey("simulatedReadsBackwards")
        val SIMULATED_VERIFIES = booleanPreferencesKey("simulatedVerifies")
        val SIMULATED_M_BOOTLOADER = booleanPreferencesKey("simulatedMBootloader")
        val SIMULATED_OPEN_FIRMWARE = booleanPreferencesKey("simulatedOpenFirmware")
        val ACKNOWLEDGED_NOTICE_VERSION = intPreferencesKey("acknowledgedNoticeVersion")

        private fun appearanceFrom(values: Preferences) =
            Appearance.entries.firstOrNull { it.name == values[APPEARANCE] } ?: Appearance.System

        suspend fun load(
            context: Context,
            scope: CoroutineScope,
        ): DataStoreAppSettings {
            val dataStore = context.applicationContext.appSettingsDataStore
            val values = withContext(Dispatchers.IO) { dataStore.data.first() }
            return DataStoreAppSettings(dataStore, values, scope)
        }
    }
}
