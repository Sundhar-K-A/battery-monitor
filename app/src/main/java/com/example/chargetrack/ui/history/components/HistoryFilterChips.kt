package com.example.chargetrack.ui.history.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.history.DateFilterOption
import com.example.chargetrack.domain.history.HistoryFilter
import com.example.chargetrack.domain.model.ChargingSetup

private val ChipSelectedColor = Color(0xFFFFB300)
private val ChipBorderColor = Color(0xFF2A3241)
private val ChipBgColor = Color(0xFF161B24)

@Composable
fun HistoryFilterChips(
    filter: HistoryFilter,
    availableSetups: List<ChargingSetup>,
    isFiltered: Boolean,
    onToggleCanonical2080: () -> Unit,
    onToggleStandardTestOnly: () -> Unit,
    onSelectDateOption: (DateFilterOption) -> Unit,
    onSelectChargingType: (ChargingType?) -> Unit,
    onSelectSetupId: (String?) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var isDateMenuOpen by remember { mutableStateOf(false) }
    var isTypeMenuOpen by remember { mutableStateOf(false) }
    var isSetupMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Canonical 20->80 Quick Filter
        FilterChip(
            selected = filter.canonical2080Only,
            onClick = onToggleCanonical2080,
            label = { Text("20→80 Benchmark", fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = if (filter.canonical2080Only) Color.Black else ChipSelectedColor,
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ChipSelectedColor,
                selectedLabelColor = Color.Black,
                containerColor = ChipBgColor,
                labelColor = Color.White,
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = ChipBorderColor,
                selectedBorderColor = ChipSelectedColor,
                enabled = true,
                selected = filter.canonical2080Only,
            ),
        )

        // 2. All Standard Tests Filter
        FilterChip(
            selected = filter.standardTestOnly,
            onClick = onToggleStandardTestOnly,
            label = { Text("Standard Tests", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF29B6F6),
                selectedLabelColor = Color.Black,
                containerColor = ChipBgColor,
                labelColor = Color.White,
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = ChipBorderColor,
                selectedBorderColor = Color(0xFF29B6F6),
                enabled = true,
                selected = filter.standardTestOnly,
            ),
        )

        // 3. Date Filter Dropdown Chip
        Row {
            val isDateActive = filter.dateOption != DateFilterOption.ALL
            FilterChip(
                selected = isDateActive,
                onClick = { isDateMenuOpen = true },
                label = { Text(filter.dateOption.label, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = if (isDateActive) Color.Black else Color(0xFF8C9BAE),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF81C784),
                    selectedLabelColor = Color.Black,
                    containerColor = ChipBgColor,
                    labelColor = Color.White,
                ),
            )
            DropdownMenu(
                expanded = isDateMenuOpen,
                onDismissRequest = { isDateMenuOpen = false },
            ) {
                DateFilterOption.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.label) },
                        onClick = {
                            onSelectDateOption(opt)
                            isDateMenuOpen = false
                        }
                    )
                }
            }
        }

        // 4. Wired / Wireless Filter Dropdown
        Row {
            val isTypeActive = filter.chargingType != null
            val typeLabel = when (filter.chargingType) {
                ChargingType.WIRED -> "Wired"
                ChargingType.WIRELESS -> "Wireless"
                ChargingType.UNKNOWN -> "Other"
                null -> "Connection"
            }
            FilterChip(
                selected = isTypeActive,
                onClick = { isTypeMenuOpen = true },
                label = { Text(typeLabel, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Power,
                        contentDescription = null,
                        tint = if (isTypeActive) Color.Black else Color(0xFF8C9BAE),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFBA68C8),
                    selectedLabelColor = Color.Black,
                    containerColor = ChipBgColor,
                    labelColor = Color.White,
                ),
            )
            DropdownMenu(
                expanded = isTypeMenuOpen,
                onDismissRequest = { isTypeMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All Connections") },
                    onClick = {
                        onSelectChargingType(null)
                        isTypeMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Wired Only") },
                    onClick = {
                        onSelectChargingType(ChargingType.WIRED)
                        isTypeMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Wireless Only") },
                    onClick = {
                        onSelectChargingType(ChargingType.WIRELESS)
                        isTypeMenuOpen = false
                    }
                )
            }
        }

        // 5. Setup Filter Dropdown
        if (availableSetups.isNotEmpty()) {
            Row {
                val selectedSetup = availableSetups.firstOrNull { it.id == filter.chargingSetupId }
                val isSetupActive = selectedSetup != null
                val setupLabel = selectedSetup?.chargerModel ?: "Setup"

                FilterChip(
                    selected = isSetupActive,
                    onClick = { isSetupMenuOpen = true },
                    label = { Text(setupLabel, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF8A65),
                        selectedLabelColor = Color.Black,
                        containerColor = ChipBgColor,
                        labelColor = Color.White,
                    ),
                )
                DropdownMenu(
                    expanded = isSetupMenuOpen,
                    onDismissRequest = { isSetupMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All Setups") },
                        onClick = {
                            onSelectSetupId(null)
                            isSetupMenuOpen = false
                        }
                    )
                    availableSetups.forEach { setup ->
                        val name = listOfNotNull(setup.chargerBrand, setup.chargerModel)
                            .joinToString(" ")
                            .ifBlank { "Setup ${setup.id.take(6)}" }
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onSelectSetupId(setup.id)
                                isSetupMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // 6. Reset Filters Button
        if (isFiltered) {
            InputChip(
                selected = false,
                onClick = onResetFilters,
                label = { Text("Reset", fontSize = 12.sp, color = Color(0xFFEF5350)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Reset filters",
                        tint = Color(0xFFEF5350),
                    )
                },
                colors = InputChipDefaults.inputChipColors(
                    containerColor = Color(0xFFEF5350).copy(alpha = 0.15f),
                ),
            )
        }
    }
}
