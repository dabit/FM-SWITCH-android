package dev.drewr.fmswitch.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.ui.navigation.AppNavigator

object SettingsScreen {
    @Composable operator fun invoke(navigator: AppNavigator) {
        Text(stringResource(R.string.title_settings))
    }
}
