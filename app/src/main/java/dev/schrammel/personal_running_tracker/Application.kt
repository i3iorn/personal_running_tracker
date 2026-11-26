package dev.schrammel.personal_running_tracker

import android.app.Application
import android.content.Context
import android.util.Log
import android.os.Environment
import dev.schrammel.personal_running_tracker.data.AppDatabase
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunningTrackerApp : Application() {
    private val TAG = "RunningTrackerApp"
    private val LOG_FILE_NAME = "crash_log.txt"

    // Write a startup marker immediately so we know the Application class loaded
    init {
        try {
            writeStartMarker()
        } catch (e: Exception) {
            // Never throw from init block
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Write startup marker on every onCreate
        writeStartMarker()
        try {
            AppDatabase.getInstance(this)
        } catch (e: Exception) {
            Log.e(TAG, "AppDatabase init failed", e)
        }
        installCrashHandler()
    }

    private fun writeStartMarker() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = "[$timestamp] APP_START\n"
            val logFile = File(getFilesDir(), LOG_FILE_NAME)
            // Append only; create if missing
            FileWriter(logFile, true).apply {
                append(entry)
                close()
            }
            Log.d(TAG, "Start marker written")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write start marker", e)
        }
    }

    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLogFile("UNCAUGHT_EXCEPTION", "${throwable.javaClass.name}: ${throwable.localizedMessage}\n${throwable.stackTraceToString()}")
            } catch (e: Exception) {
                // Last resort: system log only
                Log.e(TAG, "Crash handler itself failed", e)
            }
            // Fallback: also try writing to system error stream
            try {
                val fallback = File("/data/data/${packageName}/files/crash_fallback.log")
                fallback.parentFile.mkdirs()
                fallback.writeText("UNCAUGHT_EXCEPTION: ${throwable.localizedMessage}\n${throwable.stackTraceToString()}\n")
            } catch (e2: Exception) {
                // Ignore - we did our best
            }
        }
    }

    private fun writeLogFile(label: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = "[$timestamp] [$label] $message\n"
            val logFile = File(getFilesDir(), LOG_FILE_NAME)
            FileWriter(logFile, true).apply {
                append(entry)
                close()
            }
            val truncatedMsg = message.take(60)
            Log.d(TAG, "Log written: $label - $truncatedMsg")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write log file", e)
        }
    }

    // Safe accessor for the package name (used by crash handler fallback)
    private val packageNameSafe: String
        get() = try { packageName } catch (e: Exception) { "unknown" }
}
