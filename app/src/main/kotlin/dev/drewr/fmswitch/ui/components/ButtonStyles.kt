// Ported from FM-SWITCH App/Standard/Theme/Motion.swift.
package dev.drewr.fmswitch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.ui.theme.LocalReduceMotion
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import dev.drewr.fmswitch.ui.theme.Motion
import dev.drewr.fmswitch.ui.theme.Palette

@Composable
fun ProminentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colour: Color = Color(Palette.ACCENT),
) = StandardButton(text, onClick, modifier, enabled, colour, Color.White)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = StandardButton(
    text,
    onClick,
    modifier,
    enabled,
    LocalStandardColors.current.control,
    LocalStandardColors.current.ink,
)

@Composable
private fun StandardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    colour: Color,
    textColour: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed &&
            !LocalReduceMotion.current
        ) {
            .97f
        } else {
            1f
        },
        Motion.press,
        label = "button scale",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .scale(scale)
                .alpha(if (pressed) .85f else 1f),
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colour,
                contentColor = textColour,
                disabledContainerColor = LocalStandardColors.current.control,
                disabledContentColor = LocalStandardColors.current.secondaryInk,
            ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}
