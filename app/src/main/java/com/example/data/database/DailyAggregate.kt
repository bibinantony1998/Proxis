package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_aggregates")
data class DailyAggregate(
    @PrimaryKey val date: String,
    val socialTimeMs: Long,
    val musicTimeMs: Long,
    val chatTimeMs: Long,
    val otherTimeMs: Long,
    val callCount: Int,
    val callDurationSec: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
