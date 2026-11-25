package dev.schrammel.personal_running_tracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.schrammel.personal_running_tracker.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleText.text = "Personal Running Tracker"
        binding.statusText.text = "Ready to start"
        binding.startButton.setOnClickListener {
            // TODO Phase 2: start tracking screen
        }
    }
}
