package com.example.chargetrack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.chargetrack.ui.comparison.CompareScreen
import com.example.chargetrack.ui.device.DeviceScreen
import com.example.chargetrack.ui.diagnostics.DiagnosticsScreen
import com.example.chargetrack.ui.history.HistoryScreen
import com.example.chargetrack.ui.home.HomeScreen
import com.example.chargetrack.ui.live.LiveSessionScreen
import com.example.chargetrack.ui.settings.SettingsScreen
import com.example.chargetrack.ui.standardtest.StandardTestConfigScreen
import com.example.chargetrack.ui.summary.SessionSummaryScreen

private val NavBackground = Color(0xFF0D0D0D)
private val NavIndicator = Color(0xFF242424)
private val AmberAccent = Color(0xFFFFB300)
private val TextSecondary = Color(0xFF888888)

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    val currentKey: NavKey = backStack.lastOrNull() ?: Main

    // Check if the current screen is one of the top-level tab destinations
    val isTopLevelDestination = when (currentKey) {
        is Main,
        is History,
        is CompareNav,
        is DeviceNav,
        is SettingsNav -> true
        else -> false
    }

    val selectedTopLevelIndex = when (currentKey) {
        is Main -> 0
        is History -> 1
        is CompareNav -> 2
        is DeviceNav -> 3
        is SettingsNav -> 4
        else -> -1
    }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = NavBackground,
                    contentColor = Color.White,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = selectedTopLevelIndex == 0,
                        onClick = {
                            if (selectedTopLevelIndex != 0) {
                                backStack.clear()
                                backStack.add(Main)
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberAccent,
                            selectedTextColor = AmberAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NavIndicator,
                        ),
                    )
                    NavigationBarItem(
                        selected = selectedTopLevelIndex == 1,
                        onClick = {
                            if (selectedTopLevelIndex != 1) {
                                backStack.clear()
                                backStack.add(History)
                            }
                        },
                        icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberAccent,
                            selectedTextColor = AmberAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NavIndicator,
                        ),
                    )
                    NavigationBarItem(
                        selected = selectedTopLevelIndex == 2,
                        onClick = {
                            if (selectedTopLevelIndex != 2) {
                                backStack.clear()
                                backStack.add(CompareNav())
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Compare") },
                        label = { Text("Compare") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberAccent,
                            selectedTextColor = AmberAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NavIndicator,
                        ),
                    )
                    NavigationBarItem(
                        selected = selectedTopLevelIndex == 3,
                        onClick = {
                            if (selectedTopLevelIndex != 3) {
                                backStack.clear()
                                backStack.add(DeviceNav)
                            }
                        },
                        icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = "Device") },
                        label = { Text("Device") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberAccent,
                            selectedTextColor = AmberAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NavIndicator,
                        ),
                    )
                    NavigationBarItem(
                        selected = selectedTopLevelIndex == 4,
                        onClick = {
                            if (selectedTopLevelIndex != 4) {
                                backStack.clear()
                                backStack.add(SettingsNav)
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberAccent,
                            selectedTextColor = AmberAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NavIndicator,
                        ),
                    )
                }
            }
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    // 1. Home
                    entry<Main> {
                        HomeScreen(
                            onNavigateToLiveSession = { backStack.add(LiveSession) },
                            onNavigateToStandardTest = { backStack.add(StandardTestConfig) },
                            onNavigateToDiagnostics = { backStack.add(Diagnostics) },
                            onNavigateToCompare = {
                                backStack.clear()
                                backStack.add(CompareNav(initialTab = 1))
                            },
                            onNavigateToHistory = {
                                backStack.clear()
                                backStack.add(History)
                            },
                        )
                    }

                    // 2. History
                    entry<History> {
                        HistoryScreen(
                            onNavigateBack = {
                                backStack.clear()
                                backStack.add(Main)
                            },
                            onNavigateToSummary = { sessionId -> backStack.add(SessionSummaryDetail(sessionId)) },
                        )
                    }

                    // 3. Compare (Sub-tabs: Pairwise Comparison & Longitudinal Degradation)
                    entry<CompareNav> { key ->
                        CompareScreen(
                            initialTab = key.initialTab,
                            onNavigateBack = {
                                backStack.clear()
                                backStack.add(Main)
                            },
                        )
                    }

                    // 4. Device Profile
                    entry<DeviceNav> {
                        DeviceScreen()
                    }

                    // 5. Settings
                    entry<SettingsNav> {
                        SettingsScreen()
                    }

                    // Detail destinations
                    entry<Diagnostics> {
                        DiagnosticsScreen(
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<LiveSession> {
                        LiveSessionScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onNavigateToCharts = { sessionId -> backStack.add(SessionCharts(sessionId)) },
                        )
                    }
                    entry<StandardTestConfig> {
                        StandardTestConfigScreen(
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToLiveSession = {
                                backStack.removeLastOrNull() // pop config
                                backStack.add(LiveSession) // push live session
                            },
                        )
                    }
                    entry<SessionSummaryDetail> { key ->
                        SessionSummaryScreen(
                            sessionId = key.sessionId,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToCharts = { sessionId -> backStack.add(SessionCharts(sessionId)) },
                            onNavigateToComparison = { sessionId -> backStack.add(StandardTestComparisonNav(primarySessionId = sessionId)) },
                        )
                    }
                    entry<SessionCharts> { key ->
                        com.example.chargetrack.ui.charts.SessionChartsScreen(
                            sessionId = key.sessionId,
                            onNavigateBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<StandardTestComparisonNav> { key ->
                        com.example.chargetrack.ui.comparison.StandardTestComparisonScreen(
                            primarySessionId = key.primarySessionId,
                            candidateSessionId = key.candidateSessionId,
                            onNavigateBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<DegradationAnalysisNav> {
                        val viewModel: com.example.chargetrack.ui.degradation.DegradationViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                        com.example.chargetrack.ui.degradation.DegradationScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}
