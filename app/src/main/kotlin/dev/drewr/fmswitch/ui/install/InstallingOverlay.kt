// Ported from FM-SWITCH App/Standard/Install/InstallingView.swift.
package dev.drewr.fmswitch.ui.install

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.device.FlashJob
import dev.drewr.fmswitch.model.device.FlashPhase
import dev.drewr.fmswitch.model.device.InstallProgress
import dev.drewr.fmswitch.ui.FM1Drawing
import dev.drewr.fmswitch.ui.theme.LocalReduceMotion
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import dev.drewr.fmswitch.ui.theme.Motion

object InstallingOverlay {
    @Composable
    operator fun invoke() {
        val model = LocalAppModel.current
        val job by model.device.job.collectAsState()
        val currentJob = job ?: return
        BackHandler(enabled = true) {}
        KeepScreenOn()
        OutcomeFeedback(currentJob.phase)
        InstallOverlay(currentJob)
    }
}

/** A buzz for how the install ended, as on iPhone: confirm on success, reject on failure. */
@Composable
private fun OutcomeFeedback(phase: FlashPhase) {
    val haptics = LocalHapticFeedback.current
    val ended =
        when (phase) {
            is FlashPhase.Succeeded -> HapticFeedbackType.Confirm
            is FlashPhase.Failed -> HapticFeedbackType.Reject
            else -> null
        }
    // Only when it ends while on screen, not when the app reopens on an outcome.
    var wasRunning by remember { mutableStateOf(ended == null) }
    LaunchedEffect(ended) {
        if (ended == null) {
            wasRunning = true
        } else if (wasRunning) {
            haptics.performHapticFeedback(ended)
            wasRunning = false
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current.activityOrNull() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun Context.activityOrNull(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activityOrNull()
        else -> null
    }

@Composable
private fun InstallOverlay(job: FlashJob) {
    val colors = LocalStandardColors.current
    val progress = remember { InstallProgress.Tracker() }.also { it.update(job) }.current
    val screenLine =
        when (val phase = job.phase) {
            is FlashPhase.Succeeded -> {
                stringResource(R.string.install_screen_hello)
            }

            is FlashPhase.Failed -> {
                stringResource(
                    if (phase.canResume) R.string.install_screen_waiting else R.string.install_screen_stopped,
                )
            }

            else -> {
                stringResource(R.string.install_screen_stage, stringResource(progress.stage.stringRes))
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    colors.ground,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        FM1Drawing(
            job.current.name.uppercase(),
            line2 = screenLine,
            progress = drawingProgress(job.isRunning, progress.fraction),
            screenTint = INSTALL_SCREEN_TINT,
        )
        // The overlay sits outside the app's surface, so text needs the theme's colour set here.
        CompositionLocalProvider(LocalContentColor provides colors.ink) {
            when (val phase = job.phase) {
                is FlashPhase.Succeeded -> InstallDone(job, phase.identity)
                is FlashPhase.Failed -> InstallProblem(job, phase.message, phase.canResume)
                else -> Installing(job, progress)
            }
        }
    }
}

@Composable
private fun Installing(
    job: FlashJob,
    progress: InstallProgress,
) {
    val colors = LocalStandardColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.installing_title, job.current.name),
            Modifier.semantics { heading() },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        val darkScreen = stringResource(R.string.installing_dark_screen)
        Text(
            if (job.steps.size > 1) {
                stringResource(R.string.installing_step, job.index + 1, job.steps.size, darkScreen)
            } else {
                darkScreen
            },
            color = colors.secondaryInk,
            textAlign = TextAlign.Center,
        )
    }
    InstallProgressBar(progress.fraction)
    InstallStages(progress.stage)
    Text(
        stringResource(R.string.installing_keep_open),
        style = MaterialTheme.typography.bodySmall,
        color = colors.secondaryInk,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun InstallProgressBar(fraction: Double) {
    val colors = LocalStandardColors.current
    val animated by animateFloatAsState(
        fraction.toFloat(),
        Motion.standard(LocalReduceMotion.current),
        label = "install progress",
    )
    val accessibilityLabel = stringResource(R.string.installing_accessibility_label)
    val percentValue = (animated.coerceIn(0f, 1f) * 100).toInt()
    val percent = pluralStringResource(R.plurals.installing_percent, percentValue, percentValue)
    Column(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(colors.control).semantics {
            contentDescription = accessibilityLabel
            stateDescription = percent
            progressBarRangeInfo =
                androidx.compose.ui.semantics
                    .ProgressBarRangeInfo(animated, 0f..1f)
        },
    ) {
        Spacer(
            Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).height(10.dp).background(colors.firmwaresTint),
        )
    }
}

@Composable
private fun InstallStages(current: InstallProgress.Stage) {
    val colors = LocalStandardColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(vertical = 4.dp),
    ) {
        InstallProgress.Stage.entries.forEach { stage ->
            val done = stage.ordinal < current.ordinal
            val now = stage == current
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (done) "✓" else "○",
                    color =
                        if (done) {
                            colors.good
                        } else if (now) {
                            colors.firmwaresTint
                        } else {
                            colors.line
                        },
                    modifier = Modifier.width(28.dp).semantics { hideFromAccessibility() },
                )
                Text(
                    stringResource(stage.stringRes),
                    Modifier.weight(1f).semantics {
                        if (now) liveRegion = LiveRegionMode.Polite
                    },
                    fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (done ||
                            now
                        ) {
                            colors.ink
                        } else {
                            colors.secondaryInk
                        },
                )
                if (now) Text(stringResource(R.string.installing_now), color = colors.secondaryInk)
            }
        }
    }
}

private val InstallProgress.Stage.stringRes: Int
    get() =
        when (this) {
            InstallProgress.Stage.GettingReady -> R.string.install_stage_getting_ready
            InstallProgress.Stage.Installing -> R.string.install_stage_installing
            InstallProgress.Stage.Restarting -> R.string.install_stage_restarting
        }

private val INSTALL_SCREEN_TINT =
    androidx.compose.ui.graphics
        .Color(0xFFFFB27A)
