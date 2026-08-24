package com.example.chargetrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.chargetrack.domain.enums.TestType
import java.util.Locale

// OLED Dark Theme Colors
private val ScreenBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF121212)
private val CardBorder = Color(0xFF242424)
private val AmberAccent = Color(0xFFFFB300)
private val CyanAccent = Color(0xFF00E5FF)
private val GreenCharging = Color(0xFF00E676)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLiveSession: () -> Unit,
    onNavigateToStandardTest: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToCompare: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ChargeTrack",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(CardBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "iQOO 15",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBackground,
                ),
            )
        },
        containerColor = ScreenBackground,
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Active Session Resume Banner (if recording in background)
            if (uiState.hasActiveSession) {
                ActiveSessionBanner(
                    testType = uiState.activeSessionTestType,
                    startPercent = uiState.activeSessionStartPercent,
                    onResumeClick = onNavigateToLiveSession,
                )
            }

            // 2. Real-time Hero Battery Status Card
            HeroBatteryCard(
                uiState = uiState,
            )

            // 3. Primary Quick Actions Grid
            Text(
                text = "Charging Actions",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
            )

            // Primary Standard Test Action
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToStandardTest() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AmberAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Standard Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Guided 20% → 80% benchmark for baseline & degradation tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AmberAccent)
                }
            }

            // Secondary Quick Actions Row (Live Monitor & Diagnostics)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Free-form Monitor
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToLiveSession() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Live Monitor", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Free-form session recording", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Diagnostics
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToDiagnostics() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Diagnostics", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Inspect hardware properties", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 4. Latest Benchmark Summary Card (if available)
            if (uiState.latestBenchmarkDurationMs != null) {
                Text(
                    text = "Recent Benchmark Snapshot",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCompare() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Standard Test (20% → 80%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Text("View Trends →", color = CyanAccent, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Duration", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                val durationMins = (uiState.latestBenchmarkDurationMs ?: 0L) / 60000.0
                                Text(
                                    text = String.format(Locale.US, "%.1f min", durationMins),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary,
                                )
                            }
                            if (uiState.latestBenchmarkAveragePowerUw != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Avg Battery-Side Power", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    val avgPowerW = (uiState.latestBenchmarkAveragePowerUw ?: 0L) / 1_000_000.0
                                    Text(
                                        text = String.format(Locale.US, "%.1f W", avgPowerW),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = AmberAccent,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActiveSessionBanner(
    testType: TestType?,
    startPercent: Int?,
    onResumeClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResumeClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF00363A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Active Session Recording",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "${testType?.name ?: "FREE_FORM"} • Started at ${startPercent ?: 0}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                    )
                }
            }
            Button(
                onClick = onResumeClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Resume", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroBatteryCard(
    uiState: HomeUiState,
) {
    val snapshot = uiState.batterySnapshot
    val percent = snapshot?.percent ?: 0
    val isCharging = uiState.isCharging

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCharging) GreenCharging.copy(alpha = 0.5f) else CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Current Battery State",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (snapshot?.percent != null) "$percent%" else "—%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isCharging) GreenCharging else TextPrimary,
                            fontSize = 44.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .background(
                                    if (isCharging) GreenCharging.copy(alpha = 0.2f) else CardBorder,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isCharging) "CHARGING" else "DISCHARGING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCharging) GreenCharging else TextSecondary,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isCharging) GreenCharging.copy(alpha = 0.15f) else CardBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Filled.Power else Icons.Filled.PowerOff,
                        contentDescription = null,
                        tint = if (isCharging) GreenCharging else TextSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Voltage
                Column {
                    Text("Voltage", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    val voltMv = snapshot?.voltageMv
                    Text(
                        text = if (voltMv != null) String.format(Locale.US, "%.3f V", voltMv / 1000.0) else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                    )
                }

                // Current
                Column {
                    Text("Current", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    val currUa = snapshot?.currentNowUa
                    Text(
                        text = if (currUa != null) String.format(Locale.US, "%+d mA", currUa / 1000) else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                    )
                }

                // Temperature
                Column {
                    Text("Temperature", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    val tempDeciC = snapshot?.temperatureDeciC
                    Text(
                        text = if (tempDeciC != null) String.format(Locale.US, "%.1f °C", tempDeciC / 10.0) else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                    )
                }

                // Estimated Power
                Column(horizontalAlignment = Alignment.End) {
                    Text("Power", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    val powerUw = uiState.estimatedPowerUw
                    Text(
                        text = if (powerUw != null) String.format(Locale.US, "%+.1f W", powerUw / 1_000_000.0) else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isCharging) AmberAccent else TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Estimated battery-side power • Not charger/wall power",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
    }
}
