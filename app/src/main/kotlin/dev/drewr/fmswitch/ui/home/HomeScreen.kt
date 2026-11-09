// Ported from FM-SWITCH App/Standard/Home/HomeView.swift.
package dev.drewr.fmswitch.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.catalogue.FirmwareFilter
import dev.drewr.fmswitch.model.device.DeviceSummary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.ui.FM1Drawing
import dev.drewr.fmswitch.ui.components.ProminentButton
import dev.drewr.fmswitch.ui.components.SecondaryButton
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalReduceMotion
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import dev.drewr.fmswitch.ui.theme.Motion
import kotlinx.coroutines.launch

object HomeScreen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    operator fun invoke(navigator: AppNavigator) {
        val model = LocalAppModel.current
        val identity by model.device.identity.collectAsState()
        val error by model.device.transportError.collectAsState()
        val catalogue by model.catalogue.catalogueFlow.collectAsState()
        val saved by model.library.itemsFlow.collectAsState()
        val summary = DeviceSummary.from(identity, model.history, catalogue?.entries.orEmpty())
        val scope = rememberCoroutineScope()
        var rescanning by remember { mutableStateOf(false) }
        val savedCount =
            catalogue?.entries.orEmpty().count {
                model.library.savedCopies(it).isNotEmpty()
            }
        var finishers by remember { mutableStateOf<List<StoredFirmware>>(emptyList()) }

        LaunchedEffect(identity, saved) {
            finishers =
                identity
                    ?.takeIf { it.isLoader }
                    ?.let { loader -> model.library.packagesThatCanFinish(loader) }
                    .orEmpty()
        }
        LaunchedEffect(Unit) { model.device.scan() }
        val settings = stringResource(R.string.home_settings)
        PullToRefreshBox(
            isRefreshing = rescanning,
            onRefresh = {
                scope.launch {
                    rescanning = true
                    try {
                        model.device.scan()
                    } finally {
                        rescanning = false
                    }
                }
            },
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.title_home),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        "⚙",
                        Modifier
                            .size(48.dp)
                            .wrapContentSize(Alignment.Center)
                            .clickable(role = Role.Button, onClick = navigator::openSettings)
                            .semantics { contentDescription = settings },
                        color = LocalStandardColors.current.ink,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FM1Drawing(summary.screenTitle, line2 = summary.screenLine)
                    AnimatedStatusPill(summary.statusLine, summary.state !is DeviceSummary.State.NotConnected)
                }
                ChangeFirmwareCard(
                    FirmwareFilter()
                        .visible(
                            catalogue?.entries.orEmpty(),
                            model.settings.showBeta,
                            model.settings.showLinksOnly,
                            saved.map { it.id },
                        ).size,
                    navigator::openFirmwares,
                )
                TaskCard(
                    stringResource(R.string.home_my_firmwares),
                    pluralStringResource(
                        R.plurals.home_saved_count,
                        savedCount,
                        savedCount,
                    ),
                    navigator::openMyFirmwares,
                )
                if (summary.state is DeviceSummary.State.Waiting) FinishCard(finishers, navigator)
                if (summary.state is DeviceSummary.State.NotConnected) {
                    ConnectCard(error) {
                        model.device.configure(model.settings.transportKind)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedStatusPill(
    status: String,
    connected: Boolean,
) {
    val fade = if (LocalReduceMotion.current) tween<Float>(0) else Motion.fade
    AnimatedContent(
        targetState = StatusPillState(status, connected),
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        label = "status pill",
    ) { state -> StatusPill(state.status, state.connected) }
}

private data class StatusPillState(
    val status: String,
    val connected: Boolean,
)

@Composable
private fun StatusPill(
    status: String,
    connected: Boolean,
) = Row(
    Modifier
        .background(
            if (connected) LocalStandardColors.current.goodBackground else LocalStandardColors.current.control,
            RoundedCornerShape(999.dp),
        ).padding(horizontal = 12.dp, vertical = 6.dp),
) {
    val dotColour = if (connected) LocalStandardColors.current.goodInk else LocalStandardColors.current.secondaryInk
    Canvas(Modifier.size(8.dp).clearAndSetSemantics {}) {
        drawCircle(dotColour)
    }
    Spacer(Modifier.size(6.dp))
    Text(
        status,
        color = if (connected) LocalStandardColors.current.goodInk else LocalStandardColors.current.secondaryInk,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun ChangeFirmwareCard(
    count: Int,
    onClick: () -> Unit,
) {
    PressableCard(
        colour = LocalStandardColors.current.firmwaresTint,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
        padding = 20.dp,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CardTitle(
            "↔",
            stringResource(R.string.home_change_firmware),
            LocalStandardColors.current.card,
            MaterialTheme.typography.titleLarge,
        )
        Text(
            if (count ==
                0
            ) {
                stringResource(R.string.home_firmwares_empty)
            } else {
                pluralStringResource(R.plurals.home_firmwares_count, count, count)
            },
            color = LocalStandardColors.current.card,
        )
    }
}

@Composable
private fun TaskCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    PressableCard(
        colour = LocalStandardColors.current.card,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        padding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CardTitle("▣", title, LocalStandardColors.current.ink, MaterialTheme.typography.titleMedium)
        Text(detail, color = LocalStandardColors.current.secondaryInk)
    }
}

@Composable
private fun PressableCard(
    colour: androidx.compose.ui.graphics.Color,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    padding: androidx.compose.ui.unit.Dp,
    verticalArrangement: Arrangement.Vertical,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !LocalReduceMotion.current) .97f else 1f,
        Motion.press,
        label = "card scale",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (pressed) .85f else 1f)
            .background(colour, shape)
            .clickable(interactionSource = interaction, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = { content() },
    )
}

@Composable
private fun CardTitle(
    icon: String,
    title: String,
    colour: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, Modifier.clearAndSetSemantics {}, color = colour)
        Spacer(Modifier.size(8.dp))
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            color = colour,
            style = style,
            fontWeight = FontWeight.Bold,
        )
        Text("›", Modifier.clearAndSetSemantics {}, color = colour, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun FinishCard(
    finishers: List<StoredFirmware>,
    navigator: AppNavigator,
) {
    Column(
        Modifier.fillMaxWidth().background(LocalStandardColors.current.card, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.home_finish_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        val first = finishers.firstOrNull()
        if (first ==
            null
        ) {
            Text(stringResource(R.string.home_finish_missing), color = LocalStandardColors.current.secondaryInk)
        } else {
            Text(
                stringResource(R.string.home_finish_detail, first.name),
                color = LocalStandardColors.current.secondaryInk,
            )
            ProminentButton(
                text = stringResource(R.string.home_finish_action, first.name),
                onClick = { navigator.openBeforeYouStart(first.id) },
            )
        }
    }
}

@Composable
private fun ConnectCard(
    error: String?,
    onTryAgain: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(LocalStandardColors.current.card, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.home_connect_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.home_connect_one))
        Text(stringResource(R.string.home_connect_two))
        Text(stringResource(R.string.home_connect_three))
        if (error != null) {
            Text(error, color = LocalStandardColors.current.warningInk, style = MaterialTheme.typography.bodySmall)
            SecondaryButton(stringResource(R.string.home_try_again), onTryAgain)
        }
    }
}
