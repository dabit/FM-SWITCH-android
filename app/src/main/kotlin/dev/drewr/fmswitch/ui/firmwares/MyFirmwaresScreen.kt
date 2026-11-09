// Ported from FM-SWITCH App/Standard/Firmwares/MyFirmwaresView.swift.
package dev.drewr.fmswitch.ui.firmwares

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.LocalAppModel
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.catalogue.FirmwareFilter
import dev.drewr.fmswitch.model.catalogue.FirmwareOrder
import dev.drewr.fmswitch.model.catalogue.FirmwareScope
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.ui.navigation.AppNavigator
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.launch

object MyFirmwaresScreen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    operator fun invoke(navigator: AppNavigator) {
        val model = LocalAppModel.current
        val catalogue by model.catalogue.catalogueFlow.collectAsState()
        val refreshing by model.catalogue.isRefreshingFlow.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val saved by model.library.itemsFlow.collectAsState()
        val listed =
            catalogue?.entries.orEmpty().filter { model.library.savedCopies(it).isNotEmpty() }
        val listedHashes = listed.mapNotNull { it.sha256 }.toSet()
        val imported =
            saved
                .filter { it.source == StoredFirmware.Source.Imported && it.sha256 !in listedHashes }
                .sortedBy { it.name.lowercase() }
                .map(CatalogueEntry::addedFrom)
        val entries = (listed + imported).distinctBy(CatalogueEntry::id)
        val scopes = FirmwareFilter.scopes(entries)
        var selectedScopeStored by remember { mutableStateOf(model.settings.myFirmwaresScope) }
        val selectedScope =
            FirmwareScope.fromStored(selectedScopeStored)?.offered(scopes) ?: FirmwareScope.All
        val configuredOrder = model.settings.myFirmwaresOrder
        var order by remember { mutableStateOf(configuredOrder) }
        LaunchedEffect(configuredOrder) { order = configuredOrder }
        val back = stringResource(R.string.back)

        Column(Modifier.background(LocalStandardColors.current.ground)) {
            TopAppBar(
                title = { Text(stringResource(R.string.title_my_firmwares)) },
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalStandardColors.current.ground),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { coroutineScope.launch { model.catalogue.refresh() } },
            ) {
                FilteredFirmwareList(
                    order = order,
                    onOrder = { selected ->
                        order = selected
                        model.settings.myFirmwaresOrder = selected
                    },
                    scope = selectedScope,
                    onScope = { selected ->
                        selectedScopeStored = selected.storedValue
                        model.settings.myFirmwaresScope = selected.storedValue
                    },
                    scopes = scopes,
                    filtersIndex = 0,
                ) { picked ->
                    val shown = order.sorted(entries.filter(picked::includes))
                    item {
                        Text(
                            stringResource(R.string.firmware_saved_on_phone),
                            Modifier.padding(horizontal = 4.dp).semantics { heading() },
                            color = LocalStandardColors.current.secondaryInk,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(shown, key = CatalogueEntry::id) { entry ->
                        FirmwareCard(entry) { navigator.openFirmware(entry.id) }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                stringResource(
                                    if (entries.isEmpty()) {
                                        R.string.firmware_saved_empty
                                    } else {
                                        R.string.firmware_filtered_empty
                                    },
                                ),
                                color = LocalStandardColors.current.secondaryInk,
                            )
                        }
                    }
                }
            }
        }
    }
}
