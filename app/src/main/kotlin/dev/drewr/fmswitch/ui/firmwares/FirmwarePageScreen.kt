// Ported from FM-SWITCH App/Standard/Firmwares/FirmwarePageView.swift.
package dev.drewr.fmswitch.ui.firmwares

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.drewr.fm1.protocol.Fm1FlashChip
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.catalogue.CatalogueLink
import dev.drewr.fmswitch.model.catalogue.DescriptionSource
import dev.drewr.fmswitch.model.catalogue.FirmwareDescription
import dev.drewr.fmswitch.model.catalogue.HubFlashChip
import dev.drewr.fmswitch.model.catalogue.badge
import dev.drewr.fmswitch.model.catalogue.isFromFile
import dev.drewr.fmswitch.model.catalogue.listedOn
import dev.drewr.fmswitch.model.catalogue.packageSource
import dev.drewr.fmswitch.model.catalogue.sourceBuildSummary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

object FirmwarePageScreen {
    @Composable
    operator fun invoke(
        entryId: String,
        navigator: AppNavigator,
    ) {
        val model = LocalAppModel.current
        val catalogue by model.catalogue.catalogueFlow.collectAsState()
        val identity by model.device.identity.collectAsState()
        val flashChipId by model.device.flashChipId.collectAsState()
        val saved by model.library.itemsFlow.collectAsState()
        val downloads by model.firmwareDownloads.collectAsState()
        var detailsExpanded by remember { mutableStateOf(false) }
        val entry =
            model.catalogue.entries.firstOrNull { it.id == entryId }
                ?: saved.firstOrNull { "file-${it.sha256}" == entryId }?.let(CatalogueEntry::addedFrom)
                ?: run {
                    Text(
                        stringResource(
                            if (catalogue == null) R.string.firmware_loading else R.string.firmware_not_found,
                        ),
                    )
                    return
                }
        val context = LocalContext.current
        val stored = model.library.stored(entry, saved)
        val badge = entry.badge
        var linkError by remember { mutableStateOf<String?>(null) }
        val download = downloads[entry.id]
        val isDownloading = download?.isDownloading == true
        val errorMessage = download?.errorMessage ?: linkError
        val linkUnavailable = stringResource(R.string.firmware_link_unavailable)
        val back = stringResource(R.string.back)
        val disabled = entry.needsBuildChoice && stored == null
        Column(Modifier.background(LocalStandardColors.current.ground).verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics
                            .Color(badge.strongerHalf.toLong() or 0xFF000000),
                    ).padding(20.dp),
            ) {
                Text(
                    "←",
                    Modifier
                        .size(48.dp)
                        .wrapContentSize(Alignment.Center)
                        .clickable(role = Role.Button, onClick = navigator::back)
                        .semantics { contentDescription = back },
                    style = MaterialTheme.typography.headlineMedium,
                    color =
                        androidx.compose.ui.graphics
                            .Color(badge.ink.toLong() or 0xFF000000),
                )
                Text(
                    entry.name.ifBlank { stringResource(R.string.title_firmware) },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color =
                        androidx.compose.ui.graphics
                            .Color(badge.ink.toLong() or 0xFF000000),
                )
                entry.version?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                            androidx.compose.ui.graphics
                                .Color(badge.ink.toLong() or 0xFF000000),
                    )
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                (entry.author ?: stored?.author)?.let {
                    Text(
                        stringResource(R.string.firmware_by, it),
                        color = LocalStandardColors.current.secondaryInk,
                    )
                }
                entry.what?.let { Text(annotated(FirmwareDescription.runs(it))) }
                if (entry.whatSource == DescriptionSource.GitHub) {
                    Text(
                        stringResource(R.string.firmware_github_description),
                        color = LocalStandardColors.current.secondaryInk,
                    )
                }
                if (entry.isFromFile) {
                    Warning(stringResource(R.string.firmware_from_file_warning))
                } else if (!entry.isHardwareTested) {
                    Warning(stringResource(R.string.firmware_untested_warning))
                }
                if (entry.flashChip == HubFlashChip.Old && flashChipId != Fm1FlashChip.P25Q80) {
                    FlashChipNotice(
                        names = listOf(entry.name),
                        hasNewerFlashChip = flashChipId == Fm1FlashChip.UC25HQ80,
                    )
                }
                if (disabled) {
                    Text(
                        stringResource(R.string.firmware_needs_build_choice),
                        color = LocalStandardColors.current.secondaryInk,
                    )
                }
                if (stored != null || entry.isDownloadable) {
                    FirmwarePageSaving(
                        entry = entry,
                        stored = stored,
                        savedCopies = model.library.pageCopies(entry, saved),
                        isConnected = identity != null,
                        isDownloading = isDownloading,
                        disabled = disabled,
                        library = model.library,
                        onDownload = { onSaved ->
                            linkError = null
                            model.clearFirmwareDownloadError(entry.id)
                            model.downloadFirmware(entry, onSaved)
                        },
                        onInstall = { item ->
                            navigator.openBeforeYouStart(item.id, entry.requiresOfficialFirmware)
                        },
                        onError = { linkError = it },
                    )
                    if (isDownloading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.firmware_downloading),
                                color = LocalStandardColors.current.secondaryInk,
                            )
                        }
                    }
                    if (stored != null) {
                        model.library.url(stored)?.let { file ->
                            SaveACopy(file) { linkError = it }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.firmware_no_download),
                        color = LocalStandardColors.current.secondaryInk,
                    )
                }
                errorMessage?.let { Warning(it) }
                entry.pageLinks().forEach { link ->
                    PageLink("${link.displayLabel()} ↗", link.url) {
                        try {
                            CustomTabsIntent.Builder().build().launchUrl(context, link.url.toUri())
                        } catch (_: ActivityNotFoundException) {
                            linkError = linkUnavailable
                        }
                    }
                }
                Details(entry, stored, detailsExpanded) { detailsExpanded = !detailsExpanded }
            }
        }
    }
}

