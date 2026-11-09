// Ported from FM-SWITCH App/Catalogue/FirmwareDescription.swift.
package dev.drewr.fmswitch.ui.firmwares

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.drewr.fmswitch.model.catalogue.FirmwareDescription

fun annotated(runs: List<FirmwareDescription.Run>): AnnotatedString =
    buildAnnotatedString {
        runs.forEach { run ->
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    fontFamily = if (run.code) FontFamily.Monospace else null,
                ),
            ) {
                append(run.text)
            }
        }
    }
