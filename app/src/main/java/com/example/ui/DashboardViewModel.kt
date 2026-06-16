package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.ContextLog
import com.example.data.database.DailyAggregate
import com.example.data.repository.TelemetryRepository
import com.example.state.AutomationStateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Clean Architectural ViewModel coordinating State manager and Room persistences.
 */
class DashboardViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val context: Context
) : ViewModel() {

    private val stateManager = AutomationStateManager.getInstance()

    // Central UI state flows from StateManager
    val currentActivity: StateFlow<String> = stateManager.currentActivity
    val bluetoothDevice: StateFlow<String> = stateManager.bluetoothDevice
    val isDrivingModeEngaged: StateFlow<Boolean> = stateManager.isDrivingModeEngaged
    val lastActionLog: StateFlow<String> = stateManager.lastActionLog

    // Room Database Observables
    val dailyAggregates: StateFlow<List<DailyAggregate>> = telemetryRepository.allAggregates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ContextLog>> = telemetryRepository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Permission status flows
    private val _isUsagePermissionGranted = MutableStateFlow(telemetryRepository.isUsageStatsPermissionGranted())
    val isUsagePermissionGranted = _isUsagePermissionGranted.asStateFlow()

    private val _isCallLogPermissionGranted = MutableStateFlow(telemetryRepository.isCallLogPermissionGranted())
    val isCallLogPermissionGranted = _isCallLogPermissionGranted.asStateFlow()

    init {
        // Trigger initial data aggregation on Startup
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isUsagePermissionGranted.value = telemetryRepository.isUsageStatsPermissionGranted()
            _isCallLogPermissionGranted.value = telemetryRepository.isCallLogPermissionGranted()
            telemetryRepository.fetchAndAggregateDailyData()
        }
    }

    /**
     * Trigger a simulated activity state shift from the UI dashboard.
     * Broadcasts to Foreground service to update notification and trigger Automations.
     */
    fun simulateContextChange(activity: String, bluetooth: String) {
        viewModelScope.launch {
            telemetryRepository.insertLog("Action: Simulating $activity context shift", "SENSOR")
            
            // Broadcast intent to the Context Foreground Service
            val intent = Intent(context, com.example.service.ContextService::class.java).apply {
                action = "ACTION_SIMULATE_CONTEXT"
                putExtra("EXTRA_ACTIVITY", activity)
                putExtra("EXTRA_BLUETOOTH", bluetooth)
            }
            context.startService(intent)
        }
    }

    /**
     * On-Device Data Analysis & Dynamic Recommendations
     */
    fun generateRecommendation(aggregate: DailyAggregate?): String {
        if (aggregate == null) return "No active metrics. Please grant necessary permissions and simulation tokens."
        
        val socialMins = aggregate.socialTimeMs / 60000
        val chatMins = aggregate.chatTimeMs / 60000
        val musicMins = aggregate.musicTimeMs / 60000
        val callCount = aggregate.callCount

        return when {
            socialMins > 80 -> "High Social Web usage (${socialMins}m). Consider turning on Focus block rules."
            chatMins > 30 -> "Intense Messaging sessions recorded (${chatMins}m). Plan a deep work session."
            musicMins > 60 -> "High mobile playback activities. Automation has tuned your favorite driving playlist."
            callCount > 8 -> "Elevated call frequency (${callCount} dials). High productivity profile detected."
            else -> "Digital wellbeing profile is perfectly balanced. Zero background stress."
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            telemetryRepository.clearAll()
            telemetryRepository.insertLog("Telemetry trace history cleared by operator.", "TELEMETRY")
            refreshData()
        }
    }

    // Manual Dependency Injection ViewModel Provider Factory
    class Factory(
        private val telemetryRepository: TelemetryRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(telemetryRepository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
