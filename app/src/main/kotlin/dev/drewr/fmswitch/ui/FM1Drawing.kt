// Ported from FM-SWITCH App/Standard/FM1Drawing.swift.
package dev.drewr.fmswitch.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.ui.theme.LocalReduceMotion
import dev.drewr.fmswitch.ui.theme.Motion

enum class FM1Key(
    val label: String,
) {
    PLAY("PLAY"),
    REC("REC"),
    OCTAVE_DOWN("OCT−"),
}

@Composable
fun FM1Drawing(
    line1: String,
    modifier: Modifier = Modifier,
    line2: String = "",
    litKey: FM1Key? = null,
    progress: Float? = null,
    screenTint: Color = Color(0xFF9BE7B4),
    onScreenTap: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val screen = listOf(line1, line2).filter(String::isNotBlank).joinToString(" ")
    val description =
        listOfNotNull(
            stringResource(R.string.fm1_description),
            screen.takeIf(String::isNotBlank)?.let {
                stringResource(R.string.fm1_screen_description, it)
            },
            litKey?.let { stringResource(R.string.fm1_lit_key_description, it.label) },
        ).joinToString(", ")
    val pulse =
        if (litKey ==
            null
        ) {
            0f
        } else if (LocalReduceMotion.current) {
            1f
        } else {
            rememberInfiniteTransition("key glow").animateFloat(.4f, .8f, Motion.pulse, "key glow").value
        }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(372f / 200f)
            .semantics {
                contentDescription = description
                role =
                    Role.Image
            }.pointerInput(onScreenTap) {
                detectTapGestures { p ->
                    if (p.x in
                        size.width * .22f..size.width * .52f &&
                        p.y < size.height * .6f
                    ) {
                        onScreenTap?.invoke()
                    }
                }
            },
    ) {
        fun x(v: Float) = v * size.width / 372f

        fun box(
            c: Color,
            l: Float,
            t: Float,
            w: Float,
            h: Float,
            r: Float = 5f,
        ) = drawRoundRect(c, Offset(x(l), x(t)), Size(x(w), x(h)), CornerRadius(x(r)))

        fun label(
            value: String,
            l: Float,
            t: Float,
            w: Float,
            h: Float,
            size: Float,
            color: Color,
            weight: FontWeight,
            lines: Int = 1,
        ) {
            val c = Constraints(maxWidth = x(w).toInt(), maxHeight = x(h).toInt())

            fun measured(scale: Float) =
                measurer.measure(
                    AnnotatedString(value),
                    style =
                        TextStyle(
                            fontSize =
                                (
                                    size *
                                        scale
                                ).sp,
                            color = color,
                            fontWeight = weight,
                        ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = lines,
                    constraints = c,
                )
            var result = measured(1f)
            if (result.multiParagraph.didExceedMaxLines) result = measured(.8f)
            clipRect(x(l), x(t), x(l + w), x(t + h)) {
                drawText(result, topLeft = Offset(x(l) + (x(w) - result.size.width) / 2, x(t)))
            }
        }

        fun key(
            name: String,
            lit: Boolean,
            l: Float,
            t: Float,
            w: Float,
            h: Float,
            s: Float,
        ) {
            if (lit) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = pulse * .35f), Color.Transparent)),
                    x(9f),
                    Offset(
                        x(l + w / 2),
                        x(t + h / 2),
                    ),
                )
            }
            box(if (lit) Color(0xFF4A4E53) else Color(0xFF33363A), l, t, w, h)
            if (lit) {
                drawRoundRect(
                    Color.White.copy(alpha = .9f),
                    Offset(x(l), x(t)),
                    Size(x(w), x(h)),
                    CornerRadius(x(5f)),
                    style = Stroke(x(1.5f)),
                )
            }
            label(
                name,
                l,
                t,
                w,
                h,
                s,
                when (name) {
                    "PLAY" -> Color(0xFF8FE36B)
                    "REC" -> Color(0xFFFF7A6E)
                    "HOME" -> Color.White
                    else -> if (lit) Color.White else Color(0xFFC8CBCE)
                },
                FontWeight.Bold,
            )
        }
        box(Color(0xFF4A4E52), 0f, 0f, 372f, 200f, 22f)
        repeat(4) { box(Color(0xFF6E7276), 12f + it % 2 * 25f, 18f + it / 2 * 25f, 18f, 18f, 9f) }
        listOf("OCT−", "OCT+").forEachIndexed { i, n -> key(n, litKey?.label == n, 10f + i * 25f, 78f, 22f, 22f, 9f) }
        box(Color(0xFF12171A), 82f, 18f, 98f, 92f, 10f)
        drawRoundRect(
            Color(0xFF2B2F33),
            Offset(x(82f), x(18f)),
            Size(x(98f), x(92f)),
            CornerRadius(x(10f)),
            style = Stroke(x(3f)),
        )
        label(line1, 92f, 31f, 78f, if (line2.isBlank()) 52f else 27f, 13f, screenTint, FontWeight.SemiBold, 2)
        if (line2.isNotBlank()) {
            label(
                line2,
                92f,
                61f,
                78f,
                22f,
                11.5f,
                screenTint.copy(alpha = .8f),
                FontWeight.Normal,
                2,
            )
        }
        progress?.let {
            box(Color.White.copy(alpha = .15f), 92f, 88f, 78f, 6f, 3f)
            box(
                screenTint,
                92f,
                88f,
                78f * it.coerceIn(0f, 1f),
                6f,
                3f,
            )
        }
        repeat(4) { box(Color(0xFF6E7276), 198f + it * 34f, 18f, 18f, 18f, 9f) }
        listOf("FX", "SEL", "ENV", "LFO", "EDIT", "GLO", "HOME", "SAVE", "ARP", "SEQ", "PLAY", "REC").forEachIndexed {
            i,
            n,
            ->
            key(
                n,
                litKey?.label == n,
                198f + i % 6 * 27f,
                54f + i / 6 * 28f,
                23f,
                22f,
                8f,
            )
        }
        box(Color(0xFF26292C), 8f, 126f, 356f, 58f, 12f)
        repeat(16) { box(Color(0xFF33363A), 18f + it * 21f, 135f, 17f, 38f, 4f) }
    }
}
