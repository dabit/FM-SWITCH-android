package dev.drewr.fmswitch.ui.install

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.drewr.fmswitch.ui.navigation.AppNavigator

object BeforeYouStartSheet {
    @Composable operator fun invoke(navigator: AppNavigator) {
        Text("Before you start")
    }
}
