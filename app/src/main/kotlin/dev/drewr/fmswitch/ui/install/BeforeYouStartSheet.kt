// Ported from FM-SWITCH App/Standard/Firmwares/BeforeYouStartView.swift.
package dev.drewr.fmswitch.ui.install

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.drewr.fm1.pkg.LeavingRoute
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.data.HttpFetch
import dev.drewr.fmswitch.model.device.BeforeYouStartState
import dev.drewr.fmswitch.model.device.InstallCheckState
import dev.drewr.fmswitch.model.device.InstallReadiness
import dev.drewr.fmswitch.model.device.SwitchingCheck
import dev.drewr.fmswitch.model.device.SwitchingWords
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.StartUp
import dev.drewr.fmswitch.ui.components.ProminentButton
import dev.drewr.fmswitch.ui.components.SecondaryButton
import dev.drewr.fmswitch.ui.firmwares.FlashChipNotice
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.Dispatchers

object BeforeYouStartSheet {
    @Composable
    operator fun invoke(
        targetId: String,
        requiresOfficial: Boolean,
        navigator: AppNavigator,
    ) {
        val model = LocalAppModel.current
        val identity by model.device.identity.collectAsState()
        val saved by model.library.itemsFlow.collectAsState()
        val catalogue by model.catalogue.catalogueFlow.collectAsState()
        val target = saved.firstOrNull { it.id == targetId }
        val currentTarget = target ?: return MissingTarget(navigator)
        var checked by remember { mutableStateOf<InstallCheckState>(InstallCheckState.Checking) }
        var hasDataCable by remember { mutableStateOf(false) }
        var staysPluggedIn by remember { mutableStateOf(false) }
        var hasSaved by remember { mutableStateOf(false) }
        var acceptsUntested by remember { mutableStateOf(false) }
        var confirmsNotOfficial by remember { mutableStateOf(false) }
        var switching by remember { mutableStateOf(SwitchingCheck()) }
        var selectedStartUp by remember { mutableStateOf(model.settings.startUp) }
        var asksStartUp by remember { mutableStateOf(false) }
        val flashChipId by model.device.flashChipId.collectAsState()
        val catalogueItem = catalogue?.entries?.firstOrNull { it.id == currentTarget.catalogueID }
        val isUntested =
            catalogueItem?.isHardwareTested == false && currentTarget.source == StoredFirmware.Source.Catalogue

        LaunchedEffect(currentTarget, identity, selectedStartUp, catalogue, flashChipId) {
            checked = InstallCheckState.Checking
            val savedGuide =
                identity?.text?.let {
                    model.history
                        .lastInstall(it)
                        ?.guide
                        ?.toGuide()
                }
            switching =
                SwitchingCheck.read(
                    currentTarget,
                    requiresOfficial,
                    model.settings.alwaysViaOfficial,
                    model.device,
                    model.library,
                    savedGuide,
                    flashChipId,
                    selectedStartUp,
                    catalogue?.entries.orEmpty(),
                    HttpFetch(Dispatchers.IO)::invoke,
                )
            asksStartUp = asksStartUp || (switching.dependsOnStartUp && selectedStartUp != StartUp.MVave)
            val checkingReadiness =
                InstallReadiness.fromTarget(
                    currentTarget,
                    requiresOfficial,
                    checked,
                    switching,
                    isUntested,
                    selectedStartUp,
                    model.device,
                    model.library,
                    model.settings.alwaysViaOfficial,
                )
            checked = InstallReadiness.check(checkingReadiness.steps.firstOrNull(), model.device)
        }

        val readiness = readinessFor(currentTarget, requiresOfficial, checked, switching, isUntested, selectedStartUp)
        val state =
            BeforeYouStartState(
                readiness.isSwitching,
                readiness.isSameIdentity,
                readiness.isUntested,
                hasDataCable,
                staysPluggedIn,
                hasSaved,
                acceptsUntested,
                currentTarget.name,
                confirmsNotOfficial = confirmsNotOfficial,
            )
        val colors = LocalStandardColors.current
        val back = stringResource(R.string.back)
        Column(Modifier.fillMaxSize().background(colors.ground)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
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
                    Text(
                        stringResource(R.string.before_start_title),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                InstallPlan(currentTarget, readiness)
                StartUpSection(asksStartUp, selectedStartUp) {
                    selectedStartUp = it
                    model.settings.startUp = it
                }
                SwitchingWarnings(switching.warnings)
                InstallStatus(identity == null, identity?.isLoader == true, checked, readiness, identity)
                Checklist(
                    readiness,
                    hasDataCable,
                    staysPluggedIn,
                    hasSaved,
                    acceptsUntested,
                    confirmsNotOfficial,
                    { hasDataCable = it },
                    { staysPluggedIn = it },
                    { hasSaved = it },
                    { acceptsUntested = it },
                    { confirmsNotOfficial = it },
                )
            }
            Row(
                Modifier.fillMaxWidth().background(colors.ground).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (switching.warnings.isNotEmpty()) {
                    SecondaryButton(
                        stringResource(R.string.before_start_dont_install),
                        navigator::back,
                        modifier = Modifier.weight(1f),
                    )
                }
                ProminentButton(
                    state.buttonTitle(readiness),
                    onClick = {
                        model.device.flash(
                            readiness.steps,
                            confirmsSwitchingFirmware = state.confirmsSwitchingFirmware(readiness),
                            confirmsSameIdentity = state.confirmsSameIdentity,
                            confirmsReinstallingOfficial = state.confirmsReinstallingOfficial(readiness),
                        )
                        navigator.back()
                    },
                    enabled = state.isEnabled(readiness),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun readinessFor(
        target: StoredFirmware,
        requiresOfficial: Boolean,
        checked: InstallCheckState,
        switching: SwitchingCheck,
        isUntested: Boolean,
        startUp: StartUp?,
    ): InstallReadiness {
        val model = LocalAppModel.current
        return InstallReadiness.fromTarget(
            target,
            requiresOfficial,
            checked,
            switching,
            isUntested,
            startUp,
            model.device,
            model.library,
            model.settings.alwaysViaOfficial,
        )
    }
}

@Composable
private fun InstallPlan(
    target: StoredFirmware,
    readiness: InstallReadiness,
) {
    val colors = LocalStandardColors.current
    val planned = readiness.steps.filter { it != readiness.switching.firstStep }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${target.name} ${target.version}", style = MaterialTheme.typography.titleMedium)
        readiness.switching.firstStep?.let { first ->
            readiness.switching.running?.let { running ->
                Text(
                    SwitchingWords.firstStep(first.name, running.firmwareName, target.name),
                    color = colors.secondaryInk,
                )
            }
        }
        planned.firstOrNull()?.takeIf { planned.size > 1 }?.let { first ->
            Text(stringResource(R.string.before_start_plan, first.name, target.name), color = colors.secondaryInk)
        }
        if (readiness.isMissingOfficial) Notice(stringResource(R.string.before_start_missing_official), warning = true)
        if (readiness.isMissingFirstStep) {
            readiness.switching.running?.let { running ->
                planned.firstOrNull()?.let { next ->
                    Notice(
                        SwitchingWords.missingFirstStep(
                            running.firmwareName,
                            next.name,
                            readiness.switching.firstStepProblem,
                        ),
                        warning = true,
                    )
                }
            }
        }
        if (readiness.isUntested) Notice(InstallReadiness.UNTESTED_WARNING, warning = true)
        if (readiness.switching.knowsOnlyFirstChip.isNotEmpty()) {
            FlashChipNotice(readiness.switching.knowsOnlyFirstChip, readiness.switching.hasNewerFlashChip)
        }
        if (readiness.switching.installsV16) Notice(SwitchingWords.BEFORE_V16_WARNING, warning = true)
        if (readiness.switching.followsV16) Notice(SwitchingWords.AFTER_V16_WARNING, warning = true)
        SwitchingRoute(readiness.switching)
    }
}

@Composable
private fun SwitchingRoute(switching: SwitchingCheck) {
    val running = switching.running ?: return
    val destination = switching.destination ?: return
    when (switching.route) {
        LeavingRoute.THROUGH_FIXED_FIRMWARE -> {
            if (switching.firstStep != null) {
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Text("Why?")
                }
                if (expanded) Notice(running.instructions(destination).joinToString(" "), warning = false)
            }
        }

        LeavingRoute.UNCERTAIN -> {
            Notice(running.instructions(destination).joinToString(" "), warning = false)
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun SwitchingWarnings(warnings: List<String>) {
    val colors = LocalStandardColors.current
    warnings.forEach { warning ->
        Column(
            Modifier.fillMaxWidth().background(colors.warningBackground, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                SwitchingWords.warningTitle,
                style = MaterialTheme.typography.titleSmall,
                color = colors.warningInk,
            )
            Text(warning, color = colors.warningInk)
        }
    }
}

@Composable
private fun StartUpSection(
    asksStartUp: Boolean,
    selectedStartUp: StartUp?,
    onStartUp: (StartUp) -> Unit,
) {
    val colors = LocalStandardColors.current
    if (asksStartUp) {
        Text(stringResource(R.string.before_start_startup_question), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.before_start_startup_detail), color = colors.secondaryInk)
        Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp))) {
            StartUp.entries.forEachIndexed { index, startUp ->
                if (index > 0) HorizontalDivider(color = colors.line)
                StartUpRow(startUp, selectedStartUp == startUp) { onStartUp(startUp) }
            }
        }
    }
}

@Composable
private fun StartUpRow(
    startUp: StartUp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalStandardColors.current
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            })
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (selected) "◉" else "○", color = if (selected) colors.firmwaresTint else colors.secondaryInk)
        Text(startUp.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun InstallStatus(
    disconnected: Boolean,
    isLoader: Boolean,
    checked: InstallCheckState,
    readiness: InstallReadiness,
    identity: Fm1Identity?,
) {
    if (checked is InstallCheckState.Found && readiness.knowsItIsntOfficial) {
        val running = readiness.switching.running
        if (identity != null && running != null) {
            Notice(SwitchingWords.replacesLookAlike(running.firmwareName, identity), warning = false)
        }
        return
    }
    when (
        installStatusNotice(
            !disconnected,
            isLoader,
            checked,
            readiness.isAlreadyInstalled,
            readiness.isSameIdentity,
        )
    ) {
        InstallStatusNotice.PLUG_IN -> {
            Notice(stringResource(R.string.before_start_plug_in), warning = true)
        }

        InstallStatusNotice.WAITING -> {
            Notice(stringResource(R.string.before_start_waiting), warning = false)
        }

        InstallStatusNotice.CHECKING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.before_start_checking),
                    Modifier.padding(start = 10.dp),
                    color = LocalStandardColors.current.secondaryInk,
                )
            }
        }

        InstallStatusNotice.FAILED -> {
            Notice((checked as InstallCheckState.Failed).message, warning = true)
        }

        InstallStatusNotice.ALREADY_RUNNING -> {
            val text =
                if (readiness.canReinstallOfficial && identity != null) {
                    SwitchingWords.alreadyOfficial(identity)
                } else {
                    stringResource(R.string.before_start_already_running)
                }
            Notice(text, warning = false)
        }

        InstallStatusNotice.SAME_IDENTITY -> {
            Notice(
                stringResource(R.string.before_start_same_identity, identity?.text.orEmpty()),
                warning = false,
            )
        }

        InstallStatusNotice.NONE -> {
            Unit
        }
    }
}

