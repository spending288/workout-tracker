package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.service.WorkoutForegroundService
import com.example.ui.RepDayApp

class MainActivity : ComponentActivity() {

    private var openActiveWorkoutSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWorkoutIntent(intent)
        setContent {
            RepDayApp(openActiveWorkoutSignal = openActiveWorkoutSignal)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWorkoutIntent(intent)
    }

    private fun handleWorkoutIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(WorkoutForegroundService.EXTRA_OPEN_ACTIVE_WORKOUT, false) == true) {
            intent.removeExtra(WorkoutForegroundService.EXTRA_OPEN_ACTIVE_WORKOUT)
            openActiveWorkoutSignal += 1
        }
    }
}
