// Ported from FM-SWITCH App/Standard/Firmwares/FilteredFirmwareList.swift.
package dev.drewr.fmswitch.ui.firmwares

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.catalogue.FirmwareFilter
import dev.drewr.fmswitch.model.catalogue.FirmwareOrder
import dev.drewr.fmswitch.model.catalogue.FirmwareScope
import dev.drewr.fmswitch.ui.theme.LocalStandardColors
import kotlinx.coroutines.launch

@Composable
fun FilteredFirmwareList(
    order: FirmwareOrder,
    onOrder: (FirmwareOrder) -> Unit,
    scope: FirmwareScope,
    onScope: (FirmwareScope) -> Unit,
    scopes: List<FirmwareScope>,
    filtersIndex: Int,
    beforeFilters: LazyListScope.() -> Unit = {},
    content: LazyListScope.(FirmwareScope) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val picked = scope.offered(scopes)
    val filtersHidden by remember {
        derivedStateOf { listState.firstVisibleItemIndex > filtersIndex }
    }
    val pinned = filtersHidden && picked != FirmwareScope.All

    Box {
        LazyColumn(
            Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            beforeFilters()
            item {
                ScopePicker(
                    order = order,
                    onOrder = onOrder,
                    scopes = scopes,
                    selected = picked,
                    onScope = onScope,
                )
            }
            content(picked)
        }
        if (pinned) {
            PinnedFilters(
                order = order,
                onOrder = onOrder,
                scope = picked,
                onShowFilters = { coroutineScope.launch { listState.animateScrollToItem(filtersIndex) } },
            )
        }
    }
}

@Composable
private fun PinnedFilters(
    order: FirmwareOrder,
    onOrder: (FirmwareOrder) -> Unit,
    scope: FirmwareScope,
    onShowFilters: () -> Unit,
) {
    val showsFilters = stringResource(R.string.firmware_shows_filters)
    Column(
        Modifier
            .fillMaxWidth()
            .background(LocalStandardColors.current.ground),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrderPicker(order, onOrder)
            ScopePill(
                scope,
                true,
                onShowFilters,
                Modifier.semantics { contentDescription = showsFilters },
            )
        }
        HorizontalDivider(color = LocalStandardColors.current.line)
    }
}

@Composable
private fun ScopePicker(
    order: FirmwareOrder,
    onOrder: (FirmwareOrder) -> Unit,
    scopes: List<FirmwareScope>,
    selected: FirmwareScope,
    onScope: (FirmwareScope) -> Unit,
) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
) {
    OrderPicker(order, onOrder)
    scopes.forEach { scope ->
        ScopePill(scope, scope == selected, { onScope(scope) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderPicker(
    order: FirmwareOrder,
    onOrder: (FirmwareOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortBy = stringResource(R.string.firmware_sort_by, order.label())
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(order.label()) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .semantics {
                        contentDescription = sortBy
                    },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 160.dp),
            matchAnchorWidth = false,
        ) {
            FirmwareOrder.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label(), maxLines = 1, softWrap = false) },
                    onClick = {
                        onOrder(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScopePill(
    scope: FirmwareScope,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = AssistChip(
    onClick = onClick,
    label = { Text(scope.label()) },
    colors =
        AssistChipDefaults.assistChipColors(
            containerColor = if (picked) LocalStandardColors.current.ink else LocalStandardColors.current.control,
            labelColor = if (picked) LocalStandardColors.current.card else LocalStandardColors.current.ink,
        ),
    modifier = modifier.semantics { selected = picked },
)

@Composable
private fun FirmwareOrder.label() =
    stringResource(
        when (this) {
            FirmwareOrder.ByName -> R.string.firmware_order_name
            FirmwareOrder.Updated -> R.string.firmware_order_updated
            FirmwareOrder.MostInstalled -> R.string.firmware_order_most_installed
        },
    )

@Composable
private fun FirmwareScope.label() =
    when (this) {
        FirmwareScope.All -> stringResource(R.string.firmware_scope_all)
        FirmwareScope.BuiltFromSource -> stringResource(R.string.firmware_scope_built_from_source)
        is FirmwareScope.Kind -> FirmwareFilter.name(kind)
    }
