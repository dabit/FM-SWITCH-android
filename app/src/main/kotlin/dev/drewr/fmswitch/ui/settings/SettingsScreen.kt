// Ported from FM-SWITCH App/Standard/Settings/StandardSettingsView.swift.
package dev.drewr.fmswitch.ui.settings

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.data.DataStoreAppSettings
import dev.drewr.fmswitch.model.settings.Appearance
import dev.drewr.fmswitch.model.settings.StartUp
import dev.drewr.fmswitch.model.settings.TransportKind
import dev.drewr.fmswitch.probe.ProbeHooks
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SettingsScreen {
    @Composable
    operator fun invoke(navigator: AppNavigator) {
        val model = LocalAppModel.current
        val prefs by model.settings.changes.collectAsState()
        val appearance =
            Appearance.entries.firstOrNull { it.name == prefs[DataStoreAppSettings.APPEARANCE] }
                ?: Appearance.System
        val showBeta = prefs[DataStoreAppSettings.SHOW_BETA] ?: true
        val showLinksOnly = prefs[DataStoreAppSettings.SHOW_LINKS_ONLY] ?: false
        val alwaysViaOfficial = prefs[DataStoreAppSettings.ALWAYS_VIA_OFFICIAL] ?: false
        val startUp = StartUp.entries.firstOrNull { it.name == prefs[DataStoreAppSettings.START_UP] }
        val transportKind =
            TransportKind.entries.firstOrNull { it.name == prefs[DataStoreAppSettings.TRANSPORT_KIND] }
                ?: TransportKind.UsbMidi
        val job by model.device.job.collectAsState()
        val context = LocalContext.current
        val probe = remember(context) { nullableProbeIntent(context) }
        val connectionEnabled = job?.isRunning != true
        val back = stringResource(R.string.back)

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←",
                    Modifier
                        .size(48.dp)
                        .wrapContentSize(Alignment.Center)
                        .clickable(role = Role.Button, onClick = navigator::back)
                        .semantics { contentDescription = back },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Heading(stringResource(R.string.title_settings), MaterialTheme.typography.headlineLarge)
            }
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Appearance.entries.forEach { value ->
                    ChoiceRow(appearanceName(value), appearance == value) { model.settings.appearance = value }
                }
                Text(appearanceNote(appearance), color = LocalStandardColors.current.secondaryInk)
            }
            SettingsSection(stringResource(R.string.settings_firmware_list)) {
                ToggleRow(stringResource(R.string.settings_show_test), showBeta) {
                    model.settings.showBeta = it
                }
                ToggleRow(stringResource(R.string.settings_show_without_download), showLinksOnly) {
                    model.settings.showLinksOnly = it
                }
                ToggleRow(stringResource(R.string.settings_always_official), alwaysViaOfficial) {
                    model.settings.alwaysViaOfficial = it
                }
                Text(stringResource(R.string.settings_startup), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(stringResource(R.string.settings_startup_ask), startUp == null) {
                    model.settings.startUp = null
                }
                StartUp.entries.forEach { value ->
                    ChoiceRow(startUpName(value), startUp == value) { model.settings.startUp = value }
                }
            }
            SettingsSection(stringResource(R.string.settings_connection)) {
                Text(stringResource(R.string.settings_connect_to), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    stringResource(R.string.settings_usb),
                    transportKind == TransportKind.UsbMidi,
                    connectionEnabled,
                ) {
                    model.settings.transportKind = TransportKind.UsbMidi
                    model.device.configure(TransportKind.UsbMidi)
                }
                ChoiceRow(
                    stringResource(R.string.settings_simulated),
                    transportKind == TransportKind.Simulated,
                    connectionEnabled,
                ) {
                    model.settings.transportKind = TransportKind.Simulated
                    model.device.configure(TransportKind.Simulated)
                }
                if (transportKind == TransportKind.Simulated) SimulatorSettings(prefs, connectionEnabled)
                Text(stringResource(R.string.settings_simulated_note), color = LocalStandardColors.current.secondaryInk)
            }
            SettingsSection(stringResource(R.string.settings_safety)) {
                Text(stringResource(R.string.safety_notice_text))
                Text(stringResource(R.string.safety_notice_recovery))
                Text(stringResource(R.string.settings_safety_phone))
                RecoveryLink(stringResource(R.string.settings_mflasher), "https://github.com/jvitkauskas/m-flasher")
                RecoveryLink(stringResource(R.string.settings_rescue_tools), "https://thefirmwarehub.com/tools/#rescue")
                RecoveryLink(
                    stringResource(R.string.settings_bootloader_why),
                    "https://fm-1.drewr.dev/bootloader-check/",
                )
            }
            if (probe != null) {
                SettingsSection(stringResource(R.string.settings_debug)) {
                    ActionRow(stringResource(R.string.settings_probe)) { context.startActivity(probe) }
                }
            }
            AboutSection(context)
        }
    }
}

