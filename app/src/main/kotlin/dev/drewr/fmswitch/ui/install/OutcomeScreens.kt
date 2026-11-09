// Ported from FM-SWITCH App/Standard/Install/InstallOutcomeViews.swift.
package dev.drewr.fmswitch.ui.install

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.RequestedPage
import dev.drewr.fmswitch.data.HttpFetch
import dev.drewr.fmswitch.model.device.FixedFirmware
import dev.drewr.fmswitch.model.device.FlashJob
import dev.drewr.fmswitch.model.device.FlashPhase
import dev.drewr.fmswitch.model.device.SwitchingWords
import dev.drewr.fmswitch.ui.components.ProminentButton
import dev.drewr.fmswitch.ui.components.SecondaryButton
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OutcomeScreens {
    @Composable
    operator fun invoke(navigator: AppNavigator) {
        val model = LocalAppModel.current
        val job by model.device.job.collectAsState()
        val current = job ?: return
        when (val phase = current.phase) {
            is FlashPhase.Succeeded -> InstallDone(current, phase.identity)
            is FlashPhase.Failed -> InstallProblem(current, phase.message, phase.canResume)
            else -> InstallingOverlay()
        }
    }
}

@Composable
internal fun InstallDone(
    job: FlashJob,
    reported: String,
) {
    val model = LocalAppModel.current
    val colors = LocalStandardColors.current
    Text(
        "✓",
        color = androidx.compose.ui.graphics.Color.White,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .semantics { hideFromAccessibility() }
                .clip(CircleShape)
                .background(colors.good)
                .padding(18.dp),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.install_done_title, job.steps.last().name),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.install_done_body, reported),
            color = colors.secondaryInk,
            textAlign = TextAlign.Center,
        )
    }
    if (showsStillMono(reported, job.startedOnV16)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .padding(16.dp),
        ) {
            Text(SwitchingWords.STILL_MONO, color = colors.secondaryInk)
        }
    }
    ProminentButton(stringResource(R.string.install_done), model.device::dismissFlash)
}

@Composable
internal fun InstallProblem(
    job: FlashJob,
    message: String,
    canResume: Boolean,
) {
    val model = LocalAppModel.current
    val colors = LocalStandardColors.current
    val wayRound = model.catalogue.entries.firstOrNull { it.id == FixedFirmware.MIDIMON }
    val scope = rememberCoroutineScope()
    var isFindingFirstStep by remember { mutableStateOf(false) }
    var firstStepProblem by remember { mutableStateOf<String?>(null) }
    val presentation =
        problemPresentation(
            canResume,
            job.needsAStepFirst,
            job.mightBeRefused,
            wayRound != null,
        )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (canResume) R.string.install_interrupted_title else R.string.install_stopped_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (presentation.usesJobMessage) {
                message
            } else {
                stringResource(requireNotNull(presentation.bodyRes), job.current.name)
            },
            color = colors.secondaryInk,
            textAlign = TextAlign.Center,
        )
    }
    if (canResume) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProblemStep(1, stringResource(R.string.install_resume_step_one), warning = true)
            ProblemStep(2, stringResource(R.string.install_resume_step_two))
            ProblemStep(3, stringResource(R.string.install_resume_step_three))
        }
    }
    when (presentation.action) {
        ProblemAction.MIDIMON -> {
            ProminentButton(
                SwitchingWords.installFirst("MIDIMON"),
                onClick = {
                    scope.launch {
                        isFindingFirstStep = true
                        firstStepProblem = null
                        try {
                            val firstStep =
                                FixedFirmware.find(
                                    model.library,
                                    model.catalogue.entries,
                                    HttpFetch(Dispatchers.IO)::invoke,
                                )
                            if (job.confirmsSwitchingFirmware) {
                                model.device.installFirst(firstStep)
                            } else {
                                model.device.dismissFlash()
                                model.requestPage(RequestedPage.Firmware(firstStep.id))
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            firstStepProblem = error.message ?: error::class.simpleName
                        } finally {
                            isFindingFirstStep = false
                        }
                    }
                },
                enabled = !isFindingFirstStep,
            )
            Text(
                firstStepProblem ?: SwitchingWords.installFirstDetail("MIDIMON", job.current.name),
                color = if (firstStepProblem == null) colors.secondaryInk else colors.warningInk,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        ProblemAction.RESUME,
        ProblemAction.RETRY,
        -> {
            ProminentButton(
                stringResource(
                    if (presentation.action == ProblemAction.RESUME) {
                        R.string.install_carry_on
                    } else {
                        R.string.install_try_again_button
                    },
                ),
                model.device::resume,
            )
        }

        ProblemAction.NONE -> {
            Unit
        }
    }
    if (presentation.showsClose) {
        SecondaryButton(stringResource(R.string.install_close), model.device::dismissFlash)
    }
    InstallDetails(message)
}

@Composable
private fun ProblemStep(
    number: Int,
    text: String,
    warning: Boolean = false,
) {
    val colors = LocalStandardColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            number.toString(),
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .clip(
                        CircleShape,
                    ).background(colors.control)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Text(text, Modifier.padding(start = 12.dp), color = if (warning) colors.warningInk else colors.ink)
    }
}

internal fun showsStillMono(
    reported: String,
    startedOnV16: Boolean,
): Boolean = reported == "FM-1_015" && startedOnV16

@Composable
private fun InstallDetails(message: String) {
    val model = LocalAppModel.current
    val log by model.device.log.collectAsState()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    SecondaryButton(
        stringResource(if (expanded) R.string.install_hide_details else R.string.install_show_details),
        onClick = { expanded = !expanded },
    )
    if (expanded) {
        val details = remember(message, log) { listOf(message).plus(log).joinToString("\n") }
        Text(details, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        SecondaryButton(
            stringResource(R.string.install_copy_details),
            onClick = {
                context
                    .getSystemService(
                        ClipboardManager::class.java,
                    ).setPrimaryClip(ClipData.newPlainText("FM-SWITCH install details", details))
            },
        )
    }
}
