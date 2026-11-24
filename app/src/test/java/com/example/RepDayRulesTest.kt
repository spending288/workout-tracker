package com.example

import com.example.data.SeedData
import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlannedSet
import com.example.data.model.PlateCalculatorSettings
import com.example.data.model.RoutineExercise
import com.example.data.model.SetTag
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet
import com.example.domain.RepDayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class RepDayRulesTest {

    @Test
    fun dropSetZeroRestRule_enforcesZeroBeforeDropAndRestoresWhenRemoved() {
        val initial = listOf(
            PlannedSet("s1", SetTag.WORKING, null, "10", "8", 90),
            PlannedSet("s2", SetTag.WORKING, null, "10", "8.5", 90),
            PlannedSet("s3", SetTag.DROP, null, "10", "9.5", 90)
        )
        val enforced = RepDayRules.enforceDropSetRestRules(initial, defaultRest = 90)
        assertEquals(90, enforced[0].restSeconds)
        assertEquals(0, enforced[1].restSeconds)
        assertEquals(90, enforced[2].restSeconds)

        // Removing the drop set tag restores default rest on set 2
        val untagged = enforced.mapIndexed { idx, s -> if (idx == 2) s.copy(tag = SetTag.WORKING) else s }
        val restored = RepDayRules.enforceDropSetRestRules(untagged, defaultRest = 90, previousDropIndices = setOf(2))
        assertEquals(90, restored[1].restSeconds)
    }

    @Test
    fun normalizeRepAndRpeTargets_neverReversedAndDerivesSummary() {
        assertEquals("5-7", RepDayRules.normalizeRepRange(7, 5))
        assertEquals("8", RepDayRules.normalizeRepRange(8, 8))
        assertEquals("8-9", RepDayRules.normalizeRpeTarget(9.0, 8.0))
        assertEquals("8.5", RepDayRules.normalizeRpeTarget(8.4, 8.6))

        val sets = listOf(
            PlannedSet("s1", null, null, "8", "8", 90),
            PlannedSet("s2", null, null, "4-6", "9", 90),
            PlannedSet("s3", null, null, "8-10", "8.5", 90)
        )
        val derived = RepDayRules.withDerivedSummary(
            RoutineExercise("e1", "Bench", 3, "", null, 90, sets)
        )
        assertEquals("4-10", derived.repRange)
        assertEquals("8-9", derived.targetRpe)

        // RPE-only sets produce empty repRange and format as "3 × RPE 8"
        val rpeOnlySets = List(3) { i -> PlannedSet("r$i", null, null, null, "8", 90) }
        val derivedRpeOnly = RepDayRules.withDerivedSummary(
            RoutineExercise("e2", "Squat", 3, "", null, 90, rpeOnlySets)
        )
        assertEquals("", derivedRpeOnly.repRange)
        assertEquals("3 × RPE 8", RepDayRules.formatExerciseTargetSummary(3, derivedRpeOnly.repRange, derivedRpeOnly.targetRpe))
    }

    @Test
    fun greedyPlateCalculator_handlesLbAndKgWithExactHundredths() {
        val lbSettings = PlateCalculatorSettings()
        val res225 = RepDayRules.calculatePlates(225.0, WeightUnit.LB, lbSettings)
        assertTrue(res225.isExactMatch)
        assertEquals(225.0, res225.achievedTotal, 0.001)
        assertEquals(1, res225.platesPerSide.size)
        assertEquals(45.0, res225.platesPerSide[0].plate, 0.001)
        assertEquals(2, res225.platesPerSide[0].count)

        val kgSettings = PlateCalculatorSettings(includeBar = true, barWeightKg = 20.0, perSideMode = true)
        val resKg = RepDayRules.calculatePlates(62.5, WeightUnit.KG, kgSettings)
        assertTrue(resKg.isExactMatch)
        assertEquals(62.5, resKg.achievedTotal, 0.001)
        // Per side = 21.25 kg = 20 + 1.25
        assertEquals(2, resKg.platesPerSide.size)
        assertEquals(20.0, resKg.platesPerSide[0].plate, 0.001)
        assertEquals(1.25, resKg.platesPerSide[1].plate, 0.001)
    }

    @Test
    fun duplicateNaming_preventsCaseInsensitiveCollisions() {
        val existing = listOf("Push Day", "Push Day (Copy)", "push day (copy 2)")
        assertEquals("Push Day (Copy 3)", RepDayRules.nextDuplicateName("Push Day (Copy)", existing))
    }

    @Test
    fun customExerciseLibrary_deduplicatesAndPrioritizesCustomFirst() {
        val custom = listOf("JM Press", "barbell bench press", "  Meadows   Row  ")
        val extracted = RepDayRules.extractNewCustomExercises(custom, emptyList())
        assertEquals(listOf("JM Press", "Meadows Row"), extracted)

        val unified = RepDayRules.buildUnifiedLibrary(extracted)
        assertEquals("JM Press", unified[0])
        assertEquals("Meadows Row", unified[1])
        assertEquals(SeedData.COMMON_EXERCISES.size + 2, unified.size)
        assertFalse(RepDayRules.isKnownInLibrary("Belt Squat", extracted))
    }

    @Test
    fun liveWorkout_defaultNameAndRoutineConversions() {
        val cal = Calendar.getInstance(Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 24, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatted = RepDayRules.formatLiveWorkoutDefaultName(cal.timeInMillis)
        assertEquals("Tuesday, November 24, live workout", formatted)
        assertTrue(RepDayRules.isLiveWorkoutName(formatted))
        assertFalse(RepDayRules.isLiveWorkoutName("Upper Push"))

        val emptyLive = RepDayRules.buildEmptyLiveWorkout(cal.timeInMillis)
        assertNull(emptyLive.routineId)
        assertTrue(emptyLive.exercises.isEmpty())

        val activeWithCompleted = emptyLive.copy(
            exercises = listOf(
                ActiveExercise(
                    id = "ae1",
                    name = "Barbell Bench Press",
                    targetSets = 2,
                    repRange = "",
                    restSeconds = 120,
                    sets = listOf(
                        WorkoutSet("s1", "185", "8", completed = true, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.TOP, rpe = 8.5, restSeconds = 120),
                        WorkoutSet("s2", "165", "10", completed = false, restSeconds = 120)
                    )
                )
            )
        )
        val routineFromActive = RepDayRules.buildRoutineFromActiveWorkout(activeWithCompleted, "Upper Pump", 90)
        assertEquals("Upper Pump", routineFromActive.name)
        assertEquals(1, routineFromActive.exercises.size)
        assertEquals(1, routineFromActive.exercises[0].plannedSets?.size)
        assertEquals("8", routineFromActive.exercises[0].repRange)
        assertEquals("8.5", routineFromActive.exercises[0].targetRpe)

        val historyLive = CompletedWorkout(
            id = "cw1",
            routineId = null,
            routineName = formatted,
            startedAt = "2026-11-24T18:00:00Z",
            completedAt = "2026-11-24T18:45:00Z",
            durationSeconds = 2700,
            totalVolume = 1480,
            unit = WeightUnit.LB,
            exercises = listOf(
                CompletedExercise(
                    name = "Barbell Bench Press",
                    sets = listOf(CompletedSet(185.0, 8, SetTag.WORKING, WorkingSubTag.TOP, 8.5))
                )
            )
        )
        val draft = RepDayRules.buildRoutineDraftFromHistory(historyLive, defaultRest = 90, prefillName = true)
        assertEquals("", draft.name) // generic live workout name is cleared for fresh naming
        assertEquals(1, draft.exercises.size)
        assertEquals("Barbell Bench Press", draft.exercises[0].name)
        assertEquals("8", draft.exercises[0].repRange)
    }
}
