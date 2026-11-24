package com.example

import com.example.data.model.ActiveWorkout
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.PlannedSet
import com.example.data.model.PlateCalculatorSettings
import com.example.data.model.SetTag
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet
import com.example.domain.ActiveWorkoutMutations
import com.example.domain.PlateCount
import com.example.domain.PreviousExerciseSession
import com.example.domain.RecordDecodeRules
import com.example.domain.RepDayRules
import com.example.domain.UnreadableRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class Phase4BugFixesTest {

    // ---- 12a: Drop-set rest rules tracked by set ID ----

    @Test
    fun dropSet_duplicatingSetBeforeDropRestoresOriginalAndZeroesClone() {
        val original = listOf(
            PlannedSet(id = "s1", tag = SetTag.WORKING, restSeconds = 90),
            PlannedSet(id = "s2", tag = SetTag.WORKING, restSeconds = 0),
            PlannedSet(id = "s3", tag = SetTag.DROP, restSeconds = 90)
        )
        // Duplicate set at index 1 (s2) right before DROP (s3)
        val clone = original[1].copy(id = "s2_clone", restSeconds = 90)
        val inserted = listOf(original[0], original[1], clone, original[2])

        val enforced = RepDayRules.enforceDropSetRestRules(
            sets = inserted,
            defaultRest = 90,
            previousSets = original
        )

        assertEquals(listOf(90, 90, 0, 90), enforced.map { it.restSeconds })
    }

    @Test
    fun dropSet_explicitZeroRestNotPrecedingDropIsPreserved() {
        val original = listOf(
            PlannedSet(id = "s1", tag = SetTag.WORKING, restSeconds = 0),
            PlannedSet(id = "s2", tag = SetTag.WORKING, restSeconds = 90),
            PlannedSet(id = "s3", tag = SetTag.WORKING, restSeconds = 90)
        )
        // Retagging s3 or adding a set should never clobber s1's intentional 0-second rest
        val updated = original + PlannedSet(id = "s4", tag = SetTag.WORKING, restSeconds = 90)
        val enforced = RepDayRules.enforceDropSetRestRules(updated, defaultRest = 90, previousSets = original)

        assertEquals(0, enforced[0].restSeconds)
        assertEquals(90, enforced[1].restSeconds)
    }

    @Test
    fun dropSet_removingOrRetaggingDropRestoresPrecedingSetById() {
        val original = listOf(
            PlannedSet(id = "s1", tag = SetTag.WORKING, restSeconds = 90),
            PlannedSet(id = "s2", tag = SetTag.WORKING, restSeconds = 0),
            PlannedSet(id = "s3", tag = SetTag.DROP, restSeconds = 90)
        )
        val afterRemove = listOf(original[0], original[1])
        val enforcedAfterRemove = RepDayRules.enforceDropSetRestRules(afterRemove, 120, previousSets = original)
        assertEquals(listOf(90, 120), enforcedAfterRemove.map { it.restSeconds })

        val afterRetag = listOf(original[0], original[1], original[2].copy(tag = SetTag.WORKING))
        val enforcedAfterRetag = RepDayRules.enforceDropSetRestRules(afterRetag, 120, previousSets = original)
        assertEquals(listOf(90, 120, 90), enforcedAfterRetag.map { it.restSeconds })
    }

    @Test
    fun dropSet_activeSetsRestRestoreTracksBySetId() {
        val prev = listOf(
            WorkoutSet(id = "w1", tag = SetTag.WORKING, restSeconds = 0),
            WorkoutSet(id = "w2", tag = SetTag.DROP, restSeconds = 90)
        )
        val inserted = listOf(
            prev[0],
            WorkoutSet(id = "w1_new", tag = SetTag.WORKING, restSeconds = 90),
            prev[1]
        )
        val enforced = RepDayRules.enforceDropSetRestOnActiveSets(inserted, defaultRest = 90, previousSets = prev)
        assertEquals(listOf(90, 0, 90), enforced.map { it.restSeconds })
    }

    @Test
    fun dropSet_retaggingToDropZeroesPrecedingSetAndReorderingUpdatesById() {
        val initial = listOf(
            PlannedSet(id = "s1", tag = SetTag.WORKING, restSeconds = 90),
            PlannedSet(id = "s2", tag = SetTag.WORKING, restSeconds = 120),
            PlannedSet(id = "s3", tag = SetTag.WORKING, restSeconds = 90)
        )
        // Retag s3 to DROP -> s2 becomes 0 rest
        val taggedDrop = listOf(initial[0], initial[1], initial[2].copy(tag = SetTag.DROP))
        val afterTagDrop = RepDayRules.enforceDropSetRestRules(taggedDrop, defaultRest = 90, previousSets = initial)
        assertEquals(listOf(90, 0, 90), afterTagDrop.map { it.restSeconds })

        // Reorder: move DROP (s3) between s1 and s2 -> [s1, s3(DROP), s2]
        // s1 now precedes DROP (becomes 0); s2 no longer precedes DROP (restored from 0 to defaultRest 90)
        val reordered = listOf(afterTagDrop[0], afterTagDrop[2], afterTagDrop[1])
        val afterReorder = RepDayRules.enforceDropSetRestRules(reordered, defaultRest = 90, previousSets = afterTagDrop)
        assertEquals(listOf(0, 90, 90), afterReorder.map { it.restSeconds })
    }

    // ---- 12b: Optimal plate calculator DP & closest-below / closest-above ----

    @Test
    fun plateCalc_findsExactCombinationThatGreedyMisses() {
        // 45 lb bar + {45, 35, 25} plates -> target 165 lb needs 60 lb/side = 35 + 25 (greedy picks 45 and falls short!)
        val settings = PlateCalculatorSettings(
            includeBar = true,
            barWeightLb = 45.0,
            availablePlatesLb = listOf(45.0, 35.0, 25.0),
            perSideMode = true
        )
        val result = RepDayRules.calculatePlates(165.0, WeightUnit.LB, settings)
        assertTrue(result.isExactMatch)
        assertFalse(result.isBelowBar)
        assertEquals(165.0, result.achievedTotal, 0.001)
        assertEquals(0.0, result.shortBy, 0.001)
        assertNull(result.overOption)
        assertEquals(listOf(PlateCount(35.0, 1), PlateCount(25.0, 1)), result.platesPerSide)
    }

    @Test
    fun plateCalc_prefersFewestPlatesAndHeavierTieBreak() {
        // 45 lb bar + {45, 25, 5} -> target 145 lb needs 50 lb/side:
        // Both (45 + 5) and (25 + 25) use 2 plates; heavier-plate tie-break prefers 45 + 5.
        val settings = PlateCalculatorSettings(
            includeBar = true,
            barWeightLb = 45.0,
            availablePlatesLb = listOf(45.0, 25.0, 5.0),
            perSideMode = true
        )
        val result = RepDayRules.calculatePlates(145.0, WeightUnit.LB, settings)
        assertTrue(result.isExactMatch)
        assertEquals(listOf(PlateCount(45.0, 1), PlateCount(5.0, 1)), result.platesPerSide)

        // 45 lb bar + {35, 25, 10} -> target 115 lb needs 35 lb/side: 1x35 beats 25+10 (fewer plates).
        val settings2 = PlateCalculatorSettings(
            includeBar = true,
            barWeightLb = 45.0,
            availablePlatesLb = listOf(35.0, 25.0, 10.0),
            perSideMode = true
        )
        val result2 = RepDayRules.calculatePlates(115.0, WeightUnit.LB, settings2)
        assertTrue(result2.isExactMatch)
        assertEquals(listOf(PlateCount(35.0, 1)), result2.platesPerSide)
    }

    @Test
    fun plateCalc_unachievableWeightReturnsClosestBelowAndClosestAbove() {
        // 45 lb bar + {45, 25, 10} -> target 150 lb needs 52.5 lb/side (impossible).
        // Closest below: 50 lb/side (2x25) -> 145 lb (short by 5).
        // Closest above: 55 lb/side (45 + 10) -> 155 lb (over by 5).
        val settings = PlateCalculatorSettings(
            includeBar = true,
            barWeightLb = 45.0,
            availablePlatesLb = listOf(45.0, 25.0, 10.0),
            perSideMode = true
        )
        val result = RepDayRules.calculatePlates(150.0, WeightUnit.LB, settings)
        assertFalse(result.isExactMatch)
        assertFalse(result.isBelowBar)
        assertEquals(145.0, result.achievedTotal, 0.001)
        assertEquals(5.0, result.shortBy, 0.001)
        assertEquals(listOf(PlateCount(25.0, 2)), result.platesPerSide)

        val over = result.overOption
        assertNotNull(over)
        assertEquals(155.0, over!!.totalWeight, 0.001)
        assertEquals(5.0, over.diffFromTarget, 0.001)
        assertEquals(listOf(PlateCount(45.0, 1), PlateCount(10.0, 1)), over.platesPerSide)
    }

    @Test
    fun plateCalc_handlesStandard225LbAnd62Point5KgExactLoads() {
        val defaults = PlateCalculatorSettings()

        val lb225 = RepDayRules.calculatePlates(225.0, WeightUnit.LB, defaults)
        assertTrue(lb225.isExactMatch)
        assertEquals(225.0, lb225.achievedTotal, 0.001)
        assertEquals(listOf(PlateCount(45.0, 2)), lb225.platesPerSide)
        assertNull(lb225.overOption)

        val kg625 = RepDayRules.calculatePlates(62.5, WeightUnit.KG, defaults)
        assertTrue(kg625.isExactMatch)
        assertEquals(62.5, kg625.achievedTotal, 0.001)
        assertEquals(listOf(PlateCount(20.0, 1), PlateCount(1.25, 1)), kg625.platesPerSide)
        assertNull(kg625.overOption)
    }

    // ---- 12c: Calendar-day relative dates ----

    @Test
    fun relativeDate_usesLocalCalendarDayNotElapsed24Hours() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        fun localMs(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int): Long {
            return Calendar.getInstance(tz).apply {
                clear()
                set(year, monthZeroBased, day, hour, minute, 0)
            }.timeInMillis
        }

        // 11:30 PM on Nov 23 vs 12:30 AM on Nov 24 -> only 1 hour apart, must be "Yesterday"!
        val lastNight = localMs(2026, Calendar.NOVEMBER, 23, 23, 30)
        val earlyMorning = localMs(2026, Calendar.NOVEMBER, 24, 0, 30)
        assertEquals(1, RepDayRules.calendarDaysBetween(lastNight, earlyMorning, tz))
        assertEquals("Yesterday", RepDayRules.formatRelativeDateMs(lastNight, earlyMorning, tz))

        // Same day 1:00 AM vs 11:45 PM -> "Today"
        val morning = localMs(2026, Calendar.NOVEMBER, 24, 1, 0)
        val lateNight = localMs(2026, Calendar.NOVEMBER, 24, 23, 45)
        assertEquals("Today", RepDayRules.formatRelativeDateMs(morning, lateNight, tz))

        // 2 calendar days ago -> "2 days ago"
        val twoDaysAgo = localMs(2026, Calendar.NOVEMBER, 22, 23, 55)
        assertEquals("2 days ago", RepDayRules.formatRelativeDateMs(twoDaysAgo, earlyMorning, tz))

        // Across US spring-forward DST (2026-03-08 is a 23-hour day in America/Los_Angeles)
        val beforeDst = localMs(2026, Calendar.MARCH, 7, 23, 30)
        val afterDst = localMs(2026, Calendar.MARCH, 9, 0, 30)
        assertEquals(2, RepDayRules.calendarDaysBetween(beforeDst, afterDst, tz))
        assertEquals("2 days ago", RepDayRules.formatRelativeDateMs(beforeDst, afterDst, tz))
    }

    // ---- 12d & 12j: Plan block-drop detection & blank placeholder validation ----

    @Test
    fun planEditorRules_detectsDroppedBlocksAndRejectsBlankPlaceholders() {
        val validSlot = PlanWorkoutSlot(id = "sl1", routineId = null, label = "Upper A", timesPerWeek = 2)
        val blankPlaceholder = PlanWorkoutSlot(id = "sl2", routineId = null, label = "   ", timesPerWeek = 1)
        val linkedSlot = PlanWorkoutSlot(id = "sl3", routineId = "rt1", label = "", timesPerWeek = 1)

        val block1 = PlanBlock(id = "b1", name = "Hypertrophy", weeks = 4, workouts = listOf(validSlot))
        val block2 = PlanBlock(id = "b2", name = "Strength", weeks = 4, workouts = listOf(linkedSlot))
        val block3 = PlanBlock(id = "b3", name = "  ", weeks = 2, workouts = listOf(validSlot))

        // Switching to Single Repeating with 3 blocks identifies the 2 dropped blocks
        assertEquals(
            listOf("Strength", "Block 3"),
            RepDayRules.droppedBlocksForSingleRepeating(useBlocks = false, blocks = listOf(block1, block2, block3))
        )
        // Staying in Training Blocks drops nothing
        assertTrue(
            RepDayRules.droppedBlocksForSingleRepeating(useBlocks = true, blocks = listOf(block1, block2)).isEmpty()
        )
        // Single block drops nothing
        assertTrue(
            RepDayRules.droppedBlocksForSingleRepeating(useBlocks = false, blocks = listOf(block1)).isEmpty()
        )

        // Blank placeholder blocks save
        val invalidBlock = block1.copy(workouts = listOf(validSlot, blankPlaceholder))
        assertFalse(RepDayRules.isPlanValidToSave("PPL", useBlocks = true, blocks = listOf(invalidBlock)))
        assertTrue(RepDayRules.isPlanValidToSave("PPL", useBlocks = true, blocks = listOf(block1, block2)))
        assertFalse(RepDayRules.isPlanValidToSave("   ", useBlocks = true, blocks = listOf(block1)))
    }

    // ---- 12g: Locale decimal comma normalization ----

    @Test
    fun decimalInput_normalizesCommaAndAllowsOnlyOneSeparator() {
        assertEquals("62.5", RepDayRules.sanitizeDecimalInput("62,5"))
        assertEquals("62.53", RepDayRules.sanitizeDecimalInput("62,5.3"))
        assertEquals("102.25", RepDayRules.sanitizeDecimalInput("102.2,5abc"))
        assertEquals("62.5", ActiveWorkoutMutations.sanitizeWeightInput("62,5"))
        assertEquals("12345678", RepDayRules.sanitizeDecimalInput("1234567890", maxLength = 8))
    }

    // ---- 12h: Corrupt record decoding never drops silently ----

    @Test
    fun recordDecode_preservesValidRowsAndSurfacesCorruptEntries() {
        val reported = mutableListOf<UnreadableRecord>()
        val rawRows = listOf("good-1" to "100", "bad-1" to "not-a-number", "good-2" to "200")
        val batch = RecordDecodeRules.decodeBatch(
            source = "history",
            rawItems = rawRows,
            idOf = { it.first },
            decode = { it.second.toInt() },
            onError = { rec, _ -> reported.add(rec) }
        )

        assertEquals(listOf(100, 200), batch.items)
        assertEquals(1, batch.unreadable.size)
        assertEquals("bad-1", batch.unreadable.first().recordId)
        assertEquals(1, reported.size)

        val (singleVal, singleErr) = RecordDecodeRules.decodeSingle<Int>(
            source = "settings",
            recordId = "settings_json",
            rawJson = "{corrupt",
            decode = { throw IllegalArgumentException("bad json") }
        )
        assertNull(singleVal)
        assertNotNull(singleErr)
        assertEquals("settings_json", singleErr!!.recordId)

        assertEquals("", RecordDecodeRules.formatUnreadableBannerMessage(0))
        assertEquals("1 saved item couldn't be loaded", RecordDecodeRules.formatUnreadableBannerMessage(1))
        assertEquals("3 saved items couldn't be loaded", RecordDecodeRules.formatUnreadableBannerMessage(3))
    }

    // ---- 12i: Local midnight rollover & plan progress clock refresh ----

    @Test
    fun planProgress_millisUntilNextMidnightAndWeekRollover() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val beforeMidnight = Calendar.getInstance(tz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 23, 23, 50, 0)
        }.timeInMillis
        val waitMs = RepDayRules.millisUntilNextLocalMidnight(beforeMidnight, tz)
        assertEquals(10L * 60L * 1000L, waitMs)

        // Plan progress advances from Week 1 to Week 2 as nowMs crosses 7 days
        val startMs = 1_700_000_000_000L
        val plan = TrainingPlan(
            id = "p1",
            name = "4-Week Block",
            useBlocks = true,
            blocks = listOf(
                PlanBlock(
                    id = "b1",
                    name = "Block 1",
                    weeks = 4,
                    workouts = listOf(PlanWorkoutSlot("sl1", null, "Push", timesPerWeek = 2))
                )
            ),
            startedAt = RepDayRules.formatIso(startMs),
            isActive = true,
            createdAt = RepDayRules.formatIso(startMs),
            updatedAt = RepDayRules.formatIso(startMs),
            updatedAtMs = startMs
        )
        val week1 = RepDayRules.computePlanProgress(plan, emptyList(), emptyList(), nowMs = startMs + 6 * 86_400_000L)
        val week2 = RepDayRules.computePlanProgress(plan, emptyList(), emptyList(), nowMs = startMs + 7 * 86_400_000L + 1_000L)
        assertEquals(1, week1.currentWeek)
        assertEquals(2, week2.currentWeek)
    }

    // ---- 12k: Adding exercise mid-workout leaves inputs blank ----

    @Test
    fun addExerciseMidWorkout_leavesWeightRepsAndTargetRepRangeBlank() {
        val routineWorkout = ActiveWorkout(
            id = "aw1",
            routineId = "rt1",
            routineName = "Upper A",
            startedAt = 1_700_000_000_000L,
            exercises = emptyList(),
            unit = WeightUnit.LB
        )
        val prevMap = mapOf(
            "bench press" to PreviousExerciseSession(
                completedAt = "2026-11-20T12:00:00.000Z",
                unit = WeightUnit.LB,
                sets = listOf(
                    CompletedSet(weight = 185.0, reps = 8, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.TOP),
                    CompletedSet(weight = 175.0, reps = 10, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.BACKOFF)
                ),
                completedAtMs = 1_700_000_000_000L
            )
        )

        val updated = ActiveWorkoutMutations.addExercise(
            workout = routineWorkout,
            normalizedName = "Bench Press",
            lastSessionByExercise = prevMap,
            defaultRestSeconds = 90
        )

        assertEquals(1, updated.exercises.size)
        val added = updated.exercises.first()
        assertEquals(2, added.sets.size)
        assertEquals("", added.repRange)
        assertTrue(added.sets.all { it.weight.isEmpty() && it.reps.isEmpty() && it.targetRepRange == null })
        assertEquals(WorkingSubTag.TOP, added.sets[0].workingSubTag)
        assertEquals(WorkingSubTag.BACKOFF, added.sets[1].workingSubTag)
    }

    // ---- 12m: Save history workout as routine preserves per-set reps and RPE ----

    @Test
    fun buildSyntheticRoutineFromHistory_preservesPerSetRepsAndRpe() {
        val completed = CompletedWorkout(
            id = "cw1",
            routineId = null,
            routineName = "Tuesday, November 24, live workout",
            startedAt = "2026-11-24T10:00:00.000Z",
            completedAt = "2026-11-24T11:00:00.000Z",
            durationSeconds = 3600,
            totalVolume = 5000,
            unit = WeightUnit.LB,
            exercises = listOf(
                CompletedExercise(
                    name = "Barbell Squat",
                    targetSets = 3,
                    repRange = "5-12",
                    sets = listOf(
                        CompletedSet(weight = 225.0, reps = 5, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.TOP, rpe = 8.5),
                        CompletedSet(weight = 185.0, reps = 8, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.BACKOFF, rpe = 8.0),
                        CompletedSet(weight = 155.0, reps = 12, tag = SetTag.DROP, rpe = null)
                    )
                )
            ),
            completedAtMs = 1_700_000_000_000L
        )

        val routine = RepDayRules.buildSyntheticRoutineFromHistory(completed, defaultRest = 120)
        val ex = routine.exercises.single()
        val planned = ex.plannedSets!!
        assertEquals(3, planned.size)
        assertEquals(listOf("5", "8", "12"), planned.map { it.repRange })
        assertEquals(listOf("8.5", "8", null), planned.map { it.targetRpe })
        assertEquals(listOf(120, 0, 120), planned.map { it.restSeconds })
    }
}
