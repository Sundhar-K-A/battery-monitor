package com.example.chargetrack.ui.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.power.BatteryPowerEstimator
import java.util.Locale

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Design tokens
private val DarkBackground = Color(0xFF000000)
private val CardSurface = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val StatusGreen = Color(0xFF4CAF50)
private val StatusAmber = Color(0xFFFFC107)
private val StatusRed = Color(0xFFEF5350)
private val MutedText = Color(0xFF8E9BAE)

@Composable
fun LiveSessionScreen(
    onBack: () -> Unit,
    onNavigateToCharts: ((String) -> Unit)? = null,
    viewModel: LiveSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { /* Notification permission granted or denied */ },
        )
        LaunchedEffect(Unit) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(permission)
            }
        }
    }

    LiveSessionContent(
        uiState = uiState,
        onBack = onBack,
        onStopSession = { viewModel.stopSession() },
        onResetSession = { viewModel.resetSession() },
        onDismissTargetReachedDialog = { viewModel.dismissTargetReachedDialog() },
        onNavigateToCharts = onNavigateToCharts,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionContent(
    uiState: LiveSessionUiState,
    onBack: () -> Unit,
    onStopSession: () -> Unit,
    onResetSession: () -> Unit,
    onDismissTargetReachedDialog: () -> Unit = {},
    onNavigateToCharts: ((String) -> Unit)? = null,
) {
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Live Session",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        if (uiState is LiveSessionUiState.Active) {
                            Spacer(Modifier.width(8.dp))
                            RecordingPulsingDot()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (uiState) {
                is LiveSessionUiState.NoSession -> {
                    NoSessionView(modifier = Modifier.align(Alignment.Center))
                }

                is LiveSessionUiState.Active -> {
                    ActiveSessionView(
                        state = uiState,
                        onStopSession = onStopSession,
                    )

                    if (uiState.showTargetReachedDialog) {
                        val targetPct = uiState.standardTestInfo?.targetEndPercent ?: 80
                        AlertDialog(
                            onDismissRequest = onDismissTargetReachedDialog,
                            title = {
                                Text(
                                    "Target Reached ($targetPct%)!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            text = {
                                Text(
                                    "The Standard Test benchmark is complete. Would you like to stop recording and finalize the test, or continue recording?",
                                    color = Color(0xFF8C9BAE),
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = onStopSession,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberAccent,
                                        contentColor = Color.Black,
                                    ),
                                ) {
                                    Text("Stop & Save Test", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = onDismissTargetReachedDialog,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    Text("Continue Recording")
                                }
                            },
                            containerColor = CardSurface,
                        )
                    }
                }

                is LiveSessionUiState.SessionEnded -> {
                    SessionEndedView(
                        state = uiState,
                        onResetSession = onResetSession,
                        onNavigateToCharts = onNavigateToCharts,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingPulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(StatusRed),
    )
}

@Composable
private fun NoSessionView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PowerOff,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No active session",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect your charger to start tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionView(
    state: LiveSessionUiState.Active,
    onStopSession: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.standardTestInfo?.let { stdInfo ->
            item {
                StandardTestBannerCard(
                    info = stdInfo,
                    currentPercent = state.currentPercent,
                )
            }
        }

        item {
            // 1. Status & Percentage Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Status Chip
                    StatusChip(isDebouncing = state.isDebouncing)

                    Spacer(Modifier.height(12.dp))

                    // Large percentage display
                    val percentText = state.currentPercent?.let { "$it%" } ?: "—"
                    Text(
                        text = percentText,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Thin,
                        color = Color.White,
                        lineHeight = 72.sp,
                    )

                    Spacer(Modifier.height(4.dp))

                    // Monotonic elapsed time
                    Text(
                        text = formatElapsed(state.elapsedMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = AmberAccent,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Subtitle: From X% · N samples
                    val fromPercent = state.startPercent?.let { "From $it%" } ?: "Start: —"
                    Text(
                        text = "$fromPercent · ${state.sampleCount} samples",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                    )
                }
            }
        }

        item {
            // 2. Estimated battery-side power card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = BatteryPowerEstimator.LABEL_BATTERY_SIDE_POWER,
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        val powerText = BatteryPowerEstimator.formatWattsWithUnit(state.derivedPowerUw) ?: "—"
                        Text(
                            text = powerText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberAccent,
                        )
                    }
                }
            }
        }

        item {
            // 3. Metric cards: Voltage, Current, Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricSubCard(
                    title = "Voltage",
                    value = state.voltageMv?.let { "%.2f V".format(Locale.US, it / 1000.0) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MetricSubCard(
                    title = "Current",
                    value = state.currentNowUa?.let {
                        val amps = it / 1_000_000.0
                        if (amps >= 0) "+%.2f A".format(Locale.US, amps) else "%.2f A".format(Locale.US, amps)
                    } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MetricSubCard(
                    title = "Temperature",
                    value = state.temperatureDeciC?.let { "%.1f °C".format(Locale.US, it / 10.0) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.qualityFlags.isNotEmpty()) {
            item {
                // 4. Quality flags warning chip row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.qualityFlags.forEach { flag ->
                        QualityWarningChip(flag = flag)
                    }
                }
            }
        }

        if (state.completedTransitions.isNotEmpty()) {
            item {
                Text(
                    text = "Completed Transitions (${state.completedTransitions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(state.completedTransitions, key = { it.id }) { transition ->
                TransitionRowCard(transition = transition)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onStopSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = StatusRed,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Stop Session", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SessionEndedView(
    state: LiveSessionUiState.SessionEnded,
    onResetSession: () -> Unit,
    onNavigateToCharts: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "COMPLETED: ${state.endReason.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    val startP = state.session.startPercent?.let { "$it%" } ?: "—"
                    val endP = state.session.endPercent?.let { "$it%" } ?: "—"
                    Text(
                        text = "$startP → $endP",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Total Duration: ${formatDuration(state.durationMs)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = AmberAccent,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${state.sampleCount} raw samples recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                    )
                }
            }
        }

        state.partialTransitionInfo?.let { partial ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Partial transition: ${partial.fromPercent}% → ${partial.fromPercent + 1}% (${partial.samplesCollected} samples collected)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText,
                        )
                    }
                }
            }
        }

        if (state.completedTransitions.isNotEmpty()) {
            item {
                Text(
                    text = "Completed Transitions (${state.completedTransitions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            items(state.completedTransitions, key = { it.id }) { transition ->
                TransitionRowCard(transition = transition)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            onNavigateToCharts?.let { navigate ->
                Button(
                    onClick = { navigate(state.session.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberAccent,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Session Charts", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
            FilledTonalButton(
                onClick = onResetSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset Session")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusChip(isDebouncing: Boolean) {
    val (label, color) = if (isDebouncing) {
        "DEBOUNCING..." to StatusAmber
    } else {
        "CHARGING" to StatusGreen
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun MetricSubCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun QualityWarningChip(flag: QualityFlag) {
    Surface(
        color = StatusAmber.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = StatusAmber,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = flag.name,
                style = MaterialTheme.typography.labelSmall,
                color = StatusAmber,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TransitionRowCard(transition: ChargeTransition) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${transition.fromPercent}% → ${transition.toPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Text(
                text = formatDuration(transition.durationMs),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = AmberAccent,
            )

            val avgPowerText = transition.averagePowerUw?.let {
                "%.1f W avg".format(Locale.US, it / 1_000_000.0)
            } ?: "—"
            Text(
                text = avgPowerText,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )

            QualityBadge(dataQuality = transition.quality)
        }
    }
}

@Composable
private fun QualityBadge(dataQuality: DataQuality) {
    val color = when (dataQuality) {
        DataQuality.GOOD -> StatusGreen
        DataQuality.DEGRADED -> StatusAmber
        DataQuality.INSUFFICIENT -> StatusRed
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = dataQuality.name,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

@Composable
private fun StandardTestBannerCard(
    info: StandardTestProgressInfo,
    currentPercent: Int?,
) {
    val progress = if (currentPercent != null && info.targetEndPercent > info.targetStartPercent) {
        ((currentPercent - info.targetStartPercent).toFloat() / (info.targetEndPercent - info.targetStartPercent)).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Standard Test: ${info.targetStartPercent}% → ${info.targetEndPercent}%",
                    color = AmberAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    when {
                        info.isTargetReached -> "Target Reached"
                        info.isArmed -> "Armed (Waiting for ${info.targetStartPercent}%)"
                        else -> "Benchmark Active"
                    },
                    color = when {
                        info.isTargetReached -> StatusGreen
                        info.isArmed -> StatusAmber
                        else -> AmberAccent
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (info.isTargetReached) StatusGreen else AmberAccent,
                trackColor = CardBorder,
            )
        }
    }
}

