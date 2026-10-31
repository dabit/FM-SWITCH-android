// Ported from FM-SWITCH App/Standard/Theme/Motion.swift.
package dev.drewr.fmswitch.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalReduceMotion = staticCompositionLocalOf { false }

object Motion {
    val standard: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    val press: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
    val fade: FiniteAnimationSpec<Float> = tween(200, easing = EaseInOut)
    val flick: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    val pulse = infiniteRepeatable<Float>(tween(500, easing = EaseInOut), RepeatMode.Reverse)

    fun standard(reducingMotion: Boolean): FiniteAnimationSpec<Float> = if (reducingMotion) fade else standard
}

@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reducingMotion =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    CompositionLocalProvider(LocalReduceMotion provides reducingMotion, content = content)
}
