// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.drewr.fmswitch.ui.firmwares.FirmwareListScreen
import dev.drewr.fmswitch.ui.firmwares.FirmwarePageScreen
import dev.drewr.fmswitch.ui.firmwares.MyFirmwaresScreen
import dev.drewr.fmswitch.ui.home.HomeScreen
import dev.drewr.fmswitch.ui.install.BeforeYouStartSheet
import dev.drewr.fmswitch.ui.install.InstallingOverlay
import dev.drewr.fmswitch.ui.install.OutcomeScreens
import dev.drewr.fmswitch.ui.settings.SettingsScreen
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import dev.drewr.fmswitch.ui.welcome.WelcomeNotice

@Composable
fun AppRoot(
    showWelcomeNotice: Boolean = true,
    onWelcomeOk: () -> Unit = {},
) {
    val navController = rememberNavController()
    var welcomeVisible by rememberSaveable(showWelcomeNotice) { mutableStateOf(showWelcomeNotice) }
    val navigator =
        remember(navController) {
            object : AppNavigator {
                override fun openFirmwares() = navController.navigate(Routes.FIRMWARES) { launchSingleTop = true }

                override fun openFirmware(entryId: String) =
                    navController.navigate(Routes.firmware(entryId)) {
                        launchSingleTop =
                            true
                    }

                override fun openMyFirmwares() = navController.navigate(Routes.MY_FIRMWARES) { launchSingleTop = true }

                override fun openSettings() {
                    navController.popBackStack(Routes.HOME, false)
                    navController.navigate(Routes.SETTINGS) {
                        launchSingleTop =
                            true
                    }
                }

                override fun openBeforeYouStart() =
                    navController.navigate(Routes.BEFORE_YOU_START) {
                        launchSingleTop =
                            true
                    }

                override fun openOutcome() = navController.navigate(Routes.OUTCOME) { launchSingleTop = true }

                override fun back() {
                    if (navController.previousBackStackEntry != null) navController.navigateUp()
                }
            }
        }
    Box(Modifier.fillMaxSize()) {
        Surface(color = LocalStandardColors.current.ground, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.safeDrawingPadding()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = if (welcomeVisible) Modifier.clearAndSetSemantics {} else Modifier,
                ) {
                    composable(Routes.HOME) { HomeScreen(navigator) }
                    composable(Routes.FIRMWARES) { FirmwareListScreen(navigator) }
                    composable(
                        Routes.FIRMWARE,
                        arguments =
                            listOf(
                                navArgument("entryId") { type = NavType.StringType },
                            ),
                    ) {
                        FirmwarePageScreen(
                            requireNotNull(it.arguments?.getString("entryId")),
                            navigator,
                        )
                    }
                    composable(Routes.MY_FIRMWARES) { MyFirmwaresScreen(navigator) }
                    composable(Routes.SETTINGS) { SettingsScreen(navigator) }
                    composable(Routes.BEFORE_YOU_START) { BeforeYouStartSheet(navigator) }
                    composable(Routes.OUTCOME) { OutcomeScreens(navigator) }
                }
            }
        }
        InstallingOverlay()
        if (welcomeVisible) {
            WelcomeNotice(onOk = {
                welcomeVisible = false
                onWelcomeOk()
            })
        }
    }
}
