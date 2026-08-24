package com.example.chargetrack.ui.standardtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.model.StandardTestConstants
import com.example.chargetrack.domain.model.StandardTestPreset

private val ScreenBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF161B24)
private val CardBorderColor = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val GreenReady = Color(0xFF4CAF50)
private val RedWarning = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandardTestConfigScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLiveSession: () -> Unit,
    viewModel: StandardTestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshBatteryStatus()
            kotlinx.coroutines.delay(2_000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Standard Test Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = ScreenBackground,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Canonical Benchmark Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(AmberAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Standardized Benchmark",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            "Standard tests isolate charging performance to measure battery degradation over time.",
                            color = Color(0xFF8C9BAE),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // 2. Preset Selection
            Text(
                "Benchmark Protocol",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StandardTestPreset.entries.forEach { preset ->
                    val isSelected = uiState.selectedPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectPreset(preset) },
                        label = {
                            Text(
                                preset.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBackground,
                            labelColor = Color(0xFF8C9BAE),
                            selectedContainerColor = AmberAccent.copy(alpha = 0.2f),
                            selectedLabelColor = AmberAccent,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = CardBorderColor,
                            selectedBorderColor = AmberAccent,
                        ),
                    )
                }
            }

            // 3. Custom Sliders (if CUSTOM preset selected)
            if (uiState.selectedPreset == StandardTestPreset.CUSTOM) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Custom Range: ${uiState.startPercent}% → ${uiState.targetPercent}%",
                            color = AmberAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Start: ${uiState.startPercent}%",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = uiState.startPercent.toFloat(),
                            onValueChange = {
                                viewModel.setCustomRange(it.toInt(), uiState.targetPercent)
                            },
                            valueRange = 0f..95f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberAccent,
                                activeTrackColor = AmberAccent,
                                inactiveTrackColor = CardBorderColor,
                            ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Target: ${uiState.targetPercent}%",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = uiState.targetPercent.toFloat(),
                            onValueChange = {
                                viewModel.setCustomRange(uiState.startPercent, it.toInt())
                            },
                            valueRange = (uiState.startPercent + StandardTestConstants.MIN_STANDARD_TEST_PERCENT_SPAN).toFloat()..100f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberAccent,
                                activeTrackColor = AmberAccent,
                                inactiveTrackColor = CardBorderColor,
                            ),
                        )
                    }
                }
            }

            // 4. Charging Setup & Hardware Metadata Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ElectricalServices,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Charging Setup & Profile",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Charger: ${uiState.selectedSetup?.chargerBrand ?: "iQOO"} ${uiState.selectedSetup?.chargerModel ?: "100W FlashCharge"}",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    Text(
                        "Advertised wattage: ${uiState.selectedSetup?.advertisedWattageW ?: 100} W (setup metadata)",
                        color = Color(0xFF8C9BAE),
                        fontSize = 12.sp,
                    )
                    Text(
                        "Cable: ${uiState.selectedSetup?.cableBrand ?: "iQOO"} ${uiState.selectedSetup?.cableModel ?: "Stock Type-C"}",
                        color = Color(0xFF8C9BAE),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Charging Mode",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(ChargingMode.NORMAL, ChargingMode.FLASH_CHARGE).forEach { mode ->
                                val isModeSelected = uiState.selectedChargingMode == mode
                                FilterChip(
                                    selected = isModeSelected,
                                    onClick = { viewModel.setChargingMode(mode) },
                                    label = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = mode.name.replace("_", " "),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = ScreenBackground,
                                        labelColor = Color(0xFF8C9BAE),
                                        selectedContainerColor = AmberAccent.copy(alpha = 0.2f),
                                        selectedLabelColor = AmberAccent,
                                    ),
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(ChargingMode.BYPASS, ChargingMode.OTHER).forEach { mode ->
                                val isModeSelected = uiState.selectedChargingMode == mode
                                FilterChip(
                                    selected = isModeSelected,
                                    onClick = { viewModel.setChargingMode(mode) },
                                    label = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = mode.name.replace("_", " "),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = ScreenBackground,
                                        labelColor = Color(0xFF8C9BAE),
                                        selectedContainerColor = AmberAccent.copy(alpha = 0.2f),
                                        selectedLabelColor = AmberAccent,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // 5. Preparation & Readiness Checklist
            Text(
                "Preparation Checklist",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Check 1: Charger Connected
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isCharging) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (uiState.isCharging) GreenReady else RedWarning,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (uiState.isCharging) "Charger Connected & Charging" else "Charger Disconnected",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (uiState.isCharging) "Power supply detected." else "Please connect charger before starting test.",
                                color = Color(0xFF8C9BAE),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    // Check 2: Battery Start Level
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isBatteryReady) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (uiState.isBatteryReady) GreenReady else AmberAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Battery Start Level (${uiState.startPercent}%)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                uiState.batteryReadinessMessage,
                                color = Color(0xFF8C9BAE),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    // Check 3: Environmental Guidance
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF8C9BAE),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Recommended Test Environment",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Keep phone screen off. Avoid heavy apps or gaming. Recommended ambient temperature: 20°C – 25°C.",
                                color = Color(0xFF8C9BAE),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // 6. User Notes Input
            OutlinedTextField(
                value = uiState.userNotes,
                onValueChange = { viewModel.setUserNotes(it) },
                label = { Text("Optional Test Notes") },
                placeholder = { Text("e.g. Ambient 23°C, phone cooled, stock cable") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberAccent,
                    unfocusedBorderColor = CardBorderColor,
                    focusedLabelColor = AmberAccent,
                    unfocusedLabelColor = Color(0xFF8C9BAE),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            // 7. Comparison Group Key
            Text(
                "Comparison Key: ${uiState.comparisonGroupKey}",
                color = Color(0xFF5A6978),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )

            // 8. Error Message
            uiState.errorMessage?.let { error ->
                Text(
                    error,
                    color = RedWarning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 9. Begin Standard Test Button
            val snapshot = uiState.latestSnapshot
            val currPercent = snapshot?.percent
            val buttonText = when {
                uiState.isStarting -> "Starting..."
                !uiState.isCharging -> "Connect Charger to Begin"
                !uiState.isBatteryReady -> {
                    if (currPercent != null && currPercent > uiState.startPercent) {
                        "Discharge to ${uiState.startPercent}% Before Starting"
                    } else {
                        "Battery Not Ready for ${uiState.startPercent}% Benchmark"
                    }
                }
                currPercent != null && currPercent < uiState.startPercent ->
                    "Arm Standard Test (${uiState.startPercent}% → ${uiState.targetPercent}%)"
                else ->
                    "Begin Standard Test (${uiState.startPercent}% → ${uiState.targetPercent}%)"
            }

            Button(
                onClick = { viewModel.startStandardTest(onNavigateToLiveSession) },
                enabled = uiState.canStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberAccent,
                    contentColor = Color.Black,
                    disabledContainerColor = CardBorderColor,
                    disabledContentColor = Color(0xFF5A6978),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
