package com.example.chargetrack

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Top-level Navigation Destinations (Bottom Navigation Bar)
@Serializable data object Main               : NavKey
@Serializable data object History            : NavKey
@Serializable data class CompareNav(val initialTab: Int = 0) : NavKey
@Serializable data object DeviceNav          : NavKey
@Serializable data object SettingsNav        : NavKey

// Detail / Sub-Flow Destinations
@Serializable data object Diagnostics        : NavKey
@Serializable data object LiveSession        : NavKey
@Serializable data object StandardTestConfig : NavKey
@Serializable data class SessionCharts(val sessionId: String) : NavKey
@Serializable data class SessionSummaryDetail(val sessionId: String) : NavKey
@Serializable data class StandardTestComparisonNav(val primarySessionId: String? = null, val candidateSessionId: String? = null) : NavKey
@Serializable data class DegradationAnalysisNav(val groupKey: String? = null) : NavKey
