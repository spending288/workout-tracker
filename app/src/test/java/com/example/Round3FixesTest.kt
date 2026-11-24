package com.example

import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.domain.EditableCompletedExercise
import com.example.domain.EditableCompletedSet
import com.example.domain.HistoryWorkoutEditDraft
import com.example.domain.HistoryWorkoutEditRules
import com.example.domain.RepDayRules
import com.example.domain.RestTimerRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Round3FixesTest {

    // =========================================================================
    // Workout service must not stop itself on the early empty read at startup
    // =========================================================================

    @Test
    fun shouldStopWorkoutService_ignoresEarlyEmptyReadDuringStartupGrace() {
        // Service just started and DataStore hasn't committed the new workout yet.
        assertFalse(
            RestTimerRules.shouldStopWorkoutService(
                sawNonNullWorkout = false,
                hasActiveWorkout = false,
                elapsedSinceStartMs = 200L
            )
        )
    }

    @Test
    fun shouldStopWorkoutService_stopsWhenWorkoutWasSeenThenCleared() {
        assertTrue(
            RestTimerRules.shouldStopWorkoutService(
                sawNonNullWorkout = true,
                hasActiveWorkout = false,
                elapsedSinceStartMs = 200L
            )
        )
    }

    @Test
    fun shouldStopWorkoutService_stopsAfterGraceIfNoWorkoutEverAppears() {
        assertTrue(
            RestTimerRules.shouldStopWorkoutService(
                sawNonNullWorkout = false,
                hasActiveWorkout = false,
                elapsedSinceStartMs = RestTimerRules.SERVICE_STARTUP_GRACE_MS
            )
        )
    }

    @Test
    fun shouldStopWorkoutService_neverStopsWhileWorkoutIsActive() {
        assertFalse(
            RestTimerRules.shouldStopWorkoutService(
                sawNonNullWorkout = true,
                hasActiveWorkout = true,
                elapsedSinceStartMs = 60L * 60L * 1000L
            )
        )
        assertFalse(
            RestTimerRules.shouldStopWorkoutService(
                sawNonNullWorkout = false,
                hasActiveWorkout = true,
                elapsedSinceStartMs = 60L * 60L * 1000L
            )
        )
    }

    // =========================================================================
    // Mid-week pause: a workout logged before the pause still counts that week
    // =========================================================================

    @Test
    fun midWeekPause_keepsWorkoutLoggedBeforePauseInCurrentWeek() {
        val dayMs = 24L * 60L * 60L * 1000L
        val mondayMs = 1_700_000_000_000L
        val routine = Routine(
            id = "rt_push",
            name = "Push",
            exercises = listOf(RoutineExercise("re_1", "Bench", 3, "8-10")),
            createdAt = RepDayRules.formatIso(mondayMs),
            updatedAt = RepDayRules.formatIso(mondayMs)
        )
        val plan = TrainingPlan(
            id = "pl_mid",
            name = "4-Week",
            useBlocks = false,
            blocks = listOf(
                PlanBlock(
                    id = "blk_1",
                    name = "Main",
                    weeks = 4,
                    workouts = listOf(PlanWorkoutSlot("sl_push", routine.id, routine.name, timesPerWeek = 1))
                )
            ),
            isActive = true,
            startedAt = RepDayRules.formatIso(mondayMs),
            createdAt = RepDayRules.formatIso(mondayMs),
            updatedAt = RepDayRules.formatIso(mondayMs)
        )

        // Push on Wednesday (day 2), pause Thursday (day 3), resume the next Monday (day 7).
        val wednesdayMs = mondayMs + 2L * dayMs
        val pushWorkout = CompletedWorkout(
            id = "cw_wed",
            routineId = routine.id,
            routineName = routine.name,
            startedAt = RepDayRules.formatIso(wednesdayMs - 3_600_000L),
            completedAt = RepDayRules.formatIso(wednesdayMs),
            completedAtMs = wednesdayMs,
            durationSeconds = 3600,
            totalVolume = 4000,
            unit = WeightUnit.LB,
            exercises = emptyList()
        )
        val paused = RepDayRules.pausePlan(plan, mondayMs + 3L * dayMs)
        val resumeMs = mondayMs + 7L * dayMs
        val resumed = RepDayRules.resumeOrActivatePlan(paused, resumeMs)

        val progress = RepDayRules.computePlanProgress(
            plan = resumed,
            history = listOf(pushWorkout),
            routines = listOf(routine),
            nowMs = resumeMs + 60_000L
        )

        // Only 3 active days have passed, so it's still week 1, and Wednesday's Push counts: 1/1.
        assertEquals(1, progress.currentWeek)
        assertEquals(1, progress.weekSessionsDone)
        assertEquals(1, progress.weekSessionsTarget)
        assertEquals(1, progress.slotWeeklyDone["sl_push"])
    }

    // =========================================================================
    // Finishing a workout twice must not create two history entries
    // =========================================================================

    @Test
    fun completedWorkoutIdForActive_isStableForTheSameActiveWorkout() {
        val first = RepDayRules.completedWorkoutIdForActive("aw_123")
        val second = RepDayRules.completedWorkoutIdForActive("aw_123")
        assertEquals("cw-aw_123", first)
        assertEquals(first, second)
        assertNotEquals(first, RepDayRules.completedWorkoutIdForActive("aw_456"))
    }

    @Test
    fun completedWorkoutIdForActive_fallsBackToFreshIdWhenActiveIdIsBlank() {
        val id = RepDayRules.completedWorkoutIdForActive("   ")
        assertTrue(id.startsWith("cw"))
        assertFalse(id == "cw-")
    }

    // =========================================================================
    // History editor explains why Save is disabled
    // =========================================================================

    private fun baseDraft(exercises: List<EditableCompletedExercise>, durationMinutes: Int = 45): HistoryWorkoutEditDraft {
        val original = CompletedWorkout(
            id = "cw_edit",
            routineName = "Legs",
            startedAt = RepDayRules.formatIso(1_700_000_000_000L),
            completedAt = RepDayRules.formatIso(1_700_002_700_000L),
            completedAtMs = 1_700_002_700_000L,
            durationSeconds = 2700,
            totalVolume = 1000,
            unit = WeightUnit.LB,
            exercises = listOf(CompletedExercise("Squat", sets = listOf(CompletedSet(225.0, 5))))
        )
        return HistoryWorkoutEditDraft(
            original = original,
            routineName = "Legs",
            notes = "",
            startedAtMs = 1_700_000_000_000L,
            durationMinutes = durationMinutes,
            exercises = exercises
        )
    }

    @Test
    fun validationError_isNullForAValidDraft() {
        val draft = baseDraft(
            listOf(EditableCompletedExercise("e1", "Squat", sets = listOf(EditableCompletedSet("s1", "225", "5"))))
        )
        assertNull(HistoryWorkoutEditRules.validationError(draft))
        assertTrue(HistoryWorkoutEditRules.canSave(draft))
    }

    @Test
    fun validationError_namesTheProblemForEachInvalidCase() {
        val validSet = EditableCompletedSet("s1", "225", "5")

        val zeroDuration = baseDraft(
            listOf(EditableCompletedExercise("e1", "Squat", sets = listOf(validSet))),
            durationMinutes = 0
        )
        assertEquals("Duration must be at least 1 minute.", HistoryWorkoutEditRules.validationError(zeroDuration))

        val blankName = baseDraft(listOf(EditableCompletedExercise("e1", "  ", sets = listOf(validSet))))
        assertEquals("Every exercise needs a name before saving.", HistoryWorkoutEditRules.validationError(blankName))

        val noSets = baseDraft(listOf(EditableCompletedExercise("e1", "Squat", sets = emptyList())))
        assertEquals("Add at least one set or remove \"Squat\".", HistoryWorkoutEditRules.validationError(noSets))

        val zeroReps = baseDraft(
            listOf(EditableCompletedExercise("e1", "Squat", sets = listOf(EditableCompletedSet("s1", "225", "0"))))
        )
        assertEquals(
            "Enter weight (0 or more) and reps (1 or more) for every set.",
            HistoryWorkoutEditRules.validationError(zeroReps)
        )
        assertFalse(HistoryWorkoutEditRules.canSave(zeroReps))
    }
}
