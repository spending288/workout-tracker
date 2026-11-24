package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.RepDayDataStore
import com.example.data.local.RestClaimStatus
import com.example.data.local.RestTimerSnapshot
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.domain.RepDayRules
import com.example.domain.RestCompletionOutcome
import com.example.domain.RestTimerRules
import com.example.ui.util.FeedbackUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WorkoutForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var dataStore: RepDayDataStore
    private lateinit var notificationManager: NotificationManager

    private var observerJob: Job? = null
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var latestWorkout: ActiveWorkout? = null
    private var latestRest: RestTimerSnapshot = RestTimerSnapshot()
    private var latestSettings: AppSettings = AppSettings()
    private var activeScheduledEndTimeMs: Long? = null
    private var hasReceivedInitialEmission: Boolean = false

    override fun onCreate() {
        super.onCreate()
        dataStore = RepDayDataStore(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWorkoutService()
                return START_NOT_STICKY
            }
            ACTION_ADD_30S -> {
                promoteToForeground(buildOngoingNotification(latestWorkout, latestRest))
                startObservingStateIfNeeded()
                serviceScope.launch {
                    cancelRestDoneNotification()
                    dataStore.adjustRestTimer(deltaSeconds = 30)
                }
                return START_STICKY
            }
            ACTION_SKIP_REST -> {
                promoteToForeground(buildOngoingNotification(latestWorkout, latestRest))
                startObservingStateIfNeeded()
                serviceScope.launch {
                    cancelRestDoneNotification()
                    releaseWakeLock()
                    countdownJob?.cancel()
                    countdownJob = null
                    activeScheduledEndTimeMs = null
                    dataStore.saveRestTimer(RestTimerSnapshot())
                }
                return START_STICKY
            }
            else -> {
                promoteToForeground(buildOngoingNotification(latestWorkout, latestRest))
                startObservingStateIfNeeded()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        cancelRestDoneNotification()
        countdownJob?.cancel()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startObservingStateIfNeeded() {
        if (observerJob != null) return
        observerJob = serviceScope.launch {
            combine(
                dataStore.activeWorkoutFlow,
                dataStore.restTimerFlow,
                dataStore.settingsFlow
            ) { workout, rest, settings ->
                Triple(workout, rest, settings)
            }.collect { (workout, rest, settings) ->
                hasReceivedInitialEmission = true
                latestWorkout = workout
                latestRest = rest
                latestSettings = settings

                if (workout == null) {
                    stopWorkoutService()
                    return@collect
                }

                syncCountdownAndWakeLock(rest)
                updateOngoingNotification(workout, rest)
            }
        }
    }

    private fun syncCountdownAndWakeLock(rest: RestTimerSnapshot) {
        val now = System.currentTimeMillis()
        val endMs = rest.endTimeMs
        val outcome = RestTimerRules.evaluateRestTimer(
            endTimeMs = endMs,
            completed = rest.completed,
            nowMs = now
        )

        when (outcome) {
            RestCompletionOutcome.STILL_RUNNING -> {
                val targetEndMs = endMs ?: return
                cancelRestDoneNotification()
                val wakeTimeout = RestTimerRules.computeWakeLockTimeoutMs(
                    endTimeMs = targetEndMs,
                    completed = false,
                    nowMs = now
                )
                if (wakeTimeout != null) {
                    acquireOrRefreshWakeLock(wakeTimeout)
                }
                if (activeScheduledEndTimeMs != targetEndMs || countdownJob?.isActive != true) {
                    countdownJob?.cancel()
                    activeScheduledEndTimeMs = targetEndMs
                    countdownJob = serviceScope.launch {
                        waitUntilTimestamp(targetEndMs)
                        handleRestTimerReachedZero(targetEndMs)
                    }
                }
            }
            RestCompletionOutcome.COMPLETE_WITH_ALERT,
            RestCompletionOutcome.COMPLETE_SILENT_STALE -> {
                countdownJob?.cancel()
                countdownJob = null
                activeScheduledEndTimeMs = null
                if (!rest.completed && endMs != null) {
                    serviceScope.launch {
                        handleRestTimerReachedZero(endMs)
                    }
                } else {
                    releaseWakeLock()
                }
            }
            RestCompletionOutcome.INACTIVE -> {
                countdownJob?.cancel()
                countdownJob = null
                activeScheduledEndTimeMs = null
                cancelRestDoneNotification()
                releaseWakeLock()
            }
        }
    }

    private suspend fun waitUntilTimestamp(targetEndMs: Long) {
        while (true) {
            val remaining = targetEndMs - System.currentTimeMillis()
            if (remaining <= 0L) break
            delay(remaining)
        }
    }

    private suspend fun handleRestTimerReachedZero(expectedEndTimeMs: Long) {
        try {
            val claim = dataStore.claimRestTimerCompletion(
                expectedEndTimeMs = expectedEndTimeMs,
                nowMs = System.currentTimeMillis()
            )
            when (claim.status) {
                RestClaimStatus.COMPLETED_WITH_ALERT -> {
                    val settings = dataStore.settingsFlow.first()
                    val workout = dataStore.activeWorkoutFlow.first()
                    val nextSet = RestTimerRules.findNextIncompleteSet(workout, claim.exerciseName)
                    val message = RestTimerRules.formatRestDoneNotificationText(nextSet, claim.exerciseName)
                    postRestDoneHeadsUpNotification(message)
                    if (settings.vibrateOnRestEnd) {
                        FeedbackUtils.triggerRestCompleteVibration(applicationContext)
                    }
                    if (settings.soundEnabled) {
                        FeedbackUtils.playRestCompleteChime(applicationContext)
                    }
                }
                RestClaimStatus.COMPLETED_SILENT_STALE -> {
                    // Ended more than 5s ago (e.g. process was killed): mark Done silently.
                }
                RestClaimStatus.ALREADY_HANDLED -> Unit
            }
        } finally {
            activeScheduledEndTimeMs = null
            releaseWakeLock()
        }
    }

    private fun acquireOrRefreshWakeLock(timeoutMs: Long) {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val existing = wakeLock
            if (existing != null && existing.isHeld) {
                existing.release()
            }
            val lock = existing ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                wakeLock = this
            }
            lock.acquire(timeoutMs.coerceAtLeast(1_000L))
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            val lock = wakeLock
            if (lock != null && lock.isHeld) {
                lock.release()
            }
        }
    }

    private fun stopWorkoutService() {
        countdownJob?.cancel()
        countdownJob = null
        activeScheduledEndTimeMs = null
        releaseWakeLock()
        cancelRestDoneNotification()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForeground(notification: Notification) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID_ONGOING, notification, fgsType)
    }

    private fun updateOngoingNotification(workout: ActiveWorkout, rest: RestTimerSnapshot) {
        if (!hasNotificationPermission(this)) return
        notificationManager.notify(NOTIFICATION_ID_ONGOING, buildOngoingNotification(workout, rest))
    }

    private fun buildOngoingNotification(
        workout: ActiveWorkout?,
        rest: RestTimerSnapshot
    ): Notification {
        val now = System.currentTimeMillis()
        val isCountingDown = !rest.completed && rest.endTimeMs != null && rest.endTimeMs > now
        val isRestDone = rest.completed

        val title = workout?.routineName?.ifBlank { "Active Workout" } ?: "Active Workout"
        val elapsedSec = if (workout != null) ((now - workout.startedAt) / 1000L).coerceAtLeast(0L) else 0L
        val completedSets = workout?.exercises?.sumOf { ex -> ex.sets.count { it.completed } } ?: 0
        val totalSets = workout?.exercises?.sumOf { it.sets.size } ?: 0

        val builder = NotificationCompat.Builder(this, CHANNEL_ACTIVE_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_repday)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(createOpenActiveWorkoutPendingIntent())

        when {
            isCountingDown -> {
                val endMs = rest.endTimeMs!!
                val restLabel = rest.exerciseName.ifBlank { "Rest timer" }
                builder
                    .setContentText("Resting · $restLabel")
                    .setSubText("Session ${RepDayRules.formatElapsed(elapsedSec)}")
                    .setShowWhen(true)
                    .setWhen(endMs)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(
                        0,
                        "+30s",
                        createServiceActionPendingIntent(ACTION_ADD_30S, REQUEST_CODE_ADD_30S)
                    )
                    .addAction(
                        0,
                        "Skip",
                        createServiceActionPendingIntent(ACTION_SKIP_REST, REQUEST_CODE_SKIP)
                    )
            }
            isRestDone -> {
                val nextSet = RestTimerRules.findNextIncompleteSet(workout, rest.exerciseName)
                builder
                    .setContentText(RestTimerRules.formatRestDoneNotificationText(nextSet, rest.exerciseName))
                    .setShowWhen(true)
                    .setWhen(workout?.startedAt ?: now)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .addAction(
                        0,
                        "+30s",
                        createServiceActionPendingIntent(ACTION_ADD_30S, REQUEST_CODE_ADD_30S)
                    )
                    .addAction(
                        0,
                        "Skip",
                        createServiceActionPendingIntent(ACTION_SKIP_REST, REQUEST_CODE_SKIP)
                    )
            }
            else -> {
                val progressText = if (totalSets > 0) {
                    "$completedSets/$totalSets sets completed"
                } else {
                    "Workout in progress"
                }
                builder
                    .setContentText(progressText)
                    .setShowWhen(true)
                    .setWhen(workout?.startedAt ?: now)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
            }
        }

        return builder.build()
    }

    private fun postRestDoneHeadsUpNotification(message: String) {
        postRestDoneHeadsUpNotification(applicationContext, message)
    }

    private fun cancelRestDoneNotification() {
        cancelRestDoneNotification(applicationContext)
    }

    private fun createOpenActiveWorkoutPendingIntent(): PendingIntent =
        createOpenActiveWorkoutPendingIntent(this)

    private fun createServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, WorkoutForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureNotificationChannels() {
        ensureNotificationChannels(applicationContext)
    }

    companion object {
        const val CHANNEL_ACTIVE_WORKOUT = "repday_active_workout"
        const val CHANNEL_REST_TIMER = "repday_rest_timer"

        const val NOTIFICATION_ID_ONGOING = 1001
        const val NOTIFICATION_ID_REST_DONE = 1002

        const val EXTRA_OPEN_ACTIVE_WORKOUT = "com.example.EXTRA_OPEN_ACTIVE_WORKOUT"

        const val ACTION_START_OR_SYNC = "com.example.action.START_OR_SYNC_WORKOUT"
        const val ACTION_ADD_30S = "com.example.action.REST_ADD_30S"
        const val ACTION_SKIP_REST = "com.example.action.REST_SKIP"
        const val ACTION_STOP = "com.example.action.STOP_WORKOUT"

        private const val REQUEST_CODE_OPEN_APP = 2001
        private const val REQUEST_CODE_ADD_30S = 2002
        private const val REQUEST_CODE_SKIP = 2003
        private const val WAKE_LOCK_TAG = "RepDay:RestTimerWakeLock"

        fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val ongoingChannel = NotificationChannel(
                CHANNEL_ACTIVE_WORKOUT,
                "Active workout",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows elapsed time and live rest countdown during an active workout"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val restDoneChannel = NotificationChannel(
                CHANNEL_REST_TIMER,
                "Rest timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up alerts when a workout rest timer ends"
                // Sound and vibration are handled explicitly so preferences + audio ducking apply cleanly
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ongoingChannel)
            nm.createNotificationChannel(restDoneChannel)
        }

        fun postRestDoneHeadsUpNotification(context: Context, message: String) {
            if (!hasNotificationPermission(context)) return
            ensureNotificationChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, CHANNEL_REST_TIMER)
                .setSmallIcon(R.drawable.ic_stat_repday)
                .setContentTitle("Rest Complete")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setDefaults(0)
                .setContentIntent(createOpenActiveWorkoutPendingIntent(context))
                .build()
            nm.notify(NOTIFICATION_ID_REST_DONE, notification)
        }

        fun cancelRestDoneNotification(context: Context) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID_REST_DONE)
            }
        }

        private fun createOpenActiveWorkoutPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_ACTIVE_WORKOUT, true)
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun startOrSync(context: Context) {
            if (!hasNotificationPermission(context)) return
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                action = ACTION_START_OR_SYNC
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
