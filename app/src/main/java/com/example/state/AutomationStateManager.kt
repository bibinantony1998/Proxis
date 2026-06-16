package com.example.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modern, centralized State Manager orchestrating the reactive communication loop.
 * It serves as the "connective tissue" that bridges Sensed states from the Foreground Service
 * to Execution flows inside the Accessibility Service.
 */
class AutomationStateManager private constructor() {

    // Internal mutable state flows
    private val _currentActivity = MutableStateFlow("STILL")
    private val _bluetoothDevice = MutableStateFlow("Disconnected")
    private val _isDrivingModeEngaged = MutableStateFlow(false)
    private val _lastActionLog = MutableStateFlow("Engine Idle")

    // Public read-only StateFlows
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()
    val bluetoothDevice: StateFlow<String> = _bluetoothDevice.asStateFlow()
    val isDrivingModeEngaged: StateFlow<Boolean> = _isDrivingModeEngaged.asStateFlow()
    val lastActionLog: StateFlow<String> = _lastActionLog.asStateFlow()

    fun updateActivity(activity: String) {
        _currentActivity.value = activity
    }

    fun updateBluetoothDevice(device: String) {
        _bluetoothDevice.value = device
    }

    fun setDrivingModeEngaged(engaged: Boolean) {
        _isDrivingModeEngaged.value = engaged
    }

    fun updateLastActionLog(log: String) {
        _lastActionLog.value = log
    }

    companion object {
        @Volatile
        private var INSTANCE: AutomationStateManager? = null

        fun getInstance(): AutomationStateManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AutomationStateManager()
                INSTANCE = instance
                instance
            }
        }
    }
}
