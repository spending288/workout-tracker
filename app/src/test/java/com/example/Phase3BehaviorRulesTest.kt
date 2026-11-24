package com.example

import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.EffortScale
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.SetTag
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet
import com.example.domain.ActiveWorkoutMutations
import com.example.domain.RepDayRules
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3BehaviorRulesTest {

    private val moshi: Moshi = Moshi.Builder().build()

    // =========================================================================
    // Item 7: Weight units — convert correctly
    // =========================================================================

    @Test
    fun convertWeight_usesStandardFactorAndIdentityForSameUnit() {
        assertEquals(220.462262, RepDayRules.convertWeight(100.0, WeightUnit.KG, WeightUnit.LB), 0.0001)
        assertEquals(102.05828, RepDayRules.convertWeight(225.0, WeightUnit.LB, WeightUnit.KG), 0.001)
        assertEquals(135.0, RepDayRules.convertWeight(135.0, WeightUnit.LB, WeightUnit.LB), 0.0)
        assertEquals(80.0, RepDayRules.convertWeight(80.0, WeightUnit.KG, WeightUnit.KG), 0.0)
    }

    @Test
    fun formatConvertedWeight_roundsToTenthAndStripsTrailingZero() {
        assertEquals("220.5", RepDayRules.formatConvertedWeight(100.0, WeightUnit.KG, WeightUnit.LB))
        assertEquals("102.1", RepDayRules.formatConvertedWeight(225.0, WeightUnit.LB, WeightUnit.KG))
        assertEquals("100", RepDayRules.formatConvertedWeight(100.0, WeightUnit.LB, WeightUnit.LB))
        assertEquals("132.3", RepDayRules.formatConvertedWeight(60.0, WeightUnit.KG, WeightUnit.LB))
    }

    @Test
    fun convertWeightForInput_roundsToNearestHalf() {
        // 100 kg = 220.462 lb -> nearest 0.5 is 220.5
        assertEquals(220.5, RepDayRules.convertWeightForInput(100.0, WeightUnit.KG, WeightUnit.LB), 0.001)
        // 225 lb = 102.058 kg -> nearest 0.5 is 102.0
        assertEquals(102.0, RepDayRules.convertWeightForInput(225.0, WeightUnit.LB, WeightUnit.KG), 0.001)
        // 60 kg = 132.277 lb -> nearest 0.5 is 132.5
        assertEquals(132.5, RepDayRules.convertWeightForInput(60.0, WeightUnit.KG, WeightUnit.LB), 0.001)
        // Same unit is untouched
        assertEquals(137.5, RepDayRules.convertWeightForInput(137.5, WeightUnit.LB, WeightUnit.LB), 0.001)
    }

    @Test
    fun computeTotalHistoryVolume_convertsMixedUnitWorkoutsWithoutRewritingOriginalRecords() {
        val lbWorkout = CompletedWorkout(
            id = "cw_lb",
            routineId = null,
            routineName = "LB Session",
            startedAt = "2026-01-01T10:00:00Z",
            completedAt = "2026-01-01T10:45:00Z",
            durationSeconds = 2700,
            totalVolume = 1000,
            unit = WeightUnit.LB,
            exercises = listOf(CompletedExercise(name = "Bench", sets = listOf(CompletedSet(100.0, 10))))
        )
        val kgWorkout = CompletedWorkout(
            id = "cw_kg",
            routineId = null,
            routineName = "KG Session",
            startedAt = "2026-01-02T10:00:00Z",
            completedAt = "2026-01-02T10:45:00Z",
            durationSeconds = 2700,
            totalVolume = 1000,
            unit = WeightUnit.KG,
            exercises = listOf(CompletedExercise(name = "Bench", sets = listOf(CompletedSet(100.0, 10))))
        )

        val totalInLb = RepDayRules.computeTotalHistoryVolume(listOf(lbWorkout, kgWorkout), WeightUnit.LB)
        // 1000 lb + round(1000 * 2.20462262) = 1000 + 2205 = 3205
        assertEquals(3205, totalInLb)

        val totalInKg = RepDayRules.computeTotalHistoryVolume(listOf(lbWorkout, kgWorkout), WeightUnit.KG)
        // round(1000 / 2.20462262) + 1000 = 454 + 1000 = 1454
        assertEquals(1454, totalInKg)

        // Original logged units and weights remain completely unchanged
        assertEquals(WeightUnit.LB, lbWorkout.unit)
        assertEquals(WeightUnit.KG, kgWorkout.unit)
        assertEquals(100.0, kgWorkout.exercises.first().sets.first().weight, 0.0)
    }

    @Test
    fun activeWorkoutUnitConversion_convertsEnteredWeightsToNearestHalfAndPreservesBlankSets() {
        val workout = ActiveWorkout(
            id = "aw_1",
            routineId = "rt_1",
            routineName = "Push",
            startedAt = 1_700_000_000_000L,
            unit = WeightUnit.LB,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Bench Press",
                    targetSets = 3,
                    repRange = "8",
                    sets = listOf(
                        WorkoutSet(id = "s1", weight = "225", reps = "5", completed = true),
                        WorkoutSet(id = "s2", weight = "135", reps = "8", completed = false),
                        WorkoutSet(id = "s3", weight = "", reps = "", completed = false)
                    )
                )
            )
        )

        val converted = ActiveWorkoutMutations.convertWorkoutUnit(
            workout = workout,
            targetUnit = WeightUnit.KG,
            currentFallbackUnit = WeightUnit.LB
        )

        assertEquals(WeightUnit.KG, converted.unit)
        assertEquals("102", converted.exercises[0].sets[0].weight) // 225 / 2.20462262 = 102.058 -> 102.0 -> "102"
        assertEquals("61", converted.exercises[0].sets[1].weight)  // 135 / 2.20462262 = 61.235 -> 61.0 -> "61"
        assertEquals("", converted.exercises[0].sets[2].weight)    // blank stays blank
    }

    @Test
    fun toggleSetCompleted_autoFillsBlankWeightConvertedFromPreviousSessionUnit() {
        val workout = ActiveWorkout(
            id = "aw_kg",
            routineId = "rt_1",
            routineName = "Push",
            startedAt = 1_700_000_000_000L,
            unit = WeightUnit.KG,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Bench Press",
                    targetSets = 1,
                    repRange = "5",
                    restSeconds = 120,
                    sets = listOf(
                        WorkoutSet(id = "s1", weight = "", reps = "", completed = false, targetRpe = "8")
                    )
                )
            )
        )

        // Previous session was logged in LB (225 lb x 5)
        val outcome = ActiveWorkoutMutations.toggleSetCompleted(
            workout = workout,
            exerciseId = "ex_1",
            setId = "s1",
            prevSet = CompletedSet(weight = 225.0, reps = 5),
            defaultRestSeconds = 90,
            nowMs = 1_700_000_060_000L,
            prevSetUnit = WeightUnit.LB,
            sessionUnit = WeightUnit.KG
        )!!

        val updatedSet = outcome.workout.exercises[0].sets[0]
        assertTrue(updatedSet.completed)
        assertEquals("102", updatedSet.weight) // 225 lb -> 102.0 kg (nearest 0.5)
        assertEquals("5", updatedSet.reps)
        assertEquals(1, outcome.setNumber)
        assertEquals("8", outcome.targetRpe)
    }

    @Test
    fun legacyActiveWorkoutJsonWithoutUnitField_deserializesSafely() {
        val legacyJson = """
            {"id":"aw_legacy","routineId":null,"routineName":"Live","startedAt":1700000000000,"exercises":[],"notes":""}
        """.trimIndent()
        val parsed = moshi.adapter(ActiveWorkout::class.java).fromJson(legacyJson)
        assertNotNull(parsed)
        assertNull(parsed!!.unit)
        assertEquals(WeightUnit.KG, parsed.resolvedUnit(WeightUnit.KG))
        assertEquals(WeightUnit.LB, parsed.resolvedUnit(WeightUnit.LB))
    }

    // =========================================================================
    // Item 8: Swapping an exercise mid-workout
    // =========================================================================

    @Test
    fun swapExercise_withZeroCompletedSets_renamesInPlaceAndClearsUncompleted() {
        val workout = ActiveWorkout(
            id = "aw_1",
            routineId = "rt_1",
            routineName = "Push",
            startedAt = 1_700_000_000_000L,
            unit = WeightUnit.LB,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Barbell Bench Press",
                    targetSets = 3,
                    repRange = "8-10",
                    restSeconds = 120,
                    sets = listOf(
                        WorkoutSet("s1", weight = "185", reps = "8", completed = false, tag = SetTag.WARMUP),
                        WorkoutSet("s2", weight = "", reps = "", completed = false, tag = SetTag.WORKING)
                    )
                )
            )
        )

        val swapped = ActiveWorkoutMutations.swapExercise(workout, "ex_1", "Dumbbell Bench Press")
        assertEquals(1, swapped.exercises.size)
        assertEquals("Dumbbell Bench Press", swapped.exercises[0].name)
        assertEquals("", swapped.exercises[0].sets[0].weight)
        assertEquals(SetTag.WARMUP, swapped.exercises[0].sets[0].tag)
    }

    @Test
    fun swapExercise_renameAllWithCompletedSets_keepsCompletedValuesAndClearsUncompleted() {
        val workout = ActiveWorkout(
            id = "aw_rename_all",
            routineId = "rt_1",
            routineName = "Push",
            startedAt = 1_700_000_000_000L,
            unit = WeightUnit.LB,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Barbell Bench Press",
                    targetSets = 3,
                    repRange = "8-10",
                    restSeconds = 120,
                    sets = listOf(
                        WorkoutSet("s1", weight = "185", reps = "8", completed = true, tag = SetTag.WORKING, rpe = 8.0),
                        WorkoutSet("s2", weight = "185", reps = "8", completed = false, tag = SetTag.WORKING)
                    )
                )
            )
        )

        val renamed = ActiveWorkoutMutations.swapExercise(workout, "ex_1", "Incline Barbell Press")
        assertEquals(1, renamed.exercises.size)
        val ex = renamed.exercises.single()
        assertEquals("Incline Barbell Press", ex.name)
        assertEquals(2, ex.sets.size)
        assertTrue(ex.sets[0].completed)
        assertEquals("185", ex.sets[0].weight)
        assertEquals("8", ex.sets[0].reps)
        assertFalse(ex.sets[1].completed)
        assertEquals("", ex.sets[1].weight)
        assertEquals("", ex.sets[1].reps)
    }

    @Test
    fun swapExerciseKeepCompleted_splitsCompletedFromRemainingAndInsertsReplacementImmediatelyAfter() {
        val workout = ActiveWorkout(
            id = "aw_split",
            routineId = "rt_1",
            routineName = "Upper",
            startedAt = 1_700_000_000_000L,
            unit = WeightUnit.LB,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Barbell Bench Press",
                    targetSets = 4,
                    repRange = "6-8",
                    targetRpe = "8",
                    restSeconds = 150,
                    sets = listOf(
                        WorkoutSet("s1", weight = "135", reps = "8", completed = true, tag = SetTag.WARMUP, rpe = 6.5, restSeconds = 90),
                        WorkoutSet("s2", weight = "205", reps = "6", completed = true, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.TOP, rpe = 8.5, restSeconds = 150),
                        WorkoutSet("s3", weight = "205", reps = "6", completed = false, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.BACKOFF, targetRepRange = "6-8", targetRpe = "8", restSeconds = 150),
                        WorkoutSet("s4", weight = "", reps = "", completed = false, tag = SetTag.DROP, targetRepRange = "10-12", targetRpe = "9", restSeconds = 120)
                    )
                ),
                ActiveExercise(
                    id = "ex_2",
                    name = "Pull-Up",
                    targetSets = 3,
                    repRange = "8-10",
                    sets = listOf(WorkoutSet("p1", completed = false))
                )
            )
        )

        val split = ActiveWorkoutMutations.swapExerciseKeepCompleted(workout, "ex_1", "Incline Dumbbell Press", 90)

        assertEquals(3, split.exercises.size)

        // 1. Original exercise keeps its 2 logged completed sets untouched
        val kept = split.exercises[0]
        assertEquals("ex_1", kept.id)
        assertEquals("Barbell Bench Press", kept.name)
        assertEquals(2, kept.sets.size)
        assertEquals(2, kept.targetSets)
        assertTrue(kept.sets.all { it.completed })
        assertEquals("205", kept.sets[1].weight)
        assertEquals(8.5, kept.sets[1].rpe!!, 0.001)

        // 2. New exercise inserted immediately after with the 2 remaining uncompleted sets (cleared weight/reps, preserved tags/targets/rest)
        val replacement = split.exercises[1]
        assertEquals("Incline Dumbbell Press", replacement.name)
        assertEquals(2, replacement.sets.size)
        assertEquals(2, replacement.targetSets)
        assertEquals(150, replacement.restSeconds)
        assertEquals("", replacement.sets[0].weight)
        assertEquals("", replacement.sets[0].reps)
        assertFalse(replacement.sets[0].completed)
        assertEquals(SetTag.WORKING, replacement.sets[0].tag)
        assertEquals(WorkingSubTag.BACKOFF, replacement.sets[0].workingSubTag)
        assertEquals("6-8", replacement.sets[0].targetRepRange)
        assertEquals("8", replacement.sets[0].targetRpe)
        assertEquals(150, replacement.sets[0].restSeconds)
        assertEquals(SetTag.DROP, replacement.sets[1].tag)
        assertEquals("10-12", replacement.sets[1].targetRepRange)
        assertEquals("9", replacement.sets[1].targetRpe)
        assertEquals(120, replacement.sets[1].restSeconds)

        // 3. Following exercise remains after the inserted replacement
        assertEquals("Pull-Up", split.exercises[2].name)
    }

    @Test
    fun swapExerciseKeepCompleted_whenAllSetsCompleted_createsOneFreshSetForReplacement() {
        val workout = ActiveWorkout(
            id = "aw_all_done",
            routineId = "rt_1",
            routineName = "Legs",
            startedAt = 1_700_000_000_000L,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Back Squat",
                    targetSets = 2,
                    repRange = "5",
                    targetRpe = "8",
                    restSeconds = 180,
                    sets = listOf(
                        WorkoutSet("s1", weight = "275", reps = "5", completed = true),
                        WorkoutSet("s2", weight = "275", reps = "5", completed = true)
                    )
                )
            )
        )

        val split = ActiveWorkoutMutations.swapExerciseKeepCompleted(workout, "ex_1", "Hack Squat", 90)
        assertEquals(2, split.exercises.size)
        assertEquals(2, split.exercises[0].sets.size)
        assertEquals("Hack Squat", split.exercises[1].name)
        assertEquals(1, split.exercises[1].sets.size)
        assertFalse(split.exercises[1].sets[0].completed)
        assertEquals("5", split.exercises[1].sets[0].targetRepRange)
        assertEquals("8", split.exercises[1].sets[0].targetRpe)
        assertEquals(180, split.exercises[1].sets[0].restSeconds)
    }

    // =========================================================================
    // Item 9: Faster effort logging with RPE or RIR choice
    // =========================================================================

    @Test
    fun rirAndRpeConversions_handleExactHalfStepAndInvertedRanges() {
        // RIR choices -> stored RPE
        assertEquals(6.0, RepDayRules.rirToRpe(4), 0.001)
        assertEquals(7.0, RepDayRules.rirToRpe(3), 0.001)
        assertEquals(8.0, RepDayRules.rirToRpe(2), 0.001)
        assertEquals(9.0, RepDayRules.rirToRpe(1), 0.001)
        assertEquals(10.0, RepDayRules.rirToRpe(0), 0.001)

        // Whole RPE -> RIR label
        assertEquals("4+ RIR", RepDayRules.rpeToRirLabel(6.0))
        assertEquals("3 RIR", RepDayRules.rpeToRirLabel(7.0))
        assertEquals("2 RIR", RepDayRules.rpeToRirLabel(8.0))
        assertEquals("1 RIR", RepDayRules.rpeToRirLabel(9.0))
        assertEquals("0 RIR", RepDayRules.rpeToRirLabel(10.0))

        // Half-step RPE -> RIR range label ("1–2 RIR", etc.)
        assertEquals("1–2 RIR", RepDayRules.rpeToRirLabel(8.5))
        assertEquals("2–3 RIR", RepDayRules.rpeToRirLabel(7.5))
        assertEquals("0–1 RIR", RepDayRules.rpeToRirLabel(9.5))
        assertEquals("3–4 RIR", RepDayRules.rpeToRirLabel(6.5))

        // Range inversion between RPE and RIR
        assertEquals("1–3 RIR", RepDayRules.formatEffortTarget("7-9", EffortScale.RIR))
        assertEquals("1–2 RIR", RepDayRules.formatEffortTarget("8-9", EffortScale.RIR))
        assertEquals("0–1 RIR", RepDayRules.formatEffortTarget("9-10", EffortScale.RIR))
        assertEquals("2 RIR", RepDayRules.formatEffortTarget("8", EffortScale.RIR))
        assertEquals("1–2 RIR", RepDayRules.formatEffortTarget("8.5", EffortScale.RIR))

        // RIR target -> stored RPE range string
        assertEquals("7-9", RepDayRules.rirTargetToRpeString(1, 3))
        assertEquals("8-9", RepDayRules.rirTargetToRpeString(1, 2))
        assertEquals("8", RepDayRules.rirTargetToRpeString(2))
        assertEquals("10", RepDayRules.rirTargetToRpeString(0))
        assertEquals("6", RepDayRules.rirTargetToRpeString(4))

        // Compact & full value formatting
        assertEquals("RPE 8.5", RepDayRules.formatEffortValue(8.5, EffortScale.RPE))
        assertEquals("8.5", RepDayRules.formatEffortValue(8.5, EffortScale.RPE, compact = true))
        assertEquals("1–2 RIR", RepDayRules.formatEffortValue(8.5, EffortScale.RIR))
        assertEquals("1–2", RepDayRules.formatEffortValue(8.5, EffortScale.RIR, compact = true))
        assertEquals("4+", RepDayRules.formatEffortValue(6.0, EffortScale.RIR, compact = true))
    }

    @Test
    fun matchesEffortTargetChip_highlightsExactAndRangeTargets() {
        assertTrue(RepDayRules.matchesEffortTargetChip(8.0, "8"))
        assertFalse(RepDayRules.matchesEffortTargetChip(8.5, "8"))
        assertTrue(RepDayRules.matchesEffortTargetChip(7.0, "7-9"))
        assertTrue(RepDayRules.matchesEffortTargetChip(8.0, "7-9"))
        assertTrue(RepDayRules.matchesEffortTargetChip(9.0, "7-9"))
        assertFalse(RepDayRules.matchesEffortTargetChip(9.5, "7-9"))
        assertFalse(RepDayRules.matchesEffortTargetChip(8.0, null))
    }

    @Test
    fun formatSetTargetLabelAndSummary_respectEffortScale() {
        assertEquals("8-10 @RPE 8", RepDayRules.formatSetTargetLabel("8-10", "8", EffortScale.RPE))
        assertEquals("8-10 · 2 RIR", RepDayRules.formatSetTargetLabel("8-10", "8", EffortScale.RIR))
        assertEquals("3 × 8-10 · 1–2 RIR", RepDayRules.formatExerciseTargetSummary(3, "8-10", "8-9", EffortScale.RIR))
        assertEquals("3 × 8-10 @8-9", RepDayRules.formatExerciseTargetSummary(3, "8-10", "8-9", EffortScale.RPE))
    }

    @Test
    fun updateSetRpe_updatesAndClearsEffortImmediately() {
        val workout = ActiveWorkout(
            id = "aw_rpe",
            routineId = null,
            routineName = "Live",
            startedAt = 1_700_000_000_000L,
            exercises = listOf(
                ActiveExercise(
                    id = "ex_1",
                    name = "Squat",
                    targetSets = 1,
                    repRange = "5",
                    sets = listOf(WorkoutSet("s1", weight = "225", reps = "5", completed = true, rpe = null))
                )
            )
        )

        val logged = ActiveWorkoutMutations.updateSetRpe(workout, "ex_1", "s1", 8.0)
        assertEquals(8.0, logged.exercises[0].sets[0].rpe!!, 0.001)

        val cleared = ActiveWorkoutMutations.updateSetRpe(logged, "ex_1", "s1", null)
        assertNull(cleared.exercises[0].sets[0].rpe)
    }

    @Test
    fun legacyAppSettingsJsonWithoutEffortFields_loadsWithDefaults() {
        val legacyJson = """{"unit":"KG","defaultRestSeconds":120,"soundEnabled":false}"""
        val parsed = moshi.adapter(AppSettings::class.java).fromJson(legacyJson)
        assertNotNull(parsed)
        assertEquals(WeightUnit.KG, parsed!!.unit)
        assertEquals(EffortScale.RPE, parsed.effortScale)
        assertTrue(parsed.askEffortAfterSet)
    }

    // =========================================================================
    // Item 10: Pausing a plan actually pauses it
    // =========================================================================

    @Test
    fun pausingAndResumingPlan_freezesProgressAndShiftsFutureWeeksByPausedDuration() {
        val dayMs = 24L * 60L * 60L * 1000L
        val weekMs = RepDayRules.SEVEN_DAYS_MS
        val startMs = 1_700_000_000_000L // Day 0 -> Week 1

        val routine = Routine(
            id = "rt_a",
            name = "Push Day",
            exercises = listOf(RoutineExercise("re_1", "Bench", 3, "8-10")),
            createdAt = RepDayRules.formatIso(startMs),
            updatedAt = RepDayRules.formatIso(startMs)
        )
        val initialPlan = TrainingPlan(
            id = "pl_1",
            name = "8-Week Block",
            useBlocks = false,
            blocks = listOf(
                PlanBlock(
                    id = "blk_1",
                    name = "Main",
                    weeks = 8,
                    workouts = listOf(PlanWorkoutSlot("sl_1", routine.id, routine.name, timesPerWeek = 3))
                )
            ),
            isActive = true,
            startedAt = RepDayRules.formatIso(startMs),
            createdAt = RepDayRules.formatIso(startMs),
            updatedAt = RepDayRules.formatIso(startMs)
        )

        // Advance 10 days (Week 2) and pause
        val pauseTimeMs = startMs + 10L * dayMs
        val pausedPlan = RepDayRules.pausePlan(initialPlan, pauseTimeMs)
        assertFalse(pausedPlan.isActive)
        assertEquals(RepDayRules.formatIso(pauseTimeMs), pausedPlan.pausedAt)
        assertEquals(0L, pausedPlan.pausedDurationMs)

        // 3 weeks later, while still paused, progress must remain frozen at Week 2 and report isPaused = true
        val threeWeeksLaterMs = pauseTimeMs + 3L * weekMs
        val frozenProgress = RepDayRules.computePlanProgress(
            plan = pausedPlan,
            history = emptyList(),
            routines = listOf(routine),
            nowMs = threeWeeksLaterMs
        )
        assertTrue(frozenProgress.isPaused)
        assertEquals(2, frozenProgress.currentWeek)
        assertEquals(8, frozenProgress.totalWeeks)

        // Resume at 3 weeks later -> pausedDurationMs accumulates 3 weeks, pausedAt clears, isActive = true
        val resumedPlan = RepDayRules.resumeOrActivatePlan(pausedPlan, threeWeeksLaterMs)
        assertTrue(resumedPlan.isActive)
        assertNull(resumedPlan.pausedAt)
        assertEquals(3L * weekMs, resumedPlan.pausedDurationMs)

        // Immediately after resuming, we are STILL in Week 2 (10 days active elapsed), NOT Week 5!
        val resumedProgress = RepDayRules.computePlanProgress(
            plan = resumedPlan,
            history = emptyList(),
            routines = listOf(routine),
            nowMs = threeWeeksLaterMs
        )
        assertFalse(resumedProgress.isPaused)
        assertEquals(2, resumedProgress.currentWeek)

        // A workout logged 1 day after resuming counts toward Week 2's shifted window
        val postResumeWorkoutMs = threeWeeksLaterMs + dayMs
        val postResumeWorkout = CompletedWorkout(
            id = "cw_post",
            routineId = routine.id,
            routineName = routine.name,
            startedAt = RepDayRules.formatIso(postResumeWorkoutMs - 3600_000L),
            completedAt = RepDayRules.formatIso(postResumeWorkoutMs),
            completedAtMs = postResumeWorkoutMs,
            durationSeconds = 3600,
            totalVolume = 5000,
            unit = WeightUnit.LB,
            exercises = emptyList()
        )
        val afterSessionProgress = RepDayRules.computePlanProgress(
            plan = resumedPlan,
            history = listOf(postResumeWorkout),
            routines = listOf(routine),
            nowMs = postResumeWorkoutMs + 60_000L
        )
        assertEquals(2, afterSessionProgress.currentWeek)
        assertEquals(1, afterSessionProgress.weekSessionsDone)
        assertEquals(1, afterSessionProgress.slotWeeklyDone["sl_1"])

        // 5 more active days pass (total 15 active days = Week 3)
        val week3Progress = RepDayRules.computePlanProgress(
            plan = resumedPlan,
            history = listOf(postResumeWorkout),
            routines = listOf(routine),
            nowMs = threeWeeksLaterMs + 5L * dayMs
        )
        assertEquals(3, week3Progress.currentWeek)
    }

    @Test
    fun restartPlan_resetsStartedAtPausedAtAndPausedDuration() {
        val startMs = 1_700_000_000_000L
        val restartMs = startMs + 28L * 24L * 60L * 60L * 1000L
        val pausedPlan = TrainingPlan(
            id = "pl_restart",
            name = "Strength",
            useBlocks = false,
            blocks = listOf(PlanBlock("b1", "Main", weeks = 6, workouts = emptyList())),
            isActive = false,
            startedAt = RepDayRules.formatIso(startMs),
            pausedAt = RepDayRules.formatIso(startMs + 14L * 24L * 60L * 60L * 1000L),
            pausedDurationMs = 7L * 24L * 60L * 60L * 1000L,
            createdAt = RepDayRules.formatIso(startMs),
            updatedAt = RepDayRules.formatIso(startMs)
        )

        val restarted = RepDayRules.restartPlan(pausedPlan, restartMs)
        assertTrue(restarted.isActive)
        assertEquals(RepDayRules.formatIso(restartMs), restarted.startedAt)
        assertNull(restarted.pausedAt)
        assertEquals(0L, restarted.pausedDurationMs)

        val progress = RepDayRules.computePlanProgress(restarted, emptyList(), emptyList(), nowMs = restartMs)
        assertEquals(1, progress.currentWeek)
        assertFalse(progress.isPaused)
    }

    @Test
    fun legacyTrainingPlanJsonWithoutPauseFields_deserializesSafely() {
        val legacyJson = """
            {"id":"pl_legacy","name":"PPL","useBlocks":false,"blocks":[],"isActive":true,"startedAt":"2026-01-01T00:00:00Z","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}
        """.trimIndent()
        val parsed = moshi.adapter(TrainingPlan::class.java).fromJson(legacyJson)
        assertNotNull(parsed)
        assertNull(parsed!!.pausedAt)
        assertEquals(0L, parsed.pausedDurationMs)
    }
}
