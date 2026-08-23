package com.example.chargetrack

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main               : NavKey
@Serializable data object Diagnostics        : NavKey
@Serializable data object LiveSession        : NavKey
@Serializable data object StandardTestConfig : NavKey
@Serializable data class SessionCharts(val sessionId: String) : NavKey
