// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
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
        holdLaunchScreenUntilReady()
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

/** Keeps the launch screen up until the app has loaded, then fades from it into the app. */
private fun ComponentActivity.holdLaunchScreenUntilReady() {
    val ready = (application as FmSwitchApplication).appModel
    val content = findViewById<View>(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (ready.value == null) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        },
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        splashScreen.setOnExitAnimationListener { splash ->
            // With animations turned off, Android skips the fade itself.
            splash
                .animate()
                .alpha(0f)
                .setDuration(LAUNCH_FADE_MILLIS)
                .withEndAction { splash.remove() }
                .start()
        }
    }
}

private const val LAUNCH_FADE_MILLIS = 250L

private fun dev.drewr.fmswitch.model.settings.Appearance.toThemeAppearance(): ThemeAppearance =
    when (this) {
        dev.drewr.fmswitch.model.settings.Appearance.System -> ThemeAppearance.SYSTEM
        dev.drewr.fmswitch.model.settings.Appearance.Light -> ThemeAppearance.LIGHT
        dev.drewr.fmswitch.model.settings.Appearance.Dark -> ThemeAppearance.DARK
    }
