package com.example.chargetrack.ui.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chargetrack.ui.degradation.DegradationScreen
import com.example.chargetrack.ui.degradation.DegradationViewModel

private val ScreenBackground = Color(0xFF000000)
private val TabBackground = Color(0xFF121212)
private val AmberAccent = Color(0xFFFFB300)
private val CyanAccent = Color(0xFF00E5FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    initialTab: Int = 0,
    primarySessionId: String? = null,
    candidateSessionId: String? = null,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = TabBackground,
            contentColor = Color.White,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTab),
                    color = if (selectedTab == 0) AmberAccent else CyanAccent,
                )
            },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Side-by-Side",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) AmberAccent else Color.Gray,
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = if (selectedTab == 0) AmberAccent else Color.Gray,
                    )
                },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Longitudinal Trends",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) CyanAccent else Color.Gray,
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = if (selectedTab == 1) CyanAccent else Color.Gray,
                    )
                },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    StandardTestComparisonScreen(
                        primarySessionId = primarySessionId,
                        candidateSessionId = candidateSessionId,
                        onNavigateBack = onNavigateBack,
                    )
                }
                1 -> {
                    val degradationViewModel: DegradationViewModel = hiltViewModel()
                    DegradationScreen(
                        viewModel = degradationViewModel,
                        onNavigateBack = onNavigateBack,
                    )
                }
            }
        }
    }
}
