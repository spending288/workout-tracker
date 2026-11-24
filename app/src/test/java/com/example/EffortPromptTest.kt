package com.example

import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.WorkoutSet
import com.example.domain.ActiveWorkoutMutations
import com.example.domain.RepDayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "How hard?" bar after completing a set should only appear when no effort was chosen yet. */
class EffortPromptTest {

    private fun workout(): ActiveWorkout = ActiveWorkout(
        id = "aw_effort",
        routineId = null,
        routineName = "Push",
        startedAt = 1_700_000_000_000L,
        exercises = listOf(
            ActiveExercise(
                id = "ex_1",
                name = "Bench",
                targetSets = 2,
                repRange = "6-8",
                restSeconds = 90,
                sets = listOf(
                    WorkoutSet(id = "s1", weight = "185", reps = "8", restSeconds = 90),
                    WorkoutSet(id = "s2", weight = "185", reps = "8", restSeconds = 90)
                )
            )
        )
    )

    @Test
    fun shouldPromptEffortAfterSet_onlyWhenSettingOnAndNoValueYet() {
        assertTrue(RepDayRules.shouldPromptEffortAfterSet(askEffortAfterSet = true, existingRpe = null))
        assertFalse(RepDayRules.shouldPromptEffortAfterSet(askEffortAfterSet = true, existingRpe = 8.0))
        assertFalse(RepDayRules.shouldPromptEffortAfterSet(askEffortAfterSet = false, existingRpe = null))
        assertFalse(RepDayRules.shouldPromptEffortAfterSet(askEffortAfterSet = false, existingRpe = 8.0))
    }

    @Test
    fun completingSetWithEffortAlreadyChosen_reportsThatValue_soNoPrompt() {
        val withRpe = ActiveWorkoutMutations.updateSetRpe(workout(), "ex_1", "s1", 8.0)
        val outcome = ActiveWorkoutMutations.toggleSetCompleted(
            workout = withRpe,
            exerciseId = "ex_1",
            setId = "s1",
            prevSet = null,
            defaultRestSeconds = 90,
            nowMs = 1_700_000_060_000L
        )
        assertNotNull(outcome)
        assertTrue(outcome!!.nowCompleted)
        assertNotNull(outcome.startRestSeconds) // the rest timer still starts
        assertEquals(8.0, outcome.currentRpe!!, 0.001)
        assertFalse(RepDayRules.shouldPromptEffortAfterSet(true, outcome.currentRpe))
        // The chosen value is kept on the completed set
        assertEquals(8.0, outcome.workout.exercises[0].sets[0].rpe!!, 0.001)
    }

    @Test
    fun completingSetWithoutEffort_stillPrompts() {
        val outcome = ActiveWorkoutMutations.toggleSetCompleted(
            workout = workout(),
            exerciseId = "ex_1",
            setId = "s1",
            prevSet = null,
            defaultRestSeconds = 90,
            nowMs = 1_700_000_060_000L
        )
        assertNotNull(outcome)
        assertNull(outcome!!.currentRpe)
        assertTrue(RepDayRules.shouldPromptEffortAfterSet(true, outcome.currentRpe))
    }
}
