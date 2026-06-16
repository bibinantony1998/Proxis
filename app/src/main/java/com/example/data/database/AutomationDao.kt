package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM daily_aggregates ORDER BY date DESC")
    fun getAllAggregates(): Flow<List<DailyAggregate>>

    @Query("SELECT * FROM daily_aggregates WHERE date = :date LIMIT 1")
    suspend fun getAggregateByDate(date: String): DailyAggregate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAggregate(aggregate: DailyAggregate)

    @Query("SELECT * FROM context_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<ContextLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ContextLog)

    @Query("DELETE FROM daily_aggregates")
    suspend fun clearAggregates()

    @Query("DELETE FROM context_logs")
    suspend fun clearLogs()
}
