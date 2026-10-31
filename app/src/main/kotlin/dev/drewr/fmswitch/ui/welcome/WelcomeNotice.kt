// Ported from FM-SWITCH App/Standard/Home/WelcomeNoticeView.swift.
package dev.drewr.fmswitch.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.ui.FM1Drawing
import dev.drewr.fmswitch.ui.components.ProminentButton
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.delay

const val SAFETY_NOTICE_VERSION = 2

@Composable
fun WelcomeNotice(onOk: () -> Unit) {
    val title = stringResource(R.string.before_you_start)
    var secondsLeft by remember { mutableIntStateOf(4) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(LocalStandardColors.current.ground)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = title }
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FM1Drawing(
                line1 = stringResource(R.string.welcome_screen_line_one),
                line2 = stringResource(R.string.welcome_screen_line_two),
            )
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.before_you_start), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.safety_notice_text))
            Text(stringResource(R.string.safety_notice_recovery))
            Text(
                stringResource(R.string.welcome_settings_note),
                style = MaterialTheme.typography.bodySmall,
                color = LocalStandardColors.current.secondaryInk,
            )
        }
        Spacer(Modifier.height(24.dp))
        ProminentButton(
            if (secondsLeft >
                0
            ) {
                stringResource(R.string.welcome_ok_countdown, secondsLeft)
            } else {
                stringResource(R.string.welcome_ok)
            },
            enabled =
                secondsLeft == 0,
            onClick = onOk,
        )
    }
}
