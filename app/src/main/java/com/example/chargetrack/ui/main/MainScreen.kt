package com.example.chargetrack.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chargetrack.theme.ChargeTrackTheme

import androidx.compose.material3.Surface

@Composable
fun MainScreen(
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToLiveSession: () -> Unit,
    onNavigateToStandardTest: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToComparison: () -> Unit = {},
    onNavigateToDegradation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFF000000),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        Icon(
            imageVector = Icons.Filled.BatteryChargingFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            "ChargeTrack",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "iQOO 15 battery measurement",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNavigateToStandardTest,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB300),
                contentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Standard Test (20% → 80%)", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNavigateToLiveSession) {
            Icon(Icons.Filled.Bolt, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Live Session")
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onNavigateToComparison) {
            Icon(Icons.Filled.Speed, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Compare Standard Tests")
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onNavigateToDegradation) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Degradation Analysis")
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onNavigateToHistory) {
            Icon(Icons.Filled.Speed, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Charging History")
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onNavigateToDiagnostics) {
            Icon(Icons.Filled.BatteryChargingFull, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Open Diagnostics")
        }
    }
}
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ChargeTrackTheme {
        MainScreen(
            onNavigateToDiagnostics = {},
            onNavigateToLiveSession = {},
            onNavigateToStandardTest = {},
            onNavigateToHistory = {},
            onNavigateToComparison = {},
        )
    }
}