@Composable
private fun SimulatorSettings(
    prefs: androidx.datastore.preferences.core.Preferences,
    enabled: Boolean,
) {
    val model = LocalAppModel.current
    val reset = { model.device.configure(TransportKind.Simulated) }
    ToggleRow(
        stringResource(R.string.settings_recovery),
        prefs[DataStoreAppSettings.SIMULATED_RECOVERY_MODE] ?: false,
        enabled,
    ) {
        model.settings.simulatedRecoveryMode = it
    }
    ToggleRow(
        stringResource(R.string.settings_cable),
        prefs[DataStoreAppSettings.SIMULATED_CABLE_PULL] ?: false,
        enabled,
    ) {
        model.settings.simulatedCablePull = it
    }
    ToggleRow(
        stringResource(R.string.settings_backwards),
        prefs[DataStoreAppSettings.SIMULATED_READS_BACKWARDS] ?: false,
        enabled,
    ) {
        model.settings.simulatedReadsBackwards = it
    }
    ToggleRow(
        stringResource(R.string.settings_verifies),
        prefs[DataStoreAppSettings.SIMULATED_VERIFIES] ?: false,
        enabled,
    ) {
        model.settings.simulatedVerifies = it
    }
    ToggleRow(
        stringResource(R.string.settings_m_bootloader),
        prefs[DataStoreAppSettings.SIMULATED_M_BOOTLOADER] ?: false,
        enabled,
    ) {
        model.settings.simulatedMBootloader = it
        reset()
    }
    ToggleRow(
        stringResource(R.string.settings_open),
        prefs[DataStoreAppSettings.SIMULATED_OPEN_FIRMWARE] ?: false,
        enabled,
    ) {
        model.settings.simulatedOpenFirmware = it
        reset()
    }
    ActionRow(stringResource(R.string.settings_reset), enabled, reset)
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(title, MaterialTheme.typography.titleMedium)
        Column(
            Modifier
                .fillMaxWidth()
                .background(LocalStandardColors.current.card, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() },
        )
    }
}

@Composable
private fun Heading(
    title: String,
    style: androidx.compose.ui.text.TextStyle,
) = Text(title, Modifier.semantics { heading() }, style = style, color = LocalStandardColors.current.secondaryInk)

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked, null, enabled = enabled)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null, enabled = enabled)
        Text(title, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ActionRow(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        title,
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
        LocalStandardColors.current.accentText,
    )
}

@Composable
private fun RecoveryLink(
    title: String,
    address: String,
) {
    val context = LocalContext.current
    ActionRow(title) { CustomTabsIntent.Builder().build().launchUrl(context, address.toUri()) }
}

@Composable
private fun AboutSection(context: Context) {
    val notices by
        produceState<Notices?>(null, context) {
            value = withContext(Dispatchers.IO) { readNotices(context) }
        }
    SettingsSection(stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.settings_about_sources), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_about_protocol), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_about_official), style = MaterialTheme.typography.bodySmall)
        notices?.let {
            Text(stringResource(R.string.settings_notice_fm1kit), style = MaterialTheme.typography.titleSmall)
            Text(it.fm1Kit, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_notice_mflasher), style = MaterialTheme.typography.titleSmall)
            Text(it.mFlasher, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_notice_v15), style = MaterialTheme.typography.titleSmall)
            Text(it.v15, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class Notices(
    val fm1Kit: String,
    val mFlasher: String,
    val v15: String,
)

private fun readNotices(context: Context) =
    Notices(
        context.assets
            .open("licences/FM1Kit-LICENSE.txt")
            .bufferedReader()
            .use { it.readText() },
        context.assets
            .open("licences/M-Flasher-LICENSE.txt")
            .bufferedReader()
            .use { it.readText() },
        context.assets
            .open("firmware/FM-1_015-LICENCE.txt")
            .bufferedReader()
            .use { it.readText() },
    )

private fun nullableProbeIntent(context: Context): android.content.Intent? = ProbeHooks.probeIntent(context)

@Composable
private fun appearanceName(value: Appearance) =
    stringResource(
        when (value) {
            Appearance.System -> R.string.settings_appearance_system
            Appearance.Light -> R.string.settings_appearance_light
            Appearance.Dark -> R.string.settings_appearance_dark
        },
    )

@Composable
private fun startUpName(value: StartUp) =
    stringResource(
        when (value) {
            StartUp.MVave -> R.string.settings_startup_mvave
            StartUp.MBootloader -> R.string.settings_startup_mbootloader
            StartUp.OpenFirmware -> R.string.settings_startup_open
            StartUp.NotSure -> R.string.settings_startup_unsure
        },
    )

@Composable
private fun appearanceNote(value: Appearance) =
    stringResource(
        when (value) {
            Appearance.System -> {
                if (isSystemInDarkTheme()) {
                    R.string.settings_appearance_system_dark_note
                } else {
                    R.string.settings_appearance_system_light_note
                }
            }

            Appearance.Light -> {
                R.string.settings_appearance_light_note
            }

            Appearance.Dark -> {
                R.string.settings_appearance_dark_note
            }
        },
    )