@Composable
private fun Checklist(
    readiness: InstallReadiness,
    hasDataCable: Boolean,
    staysPluggedIn: Boolean,
    hasSaved: Boolean,
    acceptsUntested: Boolean,
    confirmsNotOfficial: Boolean,
    onDataCable: (Boolean) -> Unit,
    onStaysPluggedIn: (Boolean) -> Unit,
    onHasSaved: (Boolean) -> Unit,
    onAcceptsUntested: (Boolean) -> Unit,
    onConfirmsNotOfficial: (Boolean) -> Unit,
) {
    val colors = LocalStandardColors.current
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp))) {
        if (readiness.canReinstallOfficial && !readiness.knowsItIsntOfficial) {
            val identity = requireNotNull(readiness.identity)
            CheckRow(
                SwitchingWords.notReallyOfficial,
                SwitchingWords.notReallyOfficialDetail(identity),
                confirmsNotOfficial,
                onConfirmsNotOfficial,
            )
            HorizontalDivider(color = colors.line)
        }
        CheckRow(
            stringResource(R.string.before_start_data_cable),
            stringResource(R.string.before_start_data_cable_detail),
            hasDataCable,
            onDataCable,
        )
        HorizontalDivider(color = colors.line)
        CheckRow(
            stringResource(R.string.before_start_keep_connected),
            stringResource(R.string.before_start_keep_connected_detail),
            staysPluggedIn,
            onStaysPluggedIn,
        )
        if (readiness.isSwitching) {
            HorizontalDivider(color = colors.line)
            CheckRow(
                stringResource(R.string.before_start_saved),
                stringResource(R.string.before_start_saved_detail),
                hasSaved,
                onHasSaved,
            )
        }
        if (readiness.isUntested) {
            HorizontalDivider(color = colors.line)
            CheckRow(
                stringResource(R.string.before_start_accept_untested),
                stringResource(R.string.before_start_accept_untested_detail),
                acceptsUntested,
                onAcceptsUntested,
            )
        }
    }
    Text(
        stringResource(R.string.before_start_duration),
        color = colors.secondaryInk,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun CheckRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalStandardColors.current
    val state = stringResource(if (checked) R.string.before_start_checked else R.string.before_start_not_checked)
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            })
            .semantics { stateDescription = state }
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (checked) "✓" else "○", color = if (checked) colors.firmwaresTint else colors.secondaryInk)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, color = colors.secondaryInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Notice(
    text: String,
    warning: Boolean,
) {
    val colors = LocalStandardColors.current
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .background(
                if (warning) colors.warningBackground else colors.control,
                RoundedCornerShape(12.dp),
            ).padding(12.dp),
        color = if (warning) colors.warningInk else colors.ink,
    )
}

@Composable
private fun MissingTarget(navigator: AppNavigator) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.before_start_title),
            Modifier.semantics {
                heading()
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.before_start_no_target), color = LocalStandardColors.current.secondaryInk)
        Spacer(Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.install_close), navigator::back)
    }
}
