// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.RequestedPage
import dev.drewr.fmswitch.ui.firmwares.FirmwareListScreen
import dev.drewr.fmswitch.ui.firmwares.FirmwarePageScreen
import dev.drewr.fmswitch.ui.firmwares.MyFirmwaresScreen
import dev.drewr.fmswitch.ui.home.HomeScreen
import dev.drewr.fmswitch.ui.install.BeforeYouStartSheet
import dev.drewr.fmswitch.ui.install.InstallingOverlay
import dev.drewr.fmswitch.ui.install.OutcomeScreens
import dev.drewr.fmswitch.ui.settings.SettingsScreen
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import dev.drewr.fmswitch.ui.welcome.SAFETY_NOTICE_VERSION
import dev.drewr.fmswitch.ui.welcome.WelcomeNotice

@Composable
fun AppRoot() {
    val model = LocalAppModel.current
    val job by model.device.job.collectAsState()
    val requestedPage by model.requestedPage.collectAsState()
    val navController = rememberNavController()
    var welcomeVisible by rememberSaveable {
        mutableStateOf(model.settings.acknowledgedNoticeVersion < SAFETY_NOTICE_VERSION)
    }
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

                override fun openBeforeYouStart(
                    targetId: String,
                    requiresOfficial: Boolean,
                ) = navController.navigate(Routes.beforeYouStart(targetId, requiresOfficial)) {
                    launchSingleTop =
                        true
                }

                override fun openOutcome() = navController.navigate(Routes.OUTCOME) { launchSingleTop = true }

                override fun back() {
                    if (navController.previousBackStackEntry != null) navController.navigateUp()
                }
            }
        }
    LaunchedEffect(requestedPage) {
        when (val page = requestedPage) {
            is RequestedPage.Firmware -> navigator.openFirmware(page.id)
            RequestedPage.Settings -> navigator.openSettings()
            null -> return@LaunchedEffect
        }
        model.clearRequestedPage()
    }
    Box(Modifier.fillMaxSize()) {
        Surface(color = LocalStandardColors.current.ground, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.safeDrawingPadding()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = if (welcomeVisible || job != null) Modifier.clearAndSetSemantics {} else Modifier,
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
                    composable(
                        Routes.BEFORE_YOU_START,
                        arguments =
                            listOf(
                                navArgument("targetId") { type = NavType.StringType },
                                navArgument("requiresOfficial") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                },
                            ),
                    ) {
                        BeforeYouStartSheet(
                            requireNotNull(it.arguments?.getString("targetId")),
                            it.arguments?.getBoolean("requiresOfficial") ?: false,
                            navigator,
                        )
                    }
                    composable(Routes.OUTCOME) { OutcomeScreens(navigator) }
                }
            }
        }
        if (job != null) InstallingOverlay()
        if (welcomeVisible) {
            WelcomeNotice(onOk = {
                welcomeVisible = false
                model.settings.acknowledgedNoticeVersion = SAFETY_NOTICE_VERSION
            })
        }
    }
}
