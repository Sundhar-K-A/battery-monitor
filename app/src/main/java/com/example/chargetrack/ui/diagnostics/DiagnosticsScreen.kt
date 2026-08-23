package com.example.chargetrack.ui.diagnostics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.device.BuildInfo
import com.example.chargetrack.domain.device.DeviceIdentifier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Available   = Color(0xFF4CAF50)
private val Unavailable = Color(0xFFFF9800)
private val SourceIntent    = Color(0xFF42A5F5)
private val SourceBatMgr    = Color(0xFFAB47BC)

private val TimestampFormatter = DateTimeFormatter
    .ofPattern("HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = uiState !is DiagnosticsUiState.Loading,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh diagnostics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "diagnostics_content",
        ) { state ->
            when (state) {
                is DiagnosticsUiState.Loading -> LoadingContent(paddingValues)
                is DiagnosticsUiState.Ready   -> ReadyContent(state, paddingValues)
                is DiagnosticsUiState.Error   -> ErrorContent(state.message, paddingValues)
            }
        }
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Reading battery state…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Error: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ── Ready ─────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyContent(state: DiagnosticsUiState.Ready, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeviceCard(state.buildInfo, state.knownDevice, state.originOsLabel, state.appVersion) }
        item { SectionLabel("Battery measurements") }
        item {
            SampledAtRow(
                timestamp = state.snapshot.timestamp,
                qualityFlags = state.snapshot.qualityFlags.joinToString { it.name },
            )
        }
        item { StickyIntentCard(state.snapshot) }
        item { BatteryManagerCard(state.snapshot) }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Device card ───────────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    info: BuildInfo,
    knownDevice: DeviceIdentifier.KnownDevice,
    originOsLabel: String?,
    appVersion: String,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (knownDevice != DeviceIdentifier.KnownDevice.UNKNOWN)
                        Icons.Filled.BatteryChargingFull else Icons.Filled.DeviceUnknown,
                    contentDescription = null,
                    tint = if (knownDevice != DeviceIdentifier.KnownDevice.UNKNOWN)
                        Available else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${info.brand} ${info.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val matchLabel = when (knownDevice) {
                        DeviceIdentifier.KnownDevice.IQOO_15 -> "iQOO 15 — matched ✓"
                        DeviceIdentifier.KnownDevice.UNKNOWN  -> "Device not in catalogue"
                    }
                    val matchColor = if (knownDevice != DeviceIdentifier.KnownDevice.UNKNOWN)
                        Available else Unavailable
                    Text(matchLabel, style = MaterialTheme.typography.bodySmall, color = matchColor)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            // Build info table
            DeviceInfoRow("Manufacturer",   info.manufacturer)
            DeviceInfoRow("Android",        "${info.androidVersionRelease}  (API ${info.sdkInt})")
            DeviceInfoRow("Device",         info.device)
            DeviceInfoRow("Product",        info.product)
            DeviceInfoRow("Build display",  info.buildDisplay)
            DeviceInfoRow("Incremental",    info.buildIncremental)
            DeviceInfoRow("OriginOS label", originOsLabel ?: "Not detected (best-effort)")
            DeviceInfoRow("Fingerprint",    info.buildFingerprint, monospace = true)
            DeviceInfoRow("App version",    appVersion)
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (monospace) FontFamily.Monospace else null,
            ),
            modifier = Modifier.weight(0.62f),
        )
    }
}

// ── Measurement cards ─────────────────────────────────────────────────────────

@Composable
private fun StickyIntentCard(snapshot: BatterySnapshot) {
    MeasurementCard(title = "Sticky Intent  (ACTION_BATTERY_CHANGED)", sourceColor = SourceIntent) {
        PropertyRow("Percentage",   DiagnosticsFormatter.formatPercent(snapshot.percent),       snapshot.percent != null,  "Sticky Intent")
        PropertyRow("Voltage",      DiagnosticsFormatter.formatVoltage(snapshot.voltageMv),     snapshot.voltageMv != null, "Sticky Intent")
        PropertyRow("Temperature",  DiagnosticsFormatter.formatTemperature(snapshot.temperatureDeciC), snapshot.temperatureDeciC != null, "Sticky Intent")
        PropertyRow("Status",       DiagnosticsFormatter.formatStatus(snapshot.batteryStatus),  snapshot.batteryStatus != null, "Sticky Intent")
        PropertyRow("Plugged type", DiagnosticsFormatter.formatPlugged(snapshot.pluggedType),   snapshot.pluggedType != null, "Sticky Intent")
        PropertyRow("Health",       DiagnosticsFormatter.formatHealth(snapshot.health),         snapshot.health != null,   "Sticky Intent")
        PropertyRow("Cycle count",  DiagnosticsFormatter.formatCycleCount(snapshot.cycleCount), snapshot.cycleCount != null, "Sticky Intent  (API 34+)")
    }
}

@Composable
private fun BatteryManagerCard(snapshot: BatterySnapshot) {
    MeasurementCard(title = "BatteryManager  (getIntProperty / getLongProperty)", sourceColor = SourceBatMgr) {
        PropertyRow("Current now",     DiagnosticsFormatter.formatCurrentNow(snapshot.currentNowUa),     snapshot.currentNowUa != null,    "BatteryManager")
        PropertyRow("Current average", DiagnosticsFormatter.formatCurrentAvg(snapshot.currentAverageUa), snapshot.currentAverageUa != null, "BatteryManager")
        PropertyRow("Charge counter",  DiagnosticsFormatter.formatChargeCounter(snapshot.chargeCounterUah), snapshot.chargeCounterUah != null, "BatteryManager")
        PropertyRow("Energy counter",  DiagnosticsFormatter.formatEnergyCounter(snapshot.energyCounterNwh), snapshot.energyCounterNwh != null, "BatteryManager")
    }
}

@Composable
private fun MeasurementCard(
    title: String,
    sourceColor: Color,
    content: @Composable () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 20.dp)
                        .background(sourceColor, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PropertyRow(
    label: String,
    formattedValue: String,
    isAvailable: Boolean,
    source: String,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                SourceBadge(source)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formattedValue,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (isAvailable)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                AvailabilityBadge(isAvailable)
            }
        }
    }
}

@Composable
private fun AvailabilityBadge(isAvailable: Boolean) {
    val color = if (isAvailable) Available else Unavailable
    val label = if (isAvailable) "Available" else "Unavailable"
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isAvailable) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val color = if (source.startsWith("BatteryManager")) SourceBatMgr else SourceIntent
    Text(
        source,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SampledAtRow(timestamp: Instant, qualityFlags: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Sampled at  ${TimestampFormatter.format(timestamp)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (qualityFlags.isNotEmpty()) {
            Text(
                "⚑ $qualityFlags",
                style = MaterialTheme.typography.labelSmall,
                color = Unavailable,
            )
        }
    }
}
