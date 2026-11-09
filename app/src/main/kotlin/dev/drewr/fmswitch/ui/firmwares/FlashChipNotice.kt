// Ported from FM-SWITCH App/Standard/Firmwares/FlashChipNotice.swift.
package dev.drewr.fmswitch.ui.firmwares

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.model.catalogue.FlashChipWords
import dev.drewr.fmswitch.ui.theme.LocalStandardColors

@Composable
fun FlashChipNotice(
    names: List<String>,
    hasNewerFlashChip: Boolean,
) {
    val colors = LocalStandardColors.current
    var showsHowToTell by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            FlashChipWords.warning(names, hasNewerFlashChip),
            Modifier
                .fillMaxWidth()
                .background(colors.warningBackground, RoundedCornerShape(12.dp))
                .padding(12.dp),
            color = colors.warningInk,
        )
        if (!hasNewerFlashChip) {
            TextButton(onClick = { showsHowToTell = !showsHowToTell }) {
                Text(FlashChipWords.HOW_TO_TELL_QUESTION, fontWeight = FontWeight.SemiBold)
            }
            if (showsHowToTell) {
                Text(
                    FlashChipWords.HOW_TO_TELL,
                    color = colors.ink,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
