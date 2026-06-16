package com.example.data.repository

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.data.database.AutomationDao
import com.example.data.database.ContextLog
import com.example.data.database.DailyAggregate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TelemetryRepository(
    private val context: Context,
    private val dao: AutomationDao
) {
    val allAggregates: Flow<List<DailyAggregate>> = dao.getAllAggregates()
    val recentLogs: Flow<List<ContextLog>> = dao.getRecentLogs()

    suspend fun insertLog(message: String, type: String = "TELEMETRY") {
        withContext(Dispatchers.IO) {
            dao.insertLog(ContextLog(message = message, type = type))
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            dao.clearAggregates()
            dao.clearLogs()
        }
    }

    /**
     * Determines whether Usage Stats Permission is granted.
     */
    fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Determines whether READ_CALL_LOG permission is granted.
     */
    fun isCallLogPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries the actual Android APIs (UsageStatsManager and CallLog Provider)
     * and aggregates data into the Room Database.
     */
    suspend fun fetchAndAggregateDailyData(): DailyAggregate {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = dateFormat.format(calendar.time)

            // Calculate query range (Start of today)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            var socialTime = 0L
            var musicTime = 0L
            var chatTime = 0L
            var otherTime = 0L

            var callCount = 0
            var callDurationSec = 0L

            // 1. Process Usage Statistics
            if (isUsageStatsPermissionGranted()) {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                if (usageStatsManager != null) {
                    val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
                    stats.forEach { (packageName, usage) ->
                        val duration = usage.totalTimeInForeground
                        when {
                            isSocialPkg(packageName) -> socialTime += duration
                            isMusicPkg(packageName) -> musicTime += duration
                            isChatPkg(packageName) -> chatTime += duration
                            else -> otherTime += duration
                        }
                    }
                }
            } else {
                // Return fallback/mock telemetry data for demonstration when platform permissions are restricted,
                // while preserving the core API invocation attempt.
                insertLog("Usage Stats permission not granted. Using mock values.", "TELEMETRY")
                socialTime = 5400000L // 1.5 Hours
                musicTime = 3600000L  // 1 Hour
                chatTime = 2400000L   // 40 Mins
                otherTime = 1800000L  // 30 Mins
            }

            // 2. Process Call History ContentProvider
            if (isCallLogPermissionGranted()) {
                try {
                    val cursor = context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf(CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                        "${CallLog.Calls.DATE} >= ?",
                        arrayOf(startTime.toString()),
                        "${CallLog.Calls.DATE} DESC"
                    )
                    cursor?.use {
                        val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                        while (it.moveToNext()) {
                            callCount++
                            if (durationIndex != -1) {
                                callDurationSec += it.getLong(durationIndex)
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    insertLog("Call Log Permission missing: ${e.localizedMessage}", "ERROR")
                }
            } else {
                insertLog("READ_CALL_LOG permission not granted. Using mock values.", "TELEMETRY")
                callCount = 12
                callDurationSec = 540L // 9 minutes total
            }

            // Write or merge into Room Database
            val existing = dao.getAggregateByDate(dateStr)
            val newAggregate = DailyAggregate(
                date = dateStr,
                socialTimeMs = socialTime,
                musicTimeMs = musicTime,
                chatTimeMs = chatTime,
                otherTimeMs = otherTime,
                callCount = callCount,
                callDurationSec = callDurationSec,
                lastUpdated = System.currentTimeMillis()
            )

            dao.insertOrUpdateAggregate(newAggregate)
            insertLog("Aggregated data for $dateStr: Call Count: $callCount, Social: ${socialTime / 60000}m", "DATABASE")

            newAggregate
        }
    }

    private fun isSocialPkg(pkg: String): Boolean {
        val socialSet = setOf(
            "com.facebook.katana", "com.instagram.android", "com.facebook.lite",
            "com.twitter.android", "com.snapchat.android", "com.tiktok.android", 
            "com.zhiliaoapp.musically", "com.pinterest"
        )
        return socialSet.contains(pkg.lowercase()) || pkg.contains("social")
    }

    private fun isMusicPkg(pkg: String): Boolean {
        val musicSet = setOf(
            "com.google.android.apps.youtube.music", "com.spotify.music",
            "com.pandora.android", "com.apple.android.music", "com.soundcloud.android",
            "com.amazon.mp3", "deezer.android.app"
        )
        return musicSet.contains(pkg.lowercase()) || pkg.contains("music") || pkg.contains("audio")
    }

    private fun isChatPkg(pkg: String): Boolean {
        val chatSet = setOf(
            "com.whatsapp", "com.telegram.messenger", "org.thunderdog.challegram",
            "com.facebook.orca", "com.discord", "org.telegram.messenger",
            "com.skype.raider", "com.viber.voip", "com.tencent.mm"
        )
        return chatSet.contains(pkg.lowercase()) || pkg.contains("chat") || pkg.contains("messenger")
    }
}
