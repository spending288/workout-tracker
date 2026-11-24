package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.service.WorkoutForegroundService
import com.example.ui.util.FeedbackUtils

/**
 * Platform side effects required by [RepDayViewModel], isolated behind an interface so
 * ViewModel logic can be unit-tested on the JVM without Robolectric or Android runtime dependencies.
 */
interface WorkoutPlatformHooks {
    fun triggerLightTick()
    fun triggerRestCompleteVibration()
    suspend fun playRestCompleteChime()
    fun startOrSyncWorkoutService()
    fun stopWorkoutService()
    fun postRestDoneHeadsUpNotification(message: String)
    fun cancelRestDoneNotification()
    fun shouldPromptNotificationPermission(): Boolean
    fun registerProcessStopObserver(onStop: () -> Unit): () -> Unit
}

class AndroidWorkoutPlatformHooks(
    private val application: Application
) : WorkoutPlatformHooks {

    override fun triggerLightTick() {
        FeedbackUtils.triggerLightTick(application)
    }

    override fun triggerRestCompleteVibration() {
        FeedbackUtils.triggerRestCompleteVibration(application)
    }

    override suspend fun playRestCompleteChime() {
        FeedbackUtils.playRestCompleteChime(application)
    }

    override fun startOrSyncWorkoutService() {
        WorkoutForegroundService.startOrSync(application)
    }

    override fun stopWorkoutService() {
        WorkoutForegroundService.stop(application)
    }

    override fun postRestDoneHeadsUpNotification(message: String) {
        WorkoutForegroundService.postRestDoneHeadsUpNotification(application, message)
    }

    override fun cancelRestDoneNotification() {
        WorkoutForegroundService.cancelRestDoneNotification(application)
    }

    override fun shouldPromptNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !WorkoutForegroundService.hasNotificationPermission(application)
    }

    override fun registerProcessStopObserver(onStop: () -> Unit): () -> Unit {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                onStop()
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        return { lifecycle.removeObserver(observer) }
    }
}
