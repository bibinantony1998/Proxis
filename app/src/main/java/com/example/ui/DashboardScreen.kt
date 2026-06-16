package com.example.ui

import android.app.usage.UsageStatsManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ContextLog
import com.example.data.database.DailyAggregate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe State flows from VM
    val currentActivity by viewModel.currentActivity.collectAsState()
    val bluetoothDevice by viewModel.bluetoothDevice.collectAsState()
    val isDrivingModeEngaged by viewModel.isDrivingModeEngaged.collectAsState()
    val lastActionLog by viewModel.lastActionLog.collectAsState()

    val dailyAggregates by viewModel.dailyAggregates.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()

    val hasUsagePermission by viewModel.isUsagePermissionGranted.collectAsState()
    val hasCallLogPermission by viewModel.isCallLogPermissionGranted.collectAsState()

    val todayAggregate = dailyAggregates.firstOrNull()

    // Local trigger for Mock Player Simulator UI
    var showPlayerSimulator by remember { mutableStateOf(false) }
    var isSimulatedPlaying by remember { mutableStateOf(false) }

    // Auto trigger simulated playlist display for on-screen clicker demonstration
    LaunchedEffect(isDrivingModeEngaged) {
        if (isDrivingModeEngaged) {
            showPlayerSimulator = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Context Icon",
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "CONTEXT",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshData() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Data",
                            tint = Color(0xFF00FFCC)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Core Status Overview
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Engine Status",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isDrivingModeEngaged) Color(0xFFEF4444).copy(alpha = 0.2f)
                                        else Color(0xFF10B981).copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isDrivingModeEngaged) "AUTOMATION ACTIVE" else "LISTENING",
                                    color = if (isDrivingModeEngaged) Color(0xFFF87171) else Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IndicatorMetric(
                                label = "Activity State",
                                value = currentActivity,
                                icon = Icons.Default.Home,
                                weight = 1f,
                                accentColor = Color(0xFF38BDF8)
                            )
                            IndicatorMetric(
                                label = "BT Device",
                                value = bluetoothDevice,
                                icon = Icons.Default.Share,
                                weight = 1.2f,
                                accentColor = Color(0xFFEC4899)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Last Action: $lastActionLog",
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }
            }

            // 2. Permission Warn Panels (If missing)
            if (!hasUsagePermission || !hasCallLogPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFF87171)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Telemetry Access Required",
                                    color = Color(0xFFFBEBEE),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "To display accurate daily logs and screen durations, please grant permissions.",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!hasUsagePermission) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            // Fallback if Uri does not work
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp).testTag("grant_usage_btn")
                                    ) {
                                        Text("Usage Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!hasCallLogPermission) {
                                    Button(
                                        onClick = {
                                            // Requests standard permission implicitly
                                            viewModel.refreshData()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp).testTag("grant_call_btn")
                                    ) {
                                        Text("Call Log Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Simulated Car Playlist Dashboard View (Connective Demonstration)
            item {
                AnimatedVisibility(
                    visible = showPlayerSimulator,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                        border = BorderStroke(2.dp, Color(0xFF00FFCC)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF022C22), Color(0xFF030712))
                                    )
                                )
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Car Audio Connected",
                                        tint = Color(0xFF00FFCC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "YT MUSIC SIMULATOR",
                                        color = Color(0xFF00FFCC),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                IconButton(
                                    onClick = { showPlayerSimulator = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Close View",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Car Playlist Cover",
                                    tint = Color(0xFF00FFCC),
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "My Driving Playlist",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Orchestrated via Accessibility AutoClicker",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Crucial click target that Accessibility AutoClicker searches for and clicks!
                            Button(
                                onClick = {
                                    isSimulatedPlaying = !isSimulatedPlaying
                                    viewModel.simulateContextChange("IN_VEHICLE", bluetoothDevice)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSimulatedPlaying) Color(0xFF10B981) else Color(0xFF00FFCC)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.81f)
                                    .testTag("play_playlist_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSimulatedPlaying) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                        contentDescription = "Play Icon",
                                        tint = if (isSimulatedPlaying) Color.White else Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isSimulatedPlaying) "Playlist Active & Playing!" else "Play Playlist",
                                        color = if (isSimulatedPlaying) Color.White else Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // 4. Recommendation Engine (Dynamic Local Logic)
            item {
                val recommendation = viewModel.generateRecommendation(todayAggregate)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B254B)),
                    border = BorderStroke(1.dp, Color(0xFF2E3D85)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f))
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Alert Symbol",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "AI CONTEXT RECOMMENDATION",
                                color = Color(0xFF90CDF4),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = recommendation,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // 5. Context Simulation Control Panel
            item {
                Text(
                    text = "TELEMETRY SIMULATOR CONTROLS",
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Simulate Activity Shift",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SimulateButton(
                                label = "STILL",
                                active = currentActivity == "STILL",
                                onClick = {
                                    isSimulatedPlaying = false
                                    viewModel.simulateContextChange("STILL", "Disconnected")
                                },
                                modifier = Modifier.weight(1f).testTag("sim_still")
                            )

                            SimulateButton(
                                label = "IN_VEHICLE",
                                active = currentActivity == "IN_VEHICLE",
                                onClick = {
                                    showPlayerSimulator = true
                                    viewModel.simulateContextChange("IN_VEHICLE", "My Audi Car Audio")
                                },
                                modifier = Modifier.weight(1f).testTag("sim_vehicle")
                            )

                            SimulateButton(
                                label = "WALKING",
                                active = currentActivity == "WALKING",
                                onClick = {
                                    isSimulatedPlaying = false
                                    viewModel.simulateContextChange("WALKING", "Disconnected")
                                },
                                modifier = Modifier.weight(1f).testTag("sim_walking")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            "Simulate Speaker Trigger Connection",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SimulateButton(
                                label = "Pair Car Audio BT",
                                active = bluetoothDevice != "Disconnected",
                                onClick = {
                                    viewModel.simulateContextChange(currentActivity, "Supercharged Tesla Audio")
                                },
                                modifier = Modifier.weight(1f).testTag("sim_bt_connect")
                            )

                            SimulateButton(
                                label = "Unpair Bluetooth",
                                active = bluetoothDevice == "Disconnected",
                                onClick = {
                                    viewModel.simulateContextChange(currentActivity, "Disconnected")
                                },
                                modifier = Modifier.weight(1f).testTag("sim_bt_disconnect")
                            )
                        }
                    }
                }
            }

            // 6. DB Aggregation Metrics
            item {
                Text(
                    text = "SND PERSISTED DATABASE METRICS",
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Today's OS Daily Telemetry",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            IconButton(onClick = { viewModel.refreshData() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Row", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        MetricRow("Social Media screen time", todayAggregate?.let { "${it.socialTimeMs / 60000} mins" } ?: "--")
                        MetricRow("Audio & Music duration", todayAggregate?.let { "${it.musicTimeMs / 60000} mins" } ?: "--")
                        MetricRow("Messaging / Chat apps", todayAggregate?.let { "${it.chatTimeMs / 60000} mins" } ?: "--")
                        MetricRow("Call Log total dials", todayAggregate?.let { "${it.callCount} calls" } ?: "--")
                        MetricRow("Call Duration volume", todayAggregate?.let { "${it.callDurationSec} secs" } ?: "--")

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Logs fetched and updated: " + (todayAggregate?.let {
                                SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date(it.lastUpdated))
                            } ?: "No aggregation yet"),
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // 7. System Trace Logger Output Terminal
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE SYSTEM TELEMETRY TERMINAL (ROOM DB)",
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "CLEAR ENGINE LOGS",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { viewModel.clearHistory() }
                    )
                }
            }

            if (recentLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFF070B19), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Terminal idle. Sensed actions will print here.",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                items(recentLogs) { log ->
                    TerminalLogItem(log)
                }
            }

            // Empty spacer at the end for clean aesthetic spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun IndicatorMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    weight: Float,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .background(Color(0xFF020617), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SimulateButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF00FFCC) else Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun TerminalLogItem(log: ContextLog) {
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
    val typeColor = when (log.type) {
        "SENSOR" -> Color(0xFF38BDF8)
        "AUTOMATION" -> Color(0xFF00FFCC)
        "DATABASE" -> Color(0xFFA78BFA)
        "ERROR" -> Color(0xFFF87171)
        else -> Color(0xFF94A3B8)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070B19), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "[$timeStr]",
            color = Color(0xFF475569),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = log.type.padEnd(10),
            color = typeColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = log.message,
            color = Color(0xFFCBD5E1),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
