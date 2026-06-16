package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "context_logs")
data class ContextLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: String // e.g. "SENSOR", "AUTOMATION", "TELEMETRY", "ERROR"
)
