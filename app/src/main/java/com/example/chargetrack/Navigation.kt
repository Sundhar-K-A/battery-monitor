package com.example.chargetrack

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.chargetrack.ui.diagnostics.DiagnosticsScreen
import com.example.chargetrack.ui.live.LiveSessionScreen
import com.example.chargetrack.ui.main.MainScreen
import com.example.chargetrack.ui.standardtest.StandardTestConfigScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    onNavigateToDiagnostics = { backStack.add(Diagnostics) },
                    onNavigateToLiveSession = { backStack.add(LiveSession) },
                    onNavigateToStandardTest = { backStack.add(StandardTestConfig) },
                    onNavigateToHistory = { backStack.add(History) },
                    onNavigateToComparison = { backStack.add(StandardTestComparisonNav()) },
                    onNavigateToDegradation = { backStack.add(DegradationAnalysisNav()) },
                    modifier = Modifier.safeDrawingPadding().padding(16.dp),
                )
            }
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
            entry<History> {
                com.example.chargetrack.ui.history.HistoryScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToSummary = { sessionId -> backStack.add(SessionSummaryDetail(sessionId)) },
                )
            }
            entry<SessionSummaryDetail> { key ->
                com.example.chargetrack.ui.summary.SessionSummaryScreen(
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
