// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.drewr.fmswitch.ui.navigation.AppRoot
import dev.drewr.fmswitch.ui.theme.FmSwitchTheme
import dev.drewr.fmswitch.ui.theme.Appearance as ThemeAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appModel by (application as FmSwitchApplication).appModel.collectAsState()
            appModel ?: return@setContent
            val model = requireNotNull(appModel)
            val appearance by model.settings.appearanceFlow.collectAsState()
            FmSwitchTheme(appearance = appearance.toThemeAppearance(), onAppearanceResolved = { dark ->
                enableEdgeToEdge(
                    statusBarStyle =
                        if (dark) {
                            SystemBarStyle.dark(
                                android.graphics.Color.TRANSPARENT,
                            )
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            )
                        },
                    navigationBarStyle =
                        if (dark) {
                            SystemBarStyle.dark(
                                android.graphics.Color.TRANSPARENT,
                            )
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            )
                        },
                )
            }) {
                CompositionLocalProvider(LocalAppModel provides model) { AppRoot() }
            }
        }
    }
}

private fun dev.drewr.fmswitch.model.settings.Appearance.toThemeAppearance(): ThemeAppearance =
    when (this) {
        dev.drewr.fmswitch.model.settings.Appearance.System -> ThemeAppearance.SYSTEM
        dev.drewr.fmswitch.model.settings.Appearance.Light -> ThemeAppearance.LIGHT
        dev.drewr.fmswitch.model.settings.Appearance.Dark -> ThemeAppearance.DARK
    }
