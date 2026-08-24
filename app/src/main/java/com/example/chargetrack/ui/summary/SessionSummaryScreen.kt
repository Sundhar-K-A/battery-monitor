package com.example.chargetrack.ui.summary

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargeTransition
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ScreenBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val BlueAccent = Color(0xFF29B6F6)
private val GreenQuality = Color(0xFF4CAF50)
private val AmberDegraded = Color(0xFFFFC107)
private val RedWarning = Color(0xFFEF5350)
private val SubtitleColor = Color(0xFF8C9BAE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCharts: (String) -> Unit,
    onNavigateToComparison: (String) -> Unit = {},
    viewModel: SessionSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSessionSummary(sessionId)
    }

    val success = uiState as? SessionSummaryUiState.Success
    val isStandardTest = success?.session?.testType == TestType.STANDARD

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Session Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isStandardTest) {
                        IconButton(onClick = { onNavigateToComparison(sessionId) }) {
                            Icon(Icons.Filled.Speed, contentDescription = "Compare test", tint = AmberAccent)
                        }
                    }
                    IconButton(onClick = { onNavigateToCharts(sessionId) }) {
                        Icon(Icons.Filled.Bolt, contentDescription = "Open charts", tint = AmberAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = ScreenBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isStandardTest) {
                        Button(
                            onClick = { onNavigateToComparison(sessionId) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF29B6F6),
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Compare", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { onNavigateToCharts(sessionId) },
                        modifier = Modifier
                            .weight(if (isStandardTest) 1.5f else 1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberAccent,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Charts", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = ScreenBackground,
    ) { paddingValues ->
        when (val state = uiState) {
            is SessionSummaryUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AmberAccent)
                }
            }

            is SessionSummaryUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = RedWarning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is SessionSummaryUiState.Success -> {
                SummaryContent(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryContent(
    state: SessionSummaryUiState.Success,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary
    val session = state.session

    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm:ss", Locale.US)
        .withZone(ZoneId.systemDefault())
    val startedAtFmt = formatter.format(session.startedAt)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Header Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(startedAtFmt, color = SubtitleColor, fontSize = 12.sp)

                        session.endReason?.let { reason ->
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = reason.name.replace("_", " "),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Percentage Range & Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            val startP = "${summary.startPercent}%"
                            val endP = summary.endPercent?.let { "$it%" } ?: "—"
                            Text(
                                text = "$startP → $endP",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            summary.percentGained?.let { g ->
                                val gainText = if (g >= 0) "+$g% Gained" else "$g% Discharged"
                                val gainColor = if (g >= 0) GreenQuality else RedWarning
                                Text(
                                    text = gainText,
                                    color = gainColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatDuration(summary.durationMs),
                                color = AmberAccent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(4.dp))
                            QualityBadge(quality = summary.overallQuality)
                        }
                    }
                }
            }
        }

        // 2. Standard Test Details (if applicable)
        if (state.standardTest != null) {
            item {
                val std = state.standardTest
                val isComplete = summary.isCompleteStandardTest ?: false
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Speed, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Standard Test Benchmark", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Surface(
                                color = if (isComplete) GreenQuality.copy(alpha = 0.15f) else RedWarning.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = if (isComplete) "COMPLETE" else "INCOMPLETE",
                                    color = if (isComplete) GreenQuality else RedWarning,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Target Range: ${std.targetStartPercent}% → ${std.targetEndPercent}%",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                        std.benchmarkStartedElapsedMs?.let { startMs ->
                            Text(
                                text = "Benchmark Start Boundary: ${startMs / 1000}s",
                                color = SubtitleColor,
                                fontSize = 12.sp,
                            )
                        }
                        std.benchmarkEndedElapsedMs?.let { endMs ->
                            Text(
                                text = "Benchmark End Boundary: ${endMs / 1000}s",
                                color = SubtitleColor,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Comparison Group Key:",
                            color = SubtitleColor,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = std.comparisonGroupKey,
                            color = AmberAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // 3. Key Metrics Grid
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Session Analytics", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))

                    MetricRow("Estimated Battery-Side Power", "Avg: ${formatPowerW(summary.averagePowerUw)}", "Peak: ${formatPowerW(summary.peakPowerUw)}")
                    MetricRow("Battery Temperature", "Avg: ${formatTempC(summary.averageTemperatureDeciC)}", "Max: ${formatTempC(summary.peakTemperatureDeciC)}")
                    MetricRow("Charging Current", "Avg: ${formatCurrentA(summary.averageCurrentUa)}", "Max: ${formatCurrentA(summary.maxCurrentUa)}")
                    MetricRow("Voltage & Pacing", "Avg: ${formatVoltageV(summary.averageVoltageMv)}", "1% Pace: ${formatPace(summary.averageTimePerOnePercentMs)}")
                }
            }
        }

        // 4. Hardware & Setup Card
        state.setup?.let { setup ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hardware & Charging Setup", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))

                        val chargerName = listOfNotNull(setup.chargerBrand, setup.chargerModel).joinToString(" ")
                        Text("Charger: $chargerName", color = Color.White, fontSize = 13.sp)
                        Text("Advertised charger wattage: ${setup.advertisedWattageW ?: "—"}W", color = AmberAccent, fontSize = 13.sp)
                        Text("Protocol: ${setup.protocol ?: "Standard"}", color = SubtitleColor, fontSize = 12.sp)
                        Text("Cable: ${setup.cableBrand ?: "Stock"} ${setup.cableModel ?: "Type-C"}", color = SubtitleColor, fontSize = 12.sp)
                        Text("Mode: ${setup.chargingMode.name} (${setup.chargingType.name})", color = SubtitleColor, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Data Quality Breakdown
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Telemetry & Quality Diagnostics", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))

                    Text("Total Samples Captured: ${summary.totalSampleCount}", color = Color.White, fontSize = 13.sp)
                    Text("Valid Power Samples: ${summary.validPowerSampleCount}", color = GreenQuality, fontSize = 13.sp)
                    Text("Missing Required Values: ${summary.missingValueSampleCount}", color = if (summary.missingValueSampleCount > 0) RedWarning else SubtitleColor, fontSize = 12.sp)
                    Text("Measurement Gaps: ${summary.gapSampleCount}", color = if (summary.gapSampleCount > 0) AmberDegraded else SubtitleColor, fontSize = 12.sp)
                    Text("Outliers Detected: ${summary.outlierSampleCount}", color = if (summary.outlierSampleCount > 0) RedWarning else SubtitleColor, fontSize = 12.sp)
                    Text("Percentage Jitter Events: ${summary.jitterSampleCount}", color = SubtitleColor, fontSize = 12.sp)
                }
            }
        }

        // 6. Transitions List
        if (state.transitions.isNotEmpty()) {
            item {
                Text(
                    text = "Transitions Breakdown (${state.transitions.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }

            items(state.transitions, key = { it.id }) { transition ->
                TransitionSummaryRow(transition = transition)
            }
        }

        item {
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MetricRow(label: String, val1: String, val2: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = SubtitleColor, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = val1, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = val2, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TransitionSummaryRow(transition: ChargeTransition) {
    val span = transition.toPercent - transition.fromPercent
    val isGap = span > 1 || transition.quality == DataQuality.INSUFFICIENT

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                val label = if (isGap) "${transition.fromPercent}% → ${transition.toPercent}% (GAP)" else "${transition.fromPercent}% → ${transition.toPercent}%"
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    color = if (isGap) RedWarning else Color.White,
                    fontSize = 14.sp,
                )
                Text(
                    text = "${transition.sampleCount} samples",
                    color = SubtitleColor,
                    fontSize = 11.sp,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val durSec = transition.durationMs / 1000.0
                Text(
                    text = String.format(Locale.US, "%.1fs", durSec),
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
                QualityBadge(quality = transition.quality)
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: DataQuality) {
    val (color, text) = when (quality) {
        DataQuality.GOOD -> GreenQuality to "GOOD"
        DataQuality.DEGRADED -> AmberDegraded to "DEGRADED"
        DataQuality.INSUFFICIENT -> RedWarning to "INSUFFICIENT"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatPowerW(uw: Long?): String = uw?.let { String.format(Locale.US, "%.1f W", it / 1_000_000.0) } ?: "—"
private fun formatTempC(deciC: Int?): String = deciC?.let { String.format(Locale.US, "%.1f °C", it / 10.0) } ?: "—"
private fun formatCurrentA(ua: Int?): String = ua?.let { String.format(Locale.US, "%.2f A", it / 1_000_000.0) } ?: "—"
private fun formatVoltageV(mv: Int?): String = mv?.let { String.format(Locale.US, "%.2f V", it / 1000.0) } ?: "—"
private fun formatPace(ms: Long?): String = ms?.let { String.format(Locale.US, "%.1fs", it / 1000.0) } ?: "—"

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "—"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%dm %02ds", minutes, seconds)
    }
}
