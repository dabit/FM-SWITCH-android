// Ported from FM-SWITCH App/Standard/Firmwares/FirmwareListView.swift.
package dev.drewr.fmswitch.ui.firmwares

import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.catalogue.FirmwareFilter
import dev.drewr.fmswitch.model.catalogue.FirmwareOrder
import dev.drewr.fmswitch.model.catalogue.FirmwareScope
import dev.drewr.fmswitch.model.catalogue.isFromFile
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalReduceMotion
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FirmwareListScreen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    operator fun invoke(navigator: AppNavigator) {
        val model = LocalAppModel.current
        val catalogue by model.catalogue.catalogueFlow.collectAsState()
        val refreshing by model.catalogue.isRefreshingFlow.collectAsState()
        val error by model.catalogue.errorMessageFlow.collectAsState()
        val saved by model.library.itemsFlow.collectAsState()
        val context = LocalContext.current
        val resources = LocalResources.current
        val scope = rememberCoroutineScope()
        var importMessage by remember { mutableStateOf<String?>(null) }
        var search by remember { mutableStateOf("") }
        var selectedScopeKey by rememberSaveable { mutableStateOf("all") }
        val selectedScope = savedScope(selectedScopeKey)
        val configuredOrder = model.settings.firmwareOrder
        val back = stringResource(R.string.back)
        var order by remember { mutableStateOf(configuredOrder) }
        LaunchedEffect(configuredOrder) { order = configuredOrder }
        val savedCatalogueIds = saved.mapNotNull { it.catalogueID }
        val visible =
            FirmwareFilter().visible(
                model.catalogue.entries,
                model.settings.showBeta,
                model.settings.showLinksOnly,
                savedCatalogueIds,
            )
        val entries =
            order.sorted(
                FirmwareFilter(search).matching(visible, model.settings.showBeta).filter(selectedScope::includes),
            )
        val importer =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    try {
                        val (bytes, name) =
                            withContext(Dispatchers.IO) {
                                val displayName =
                                    context.contentResolver
                                        .query(uri, null, null, null, null)
                                        ?.use { cursor ->
                                            cursor
                                                .takeIf { it.moveToFirst() }
                                                ?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                                ?.takeIf { it >= 0 }
                                                ?.let(cursor::getString)
                                        }.orEmpty()
                                        .removeSuffix(".fwsc")
                                val file =
                                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        ?: error(resources.getString(R.string.firmware_could_not_open_file))
                                file to displayName.ifBlank { resources.getString(R.string.firmware_error_title) }
                            }
                        val item = model.library.add(bytes, name)
                        importMessage =
                            when {
                                item.source == StoredFirmware.Source.Official -> {
                                    resources.getString(
                                        R.string.firmware_added_official,
                                    )
                                }

                                model.catalogue.entries.any {
                                    it.sha256.equals(item.sha256, true)
                                } -> {
                                    resources.getString(R.string.firmware_added_saved, item.name)
                                }

                                else -> {
                                    resources.getString(R.string.firmware_added_unknown, item.name)
                                }
                            }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        importMessage = error.message ?: resources.getString(R.string.firmware_import_failed)
                    }
                }
            }
        Column(Modifier.background(LocalStandardColors.current.ground)) {
            TopAppBar(
                title = { Text(stringResource(R.string.title_firmwares)) },
                navigationIcon = {
                    Text(
                        "←",
                        Modifier
                            .size(48.dp)
                            .wrapContentSize(Alignment.Center)
                            .clickable(role = Role.Button, onClick = navigator::back)
                            .semantics { contentDescription = back },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    Text(
                        stringResource(R.string.firmware_add_file),
                        Modifier
                            .clickable(role = Role.Button) { importer.launch(arrayOf("*/*")) }
                            .padding(16.dp),
                        color = LocalStandardColors.current.accentText,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalStandardColors.current.ground),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { model.catalogue.refresh() } },
            ) {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OutlinedTextField(
                            search,
                            { search = it },
                            Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.firmware_search,
                                        visible.size,
                                        visible.size,
                                    ),
                                )
                            },
                            singleLine = true,
                        )
                    }
                    item {
                        ScopePicker(
                            order,
                            { selected ->
                                order = selected
                                model.settings.firmwareOrder = selected
                            },
                            FirmwareFilter.scopes(visible),
                            selectedScope,
                            { selectedScopeKey = scopeKey(it) },
                        )
                    }
                    item { FirmwareSummary(entries.size, catalogue?.builtAt, refreshing, error) }
                    items(entries, key = CatalogueEntry::id) { entry ->
                        FirmwareCard(entry) { navigator.openFirmware(entry.id) }
                    }
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                if (search.isEmpty()) {
                                    stringResource(R.string.firmware_none)
                                } else {
                                    stringResource(R.string.firmware_no_match, search)
                                },
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                color = LocalStandardColors.current.secondaryInk,
                            )
                        }
                    }
                }
            }
        }
        importMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { importMessage = null },
                confirmButton = { TextButton({ importMessage = null }) { Text(stringResource(R.string.firmware_ok)) } },
                title = { Text(stringResource(R.string.firmware_error_title)) },
                text = { Text(message) },
            )
        }
    }
}

