package com.example

import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.WorkoutSet
import com.example.domain.NextSetHint
import com.example.domain.RestCompletionOutcome
import com.example.domain.RestTimerRules
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerRulesTest {

    @Test
    fun evaluateRestTimer_handlesInactiveRunningOnTimeAndStaleThreshold() {
        val now = 1_000_000L

        assertEquals(
            RestCompletionOutcome.INACTIVE,
            RestTimerRules.evaluateRestTimer(endTimeMs = null, completed = false, nowMs = now)
        )
        assertEquals(
            RestCompletionOutcome.INACTIVE,
            RestTimerRules.evaluateRestTimer(endTimeMs = 0L, completed = false, nowMs = now)
        )
        assertEquals(
            RestCompletionOutcome.STILL_RUNNING,
            RestTimerRules.evaluateRestTimer(endTimeMs = now + 15_000L, completed = false, nowMs = now)
        )
        // Exact 0:00 and within 5s grace window trigger alert
        assertEquals(
            RestCompletionOutcome.COMPLETE_WITH_ALERT,
            RestTimerRules.evaluateRestTimer(endTimeMs = now, completed = false, nowMs = now)
        )
        assertEquals(
            RestCompletionOutcome.COMPLETE_WITH_ALERT,
            RestTimerRules.evaluateRestTimer(endTimeMs = now - 5_000L, completed = false, nowMs = now)
        )
        // More than 5s overdue (e.g. process was killed) completes silently without chime
        assertEquals(
            RestCompletionOutcome.COMPLETE_SILENT_STALE,
            RestTimerRules.evaluateRestTimer(endTimeMs = now - 5_001L, completed = false, nowMs = now)
        )
        // Already completed never re-alerts
        assertEquals(
            RestCompletionOutcome.COMPLETE_SILENT_STALE,
            RestTimerRules.evaluateRestTimer(endTimeMs = null, completed = true, nowMs = now)
        )
    }

    @Test
    fun computeWakeLockTimeoutMs_addsTenSecondsAndCapsAtTenMinutesPlusBuffer() {
        val now = 2_000_000L

        assertEquals(
            100_000L,
            RestTimerRules.computeWakeLockTimeoutMs(endTimeMs = now + 90_000L, completed = false, nowMs = now)
        )
        // 10:00 rest -> 600s + 10s = 610s
        assertEquals(
            610_000L,
            RestTimerRules.computeWakeLockTimeoutMs(endTimeMs = now + 600_000L, completed = false, nowMs = now)
        )
        // Over 10:00 still capped at 610s
        assertEquals(
            610_000L,
            RestTimerRules.computeWakeLockTimeoutMs(endTimeMs = now + 900_000L, completed = false, nowMs = now)
        )
        // Inactive, expired, or already completed -> no wake lock held
        assertNull(RestTimerRules.computeWakeLockTimeoutMs(endTimeMs = null, completed = false, nowMs = now))
        assertNull(RestTimerRules.computeWakeLockTimeoutMs(endTimeMs = now, completed = false, nowMs = now))
        assertNull(RestTimerRules.computeWakeLockTimeoutMs(endTimeMs = now + 30_000L, completed = true, nowMs = now))
    }

    @Test
    fun adjustRestEndTime_extendsTrimsCapsAndStartsFromCompletedState() {
        val now = 5_000_000L

        // +30s while running extends end timestamp and total duration
        val extended = RestTimerRules.adjustRestEndTime(
            currentEndTimeMs = now + 45_000L,
            currentTotalDurationSec = 60,
            completed = false,
            deltaSeconds = 30,
            nowMs = now
        )
        assertEquals(now + 75_000L, extended.endTimeMs)
        assertEquals(75, extended.totalDurationSec)
        assertFalse(extended.completed)

        // +30s caps at 10:00 (600s) from now
        val capped = RestTimerRules.adjustRestEndTime(
            currentEndTimeMs = now + 590_000L,
            currentTotalDurationSec = 590,
            completed = false,
            deltaSeconds = 30,
            nowMs = now
        )
        assertEquals(now + 600_000L, capped.endTimeMs)
        assertEquals(600, capped.totalDurationSec)

        // -30s past zero clears timer
        val dismissed = RestTimerRules.adjustRestEndTime(
            currentEndTimeMs = now + 10_000L,
            currentTotalDurationSec = 60,
            completed = false,
            deltaSeconds = -30,
            nowMs = now
        )
        assertNull(dismissed.endTimeMs)
        assertFalse(dismissed.completed)

        // +30s when completed starts a fresh 30s countdown
        val restarted = RestTimerRules.adjustRestEndTime(
            currentEndTimeMs = null,
            currentTotalDurationSec = 90,
            completed = true,
            deltaSeconds = 30,
            nowMs = now
        )
        assertEquals(now + 30_000L, restarted.endTimeMs)
        assertEquals(30, restarted.totalDurationSec)
        assertFalse(restarted.completed)
    }

    @Test
    fun findNextIncompleteSetAndNotificationText_prioritizesCurrentExerciseThenAdvances() {
        val workout = ActiveWorkout(
            id = "aw_1",
            routineId = "rt_1",
            routineName = "Push Day",
            startedAt = 1_000L,
            exercises = listOf(
                ActiveExercise(
                    id = "ae_1",
                    name = "Bench Press",
                    targetSets = 3,
                    repRange = "6-8",
                    sets = listOf(
                        WorkoutSet("s1", "185", "8", completed = true),
                        WorkoutSet("s2", "185", "8", completed = false),
                        WorkoutSet("s3", "185", "8", completed = false)
                    )
                ),
                ActiveExercise(
                    id = "ae_2",
                    name = "Incline Dumbbell Press",
                    targetSets = 2,
                    repRange = "8-10",
                    sets = listOf(
                        WorkoutSet("s4", "65", "10", completed = false),
                        WorkoutSet("s5", "65", "10", completed = false)
                    )
                )
            )
        )

        val nextInBench = RestTimerRules.findNextIncompleteSet(workout, preferredExerciseName = "Bench Press")
        assertEquals(NextSetHint(setNumber = 2, exerciseName = "Bench Press"), nextInBench)
        assertEquals(
            "Rest done · next: Set 2, Bench Press",
            RestTimerRules.formatRestDoneNotificationText(nextInBench, "Bench Press")
        )

        // When Bench Press finishes all sets, advances to Set 1 of next exercise
        val benchDoneWorkout = workout.copy(
            exercises = workout.exercises.mapIndexed { idx, ex ->
                if (idx == 0) ex.copy(sets = ex.sets.map { it.copy(completed = true) }) else ex
            }
        )
        val nextExercise = RestTimerRules.findNextIncompleteSet(benchDoneWorkout, preferredExerciseName = "Bench Press")
        assertEquals(NextSetHint(setNumber = 1, exerciseName = "Incline Dumbbell Press"), nextExercise)
        assertEquals(
            "Rest done · next: Set 1, Incline Dumbbell Press",
            RestTimerRules.formatRestDoneNotificationText(nextExercise, "Bench Press")
        )

        // When all sets are complete, formats clean fallback
        val allDoneWorkout = benchDoneWorkout.copy(
            exercises = benchDoneWorkout.exercises.map { ex ->
                ex.copy(sets = ex.sets.map { it.copy(completed = true) })
            }
        )
        assertNull(RestTimerRules.findNextIncompleteSet(allDoneWorkout, preferredExerciseName = "Bench Press"))
        assertEquals(
            "Rest done · Bench Press",
            RestTimerRules.formatRestDoneNotificationText(null, "Bench Press")
        )
    }

    @Test
    fun appSettingsLegacyJson_deserializesWithNewPreferenceDefaults() {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(AppSettings::class.java)
        val legacyJson = """{"unit":"KG","defaultRestSeconds":120,"soundEnabled":false}"""

        val parsed = adapter.fromJson(legacyJson)
        assertNotNull(parsed)
        assertEquals(120, parsed!!.defaultRestSeconds)
        assertFalse(parsed.soundEnabled)
        // New Phase 1 preferences preserve backward-compatible defaults
        assertTrue(parsed.vibrateOnRestEnd)
        // Screen-on during workouts is now on by default when the saved settings don't say otherwise.
        assertTrue(parsed.keepScreenOnDuringWorkout)
        assertFalse(parsed.screenOnPromptShown)
    }
}
