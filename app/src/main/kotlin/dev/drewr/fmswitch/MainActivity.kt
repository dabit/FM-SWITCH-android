// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.drewr.fmswitch.ui.navigation.AppRoot
import dev.drewr.fmswitch.ui.theme.FmSwitchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FmSwitchTheme(onAppearanceResolved = { dark ->
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
                AppRoot()
            }
        }
    }
}
