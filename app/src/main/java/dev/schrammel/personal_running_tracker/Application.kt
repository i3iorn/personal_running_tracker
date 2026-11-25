package dev.schrammel.personal_running_tracker

import android.app.Application
import dev.schrammel.personal_running_tracker.data.AppDatabase

class RunningTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
    }
}
