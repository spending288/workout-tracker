package com.example

import com.example.data.RepDayDataSource
import com.example.data.local.RestClaimResult
import com.example.data.local.RestClaimStatus
import com.example.data.local.RestTimerSnapshot
import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlannedSet
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkoutSet
import com.example.domain.BackupImportMode
import com.example.domain.BackupImportPlan
import com.example.domain.RepDayRules
import com.example.domain.RestTimerRules
import com.example.domain.SampleDataRules
import com.example.ui.viewmodel.RepDayViewModel
import com.example.ui.viewmodel.WorkoutPlatformHooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class ActiveWorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var fakeNowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeNowMs = 1_700_000_000_000L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        repo: FakeRepDayDataSource = FakeRepDayDataSource(),
        platform: FakeWorkoutPlatformHooks = FakeWorkoutPlatformHooks(),
        startClockLoop: Boolean = false
    ): RepDayViewModel = RepDayViewModel(
        repository = repo,
        platform = platform,
        clock = { fakeNowMs },
        persistDebounceMs = 400L,
        startClockLoop = startClockLoop,
        computeDispatcher = testDispatcher
    )

    private fun sampleActiveWorkout(): ActiveWorkout = ActiveWorkout(
        id = "aw_test",
        routineId = "rt_1",
        routineName = "Push Day",
        startedAt = fakeNowMs - 60_000L,
        exercises = listOf(
            ActiveExercise(
                id = "ex_bench",
                name = "Barbell Bench Press",
                targetSets = 2,
                repRange = "6-8",
                restSeconds = 90,
                sets = listOf(
                    WorkoutSet(id = "s1", weight = "", reps = "", completed = false, targetRepRange = "6-8", restSeconds = 90),
                    WorkoutSet(id = "s2", weight = "", reps = "", completed = false, targetRepRange = "6-8", restSeconds = 90)
                )
            )
        )
    )

    @Test
    fun typing225AndImmediatelyTogglingDone_logs225WithoutAutoFillOverwrite() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(
            initialActiveWorkout = sampleActiveWorkout(),
            saveDelayMs = 200L // Simulate slow DataStore I/O
        )
        val vm = createViewModel(repo = repo)
        advanceUntilIdle() // Load initial active workout once at startup

        val prevSet = CompletedSet(weight = 135.0, reps = 8)

        // Rapid keystrokes "2", "22", "225" followed immediately by tapping Done — zero waiting in between
        vm.updateSetWeight("ex_bench", "s1", "2")
        vm.updateSetWeight("ex_bench", "s1", "22")
        vm.updateSetWeight("ex_bench", "s1", "225")
        vm.toggleSetCompleted("ex_bench", "s1", prevSet)

        // Synchronous in-memory state immediately reflects 225 and completed=true (NOT overwritten by prevSet 135)
        val set1 = vm.activeWorkout.value!!.exercises[0].sets[0]
        assertEquals("225", set1.weight)
        assertEquals("8", set1.reps) // Blank reps auto-filled from previous session
        assertTrue(set1.completed)

        // Debounce has not fired yet
        assertEquals(0, repo.activeWorkoutSaves.size)

        // Finish workout immediately and verify 225.0 is logged to history
        vm.finishActiveWorkout(notes = "Felt strong")
        advanceUntilIdle()

        assertEquals(1, repo.savedCompletedWorkouts.size)
        val loggedSet = repo.savedCompletedWorkouts.single().exercises.single().sets.single()
        assertEquals(225.0, loggedSet.weight, 0.001)
        assertEquals(8, loggedSet.reps)
        assertEquals("Felt strong", repo.savedCompletedWorkouts.single().notes)
    }

    @Test
    fun completingSet_promptsForEffortOnlyWhenNoneChosenYet() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        val vm = createViewModel(repo = repo)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()

        // Set 1: effort picked in the set row first, then completed -> no "How hard?" bar
        vm.setActiveSetRpe("ex_bench", "s1", 8.0)
        vm.toggleSetCompleted("ex_bench", "s1", CompletedSet(185.0, 8))
        runCurrent()
        assertNull(vm.uiState.value.pendingEffortPrompt)
        assertEquals(8.0, vm.activeWorkout.value!!.exercises[0].sets[0].rpe!!, 0.001)

        // Set 2: no effort chosen -> the bar appears for set 2
        vm.toggleSetCompleted("ex_bench", "s2", CompletedSet(185.0, 8))
        runCurrent()
        val prompt = vm.uiState.value.pendingEffortPrompt
        assertNotNull(prompt)
        assertEquals("s2", prompt!!.setId)

        collector.cancel()
    }

    @Test
    fun rapidBurstOfAddSetFillPreviousToggleDoneAndNotes_losesNothingAndDebouncesPersistence() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(
            initialActiveWorkout = sampleActiveWorkout(),
            saveDelayMs = 150L
        )
        val vm = createViewModel(repo = repo)
        advanceUntilIdle()

        // Rapid burst with zero waiting between calls
        vm.fillSetFromPrevious("ex_bench", "s1", prevWeight = 185.0, prevReps = 8)
        vm.toggleSetCompleted("ex_bench", "s1", CompletedSet(185.0, 8))
        vm.fillSetFromPrevious("ex_bench", "s2", prevWeight = 195.0, prevReps = 6)
        vm.toggleSetCompleted("ex_bench", "s2", CompletedSet(195.0, 6))
        vm.addSetToActiveExercise("ex_bench")
        val addedSetId = vm.activeWorkout.value!!.exercises[0].sets[2].id
        vm.updateSetWeight("ex_bench", addedSetId, "205")
        vm.updateSetReps("ex_bench", addedSetId, "5")
        vm.toggleSetCompleted("ex_bench", addedSetId, null)
        vm.updateActiveWorkoutNotes("Top set PR at 205x5")

        // Every mutation is immediately present in memory before any coroutine resumes
        val workout = vm.activeWorkout.value!!
        assertEquals(3, workout.exercises[0].sets.size)
        assertEquals("185", workout.exercises[0].sets[0].weight)
        assertEquals("8", workout.exercises[0].sets[0].reps)
        assertTrue(workout.exercises[0].sets[0].completed)
        assertEquals("195", workout.exercises[0].sets[1].weight)
        assertEquals("6", workout.exercises[0].sets[1].reps)
        assertTrue(workout.exercises[0].sets[1].completed)
        assertEquals("205", workout.exercises[0].sets[2].weight)
        assertEquals("5", workout.exercises[0].sets[2].reps)
        assertTrue(workout.exercises[0].sets[2].completed)
        assertEquals("Top set PR at 205x5", workout.notes)

        // Before 400ms debounce, zero disk writes have occurred
        advanceTimeBy(399L)
        runCurrent()
        assertEquals(0, repo.activeWorkoutSaves.size)

        // At 400ms, a single debounced write persists the entire final snapshot
        advanceTimeBy(1L)
        advanceUntilIdle()
        assertEquals(1, repo.activeWorkoutSaves.size)
        val persisted = repo.activeWorkoutSaves.single()!!
        assertEquals(3, persisted.exercises[0].sets.size)
        assertEquals("205", persisted.exercises[0].sets[2].weight)
        assertEquals("Top set PR at 205x5", persisted.notes)

        // Debounced saves must NOT call registerCustomExercises
        assertEquals(0, repo.registerCustomExerciseCalls.size)
    }

    @Test
    fun processOnStop_flushesDirtyActiveWorkoutImmediatelyBeforeDebounceExpires() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        val platform = FakeWorkoutPlatformHooks()
        val vm = createViewModel(repo = repo, platform = platform)
        advanceUntilIdle()

        vm.updateSetWeight("ex_bench", "s1", "315")
        vm.updateActiveWorkoutNotes("Mid-set notes")

        // Only 100ms elapsed (< 400ms debounce): not saved yet
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(0, repo.activeWorkoutSaves.size)

        // App goes to background -> ProcessLifecycleOwner ON_STOP fires
        platform.triggerProcessStop()
        advanceUntilIdle()

        assertEquals(1, repo.activeWorkoutSaves.size)
        assertEquals("315", repo.activeWorkoutSaves.single()!!.exercises[0].sets[0].weight)
        assertEquals("Mid-set notes", repo.activeWorkoutSaves.single()!!.notes)
    }

    @Test
    fun discardAndReplace_flushImmediatelyAndPreventStaleDebouncedOverwrite() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        val vm = createViewModel(repo = repo)
        advanceUntilIdle()

        // Make dirty edit then immediately replace with a new routine
        vm.updateSetWeight("ex_bench", "s1", "275")
        val newRoutine = Routine(
            id = "rt_pull",
            name = "Pull Day",
            focus = "Back",
            exercises = listOf(
                RoutineExercise(
                    id = "re_1",
                    name = "Pull-Up",
                    targetSets = 1,
                    repRange = "8-10",
                    restSeconds = 90,
                    plannedSets = listOf(PlannedSet("ps_1", null, null, "8-10", null, 90))
                )
            ),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        vm.requestStartRoutine(newRoutine)
        vm.confirmReplaceActiveWorkout()

        // Immediately replaced in memory
        assertEquals("Pull Day", vm.activeWorkout.value!!.routineName)

        // Advance past debounce — old workout flushed before replacement, final persisted state is Pull Day
        advanceTimeBy(1_000L)
        advanceUntilIdle()
        assertEquals(2, repo.activeWorkoutSaves.size)
        assertEquals("275", repo.activeWorkoutSaves[0]!!.exercises[0].sets[0].weight)
        assertEquals("Pull Day", repo.activeWorkoutSaves[1]!!.routineName)

        // Now make a dirty edit on Pull Day and immediately discard
        val pullEx = vm.activeWorkout.value!!.exercises[0]
        vm.updateSetWeight(pullEx.id, pullEx.sets[0].id, "45")
        vm.discardActiveWorkout()
        assertNull(vm.activeWorkout.value)

        advanceTimeBy(1_000L)
        advanceUntilIdle()
        // Final persisted active workout MUST remain null (no stale debounced resurrection)
        assertNull(repo.persistedActiveWorkout)
    }

    @Test
    fun registerCustomExercises_calledOnlyOnAddSwapAndFinish_neverOnSetEdits() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        val vm = createViewModel(repo = repo)
        advanceUntilIdle()

        // Weight/reps/notes/toggle/add-set edits trigger debounced save but ZERO registerCustomExercises calls
        vm.updateSetWeight("ex_bench", "s1", "185")
        vm.updateSetReps("ex_bench", "s1", "8")
        vm.updateActiveWorkoutNotes("Warmup felt good")
        vm.addSetToActiveExercise("ex_bench")
        vm.toggleSetCompleted("ex_bench", "s1", null)
        advanceTimeBy(500L)
        advanceUntilIdle()
        assertEquals(0, repo.registerCustomExerciseCalls.size)

        // Adding a custom exercise registers it once
        vm.addExerciseToActiveWorkout("Meadows Row")
        advanceUntilIdle()
        assertEquals(listOf(listOf("Meadows Row")), repo.registerCustomExerciseCalls)

        // Swapping an exercise registers the new name once
        val addedExId = vm.activeWorkout.value!!.exercises.last().id
        vm.swapActiveExercise(addedExId, "Chest-Supported T-Bar Row")
        advanceUntilIdle()
        assertEquals(
            listOf(listOf("Meadows Row"), listOf("Chest-Supported T-Bar Row")),
            repo.registerCustomExerciseCalls
        )

        // Finishing registers exercises via saveCompletedWorkout
        vm.finishActiveWorkout(notes = "")
        advanceUntilIdle()
        assertEquals(3, repo.registerCustomExerciseCalls.size)
        assertTrue(repo.registerCustomExerciseCalls.last().contains("Barbell Bench Press"))
    }

    @Test
    fun mutationsWorkWithoutActiveUiStateCollector_andStartupEmissionsDoNotClobberEdits() = runTest(testDispatcher) {
        // Notice: uiState is NEVER collected in this test (WhileSubscribed stays inactive)
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        val vm = createViewModel(repo = repo)
        advanceUntilIdle()

        vm.updateSetWeight("ex_bench", "s1", "225")
        vm.toggleSetCompleted("ex_bench", "s1", CompletedSet(135.0, 8))
        vm.toggleSoundEnabled()

        // Even though DataStore emits an older snapshot externally, in-memory state stays authoritative
        repo.activeWorkoutFlowMutable.value = sampleActiveWorkout()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals("225", vm.activeWorkout.value!!.exercises[0].sets[0].weight)
        assertTrue(vm.activeWorkout.value!!.exercises[0].sets[0].completed)

        advanceTimeBy(500L)
        advanceUntilIdle()
        assertFalse(repo.persistedSettings.soundEnabled)
        assertEquals("225", repo.persistedActiveWorkout!!.exercises[0].sets[0].weight)
    }

    @Test
    fun clockAndRestTimerTicks_updateDedicatedFlowsWithoutRebuildingUiState() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        val vm = createViewModel(repo = repo, startClockLoop = true)

        var uiStateEmissions = 0
        val uiCollector = backgroundScope.launch(testDispatcher) {
            vm.uiState.collect { uiStateEmissions++ }
        }
        val elapsedValues = mutableListOf<Long>()
        val elapsedCollector = backgroundScope.launch(testDispatcher) {
            vm.elapsedSeconds.collect { elapsedValues.add(it) }
        }

        // Let startup and initial emission settle
        runCurrent()
        val baselineEmissions = uiStateEmissions

        // Start a 60s rest timer and advance wall clock by 3 seconds
        vm.startRestTimer(60, "Barbell Bench Press")
        runCurrent()
        assertTrue(vm.restTimer.value.isActive)
        assertEquals(60, vm.restTimer.value.remainingSeconds)

        repeat(3) {
            fakeNowMs += 1_000L
            advanceTimeBy(1_000L)
            runCurrent()
        }

        // Dedicated elapsedSeconds and restTimer flows advanced as expected
        assertEquals(63L, vm.elapsedSeconds.value)
        assertEquals(57, vm.restTimer.value.remainingSeconds)
        assertTrue(elapsedValues.size >= 3)

        // RepDayUiState did NOT emit a single time from elapsed or rest timer ticks!
        assertEquals(baselineEmissions, uiStateEmissions)

        uiCollector.cancel()
        elapsedCollector.cancel()
    }

    @Test
    fun historyEmission_populatesLastSessionByExerciseAndUsesItOnAddExercise() = runTest(testDispatcher) {
        val repo = FakeRepDayDataSource(initialActiveWorkout = sampleActiveWorkout())
        repo.historyFlow.value = listOf(
            CompletedWorkout(
                id = "cw_old",
                routineId = null,
                routineName = "Old",
                startedAt = "2026-01-01T10:00:00Z",
                completedAt = "2026-01-01T10:45:00Z",
                completedAtMs = 1_767_264_300_000L,
                durationSeconds = 2700,
                totalVolume = 2000,
                unit = WeightUnit.LB,
                exercises = listOf(
                    CompletedExercise(
                        name = "Romanian Deadlift",
                        sets = listOf(CompletedSet(185.0, 8), CompletedSet(185.0, 8))
                    )
                )
            ),
            CompletedWorkout(
                id = "cw_new",
                routineId = null,
                routineName = "Newer",
                startedAt = "2026-02-01T10:00:00Z",
                completedAt = "2026-02-01T10:45:00Z",
                completedAtMs = 1_769_942_700_000L,
                durationSeconds = 2700,
                totalVolume = 6000,
                unit = WeightUnit.LB,
                exercises = listOf(
                    CompletedExercise(
                        name = "  romanian  deadlift ",
                        sets = listOf(
                            CompletedSet(225.0, 8),
                            CompletedSet(225.0, 8),
                            CompletedSet(225.0, 8),
                            CompletedSet(225.0, 8)
                        )
                    )
                )
            )
        )
        val vm = createViewModel(repo = repo)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()

        val map = vm.uiState.value.lastSessionByExercise
        val prev = map[RepDayRules.exerciseKey("Romanian Deadlift")]
        assertNotNull(prev)
        assertEquals(4, prev!!.sets.size)
        assertEquals(225.0, prev.sets.first().weight, 0.001)

        // Adding the exercise mid-workout creates 4 sets (seeded from lastSessionByExercise)
        vm.addExerciseToActiveWorkout("Romanian Deadlift")
        val added = vm.activeWorkout.value!!.exercises.last()
        assertEquals("Romanian Deadlift", added.name)
        assertEquals(4, added.sets.size)

        collector.cancel()
    }

    private class FakeRepDayDataSource(
        initialActiveWorkout: ActiveWorkout? = null,
        initialSettings: AppSettings = AppSettings(),
        private val saveDelayMs: Long = 0L
    ) : RepDayDataSource {
        override val routinesFlow = MutableStateFlow<List<Routine>>(emptyList())
        override val plansFlow = MutableStateFlow<List<TrainingPlan>>(emptyList())
        override val historyFlow = MutableStateFlow<List<CompletedWorkout>>(emptyList())
        override val customExercisesFlow = MutableStateFlow<List<String>>(emptyList())
        override val settingsFlow = MutableStateFlow(initialSettings)
        val activeWorkoutFlowMutable = MutableStateFlow(initialActiveWorkout)
        override val activeWorkoutFlow: Flow<ActiveWorkout?> = activeWorkoutFlowMutable
        override val restTimerFlow = MutableStateFlow(RestTimerSnapshot())
        override val dismissedSampleHistoryBannerFlow = MutableStateFlow(false)
        override val unreadableRecordCountFlow = MutableStateFlow(0)

        var persistedActiveWorkout: ActiveWorkout? = initialActiveWorkout
        var persistedSettings: AppSettings = initialSettings
        val activeWorkoutSaves = mutableListOf<ActiveWorkout?>()
        val savedCompletedWorkouts = mutableListOf<CompletedWorkout>()
        val registerCustomExerciseCalls = mutableListOf<List<String>>()
        private var askedNotifPerm = false

        override suspend fun initializeAndBackfill() = Unit

        override suspend fun getActiveWorkoutOnce(): ActiveWorkout? = persistedActiveWorkout

        override suspend fun getSettingsOnce(): AppSettings = persistedSettings

        override suspend fun registerCustomExercises(names: Iterable<String>) {
            registerCustomExerciseCalls.add(names.toList())
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
            savedCompletedWorkouts.add(workout)
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

        override suspend fun saveActiveWorkout(workout: ActiveWorkout?) {
            if (saveDelayMs > 0L) delay(saveDelayMs)
            persistedActiveWorkout = workout
            activeWorkoutSaves.add(workout)
            activeWorkoutFlowMutable.value = workout
        }

        override suspend fun saveSettings(settings: AppSettings) {
            if (saveDelayMs > 0L) delay(saveDelayMs)
            persistedSettings = settings
            settingsFlow.value = settings
        }

        override suspend fun saveRestTimer(snapshot: RestTimerSnapshot) {
            restTimerFlow.value = snapshot
        }

        override suspend fun claimRestTimerCompletion(
            expectedEndTimeMs: Long,
            nowMs: Long
        ): RestClaimResult {
            val cur = restTimerFlow.value
            if (cur.completed || cur.endTimeMs != expectedEndTimeMs) {
                return RestClaimResult(RestClaimStatus.ALREADY_HANDLED, cur.exerciseName)
            }
            restTimerFlow.value = cur.copy(endTimeMs = null, completed = true)
            return RestClaimResult(RestClaimStatus.COMPLETED_WITH_ALERT, cur.exerciseName)
        }

        override suspend fun adjustRestTimer(deltaSeconds: Int, nowMs: Long): RestTimerSnapshot {
            val cur = restTimerFlow.value
            val adj = RestTimerRules.adjustRestEndTime(
                cur.endTimeMs,
                cur.totalDurationSec,
                cur.completed,
                deltaSeconds,
                nowMs
            )
            val next = RestTimerSnapshot(
                endTimeMs = adj.endTimeMs,
                totalDurationSec = adj.totalDurationSec,
                exerciseName = if (adj.endTimeMs == null) "" else cur.exerciseName,
                completed = adj.completed
            )
            restTimerFlow.value = next
            return next
        }

        override suspend fun hasAskedNotificationPermission(): Boolean = askedNotifPerm

        override suspend fun markAskedNotificationPermission() {
            askedNotifPerm = true
        }

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
                persistedActiveWorkout = null
                activeWorkoutFlowMutable.value = null
                restTimerFlow.value = RestTimerSnapshot()
                plan.settingsToSave?.let {
                    persistedSettings = it
                    settingsFlow.value = it
                }
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
            persistedActiveWorkout = null
            activeWorkoutFlowMutable.value = null
            restTimerFlow.value = RestTimerSnapshot()
            persistedSettings = AppSettings()
            settingsFlow.value = AppSettings()
        }
    }

    private class FakeWorkoutPlatformHooks : WorkoutPlatformHooks {
        private var onStopCallback: (() -> Unit)? = null
        var serviceStartCount = 0
        var serviceStopCount = 0

        override fun triggerLightTick() = Unit
        override fun triggerRestCompleteVibration() = Unit
        override suspend fun playRestCompleteChime() = Unit
        override fun startOrSyncWorkoutService() { serviceStartCount += 1 }
        override fun stopWorkoutService() { serviceStopCount += 1 }
        override fun postRestDoneHeadsUpNotification(message: String) = Unit
        override fun cancelRestDoneNotification() = Unit
        override fun shouldPromptNotificationPermission(): Boolean = false

        override fun registerProcessStopObserver(onStop: () -> Unit): () -> Unit {
            onStopCallback = onStop
            return { onStopCallback = null }
        }

        fun triggerProcessStop() {
            onStopCallback?.invoke()
        }
    }
}
