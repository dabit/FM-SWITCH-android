// Ported from FM-SWITCH App/Standard/Firmwares/FirmwareBadge.swift.
package dev.drewr.fmswitch.ui.firmwares

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.catalogue.FirmwareBadgeDesign
import dev.drewr.fmswitch.model.catalogue.badge
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import dev.drewr.fmswitch.ui.theme.Palette
import kotlin.math.pow

@Composable
fun FirmwareBadge(entry: CatalogueEntry) = FirmwareBadge(entry.badge)

@Composable
fun FirmwareBadge(design: FirmwareBadgeDesign) {
    val shape = RoundedCornerShape(12.dp)
    val colors = LocalStandardColors.current
    val dark = colors.card == Color(Palette.CARD_DARK)
    val needsOutline =
        dark && listOf(design.first, design.second).any { contrast(it, Palette.CARD_DARK.toUInt()) < 1.6 }
    val textStyle =
        if (design.letters.length <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(
        Modifier
            .size(48.dp)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (needsOutline) {
                    drawRoundRect(
                        Color.White.copy(alpha = .35f),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(12.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(design.second.toLong() or 0xFF000000))
            val first = Color(design.first.toLong() or 0xFF000000)
            if (design.split == FirmwareBadgeDesign.Split.Across) {
                drawRect(first, size = size.copy(height = size.height / 2))
            } else {
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(0f, size.height)
                        close()
                    },
                    first,
                )
            }
        }
        Text(
            design.letters,
            Modifier.clearAndSetSemantics {},
            color = Color(design.ink.toLong() or 0xFF000000),
            style = textStyle.copy(fontSize = textStyle.fontSize / fontScale),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun contrast(
    first: UInt,
    second: UInt,
): Double {
    fun luminance(rgb: UInt): Double {
        fun channel(shift: Int): Double {
            val value = ((rgb shr shift) and 0xFFu).toDouble() / 255
            return if (value <= .03928) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    return (maxOf(luminance(first), luminance(second)) + .05) / (minOf(luminance(first), luminance(second)) + .05)
}
