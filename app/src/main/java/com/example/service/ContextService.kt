package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.ContextApp
import com.example.MainActivity
import com.example.data.repository.TelemetryRepository
import com.example.state.AutomationStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothDevice
import android.content.pm.ServiceInfo

/**
 * Modern Foreground Service orchestrating physical and simulated sensor states.
 * Connects directly to the State Manager and records telemetry actions into Room.
 */
class ContextService : Service {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var stateManager: AutomationStateManager
    private lateinit var telemetryRepository: TelemetryRepository

    private val CHANNEL_ID = "context_sensing_channel"
    private val NOTIFICATION_ID = 2026

    // BroadcastReceiver to act on real system Bluetooth pairing/connections
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            val device: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val deviceName = device?.name ?: "Unknown Device"

            if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                handleBluetoothConnected(deviceName)
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
                handleBluetoothDisconnected()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateManager = AutomationStateManager.getInstance()
        telemetryRepository = (application as ContextApp).telemetryRepository
        
        createNotificationChannel()
        registerBluetoothListeners()

        serviceScope.launch {
            telemetryRepository.insertLog("Context Sensing Engine starting...", "SENSOR")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle trigger events supplied from the dashboard or system intents
        val action = intent?.action
        val targetActivity = intent?.getStringExtra("EXTRA_ACTIVITY")
        val targetBluetooth = intent?.getStringExtra("EXTRA_BLUETOOTH")

        if (action == "ACTION_SIMULATE_CONTEXT") {
            if (targetActivity != null) {
                handleActivityTransition(targetActivity)
            }
            if (targetBluetooth != null) {
                if (targetBluetooth == "Disconnected") {
                    handleBluetoothDisconnected()
                } else {
                    handleBluetoothConnected(targetBluetooth)
                }
            }
        }

        // Build status details
        val statusMessage = "Activity: ${stateManager.currentActivity.value} | BT: ${stateManager.bluetoothDevice.value}"
        startForegroundServiceCompact(statusMessage)

        return START_STICKY
    }

    private fun startForegroundServiceCompact(statusMessage: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Context Engine Active")
            .setContentText(statusMessage)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun handleActivityTransition(newActivity: String) {
        val oldActivity = stateManager.currentActivity.value
        if (oldActivity != newActivity) {
            stateManager.updateActivity(newActivity)
            serviceScope.launch {
                telemetryRepository.insertLog(
                    "Sensor transition: $oldActivity -> $newActivity",
                    "SENSOR"
                )
            }
            updateNotificationStatus()
        }
    }

    private fun handleBluetoothConnected(deviceName: String) {
        stateManager.updateBluetoothDevice(deviceName)
        serviceScope.launch {
            telemetryRepository.insertLog("Bluetooth connected to: $deviceName", "SENSOR")
        }
        updateNotificationStatus()
    }

    private fun handleBluetoothDisconnected() {
        stateManager.updateBluetoothDevice("Disconnected")
        serviceScope.launch {
            telemetryRepository.insertLog("Bluetooth disconnected", "SENSOR")
        }
        updateNotificationStatus()
    }

    private fun updateNotificationStatus() {
        val statusMessage = "Activity: ${stateManager.currentActivity.value} | BT: ${stateManager.bluetoothDevice.value}"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Context Engine Monitoring")
            .setContentText(statusMessage)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Context Engine Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device context and fires background automations."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun registerBluetoothListeners() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    constructor() : super()
}
