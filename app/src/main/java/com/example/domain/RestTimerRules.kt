package com.example.domain

import com.example.data.model.ActiveWorkout

enum class RestCompletionOutcome {
    INACTIVE,
    STILL_RUNNING,
    COMPLETE_WITH_ALERT,
    COMPLETE_SILENT_STALE
}

data class NextSetHint(
    val setNumber: Int,
    val exerciseName: String
)

data class AdjustedRestTimer(
    val endTimeMs: Long?,
    val totalDurationSec: Int,
    val completed: Boolean
)

object RestTimerRules {

    const val STALE_ALERT_GRACE_MS = 5_000L
    const val SERVICE_STARTUP_GRACE_MS = 5_000L
    const val WAKE_LOCK_BUFFER_MS = 10_000L
    const val MAX_REST_DURATION_SEC = 600
    const val MAX_WAKE_LOCK_TIMEOUT_MS = MAX_REST_DURATION_SEC * 1000L + WAKE_LOCK_BUFFER_MS

    /**
     * Evaluates the rest timer against [nowMs] using the stored [endTimeMs] as the single
     * source of truth. If the timer ended more than [STALE_ALERT_GRACE_MS] (5 s) ago,
     * returns [RestCompletionOutcome.COMPLETE_SILENT_STALE] so UI shows "Done" without
     * playing a late chime or vibration.
     */
    fun evaluateRestTimer(
        endTimeMs: Long?,
        completed: Boolean,
        nowMs: Long
    ): RestCompletionOutcome {
        if (completed) return RestCompletionOutcome.COMPLETE_SILENT_STALE
        if (endTimeMs == null || endTimeMs <= 0L) return RestCompletionOutcome.INACTIVE
        val remainingMs = endTimeMs - nowMs
        if (remainingMs > 0L) return RestCompletionOutcome.STILL_RUNNING
        val overdueMs = -remainingMs
        return if (overdueMs <= STALE_ALERT_GRACE_MS) {
            RestCompletionOutcome.COMPLETE_WITH_ALERT
        } else {
            RestCompletionOutcome.COMPLETE_SILENT_STALE
        }
    }

    /**
     * Returns the partial wake lock timeout in milliseconds for an active countdown:
     * remaining time + 10 s, capped at 10:00 + 10 s. Returns null when no countdown is active.
     */
    fun computeWakeLockTimeoutMs(
        endTimeMs: Long?,
        completed: Boolean,
        nowMs: Long
    ): Long? {
        if (completed || endTimeMs == null || endTimeMs <= nowMs) return null
        val remainingMs = endTimeMs - nowMs
        return (remainingMs + WAKE_LOCK_BUFFER_MS).coerceAtMost(MAX_WAKE_LOCK_TIMEOUT_MS)
    }

    /**
     * Computes the updated rest end timestamp when adjusting by [deltaSeconds] (e.g. +30s, -15s).
     */
    fun adjustRestEndTime(
        currentEndTimeMs: Long?,
        currentTotalDurationSec: Int,
        completed: Boolean,
        deltaSeconds: Int,
        nowMs: Long
    ): AdjustedRestTimer {
        if (completed || currentEndTimeMs == null || currentEndTimeMs <= 0L) {
            if (deltaSeconds > 0) {
                val sec = deltaSeconds.coerceIn(5, MAX_REST_DURATION_SEC)
                return AdjustedRestTimer(
                    endTimeMs = nowMs + sec * 1000L,
                    totalDurationSec = sec,
                    completed = false
                )
            }
            return AdjustedRestTimer(
                endTimeMs = null,
                totalDurationSec = currentTotalDurationSec.coerceAtLeast(1),
                completed = false
            )
        }
        val maxEndMs = nowMs + MAX_REST_DURATION_SEC * 1000L
        val newEndMs = (currentEndTimeMs + deltaSeconds * 1000L).coerceAtMost(maxEndMs)
        if (newEndMs <= nowMs) {
            return AdjustedRestTimer(
                endTimeMs = null,
                totalDurationSec = currentTotalDurationSec.coerceAtLeast(1),
                completed = false
            )
        }
        val newRem = RepDayRules.remainingRestSeconds(newEndMs, nowMs)
        val newTotal = maxOf(currentTotalDurationSec.coerceAtLeast(1), newRem)
        return AdjustedRestTimer(
            endTimeMs = newEndMs,
            totalDurationSec = newTotal,
            completed = false
        )
    }

    /**
     * Finds the next incomplete set in [workout], prioritizing [preferredExerciseName] first,
     * then scanning forward and wrapping from the top.
     */
    fun findNextIncompleteSet(
        workout: ActiveWorkout?,
        preferredExerciseName: String? = null
    ): NextSetHint? {
        if (workout == null || workout.exercises.isEmpty()) return null
        val prefKey = preferredExerciseName?.let { RepDayRules.exerciseKey(it) }.orEmpty()
        val prefIndex = if (prefKey.isNotEmpty()) {
            workout.exercises.indexOfFirst { RepDayRules.exerciseKey(it.name) == prefKey }
        } else {
            -1
        }

        if (prefIndex >= 0) {
            val ex = workout.exercises[prefIndex]
            val nextSetIdx = ex.sets.indexOfFirst { !it.completed }
            if (nextSetIdx >= 0) {
                return NextSetHint(setNumber = nextSetIdx + 1, exerciseName = ex.name)
            }
            // Exercise finished: scan forward, then wrap from top
            for (i in (prefIndex + 1) until workout.exercises.size) {
                val candidate = workout.exercises[i]
                val idx = candidate.sets.indexOfFirst { !it.completed }
                if (idx >= 0) return NextSetHint(setNumber = idx + 1, exerciseName = candidate.name)
            }
            for (i in 0 until prefIndex) {
                val candidate = workout.exercises[i]
                val idx = candidate.sets.indexOfFirst { !it.completed }
                if (idx >= 0) return NextSetHint(setNumber = idx + 1, exerciseName = candidate.name)
            }
            return null
        }

        for (ex in workout.exercises) {
            val idx = ex.sets.indexOfFirst { !it.completed }
            if (idx >= 0) return NextSetHint(setNumber = idx + 1, exerciseName = ex.name)
        }
        return null
    }

    /**
     * Formats the heads-up notification text when rest ends:
     * "Rest done · next: Set N, Exercise"
     */
    fun formatRestDoneNotificationText(
        nextSet: NextSetHint?,
        fallbackExerciseName: String = ""
    ): String {
        return when {
            nextSet != null -> "Rest done · next: Set ${nextSet.setNumber}, ${nextSet.exerciseName}"
            fallbackExerciseName.isNotBlank() -> "Rest done · $fallbackExerciseName"
            else -> "Rest done · Ready for next set"
        }
    }

    /**
     * Pure lifecycle decision for [com.example.service.WorkoutForegroundService].
     *
     * - While an active workout is present, never stop.
     * - Once a non-null workout has been observed, stop immediately when it clears to null.
     * - Before any workout has been observed (e.g. cold startup while DataStore is still
     *   committing), allow a grace window of [startupGraceMs] before stopping.
     */
    fun shouldStopWorkoutService(
        sawNonNullWorkout: Boolean,
        hasActiveWorkout: Boolean,
        elapsedSinceStartMs: Long,
        startupGraceMs: Long = SERVICE_STARTUP_GRACE_MS
    ): Boolean {
        if (hasActiveWorkout) return false
        if (sawNonNullWorkout) return true
        return elapsedSinceStartMs >= startupGraceMs
    }
}
