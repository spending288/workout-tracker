package com.example

import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.LoadType
import com.example.data.model.SetTag
import com.example.data.model.WeightUnit
import com.example.data.model.WorkoutSet
import com.example.domain.AutoBackupRules
import com.example.domain.ExerciseSearchRules
import com.example.domain.ExerciseUsage
import com.example.domain.FinishWorkoutRules
import com.example.domain.LoadTypeRules
import com.example.domain.PersonalBestKind
import com.example.domain.PersonalBestRules
import com.example.domain.RepDayBackupRules
import com.example.domain.RepDayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class EnhancementsRulesTest {

    // ---------------- Weight type ----------------

    @Test
    fun infer_guessesWeightTypeFromBuiltInNames() {
        assertEquals(LoadType.PER_HAND, LoadTypeRules.infer("Dumbbell Bench Press"))
        assertEquals(LoadType.PER_HAND, LoadTypeRules.infer("Incline Dumbbell Press"))
        assertEquals(LoadType.PER_HAND, LoadTypeRules.infer("Hammer Curl"))
        assertEquals(LoadType.STANDARD, LoadTypeRules.infer("Single-Arm Dumbbell Row"))
        assertEquals(LoadType.STANDARD, LoadTypeRules.infer("Dumbbell Pullover"))
        assertEquals(LoadType.BODYWEIGHT, LoadTypeRules.infer("Weighted Pull-Up"))
        assertEquals(LoadType.BODYWEIGHT, LoadTypeRules.infer("Chest Dip"))
        assertEquals(LoadType.BODYWEIGHT, LoadTypeRules.infer("Hanging Leg Raise"))
        assertEquals(LoadType.ASSISTED, LoadTypeRules.infer("Assisted Pull-Up"))
        assertEquals(LoadType.STANDARD, LoadTypeRules.infer("Lat Pulldown"))
        assertEquals(LoadType.STANDARD, LoadTypeRules.infer("Barbell Bench Press"))
        assertEquals(LoadType.STANDARD, LoadTypeRules.infer(""))
    }

    @Test
    fun resolve_prefersTheUsersChoiceOverTheGuess() {
        val overrides = LoadTypeRules.withOverride(emptyMap(), "Walking Lunges", LoadType.PER_HAND)
        assertEquals(LoadType.PER_HAND, LoadTypeRules.resolve("walking  lunges", overrides))
        assertEquals(LoadType.STANDARD, LoadTypeRules.resolve("Walking Lunges", emptyMap()))
    }

    @Test
    fun effectiveLoad_andVolume_followTheWeightType() {
        assertEquals(185.0, LoadTypeRules.effectiveLoad(185.0, LoadType.STANDARD, null), 0.001)
        assertEquals(80.0, LoadTypeRules.effectiveLoad(40.0, LoadType.PER_HAND, null), 0.001)
        assertEquals(205.0, LoadTypeRules.effectiveLoad(25.0, LoadType.BODYWEIGHT, 180.0), 0.001)
        assertEquals(25.0, LoadTypeRules.effectiveLoad(25.0, LoadType.BODYWEIGHT, null), 0.001)
        assertEquals(140.0, LoadTypeRules.effectiveLoad(40.0, LoadType.ASSISTED, 180.0), 0.001)
        assertEquals(0.0, LoadTypeRules.effectiveLoad(40.0, LoadType.ASSISTED, null), 0.001)
        assertEquals(800.0, LoadTypeRules.setVolume(40.0, 10, LoadType.PER_HAND, null), 0.001)
        assertEquals(0.0, LoadTypeRules.setVolume(40.0, 0, LoadType.PER_HAND, null), 0.001)
    }

    @Test
    fun labels_makeTheWeightTypeVisible() {
        assertEquals("lb ea", LoadTypeRules.weightColumnLabel(WeightUnit.LB, LoadType.PER_HAND))
        assertEquals("+kg", LoadTypeRules.weightColumnLabel(WeightUnit.KG, LoadType.BODYWEIGHT))
        assertEquals("40 lb ea", LoadTypeRules.formatLoggedWeight(40.0, WeightUnit.LB, LoadType.PER_HAND))
        assertEquals("BW", LoadTypeRules.formatLoggedWeight(0.0, WeightUnit.LB, LoadType.BODYWEIGHT))
        assertEquals("BW+25 lb", LoadTypeRules.formatLoggedWeight(25.0, WeightUnit.LB, LoadType.BODYWEIGHT))
        assertEquals("BW−40 lb", LoadTypeRules.formatLoggedWeight(40.0, WeightUnit.LB, LoadType.ASSISTED))
        assertEquals("40 ea", LoadTypeRules.formatCompactWeight("40", 40.0, LoadType.PER_HAND))
    }

    @Test
    fun bodyWeight_isStoredInKg_andShownInTheWorkoutUnit() {
        val kg = LoadTypeRules.bodyWeightToKg(180.0, WeightUnit.LB)!!
        assertEquals(180.0, LoadTypeRules.bodyWeightIn(kg, WeightUnit.LB)!!, 0.05)
        assertNull(LoadTypeRules.bodyWeightToKg(0.0, WeightUnit.LB))
        assertNull(LoadTypeRules.bodyWeightIn(null, WeightUnit.KG))
    }

    @Test
    fun liveVolume_countsPerHandDumbbellsTwice() {
        val workout = ActiveWorkout(
            id = "aw", routineName = "Push", startedAt = 0L,
            exercises = listOf(
                ActiveExercise("e1", "Dumbbell Bench Press", 1, "8-10", sets = listOf(WorkoutSet("s1", "40", "10", completed = true))),
                ActiveExercise("e2", "Barbell Bench Press", 1, "5", sets = listOf(WorkoutSet("s2", "185", "5", completed = true)))
            )
        )
        assertEquals(800 + 925, RepDayRules.computeLiveVolume(workout))
    }

    @Test
    fun finish_recordsTheWeightType_andLegacyHistoryCountsAsTyped() {
        val workout = ActiveWorkout(
            id = "aw", routineName = "Pull", startedAt = 0L,
            exercises = listOf(
                ActiveExercise("e1", "Weighted Pull-Up", 1, "6", sets = listOf(WorkoutSet("s1", "25", "6", completed = true))),
                ActiveExercise("e2", "Seated Cable Row", 1, "10", sets = listOf(WorkoutSet("s2", "120", "10", completed = false)))
            )
        )
        val done = FinishWorkoutRules.buildCompletedExercises(workout, emptyMap())
        assertEquals(1, done.size)
        assertEquals(LoadType.BODYWEIGHT, done[0].loadType)
        assertEquals((180 + 25) * 6, RepDayRules.computeCompletedVolume(done, bodyWeight = 180.0))

        val legacy = CompletedExercise("Dumbbell Bench Press", sets = listOf(CompletedSet(80.0, 10)))
        assertEquals(800, RepDayRules.computeCompletedVolume(listOf(legacy)))
    }

    // ---------------- Personal bests ----------------

    private fun history(vararg exercises: CompletedExercise, unit: WeightUnit = WeightUnit.LB, id: String = "w1") =
        CompletedWorkout(
            id = id, routineName = "Old", startedAt = "2026-11-01T10:00:00Z", completedAt = "2026-11-01T11:00:00Z",
            durationSeconds = 3600, totalVolume = 0, unit = unit, exercises = exercises.toList(), completedAtMs = 1L
        )

    @Test
    fun personalBests_heaviestBeatsEarlierHistory_warmupsIgnored() {
        val old = history(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(185.0, 5), CompletedSet(225.0, 1, tag = SetTag.WARMUP))))
        val now = listOf(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(195.0, 3))))
        val pbs = PersonalBestRules.find(now, WeightUnit.LB, listOf(old))
        assertEquals(1, pbs.size)
        assertEquals(PersonalBestKind.HEAVIEST, pbs[0].kind)
        assertEquals(185.0, pbs[0].previousBest, 0.001)
    }

    @Test
    fun personalBests_estimatedMaxWhenNotHeavier_andNothingForFirstSession() {
        val old = history(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(185.0, 5))))
        val now = listOf(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(185.0, 8))))
        val pbs = PersonalBestRules.find(now, WeightUnit.LB, listOf(old))
        assertEquals(PersonalBestKind.EST_1RM, pbs.single().kind)

        val firstTime = listOf(CompletedExercise("Front Squat", sets = listOf(CompletedSet(135.0, 5))))
        assertTrue(PersonalBestRules.find(firstTime, WeightUnit.LB, listOf(old)).isEmpty())
    }

    @Test
    fun personalBests_convertUnits_andOnlyCompareSameWeightType() {
        val oldKg = history(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(100.0, 5))), unit = WeightUnit.KG)
        val now = listOf(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(225.0, 5))))
        // 100 kg ≈ 220.5 lb, so 225 lb is heavier.
        assertEquals(PersonalBestKind.HEAVIEST, PersonalBestRules.find(now, WeightUnit.LB, listOf(oldKg)).single().kind)

        // Old dumbbell history logged as a total (legacy) isn't compared with per-hand entries.
        val legacyDb = history(CompletedExercise("Dumbbell Bench Press", sets = listOf(CompletedSet(80.0, 10))))
        val perHand = listOf(CompletedExercise("Dumbbell Bench Press", sets = listOf(CompletedSet(45.0, 10)), loadType = LoadType.PER_HAND))
        assertTrue(PersonalBestRules.find(perHand, WeightUnit.LB, listOf(legacyDb)).isEmpty())
    }

    @Test
    fun personalBests_bodyweightRepsRecord() {
        val old = history(CompletedExercise("Chest Dip", sets = listOf(CompletedSet(0.0, 12)), loadType = LoadType.BODYWEIGHT))
        val now = listOf(CompletedExercise("Chest Dip", sets = listOf(CompletedSet(0.0, 15)), loadType = LoadType.BODYWEIGHT))
        val pb = PersonalBestRules.find(now, WeightUnit.LB, listOf(old)).single()
        assertEquals(PersonalBestKind.MOST_REPS, pb.kind)
        assertTrue(PersonalBestRules.describe(pb).contains("15 at body weight"))
    }

    @Test
    fun estimatedOneRepMax_usesEpley() {
        assertEquals(225.0, PersonalBestRules.estimatedOneRepMax(225.0, 1), 0.001)
        assertEquals(200.0 * (1 + 5 / 30.0), PersonalBestRules.estimatedOneRepMax(200.0, 5), 0.001)
        assertEquals(0.0, PersonalBestRules.estimatedOneRepMax(200.0, 0), 0.001)
    }

    // ---------------- CSV ----------------

    @Test
    fun csv_addsWeightTypeEffectiveLoadAndVolumeColumns() {
        val w = history(
            CompletedExercise("Dumbbell Bench Press", sets = listOf(CompletedSet(40.0, 10)), loadType = LoadType.PER_HAND),
            CompletedExercise("Assisted Pull-Up", sets = listOf(CompletedSet(40.0, 8)), loadType = LoadType.ASSISTED)
        )
        val csv = RepDayBackupRules.exportHistoryToCsv(listOf(w))
        val lines = csv.trim().lines()
        assertTrue(lines[0].endsWith(",weight type,effective load,volume,body weight"))
        assertTrue(lines[1].endsWith(",per_hand,80,800,"))
        // No body weight recorded: assisted load is unknown, so those cells stay empty.
        assertTrue(lines[2].endsWith(",assisted,,,"))

        val withBw = RepDayBackupRules.exportHistoryToCsv(listOf(w.copy(bodyWeight = 180.0)))
        assertTrue(withBw.trim().lines()[2].endsWith(",assisted,140,1120,180"))
    }

    // ---------------- Automatic backups ----------------

    @Test
    fun autoBackup_namesSortByTime_andOnlyOurOldFilesAreRemoved() {
        val utc = TimeZone.getTimeZone("UTC")
        val name = AutoBackupRules.fileName(1_764_000_000_000L, utc)
        assertTrue(name.startsWith(AutoBackupRules.FILE_PREFIX) && name.endsWith(".json"))

        val existing = (1..12).map { "RepDay-auto-backup-2026-11-${"%02d".format(it)}_070000.json" } +
            listOf("my-notes.json", "repday-backup-manual.json")
        val toDelete = AutoBackupRules.filesToDelete(existing, keep = 10)
        assertEquals(listOf("RepDay-auto-backup-2026-11-02_070000.json", "RepDay-auto-backup-2026-11-01_070000.json"), toDelete)
    }

    @Test
    fun autoBackup_dailyCheck() {
        val day = AutoBackupRules.DAILY_INTERVAL_MS
        assertTrue(AutoBackupRules.isDailyBackupDue(true, "content://tree", 0L, 10L))
        assertTrue(AutoBackupRules.isDailyBackupDue(true, "content://tree", 1_000L, 1_000L + day))
        assertFalse(AutoBackupRules.isDailyBackupDue(true, "content://tree", 1_000L, 1_000L + day - 1))
        assertFalse(AutoBackupRules.isDailyBackupDue(false, "content://tree", 0L, 10L))
        assertFalse(AutoBackupRules.isDailyBackupDue(true, null, 0L, 10L))
    }

    // ---------------- Exercise search ----------------

    private val library = listOf(
        "Barbell Bench Press", "Dumbbell Bench Press", "Incline Dumbbell Press", "Bulgarian Split Squat",
        "Barbell Back Squat", "Lat Pulldown", "Seated Cable Row", "Cable Lateral Raise"
    )

    @Test
    fun search_toleratesTypos() {
        val ranked = ExerciseSearchRules.rank("bech", library, emptyMap(), nowMs = 0L)
        assertTrue(ranked.take(2).containsAll(listOf("Barbell Bench Press", "Dumbbell Bench Press")))
        assertEquals("Lat Pulldown", ExerciseSearchRules.rank("lat puldown", library, emptyMap(), 0L).first())
        assertTrue(ExerciseSearchRules.rank("xyz", library, emptyMap(), 0L).isEmpty())
    }

    @Test
    fun search_putsRecentThenFrequentFirst() {
        val now = 100L * 24 * 60 * 60 * 1000
        val usage = mapOf(
            RepDayRules.exerciseKey("Dumbbell Bench Press") to ExerciseUsage(count = 3, lastUsedMs = now - 1000),
            RepDayRules.exerciseKey("Seated Cable Row") to ExerciseUsage(count = 20, lastUsedMs = now - 90L * 24 * 60 * 60 * 1000)
        )
        val empty = ExerciseSearchRules.rank("", library, usage, now)
        assertEquals("Dumbbell Bench Press", empty[0]) // used recently
        assertEquals("Seated Cable Row", empty[1])     // used most, but not recently
        val bench = ExerciseSearchRules.rank("bench", library, usage, now)
        assertEquals("Dumbbell Bench Press", bench[0]) // same match quality, used recently
    }

    @Test
    fun pickerSearch_keepsExclusionsAndHidesTheExactName() {
        val results = RepDayRules.searchExercises("bench press", library, excludeNames = setOf("Dumbbell Bench Press"))
        assertTrue("Barbell Bench Press" in results)
        assertFalse("Dumbbell Bench Press" in results)
        val exact = RepDayRules.searchExercises("Lat Pulldown", library, omitExactMatch = true)
        assertFalse("Lat Pulldown" in exact)
    }

    @Test
    fun usage_isBuiltFromHistory() {
        val w1 = history(CompletedExercise("Lat Pulldown", sets = listOf(CompletedSet(100.0, 10)))).copy(completedAtMs = 5L)
        val w2 = history(CompletedExercise("lat pulldown", sets = listOf(CompletedSet(110.0, 10))), id = "w2").copy(completedAtMs = 9L)
        val usage = ExerciseSearchRules.buildUsage(listOf(w1, w2))
        assertEquals(ExerciseUsage(2, 9L), usage[RepDayRules.exerciseKey("Lat Pulldown")])
    }
}
