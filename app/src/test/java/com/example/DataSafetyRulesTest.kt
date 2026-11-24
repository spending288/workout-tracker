package com.example

import com.example.data.RepDayDataSource
import com.example.data.SeedData
import com.example.data.local.RestClaimResult
import com.example.data.local.RestClaimStatus
import com.example.data.local.RestTimerSnapshot
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.SetTag
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.domain.BackupImportMode
import com.example.domain.BackupImportPlan
import com.example.domain.BackupValidationResult
import com.example.domain.EditableCompletedSet
import com.example.domain.HistoryWorkoutEditRules
import com.example.domain.RepDayBackupRules
import com.example.domain.RepDayRules
import com.example.domain.SampleDataRules
import com.example.ui.viewmodel.RepDayViewModel
import com.example.ui.viewmodel.WorkoutPlatformHooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataSafetyRulesTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Item 4: Edit a Logged Workout ----

    @Test
    fun editLoggedWorkout_fixingTypoWeightRecomputesVolumeAndFeedsNextPrev() {
        val original = CompletedWorkout(
            id = "cw-typo",
            routineId = "rt-push",
            routineName = "Push Day",
            startedAt = "2026-10-10T10:00:00Z",
            completedAt = "2026-10-10T10:45:00Z",
            completedAtMs = RepDayRules.parseIsoToMillis("2026-10-10T10:45:00Z")!!,
            durationSeconds = 2700,
            totalVolume = 2250 * 5 + 185 * 8, // 12730 (typo 2250 instead of 225)
            unit = WeightUnit.LB,
            exercises = listOf(
                CompletedExercise(
                    name = "Barbell Bench Press",
                    targetSets = 2,
                    repRange = "5-8",
                    sets = listOf(
                        CompletedSet(weight = 2250.0, reps = 5, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.TOP, rpe = 8.5),
                        CompletedSet(weight = 185.0, reps = 8, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.BACKOFF, rpe = 8.0)
                    )
                )
            ),
            notes = "Initial note"
        )

        val draft = HistoryWorkoutEditRules.fromCompletedWorkout(original)
        val initialSig = HistoryWorkoutEditRules.snapshotSignature(draft)
        assertFalse(HistoryWorkoutEditRules.isDirty(draft, initialSig))

        val editedDraft = draft.copy(
            notes = "Fixed typo on top set",
            exercises = draft.exercises.map { ex ->
                ex.copy(
                    sets = ex.sets.mapIndexed { idx, s ->
                        if (idx == 0) s.copy(weightText = "225", rpe = 9.0, tag = SetTag.DROP, workingSubTag = null)
                        else s
                    }
                )
            }
        )
        assertTrue(HistoryWorkoutEditRules.isDirty(editedDraft, initialSig))
        assertTrue(HistoryWorkoutEditRules.canSave(editedDraft))

        val updated = HistoryWorkoutEditRules.applyEdit(editedDraft)
        assertNotNull(updated)
        updated!!

        // Recomputed total volume: 225*5 + 185*8 = 1125 + 1480 = 2605
        assertEquals(2605, updated.totalVolume)
        assertEquals("Fixed typo on top set", updated.notes)
        // Timing unchanged -> exact timestamps preserved
        assertEquals(original.startedAt, updated.startedAt)
        assertEquals(original.completedAt, updated.completedAt)
        assertEquals(original.completedAtMs, updated.completedAtMs)
        assertEquals(original.durationSeconds, updated.durationSeconds)
        // Updated set fields
        val firstSet = updated.exercises.single().sets.first()
        assertEquals(225.0, firstSet.weight, 0.001)
        assertEquals(9.0, firstSet.rpe!!, 0.001)
        assertEquals(SetTag.DROP, firstSet.tag)
        assertNull(firstSet.workingSubTag)

        // Feeds next session's "Prev" with corrected 225
        val prevMap = RepDayRules.buildLastSessionByExercise(listOf(updated))
        val prev = RepDayRules.findPreviousExerciseSession("Barbell Bench Press", prevMap)
        assertNotNull(prev)
        assertEquals(225.0, prev!!.sets.first().weight, 0.001)
    }

    @Test
    fun editLoggedWorkout_startTimeDurationRenameAddRemoveSetsAndExercises() {
        val startMs = 1_760_000_000_000L
        val original = CompletedWorkout(
            id = "cw-edit-2",
            routineId = null,
            routineName = "Live Workout",
            startedAt = RepDayRules.formatIso(startMs),
            completedAt = RepDayRules.formatIso(startMs + 30 * 60_000L),
            completedAtMs = startMs + 30 * 60_000L,
            durationSeconds = 1800,
            totalVolume = 1000,
            unit = WeightUnit.LB,
            exercises = listOf(
                CompletedExercise(
                    name = "Bench Press",
                    sets = listOf(CompletedSet(100.0, 10))
                ),
                CompletedExercise(
                    name = "Cable Fly",
                    sets = listOf(CompletedSet(25.0, 12))
                )
            )
        )

        val draft = HistoryWorkoutEditRules.fromCompletedWorkout(original)
        val newStartMs = startMs - 3_600_000L
        val edited = draft.copy(
            startedAtMs = newStartMs,
            durationMinutes = 45,
            // Rename first exercise, add a set, remove second exercise
            exercises = listOf(
                draft.exercises[0].copy(
                    name = "  Incline  Smith   Bench  ",
                    sets = draft.exercises[0].sets + EditableCompletedSet(
                        id = "hs-added",
                        weightText = "135",
                        repsText = "8",
                        rpe = 8.0,
                        tag = SetTag.WORKING,
                        workingSubTag = WorkingSubTag.TOP
                    )
                )
            )
        )

        val updated = HistoryWorkoutEditRules.applyEdit(edited)!!
        assertEquals(2700, updated.durationSeconds)
        assertEquals(newStartMs + 2700 * 1000L, updated.completedAtMs)
        assertEquals(1, updated.exercises.size)
        assertEquals("Incline Smith Bench", updated.exercises.single().name)
        assertEquals(2, updated.exercises.single().sets.size)
        assertEquals(100 * 10 + 135 * 8, updated.totalVolume)
    }

    @Test
    fun editLoggedWorkout_rejectsWhenAllExercisesOrSetsRemoved() {
        val original = CompletedWorkout(
            id = "cw-empty-check",
            routineId = null,
            routineName = "Session",
            startedAt = "2026-10-10T10:00:00Z",
            completedAt = "2026-10-10T10:30:00Z",
            completedAtMs = 1_760_000_000_000L,
            durationSeconds = 1800,
            totalVolume = 500,
            unit = WeightUnit.LB,
            exercises = listOf(CompletedExercise("Push-Up", sets = listOf(CompletedSet(50.0, 10))))
        )
        val emptyDraft = HistoryWorkoutEditRules.fromCompletedWorkout(original).copy(exercises = emptyList())
        assertFalse(HistoryWorkoutEditRules.canSave(emptyDraft))
        assertNull(HistoryWorkoutEditRules.applyEdit(emptyDraft))
    }

    @Test
    fun viewModel_saveEditedHistoryWorkout_persistsAndRegistersNewCustomExerciseName() = runTest(testDispatcher) {
        val repo = InMemoryDataSource()
        val orig = CompletedWorkout(
            id = "cw-vm-1",
            routineId = null,
            routineName = "Upper",
            startedAt = "2026-10-10T10:00:00Z",
            completedAt = "2026-10-10T10:40:00Z",
            completedAtMs = 1_760_000_000_000L,
            durationSeconds = 2400,
            totalVolume = 1000,
            unit = WeightUnit.LB,
            exercises = listOf(CompletedExercise("Barbell Bench Press", sets = listOf(CompletedSet(100.0, 10))))
        )
        repo.historyFlow.value = listOf(orig)
        val vm = RepDayViewModel(repo, NoOpPlatformHooks(), startClockLoop = false, computeDispatcher = testDispatcher)
        advanceUntilIdle()

        vm.openEditHistoryWorkout(orig)
        val updated = orig.copy(
            totalVolume = 2250,
            exercises = listOf(CompletedExercise("Swiss Bar Press", sets = listOf(CompletedSet(225.0, 10))))
        )
        vm.saveEditedHistoryWorkout(updated)
        advanceUntilIdle()

        assertEquals("Swiss Bar Press", repo.historyFlow.value.single().exercises.single().name)
        assertEquals(2250, repo.historyFlow.value.single().totalVolume)
        assertTrue(repo.customExercisesFlow.value.contains("Swiss Bar Press"))
    }

    // ---- Item 5: Export / Import JSON Backup & CSV Export ----

    @Test
    fun backupJson_roundTripWithFormatVersion_andValidationRejectsInvalidFiles() {
        val routines = SeedData.createSeedRoutines()
        val plans = SeedData.createSeedPlans()
        val history = listOf(
            CompletedWorkout(
                id = "cw-real-1",
                routineId = routines.first().id,
                routineName = routines.first().name,
                startedAt = "2026-10-12T10:00:00Z",
                completedAt = "2026-10-12T10:50:00Z",
                completedAtMs = RepDayRules.parseIsoToMillis("2026-10-12T10:50:00Z")!!,
                durationSeconds = 3000,
                totalVolume = 9000,
                unit = WeightUnit.KG,
                exercises = listOf(
                    CompletedExercise(
                        name = "Pendlay Row",
                        sets = listOf(CompletedSet(90.0, 10, SetTag.WORKING, WorkingSubTag.TOP, 8.5))
                    )
                ),
                notes = "Clean reps"
            )
        )
        val settings = AppSettings(unit = WeightUnit.KG, defaultRestSeconds = 120)
        val json = RepDayBackupRules.encodeBackupJson(
            routines = routines,
            plans = plans,
            history = history,
            customExercises = listOf("Pendlay Row"),
            settings = settings,
            nowMs = 1_760_300_000_000L
        )

        assertTrue(json.contains("\"formatVersion\": 1"))

        val validated = RepDayBackupRules.parseAndValidateBackupJson(json)
        assertTrue(validated is BackupValidationResult.Valid)
        val valid = validated as BackupValidationResult.Valid
        assertEquals(routines.size, valid.routineCount)
        assertEquals(plans.size, valid.planCount)
        assertEquals(1, valid.historyCount)
        assertEquals(1, valid.customExerciseCount)
        assertEquals(WeightUnit.KG, valid.backup.settings.unit)
        assertEquals(120, valid.backup.settings.defaultRestSeconds)

        // Invalid inputs
        assertTrue(RepDayBackupRules.parseAndValidateBackupJson("") is BackupValidationResult.Invalid)
        assertTrue(RepDayBackupRules.parseAndValidateBackupJson("{not json") is BackupValidationResult.Invalid)
        assertTrue(RepDayBackupRules.parseAndValidateBackupJson("""{"routines": []}""") is BackupValidationResult.Invalid)
        assertTrue(RepDayBackupRules.parseAndValidateBackupJson("""{"formatVersion": 99}""") is BackupValidationResult.Invalid)
    }

    @Test
    fun backupImportPlan_mergeSkipsDuplicateIdsAndPreservesActivePlan_replaceOverwritesCleanly() {
        val existingRoutine = Routine(
            id = "rt-1",
            name = "Existing Push",
            exercises = listOf(RoutineExercise("re-1", "Bench", 3, "8")),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        val newRoutine = Routine(
            id = "rt-2",
            name = "Imported Legs",
            exercises = listOf(RoutineExercise("re-2", "Hack Squat Machine", 3, "10")),
            createdAt = "2026-01-02T00:00:00Z",
            updatedAt = "2026-01-02T00:00:00Z"
        )
        val existingPlan = TrainingPlan(
            id = "pl-1",
            name = "Current Active Plan",
            isActive = true,
            blocks = listOf(PlanBlock("b1", "W1", 4, workouts = listOf(PlanWorkoutSlot("s1", "rt-1", "Push")))),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        val importedActivePlan = TrainingPlan(
            id = "pl-2",
            name = "Imported Plan",
            isActive = true,
            blocks = listOf(PlanBlock("b2", "W1", 4, workouts = listOf(PlanWorkoutSlot("s2", "rt-2", "Legs")))),
            createdAt = "2026-01-02T00:00:00Z",
            updatedAt = "2026-01-02T00:00:00Z"
        )
        val json = RepDayBackupRules.encodeBackupJson(
            routines = listOf(existingRoutine.copy(name = "Backup Copy Push"), newRoutine),
            plans = listOf(existingPlan, importedActivePlan),
            history = emptyList(),
            customExercises = listOf("Hack Squat Machine"),
            settings = AppSettings(unit = WeightUnit.KG)
        )
        val valid = RepDayBackupRules.parseAndValidateBackupJson(json) as BackupValidationResult.Valid

        // MERGE skips duplicate rt-1 & pl-1, keeps existing active plan so imported pl-2 becomes inactive, preserves settings
        val mergePlan = RepDayBackupRules.planBackupImport(
            mode = BackupImportMode.MERGE,
            backup = valid.backup,
            existingRoutines = listOf(existingRoutine),
            existingPlans = listOf(existingPlan),
            existingHistory = emptyList(),
            existingCustomExercises = emptyList()
        )
        assertEquals(listOf("rt-2"), mergePlan.routinesToSave.map { it.id })
        assertEquals(1, mergePlan.skippedRoutines)
        assertEquals(listOf("pl-2"), mergePlan.plansToSave.map { it.id })
        assertFalse(mergePlan.plansToSave.single().isActive)
        assertEquals(1, mergePlan.skippedPlans)
        assertNull(mergePlan.settingsToSave)
        assertTrue(mergePlan.customExercisesToSave.contains("Hack Squat Machine"))

        // REPLACE includes all backup items, enforces at most one active plan, and restores settings
        val replacePlan = RepDayBackupRules.planBackupImport(
            mode = BackupImportMode.REPLACE,
            backup = valid.backup,
            existingRoutines = listOf(existingRoutine),
            existingPlans = listOf(existingPlan),
            existingHistory = emptyList(),
            existingCustomExercises = emptyList()
        )
        assertEquals(2, replacePlan.routinesToSave.size)
        assertEquals(1, replacePlan.plansToSave.count { it.isActive })
        assertEquals(WeightUnit.KG, replacePlan.settingsToSave?.unit)
    }

    @Test
    fun csvExport_exactHeaderOneRowPerSetRirAndEscaping() {
        val workout = CompletedWorkout(
            id = "cw-csv-1",
            routineId = "rt-1",
            routineName = "Push, Day \"A\"",
            startedAt = "2026-10-15T14:00:00Z",
            completedAt = "2026-10-15T14:55:00Z",
            completedAtMs = 1_760_540_100_000L,
            durationSeconds = 3300,
            totalVolume = 3100,
            unit = WeightUnit.LB,
            exercises = listOf(
                CompletedExercise(
                    name = "Barbell Bench Press",
                    sets = listOf(
                        CompletedSet(weight = 135.0, reps = 10, tag = SetTag.WARMUP, rpe = null),
                        CompletedSet(weight = 225.5, reps = 5, tag = SetTag.WORKING, workingSubTag = WorkingSubTag.TOP, rpe = 8.5),
                        CompletedSet(weight = 185.0, reps = 8, tag = SetTag.DROP, workingSubTag = null, rpe = 10.0)
                    )
                )
            ),
            notes = "Felt great, bar moved fast\nNext: add 5 lb"
        )

        val csv = RepDayBackupRules.exportHistoryToCsv(listOf(workout))
        val lines = csv.trimEnd().lines()
        assertEquals("date,workout name,exercise,set number,tag,weight,unit,reps,RPE,RIR,notes,weight type,effective load,volume,body weight", lines[0])
        // Row 1: Warm-up, no RPE/RIR, multiline note properly quoted
        assertTrue(csv.contains("2026-10-15T14:55:00Z,\"Push, Day \"\"A\"\"\",Barbell Bench Press,1,Warm-up,135,lb,10,,,\"Felt great, bar moved fast\nNext: add 5 lb\""))
        // Row 2: Top set, RPE 8.5 -> RIR 1.5
        assertTrue(csv.contains("2026-10-15T14:55:00Z,\"Push, Day \"\"A\"\"\",Barbell Bench Press,2,Working · Top,225.5,lb,5,8.5,1.5,"))
        // Row 3: Drop set, RPE 10 -> RIR 0
        assertTrue(csv.contains("2026-10-15T14:55:00Z,\"Push, Day \"\"A\"\"\",Barbell Bench Press,3,Drop Set,185,lb,8,10,0,"))
    }

    // ---- Item 6: Sample Data, One-Time Banner & Erase All Data ----

    @Test
    fun sampleDataRules_loadSamplesAddsRoutinesAndPlanWithoutFakeHistoryAndWithoutDeletingExisting() {
        val existingRoutine = Routine(
            id = "rt-user",
            name = "My Custom Routine",
            exercises = listOf(RoutineExercise("re-u", "Pull-Up", 3, "8")),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        val existingPlan = TrainingPlan(
            id = "pl-user",
            name = "My Active Plan",
            isActive = true,
            blocks = listOf(PlanBlock("b-u", "W1", 4, workouts = listOf(PlanWorkoutSlot("s-u", "rt-user", "Pull")))),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )

        val firstPlan = SampleDataRules.planLoadSampleRoutinesAndPlan(
            existingRoutines = listOf(existingRoutine),
            existingPlans = listOf(existingPlan)
        )
        assertEquals(SeedData.createSeedRoutines().size, firstPlan.routinesToAdd.size)
        assertEquals(SeedData.createSeedPlans().size, firstPlan.plansToAdd.size)
        // Must NOT activate sample plan when user already has an active plan
        assertTrue(firstPlan.plansToAdd.none { it.isActive })

        // Calling again when samples already exist adds 0 duplicates
        val secondPlan = SampleDataRules.planLoadSampleRoutinesAndPlan(
            existingRoutines = listOf(existingRoutine) + firstPlan.routinesToAdd,
            existingPlans = listOf(existingPlan) + firstPlan.plansToAdd
        )
        assertTrue(secondPlan.routinesToAdd.isEmpty())
        assertTrue(secondPlan.plansToAdd.isEmpty())
    }

    @Test
    fun sampleHistoryBanner_triggersOnlyForExistingInstallsWithSeedHistoryUntilDismissed() {
        val seedHistory = SeedData.createSeedHistory()
        assertEquals(2, SampleDataRules.findSeedSampleHistoryIds(seedHistory).size)
        assertTrue(SampleDataRules.shouldShowSampleHistoryBanner(seedHistory, bannerDismissed = false))
        assertFalse(SampleDataRules.shouldShowSampleHistoryBanner(seedHistory, bannerDismissed = true))
        assertFalse(SampleDataRules.shouldShowSampleHistoryBanner(emptyList(), bannerDismissed = false))

        // Real user history only -> no banner
        val userOnly = listOf(seedHistory.first().copy(id = "cw-user-real"))
        assertFalse(SampleDataRules.shouldShowSampleHistoryBanner(userOnly, bannerDismissed = false))
    }

    @Test
    fun eraseConfirmationRequiresExactUppercaseERASE() {
        assertTrue(SampleDataRules.isEraseConfirmationValid("ERASE"))
        assertTrue(SampleDataRules.isEraseConfirmationValid("  ERASE  "))
        assertFalse(SampleDataRules.isEraseConfirmationValid("erase"))
        assertFalse(SampleDataRules.isEraseConfirmationValid("ERAS"))
        assertFalse(SampleDataRules.isEraseConfirmationValid(""))
    }

    @Test
    fun viewModel_freshInstallStartsEmpty_bannerRemovesOnlySeedHistory_eraseWipesAllWithoutFakeHistory() = runTest(testDispatcher) {
        val repo = InMemoryDataSource()
        val vm = RepDayViewModel(repo, NoOpPlatformHooks(), startClockLoop = false, computeDispatcher = testDispatcher)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Fresh install starts completely empty (no fake history or pre-seeded routines)
        assertTrue(vm.uiState.value.routines.isEmpty())
        assertTrue(vm.uiState.value.plans.isEmpty())
        assertTrue(vm.uiState.value.history.isEmpty())
        assertFalse(vm.uiState.value.showSampleHistoryBanner)

        // Load sample routines & plan -> adds routines & plan only, NO fake history
        vm.loadSampleRoutinesAndPlan()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.routines.size)
        assertEquals(1, vm.uiState.value.plans.size)
        assertTrue(vm.uiState.value.history.isEmpty())

        // Simulate existing install that still has the 2 old seed workouts + 1 real workout
        val realWorkout = SeedData.createSeedHistory().first().copy(id = "cw-real-user", routineName = "Real Session")
        repo.historyFlow.value = SeedData.createSeedHistory() + realWorkout
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showSampleHistoryBanner)
        assertEquals(2, vm.uiState.value.sampleHistoryCount)

        // Tap "Remove" on banner -> removes only the 2 seed workouts, keeps real workout, dismisses banner
        vm.removeSampleHistoryWorkouts()
        advanceUntilIdle()
        assertEquals(listOf("cw-real-user"), vm.uiState.value.history.map { it.id })
        assertFalse(vm.uiState.value.showSampleHistoryBanner)

        // Erase all data -> wipes everything and does NOT insert fake history
        vm.eraseAllData()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.routines.isEmpty())
        assertTrue(vm.uiState.value.plans.isEmpty())
        assertTrue(vm.uiState.value.history.isEmpty())
        assertTrue(vm.uiState.value.customExercises.isEmpty())

        collector.cancel()
    }

    private class InMemoryDataSource : RepDayDataSource {
        override val routinesFlow = MutableStateFlow<List<Routine>>(emptyList())
        override val plansFlow = MutableStateFlow<List<TrainingPlan>>(emptyList())
        override val historyFlow = MutableStateFlow<List<CompletedWorkout>>(emptyList())
        override val customExercisesFlow = MutableStateFlow<List<String>>(emptyList())
        override val settingsFlow = MutableStateFlow(AppSettings())
        override val activeWorkoutFlow: Flow<ActiveWorkout?> = MutableStateFlow(null)
        override val restTimerFlow = MutableStateFlow(RestTimerSnapshot())
        override val dismissedSampleHistoryBannerFlow = MutableStateFlow(false)
        override val unreadableRecordCountFlow = MutableStateFlow(0)

        override suspend fun initializeAndBackfill() = Unit
        override suspend fun getActiveWorkoutOnce(): ActiveWorkout? = null
        override suspend fun getSettingsOnce(): AppSettings = settingsFlow.value

        override suspend fun registerCustomExercises(names: Iterable<String>) {
            val added = RepDayRules.extractNewCustomExercises(names, customExercisesFlow.value)
            if (added.isNotEmpty()) {
                customExercisesFlow.value = (customExercisesFlow.value + added).distinct()
            }
        }

        override suspend fun saveRoutine(routine: Routine) {
            routinesFlow.value = routinesFlow.value.filterNot { it.id == routine.id } + routine
            registerCustomExercises(routine.exercises.map { it.name })
        }

        override suspend fun deleteRoutine(routineId: String) {
            routinesFlow.value = routinesFlow.value.filterNot { it.id == routineId }
        }

        override suspend fun savePlan(plan: TrainingPlan) {
            plansFlow.value = plansFlow.value.filterNot { it.id == plan.id } + plan
        }

        override suspend fun saveAllPlans(plans: List<TrainingPlan>) {
            plansFlow.value = plans
        }

        override suspend fun deletePlan(planId: String) {
            plansFlow.value = plansFlow.value.filterNot { it.id == planId }
        }

        override suspend fun saveCompletedWorkout(workout: CompletedWorkout) {
            historyFlow.value = listOf(workout) + historyFlow.value.filterNot { it.id == workout.id }
            registerCustomExercises(workout.exercises.map { it.name })
        }

        override suspend fun deleteCompletedWorkout(workoutId: String) {
            historyFlow.value = historyFlow.value.filterNot { it.id == workoutId }
        }

        override suspend fun deleteCompletedWorkouts(workoutIds: List<String>) {
            val idSet = workoutIds.toSet()
            historyFlow.value = historyFlow.value.filterNot { it.id in idSet }
        }

        override suspend fun saveActiveWorkout(workout: ActiveWorkout?) = Unit
        override suspend fun saveSettings(settings: AppSettings) { settingsFlow.value = settings }
        override suspend fun saveRestTimer(snapshot: RestTimerSnapshot) { restTimerFlow.value = snapshot }
        override suspend fun claimRestTimerCompletion(expectedEndTimeMs: Long, nowMs: Long) =
            RestClaimResult(RestClaimStatus.ALREADY_HANDLED, "")
        override suspend fun adjustRestTimer(deltaSeconds: Int, nowMs: Long) = RestTimerSnapshot()
        override suspend fun hasAskedNotificationPermission(): Boolean = true
        override suspend fun markAskedNotificationPermission() = Unit
        override suspend fun markSampleHistoryBannerDismissed() {
            dismissedSampleHistoryBannerFlow.value = true
        }

        override suspend fun loadSampleRoutinesAndPlan(): Pair<Int, Int> {
            val plan = SampleDataRules.planLoadSampleRoutinesAndPlan(routinesFlow.value, plansFlow.value)
            routinesFlow.value = routinesFlow.value + plan.routinesToAdd
            plansFlow.value = plansFlow.value + plan.plansToAdd
            return plan.routinesToAdd.size to plan.plansToAdd.size
        }

        override suspend fun applyBackupImport(plan: BackupImportPlan) {
            if (plan.mode == BackupImportMode.REPLACE) {
                routinesFlow.value = plan.routinesToSave
                plansFlow.value = plan.plansToSave
                historyFlow.value = plan.historyToSave
                customExercisesFlow.value = plan.customExercisesToSave
                plan.settingsToSave?.let { settingsFlow.value = it }
            } else {
                routinesFlow.value = routinesFlow.value + plan.routinesToSave
                plansFlow.value = plansFlow.value + plan.plansToSave
                historyFlow.value = historyFlow.value + plan.historyToSave
                customExercisesFlow.value = (customExercisesFlow.value + plan.customExercisesToSave).distinct()
            }
        }

        override suspend fun eraseAllData() {
            routinesFlow.value = emptyList()
            plansFlow.value = emptyList()
            historyFlow.value = emptyList()
            customExercisesFlow.value = emptyList()
            settingsFlow.value = SeedData.defaultSettings()
        }
    }

    private class NoOpPlatformHooks : WorkoutPlatformHooks {
        override fun triggerLightTick() = Unit
        override fun triggerRestCompleteVibration() = Unit
        override suspend fun playRestCompleteChime() = Unit
        override fun startOrSyncWorkoutService() = Unit
        override fun stopWorkoutService() = Unit
        override fun postRestDoneHeadsUpNotification(message: String) = Unit
        override fun cancelRestDoneNotification() = Unit
        override fun shouldPromptNotificationPermission(): Boolean = false
        override fun registerProcessStopObserver(onStop: () -> Unit): () -> Unit = {}
    }
}