private fun scopeKey(scope: FirmwareScope) =
    when (scope) {
        FirmwareScope.All -> "all"
        FirmwareScope.BuiltFromSource -> "built-from-source"
        is FirmwareScope.Kind -> "kind:${scope.kind.lowercase()}"
    }

private fun savedScope(key: String) =
    if (key.startsWith("kind:")) {
        FirmwareScope.Kind(key.removePrefix("kind:"))
    } else if (key == "built-from-source") {
        FirmwareScope.BuiltFromSource
    } else {
        FirmwareScope.All
    }

@Composable
private fun FirmwareSummary(
    count: Int,
    builtAt: java.time.Instant?,
    refreshing: Boolean,
    error: String?,
) = Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (LocalReduceMotion.current) {
        Text(pluralStringResource(R.plurals.firmware_count, count, count), fontWeight = FontWeight.SemiBold)
    } else {
        AnimatedContent(count, label = "firmware count") {
            Text(pluralStringResource(R.plurals.firmware_count, it, it), fontWeight = FontWeight.SemiBold)
        }
    }
    when {
        refreshing && builtAt == null -> {
            Text(stringResource(R.string.firmware_getting_list))
        }

        builtAt != null -> {
            Text(
                stringResource(
                    R.string.firmware_list_updated,
                    DateUtils.getRelativeTimeSpanString(builtAt.toEpochMilli()),
                ),
            )
        }
    }
    error?.let { Text(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopePicker(
    order: FirmwareOrder,
    onOrder: (FirmwareOrder) -> Unit,
    scopes: List<FirmwareScope>,
    selected: FirmwareScope,
    onScope: (FirmwareScope) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var orderExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(orderExpanded, { orderExpanded = it }) {
            AssistChip(
                { orderExpanded = true },
                {
                    Text(
                        if (order == FirmwareOrder.ByName) {
                            stringResource(R.string.firmware_order_name)
                        } else {
                            stringResource(R.string.firmware_order_updated)
                        },
                    )
                },
                Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(orderExpanded, { orderExpanded = false }) {
                DropdownMenuItem(
                    { Text(stringResource(R.string.firmware_order_name)) },
                    {
                        onOrder(FirmwareOrder.ByName)
                        orderExpanded = false
                    },
                )
                DropdownMenuItem(
                    { Text(stringResource(R.string.firmware_order_updated)) },
                    {
                        onOrder(FirmwareOrder.Updated)
                        orderExpanded = false
                    },
                )
            }
        }
        scopes.forEach { scope ->
            val picked = scope == selected
            AssistChip(
                { onScope(scope) },
                { Text(scope.label()) },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor =
                            if (picked) LocalStandardColors.current.ink else LocalStandardColors.current.control,
                        labelColor = if (picked) LocalStandardColors.current.card else LocalStandardColors.current.ink,
                    ),
                modifier = Modifier.semantics { this.selected = picked },
            )
        }
    }
}

@Composable
private fun FirmwareScope.label() =
    when (this) {
        FirmwareScope.All -> stringResource(R.string.firmware_scope_all)
        FirmwareScope.BuiltFromSource -> stringResource(R.string.firmware_scope_built_from_source)
        is FirmwareScope.Kind -> FirmwareFilter.name(kind)
    }

@Composable
internal fun FirmwareCard(
    entry: CatalogueEntry,
    onClick: () -> Unit,
) {
    val model = LocalAppModel.current
    val identity by model.device.identity.collectAsState()
    val colors = LocalStandardColors.current
    val deviceIdentity = identity
    val isOnDevice = deviceIdentity != null && deviceIdentity.text == entry.product
    val isSaved = model.library.stored(entry) != null || model.library.otherBuild(entry) != null
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FirmwareBadge(entry)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.name,
                    Modifier.weight(1f, false),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.version?.let { Text(it, color = colors.secondaryInk, maxLines = 1) }
            }
            Tag(
                if (isOnDevice) {
                    stringResource(R.string.firmware_on_your_fm1)
                } else if (entry.isOfficial) {
                    stringResource(R.string.firmware_official)
                } else if (model.library.outdated(entry) != null) {
                    stringResource(R.string.firmware_update)
                } else if (isSaved) {
                    stringResource(R.string.firmware_saved)
                } else {
                    null
                },
            )
            if (entry.isFromFile) {
                Tag(stringResource(R.string.firmware_from_file), true)
            } else if (!entry.isHardwareTested) {
                Tag(stringResource(R.string.firmware_in_testing), true)
            } else if (entry.beta) {
                Tag(stringResource(R.string.firmware_test_version), true)
            }
            entry.what?.let { Text(it, color = colors.secondaryInk, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Tag(
    text: String?,
    warning: Boolean = false,
) {
    text ?: return
    Text(
        text,
        Modifier
            .background(
                if (warning) LocalStandardColors.current.warningBackground else LocalStandardColors.current.control,
                RoundedCornerShape(99.dp),
            ).padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (warning) LocalStandardColors.current.warningInk else LocalStandardColors.current.ink,
        style = MaterialTheme.typography.labelSmall,
    )
}
