package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.TelemetryRepository

class ContextApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var telemetryRepository: TelemetryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Room Database
        database = AppDatabase.getDatabase(this)
        
        // Initialize Telemetry Repository manually (Constructor Injection)
        telemetryRepository = TelemetryRepository(this, database.automationDao())
    }
}
