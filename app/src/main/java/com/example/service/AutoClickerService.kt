package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ContextApp
import com.example.data.repository.TelemetryRepository
import com.example.state.AutomationStateManager
import kotlinx.coroutines.*
import java.lang.Exception

/**
 * Execution Engine: An Accessibility Service that subscribes to the centralized
 * StateManager core. Wakes up when driving scenarios transition and drives interactive clicks.
 */
class AutoClickerService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var stateManager: AutomationStateManager
    private lateinit var telemetryRepository: TelemetryRepository
    private var trackingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        stateManager = AutomationStateManager.getInstance()
        telemetryRepository = (application as ContextApp).telemetryRepository

        serviceScope.launch {
            telemetryRepository.insertLog("AutoClicker connected and waiting for triggers...", "AUTOMATION")
        }

        // Subscribe to State Manager Flow
        trackingJob = serviceScope.launch {
            stateManager.currentActivity.collect { activity ->
                if (activity == "IN_VEHICLE" && !stateManager.isDrivingModeEngaged.value) {
                    executeDrivingAutomationFlow()
                } else if (activity != "IN_VEHICLE" && stateManager.isDrivingModeEngaged.value) {
                    // Reset automation flow if we get out of vehicle
                    stateManager.setDrivingModeEngaged(false)
                    stateManager.updateLastActionLog("Automation reset: Left Vehicle")
                    telemetryRepository.insertLog("Leaving Vehicle: Automation state reset.", "AUTOMATION")
                }
            }
        }
    }

    private fun executeDrivingAutomationFlow() {
        stateManager.setDrivingModeEngaged(true)
        stateManager.updateLastActionLog("Automation fired: Driving Mode!")
        
        serviceScope.launch {
            telemetryRepository.insertLog("Transition to IN_VEHICLE sensed. Initiating automation flow...", "AUTOMATION")

            // 1. Launch Player App via deep linking or backup simulation dashboard
            launchTargetAudioPlayer()

            // 2. Poll the viewport and click the play element programmatically
            delay(1500) // Initial delay for rendering
            startPollingAndClick()
        }
    }

    private fun launchTargetAudioPlayer() {
        val pm = packageManager
        var launched = false

        // Attempt layout deep-link or direct query
        try {
            val ytMusicIntent = pm.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            if (ytMusicIntent != null) {
                ytMusicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(ytMusicIntent)
                launched = true
                stateManager.updateLastActionLog("Launching YouTube Music...")
                serviceScope.launch {
                    telemetryRepository.insertLog("Launching YouTube Music from device...", "AUTOMATION")
                }
            }
        } catch (e: Exception) {
            // Handled
        }

        // Backup visual driver: Launch our own built-in simulator UI
        if (!launched) {
            stateManager.updateLastActionLog("YT Music not found. Launching built-in Simulator...")
            serviceScope.launch {
                telemetryRepository.insertLog("YouTube Music missing. Fallback to built-in simulator...", "AUTOMATION")
            }
            val intent = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = "ACTION_LAUNCH_DRIVING_SIMULATOR"
                putExtra("SIMULATION_TRIGGERED", true)
            }
            startActivity(intent)
        }
    }

    private fun startPollingAndClick() {
        serviceScope.launch {
            var clicked = false
            var attempts = 0
            val maxAttempts = 15

            stateManager.updateLastActionLog("Polling UI nodes...")
            while (!clicked && attempts < maxAttempts) {
                attempts++
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val target = findTargetNode(rootNode)
                    if (target != null) {
                        val ok = performClickOnNode(target)
                        if (ok) {
                            clicked = true
                            stateManager.updateLastActionLog("SUCCESS: Programmatically clicked Play!")
                            telemetryRepository.insertLog("AutoClicker successfully clicked option: '${target.text ?: target.contentDescription}'", "AUTOMATION")
                            break
                        }
                    }
                }
                delay(1000)
            }

            if (!clicked) {
                stateManager.updateLastActionLog("Automation complete: Scanned UI but target not clicked.")
                telemetryRepository.insertLog("AutoClicker finished scanning. Target element already engaged or missing.", "AUTOMATION")
            }
        }
    }

    private fun findTargetNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        val isTarget = text.contains("My Driving Playlist", ignoreCase = true) ||
                text.contains("Play Playlist", ignoreCase = true) ||
                text.equals("Play", ignoreCase = true) ||
                desc.contains("My Driving Playlist", ignoreCase = true) ||
                desc.equals("Play", ignoreCase = true) ||
                text.contains("Activate Playlist", ignoreCase = true)

        if (isTarget) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTargetNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op - we drive action on global status shifts
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        serviceJob.cancel()
    }
}
