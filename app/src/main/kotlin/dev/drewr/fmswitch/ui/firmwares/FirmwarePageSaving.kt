// Ported from FM-SWITCH App/Standard/Firmwares/FirmwarePageView+Saving.swift.
package dev.drewr.fmswitch.ui.firmwares

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.ReleaseVersion
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.ui.components.DangerButton
import dev.drewr.fmswitch.ui.components.ProminentButton
import dev.drewr.fmswitch.ui.components.SecondaryButton
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun FirmwarePageSaving(
    entry: CatalogueEntry,
    stored: StoredFirmware?,
    savedCopies: List<StoredFirmware>,
    isConnected: Boolean,
    isDownloading: Boolean,
    disabled: Boolean,
    library: FirmwareLibrary,
    onDownload: ((StoredFirmware) -> Unit) -> Unit,
    onInstall: (StoredFirmware) -> Unit,
    onError: (String?) -> Unit,
) {
    val earlier =
        savedCopies
            .filter { copy ->
                copy.id != stored?.id &&
                    (entry.version?.let { ReleaseVersion.isNewer(it, copy.version) } ?: true)
            }.sortedWith { first, second ->
                when {
                    ReleaseVersion.isNewer(first.version, second.version) -> -1
                    ReleaseVersion.isNewer(second.version, first.version) -> 1
                    else -> first.id.compareTo(second.id)
                }
            }
    var chosenEarlierId by remember { mutableStateOf<String?>(null) }
    val chosenEarlier = earlier.firstOrNull { it.id == chosenEarlierId }
    val installable = chosenEarlier ?: stored
    val shownVersion =
        if (earlier.isNotEmpty()) {
            " ${installable?.version ?: entry.version.orEmpty()}"
        } else {
            ""
        }

    if (earlier.isNotEmpty()) {
        VersionChoice(
            newest = entry.version ?: stringResource(R.string.firmware_version_newest),
            earlier = earlier,
            chosenEarlierId = chosenEarlier?.id,
            onChoose = { chosenEarlierId = it },
        )
    }
    ProminentButton(
        if (isDownloading) {
            stringResource(R.string.firmware_downloading)
        } else {
            stringResource(R.string.firmware_install_version, entry.name, shownVersion)
        },
        onClick = {
            if (installable == null) onDownload(onInstall) else onInstall(installable)
        },
        enabled = !disabled && !isDownloading,
    )
    if (installable == null) {
        SecondaryButton(
            stringResource(R.string.firmware_save_later_version, shownVersion),
            onClick = { onDownload { } },
            enabled = !disabled && !isDownloading,
        )
    }
    FirmwareInstallNote(
        installable = installable,
        isConnected = isConnected,
        library = library,
        onError = onError,
    )
}

@Composable
private fun VersionChoice(
    newest: String,
    earlier: List<StoredFirmware>,
    chosenEarlierId: String?,
    onChoose: (String?) -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .background(LocalStandardColors.current.control, RoundedCornerShape(12.dp))
        .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    VersionChoiceItem(
        label = newest,
        selected = chosenEarlierId == null,
        onClick = { onChoose(null) },
        modifier = Modifier.weight(1f),
    )
    earlier.forEach { copy ->
        VersionChoiceItem(
            label = copy.version,
            selected = chosenEarlierId == copy.id,
            onClick = { onChoose(copy.id) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VersionChoiceItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) = Text(
    label,
    modifier
        .background(
            if (selected) LocalStandardColors.current.card else LocalStandardColors.current.control,
            RoundedCornerShape(8.dp),
        ).clickable(role = Role.RadioButton, onClick = onClick)
        .semantics { role = Role.RadioButton }
        .padding(horizontal = 12.dp, vertical = 10.dp),
    color = LocalStandardColors.current.ink,
    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    style = MaterialTheme.typography.labelLarge,
)

@Composable
private fun FirmwareInstallNote(
    installable: StoredFirmware?,
    isConnected: Boolean,
    library: FirmwareLibrary,
    onError: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    when {
        installable != null && !library.canRemove(installable) -> {
            Text(
                stringResource(R.string.firmware_bundled_offline),
                color = LocalStandardColors.current.secondaryInk,
            )
        }

        installable != null -> {
            Text(
                stringResource(R.string.firmware_saved_ready),
                color = LocalStandardColors.current.secondaryInk,
            )
            DangerButton(
                stringResource(R.string.firmware_delete_saved_version),
                onClick = {
                    scope.launch {
                        try {
                            library.remove(installable)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            onError(error.message)
                        }
                    }
                },
            )
        }

        !isConnected -> {
            Text(
                stringResource(R.string.firmware_downloads_now),
                color = LocalStandardColors.current.secondaryInk,
            )
        }
    }
}
