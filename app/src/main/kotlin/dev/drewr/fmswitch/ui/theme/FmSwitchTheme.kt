// Ported from FM-SWITCH App/Standard/Theme/Palette.swift.
package dev.drewr.fmswitch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

enum class Appearance { SYSTEM, LIGHT, DARK }

data class StandardColors(
    val ground: Color,
    val card: Color,
    val control: Color,
    val line: Color,
    val ink: Color,
    val secondaryInk: Color,
    val accentText: Color,
    val firmwaresTint: Color,
    val good: Color,
    val goodInk: Color,
    val goodBackground: Color,
    val warningInk: Color,
    val warningBackground: Color,
    val danger: Color,
)

val LocalStandardColors =
    staticCompositionLocalOf {
        StandardColors(
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
            Color.Unspecified,
        )
    }

@Composable
fun FmSwitchTheme(
    appearance: Appearance = Appearance.SYSTEM,
    onAppearanceResolved: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val systemDark =
        (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    val dark = appearance == Appearance.DARK || (appearance == Appearance.SYSTEM && systemDark)
    SideEffect { onAppearanceResolved(dark) }
    val colors = standardColors(dark)
    val material =
        if (dark) {
            darkColorScheme(
                primary = Color(Palette.ACCENT),
                onPrimary = Color.White,
                background = colors.ground,
                onBackground = colors.ink,
                surface = colors.card,
                onSurface = colors.ink,
            )
        } else {
            lightColorScheme(
                primary = Color(Palette.ACCENT),
                onPrimary = Color.White,
                background = colors.ground,
                onBackground = colors.ink,
                surface = colors.card,
                onSurface = colors.ink,
            )
        }
    CompositionLocalProvider(LocalStandardColors provides colors) {
        ProvideReduceMotion {
            MaterialTheme(colorScheme = material, content = content)
        }
    }
}

private fun standardColors(dark: Boolean): StandardColors =
    StandardColors(
        ground = Color(if (dark) Palette.GROUND_DARK else Palette.GROUND_LIGHT),
        card = Color(if (dark) Palette.CARD_DARK else Palette.CARD_LIGHT),
        control = Color(if (dark) Palette.CONTROL_DARK else Palette.CONTROL_LIGHT),
        line = Color(if (dark) Palette.LINE_DARK else Palette.LINE_LIGHT),
        ink = Color(if (dark) Palette.INK_DARK else Palette.INK_LIGHT),
        secondaryInk = Color(if (dark) Palette.SECONDARY_INK_DARK else Palette.SECONDARY_INK_LIGHT),
        accentText = Color(if (dark) Palette.ACCENT_TEXT_DARK else Palette.ACCENT_TEXT_LIGHT),
        firmwaresTint = Color(if (dark) Palette.FIRMWARES_TINT_DARK else Palette.FIRMWARES_TINT_LIGHT),
        good = Color(if (dark) Palette.GOOD_DARK else Palette.GOOD_LIGHT),
        goodInk = Color(if (dark) Palette.GOOD_INK_DARK else Palette.GOOD_INK_LIGHT),
        goodBackground = Color(if (dark) Palette.GOOD_BACKGROUND_DARK else Palette.GOOD_BACKGROUND_LIGHT),
        warningInk = Color(if (dark) Palette.WARNING_INK_DARK else Palette.WARNING_INK_LIGHT),
        warningBackground = Color(if (dark) Palette.WARNING_BACKGROUND_DARK else Palette.WARNING_BACKGROUND_LIGHT),
        danger = Color(if (dark) Palette.DANGER_DARK else Palette.DANGER_LIGHT),
    )