@Composable private fun Warning(text: String) =
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .background(
                LocalStandardColors.current.warningBackground,
                RoundedCornerShape(12.dp),
            ).padding(12.dp),
        color = LocalStandardColors.current.warningInk,
    )

@Composable
private fun Details(
    entry: CatalogueEntry,
    stored: StoredFirmware?,
    expanded: Boolean,
    onToggle: () -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(LocalStandardColors.current.card, RoundedCornerShape(16.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    val label = stringResource(R.string.firmware_details)
    val state = stringResource(if (expanded) R.string.firmware_expanded else R.string.firmware_collapsed)
    TextButton(
        onToggle,
        Modifier.fillMaxWidth().semantics {
            role = Role.Button
            contentDescription = label
            stateDescription = state
        },
    ) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(if (expanded) "−" else "+")
    }
    if (expanded) {
        Detail(
            stringResource(R.string.firmware_reports),
            entry.product ?: stored?.product ?: stringResource(R.string.firmware_known_downloaded),
        )
        (entry.size ?: stored?.size)?.let {
            Detail(
                stringResource(R.string.firmware_size),
                android.text.format.Formatter
                    .formatShortFileSize(LocalContext.current, it.toLong()),
            )
        }
        entry.date?.let { Detail(stringResource(R.string.firmware_released), it) }
        stored?.let {
            Detail(
                stringResource(R.string.firmware_saved_date),
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(it.savedAt)),
            )
        }
        if (entry.kinds.isNotEmpty()) Detail(stringResource(R.string.firmware_kind), entry.kinds.joinToString())
        entry.licence?.let { Detail(stringResource(R.string.firmware_licence), it) }
        entry.stars?.let { Detail(stringResource(R.string.firmware_github_stars), it.toString()) }
        entry.installs?.let {
            Detail(
                stringResource(R.string.firmware_installs),
                stringResource(
                    R.string.firmware_installs_value,
                    NumberFormat.getIntegerInstance().format(it),
                ),
            )
        }
        Detail(stringResource(R.string.firmware_recovery), entry.recoverySummary())
        Detail(stringResource(R.string.firmware_listed_on), entry.listedOn)
        if (!entry.isFromFile) {
            Detail(
                stringResource(R.string.firmware_tested),
                if (entry.isHardwareTested) {
                    stringResource(
                        R.string.firmware_yes,
                    )
                } else {
                    stringResource(R.string.firmware_in_testing)
                },
            )
        }
        entry.flashChip?.let { Detail(stringResource(R.string.firmware_saves_on), it.savesOn) }
        Detail(stringResource(R.string.firmware_built_from_source), entry.sourceBuildSummary)
        Detail(stringResource(R.string.firmware_file_from), entry.packageSource)
        Detail(
            stringResource(R.string.firmware_sha256),
            entry.sha256?.take(16)?.plus("…") ?: stringResource(R.string.firmware_not_published),
        )
        if (entry.requiresOfficialFirmware) {
            Detail(
                stringResource(R.string.firmware_installs),
                stringResource(R.string.firmware_installs_official),
            )
        }
    }
}

