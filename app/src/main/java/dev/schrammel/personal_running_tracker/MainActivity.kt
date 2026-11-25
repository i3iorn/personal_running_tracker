package dev.schrammel.personal_running_tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.schrammel.personal_running_tracker.databinding.ActivityMainBinding
import dev.schrammel.personal_running_tracker.tracking.TrackingService

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleText.text = "Personal Running Tracker"
        binding.statusText.text = "Ready"

        binding.startButton.setOnClickListener { startRun() }
        binding.pauseButton.setOnClickListener { pauseRun() }
        binding.finishButton.setOnClickListener { finishRun() }
    }

    private fun startRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_RUN
        })
        binding.statusText.text = "Tracking…"
    }

    private fun pauseRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE_RUN
        })
        binding.statusText.text = "Paused"
    }

    private fun finishRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_FINISH_RUN
        })
        binding.statusText.text = "Run finished"
    }
}