@Composable
private fun CatalogueLink.displayLabel() =
    if (label.equals("Install", ignoreCase = true)) stringResource(R.string.firmware_web_installer) else label

@Composable
private fun CatalogueEntry.pageLinks() =
    sourceBuild
        ?.takeIf { isBuiltFromSource && it.run != null }
        ?.let {
            links +
                CatalogueLink(
                    stringResource(R.string.firmware_signed_build, it.shortCommit),
                    requireNotNull(it.run),
                )
        }
        ?: links

@Composable
private fun CatalogueEntry.recoverySummary() =
    when (id) {
        "choralroot" -> {
            stringResource(R.string.firmware_recovery_octave_down_since, "0.16")
        }

        "dinghy", "etch", "floyd", "ghoulbox", "jangada", "sloop", "sloopbanks", "sloopdx", "winampfm", "zp12" -> {
            stringResource(R.string.firmware_recovery_octave_down)
        }

        "fimba", "fumi" -> {
            stringResource(R.string.firmware_recovery_both_octaves)
        }

        "felucca" -> {
            stringResource(R.string.firmware_recovery_none, "1.6.1")
        }

        "mvave-official" -> {
            stringResource(R.string.firmware_recovery_none, "V15")
        }

        "melodee" -> {
            stringResource(R.string.firmware_recovery_octave_down_since, "1.3.5")
        }

        else -> {
            stringResource(R.string.firmware_not_known)
        }
    }

@Composable private fun Detail(
    label: String,
    value: String,
) = Text("$label: $value", color = LocalStandardColors.current.secondaryInk, style = MaterialTheme.typography.bodySmall)

/** A link: tapping opens it, and pressing and holding shares it, as on iPhone. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageLink(
    label: String,
    url: String,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val shareLabel = stringResource(R.string.firmware_share_link)
    val share = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url)
        context.startActivity(Intent.createChooser(send, null))
    }
    Text(
        label,
        Modifier
            .heightIn(min = 48.dp)
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = share)
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(shareLabel) {
                            share()
                            true
                        },
                    )
            }.padding(horizontal = 12.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Copies a saved firmware to wherever the person picks, so it can be kept or shared. */
@Composable
private fun SaveACopy(
    file: java.io.File,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.firmware_copy_failed)
    val saveTo =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val copied =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { it.copyTo(out) }
                            } ?: error("no stream")
                        }.isSuccess
                    }
                onError(if (copied) null else failed)
            }
        }
    TextButton({ saveTo.launch(file.name) }) { Text(stringResource(R.string.firmware_save_copy)) }
}
