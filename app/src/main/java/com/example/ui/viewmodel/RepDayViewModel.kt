package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.RepDayDataSource
import com.example.data.RepDayRepository
import com.example.data.SeedData
import com.example.data.local.RepDayDataStore
import com.example.data.local.RepDayDatabase
import com.example.data.local.RestClaimStatus
import com.example.data.local.RestTimerSnapshot
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlateCalculatorSettings
import com.example.data.model.Routine
import com.example.data.model.RoutineSortField
import com.example.data.model.SetTag
import com.example.data.model.SortDirection
import com.example.data.model.ThemeConfig
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.domain.ActiveWorkoutMutations
import com.example.domain.BackupImportMode
import com.example.domain.BackupValidationResult
import com.example.domain.PreviousExerciseSession
import com.example.domain.RepDayBackupRules
import com.example.domain.RepDayRules
import com.example.domain.RestCompletionOutcome
import com.example.domain.RestTimerRules
import com.example.domain.SampleDataRules
import com.example.domain.ToggleSetOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PrimaryTab {
    ROUTINES,
    PLANS,
    HISTORY
}

data class HistoryRoutineFilter(
    val routineId: String? = null,
    val routineName: String,
    val isLiveWorkoutsOnly: Boolean = false
)

data class RoutineEditorRequest(
    val initialRoutine: Routine? = null,
    val isDuplicateDraft: Boolean = false,
    val isFromHistoryDraft: Boolean = false,
    val prefilledName: String? = null,
    val linkToPlanId: String? = null,
    val linkToBlockId: String? = null,
    val linkToSlotId: String? = null,
    val linkToHistoryWorkoutId: String? = null
)

sealed class PendingWorkoutStart {
    data class FromRoutine(val routine: Routine) : PendingWorkoutStart()
    data object LiveWorkout : PendingWorkoutStart()
}

data class PlanEditorRequest(
    val initialPlan: TrainingPlan? = null,
    val isDuplicateDraft: Boolean = false
)

data class RestTimerUiState(
    val isActive: Boolean = false,
    val isComplete: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 90,
    val exerciseName: String = ""
)

data class EffortPromptTarget(
    val exerciseId: String,
    val setId: String,
    val setNumber: Int,
    val exerciseName: String,
    val targetRpe: String?,
    val currentRpe: Double?
)

data class RepDayUiState(
    val selectedTab: PrimaryTab = PrimaryTab.ROUTINES,
    val routines: List<Routine> = emptyList(),
    val plans: List<TrainingPlan> = emptyList(),
    val history: List<CompletedWorkout> = emptyList(),
    val lastSessionByExercise: Map<String, PreviousExerciseSession> = emptyMap(),
    val customExercises: List<String> = emptyList(),
    val exerciseLibrary: List<String> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val activeWorkout: ActiveWorkout? = null,
    val isActiveWorkoutFullScreen: Boolean = false,
    val historyFilter: HistoryRoutineFilter? = null,
    val routineEditor: RoutineEditorRequest? = null,
    val planEditor: PlanEditorRequest? = null,
    val editingHistoryWorkout: CompletedWorkout? = null,
    val showSettingsSheet: Boolean = false,
    val pendingUnitChange: WeightUnit? = null,
    val pendingEffortPrompt: EffortPromptTarget? = null,
    val pendingBackupImport: BackupValidationResult.Valid? = null,
    val backupImportError: String? = null,
    val showSampleHistoryBanner: Boolean = false,
    val sampleHistoryCount: Int = 0,
    val unreadableRecordCount: Int = 0,
    val showUnreadableRecordsNotice: Boolean = false,
    val plateCalcSeedWeight: Double? = null,
    val showPlateCalculator: Boolean = false,
    val pendingReplaceWorkout: PendingWorkoutStart? = null,
    val showNotificationPermissionPrompt: Boolean = false,
    val toastMessage: String? = null,
    val isInitialized: Boolean = false
)

class RepDayViewModel(
    private val repository: RepDayDataSource,
    private val platform: WorkoutPlatformHooks,
    private val clock: () -> Long = System::currentTimeMillis,
    private val persistDebounceMs: Long = ACTIVE_WORKOUT_PERSIST_DEBOUNCE_MS,
    private val startClockLoop: Boolean = true,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    // In-memory sources of truth: mutations read & update these synchronously instead of uiState.value
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    private val _plans = MutableStateFlow<List<TrainingPlan>>(emptyList())
    private val _history = MutableStateFlow<List<CompletedWorkout>>(emptyList())
    private val _lastSessionByExercise = MutableStateFlow<Map<String, PreviousExerciseSession>>(emptyMap())
    private val _customExercises = MutableStateFlow<List<String>>(emptyList())
    private val _settings = MutableStateFlow(AppSettings())
    private val _activeWorkout = MutableStateFlow<ActiveWorkout?>(null)
    private val _dismissedSampleHistoryBanner = MutableStateFlow(false)
    private val _unreadableRecordCount = MutableStateFlow(0)

    val activeWorkout: StateFlow<ActiveWorkout?> = _activeWorkout.asStateFlow()

    private val _localState = MutableStateFlow(LocalUiState())
    private val _restSnapshot = MutableStateFlow(RestTimerSnapshot())
    private val _restUi = MutableStateFlow(RestTimerUiState())
    private val _elapsedSeconds = MutableStateFlow(0L)

    // Exposed as separate StateFlows so tick updates only recompose leaf clock/timer composables
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()
    val restTimer: StateFlow<RestTimerUiState> = _restUi.asStateFlow()

    private var toastJob: Job? = null
    private var lastChimedEndTimeMs: Long? = null

    private var hasLocalActiveWorkoutMutation: Boolean = false
    private var activeWorkoutVersion: Long = 0L
    private var lastPersistedWorkoutVersion: Long = 0L
    private var activeWorkoutDebounceJob: Job? = null
    private val activeWorkoutPersistMutex = Mutex()

    private var hasLocalSettingsMutation: Boolean = false
    private var settingsVersion: Long = 0L
    private var lastPersistedSettingsVersion: Long = 0L
    private var settingsDebounceJob: Job? = null
    private val settingsPersistMutex = Mutex()

    private var removeProcessStopObserver: (() -> Unit)? = null

    private data class LocalUiState(
        val selectedTab: PrimaryTab = PrimaryTab.ROUTINES,
        val isActiveWorkoutFullScreen: Boolean = false,
        val historyFilter: HistoryRoutineFilter? = null,
        val routineEditor: RoutineEditorRequest? = null,
        val planEditor: PlanEditorRequest? = null,
        val editingHistoryWorkout: CompletedWorkout? = null,
        val showSettingsSheet: Boolean = false,
        val pendingUnitChange: WeightUnit? = null,
        val pendingEffortPrompt: EffortPromptTarget? = null,
        val pendingBackupImport: BackupValidationResult.Valid? = null,
        val backupImportError: String? = null,
        val showPlateCalculator: Boolean = false,
        val plateCalcSeedWeight: Double? = null,
        val pendingReplaceWorkout: PendingWorkoutStart? = null,
        val showNotificationPermissionPrompt: Boolean = false,
        val pendingOpenActiveWorkoutFromNotification: Boolean = false,
        val dismissedUnreadableCount: Int = 0,
        val toastMessage: String? = null,
        val isInitialized: Boolean = false
    )

    private data class CoreDataBundle(
        val routines: List<Routine>,
        val plans: List<TrainingPlan>,
        val history: List<CompletedWorkout>,
        val customExercises: List<String>,
        val settings: AppSettings,
        val activeWorkout: ActiveWorkout?
    )

    private val coreDataFlow = combine(
        _routines,
        _plans,
        _history,
        _customExercises,
        _settings
    ) { routines, plans, history, customExercises, settings ->
        CoreDataBundle(routines, plans, history, customExercises, settings, null)
    }.combine(_activeWorkout) { bundle, active ->
        bundle.copy(activeWorkout = active)
    }

    val uiState: StateFlow<RepDayUiState> = combine(
        coreDataFlow,
        _lastSessionByExercise,
        _dismissedSampleHistoryBanner,
        _unreadableRecordCount,
        _localState
    ) { core, lastByExercise, dismissedBanner, unreadableCount, local ->
        val sampleHistoryCount = SampleDataRules.findSeedSampleHistoryIds(core.history).size
        RepDayUiState(
            selectedTab = local.selectedTab,
            routines = core.routines,
            plans = core.plans,
            history = core.history,
            lastSessionByExercise = lastByExercise,
            customExercises = core.customExercises,
            exerciseLibrary = RepDayRules.buildUnifiedLibrary(core.customExercises),
            settings = core.settings,
            activeWorkout = core.activeWorkout,
            isActiveWorkoutFullScreen = local.isActiveWorkoutFullScreen && core.activeWorkout != null,
            historyFilter = local.historyFilter,
            routineEditor = local.routineEditor,
            planEditor = local.planEditor,
            editingHistoryWorkout = local.editingHistoryWorkout,
            showSettingsSheet = local.showSettingsSheet,
            pendingUnitChange = local.pendingUnitChange,
            pendingEffortPrompt = local.pendingEffortPrompt,
            pendingBackupImport = local.pendingBackupImport,
            backupImportError = local.backupImportError,
            showSampleHistoryBanner = SampleDataRules.shouldShowSampleHistoryBanner(
                history = core.history,
                bannerDismissed = dismissedBanner
            ),
            sampleHistoryCount = sampleHistoryCount,
            unreadableRecordCount = unreadableCount,
            showUnreadableRecordsNotice = unreadableCount > 0 && unreadableCount > local.dismissedUnreadableCount,
            showPlateCalculator = local.showPlateCalculator,
            plateCalcSeedWeight = local.plateCalcSeedWeight,
            pendingReplaceWorkout = local.pendingReplaceWorkout,
            showNotificationPermissionPrompt = local.showNotificationPermissionPrompt,
            toastMessage = local.toastMessage,
            isInitialized = local.isInitialized
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RepDayUiState()
    )

    init {
        removeProcessStopObserver = platform.registerProcessStopObserver {
            flushPendingPersistenceAsync()
        }

        viewModelScope.launch { repository.routinesFlow.collect { _routines.value = it } }
        viewModelScope.launch { repository.plansFlow.collect { _plans.value = it } }
        viewModelScope.launch {
            repository.historyFlow
                .map { history -> history to RepDayRules.buildLastSessionByExercise(history) }
                .flowOn(computeDispatcher)
                .collect { (history, lastByExercise) ->
                    _history.value = history
                    _lastSessionByExercise.value = lastByExercise
                }
        }
        viewModelScope.launch { repository.customExercisesFlow.collect { _customExercises.value = it } }
        viewModelScope.launch {
            repository.dismissedSampleHistoryBannerFlow.collect { _dismissedSampleHistoryBanner.value = it }
        }
        viewModelScope.launch {
            repository.unreadableRecordCountFlow.collect { count ->
                _unreadableRecordCount.value = count
                if (count == 0) {
                    _localState.update { it.copy(dismissedUnreadableCount = 0) }
                }
            }
        }

        // Load persisted ActiveWorkout and AppSettings once at startup into in-memory state
        viewModelScope.launch {
            repository.initializeAndBackfill()
            val savedSettings = repository.getSettingsOnce()
            if (!hasLocalSettingsMutation) {
                _settings.value = savedSettings
            }
            val savedWorkout = repository.getActiveWorkoutOnce()
            if (!hasLocalActiveWorkoutMutation) {
                _activeWorkout.value = savedWorkout
            }
            _localState.update { it.copy(isInitialized = true) }

            val active = _activeWorkout.value
            if (active != null) {
                platform.startOrSyncWorkoutService()
                if (_localState.value.pendingOpenActiveWorkoutFromNotification) {
                    _localState.update {
                        it.copy(
                            isActiveWorkoutFullScreen = true,
                            pendingOpenActiveWorkoutFromNotification = false
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            _activeWorkout
                .map { it?.id }
                .distinctUntilChanged()
                .collect { activeId ->
                    if (activeId != null) {
                        platform.startOrSyncWorkoutService()
                        if (_localState.value.pendingOpenActiveWorkoutFromNotification) {
                            _localState.update {
                                it.copy(
                                    isActiveWorkoutFullScreen = true,
                                    pendingOpenActiveWorkoutFromNotification = false
                                )
                            }
                        }
                    } else if (_localState.value.isInitialized) {
                        platform.stopWorkoutService()
                    }
                }
        }

        viewModelScope.launch {
            repository.restTimerFlow.collect { snap ->
                _restSnapshot.value = snap
                syncRestUiFromSnapshot(snap)
            }
        }

        if (startClockLoop) {
            startClocks()
        }
    }

    override fun onCleared() {
        removeProcessStopObserver?.invoke()
        removeProcessStopObserver = null
        super.onCleared()
    }

    // ---- Background Persistence & Flushing ----

    private fun mutateActiveWorkout(transform: (ActiveWorkout) -> ActiveWorkout) {
        var changed = false
        _activeWorkout.update { current ->
            if (current == null) {
                null
            } else {
                val next = transform(current)
                if (next != current) changed = true
                next
            }
        }
        if (changed) {
            hasLocalActiveWorkoutMutation = true
            activeWorkoutVersion += 1L
            scheduleActiveWorkoutPersist()
        }
    }

    private fun scheduleActiveWorkoutPersist() {
        activeWorkoutDebounceJob?.cancel()
        activeWorkoutDebounceJob = viewModelScope.launch {
            delay(persistDebounceMs)
            flushActiveWorkoutNow()
        }
    }

    private suspend fun flushActiveWorkoutNow() {
        activeWorkoutPersistMutex.withLock {
            while (lastPersistedWorkoutVersion < activeWorkoutVersion) {
                val targetVersion = activeWorkoutVersion
                val snapshot = _activeWorkout.value
                repository.saveActiveWorkout(snapshot)
                lastPersistedWorkoutVersion = targetVersion
            }
        }
    }

    private fun mutateSettings(transform: (AppSettings) -> AppSettings) {
        var changed = false
        _settings.update { current ->
            val next = transform(current)
            if (next != current) changed = true
            next
        }
        if (changed) {
            hasLocalSettingsMutation = true
            settingsVersion += 1L
            scheduleSettingsPersist()
        }
    }

    private fun scheduleSettingsPersist() {
        settingsDebounceJob?.cancel()
        settingsDebounceJob = viewModelScope.launch {
            delay(SETTINGS_PERSIST_DEBOUNCE_MS)
            flushSettingsNow()
        }
    }

    private suspend fun flushSettingsNow() {
        settingsPersistMutex.withLock {
            while (lastPersistedSettingsVersion < settingsVersion) {
                val targetVersion = settingsVersion
                val snapshot = _settings.value
                repository.saveSettings(snapshot)
                lastPersistedSettingsVersion = targetVersion
            }
        }
    }

    fun flushPendingPersistenceAsync() {
        activeWorkoutDebounceJob?.cancel()
        activeWorkoutDebounceJob = null
        settingsDebounceJob?.cancel()
        settingsDebounceJob = null
        viewModelScope.launch {
            flushActiveWorkoutNow()
            flushSettingsNow()
        }
    }

    // ---- Clocks & Rest Timer Sync ----

    private fun startClocks() {
        viewModelScope.launch {
            while (isActive) {
                val now = clock()
                val active = _activeWorkout.value
                if (active != null) {
                    _elapsedSeconds.value = ((now - active.startedAt) / 1000L).coerceAtLeast(0L)
                } else {
                    _elapsedSeconds.value = 0L
                }
                val snap = _restSnapshot.value
                if (snap.endTimeMs != null || snap.completed) {
                    syncRestUiFromSnapshot(snap)
                }
                delay(250L)
            }
        }
    }

    private fun syncRestUiFromSnapshot(snap: RestTimerSnapshot) {
        val now = clock()
        when (RestTimerRules.evaluateRestTimer(snap.endTimeMs, snap.completed, now)) {
            RestCompletionOutcome.INACTIVE -> {
                _restUi.value = RestTimerUiState(
                    isActive = false,
                    isComplete = false,
                    remainingSeconds = 0,
                    totalSeconds = _settings.value.defaultRestSeconds,
                    exerciseName = ""
                )
            }
            RestCompletionOutcome.STILL_RUNNING -> {
                val rem = RepDayRules.remainingRestSeconds(snap.endTimeMs, now)
                _restUi.value = RestTimerUiState(
                    isActive = true,
                    isComplete = false,
                    remainingSeconds = rem,
                    totalSeconds = snap.totalDurationSec.coerceAtLeast(1),
                    exerciseName = snap.exerciseName
                )
            }
            RestCompletionOutcome.COMPLETE_WITH_ALERT,
            RestCompletionOutcome.COMPLETE_SILENT_STALE -> {
                _restUi.value = RestTimerUiState(
                    isActive = true,
                    isComplete = true,
                    remainingSeconds = 0,
                    totalSeconds = snap.totalDurationSec.coerceAtLeast(1),
                    exerciseName = snap.exerciseName
                )
                val targetEndMs = snap.endTimeMs
                if (!snap.completed && targetEndMs != null && targetEndMs != lastChimedEndTimeMs) {
                    lastChimedEndTimeMs = targetEndMs
                    viewModelScope.launch {
                        val claim = repository.claimRestTimerCompletion(
                            expectedEndTimeMs = targetEndMs,
                            nowMs = now
                        )
                        if (claim.status == RestClaimStatus.COMPLETED_WITH_ALERT) {
                            val currentSettings = _settings.value
                            val nextSet = RestTimerRules.findNextIncompleteSet(
                                _activeWorkout.value,
                                claim.exerciseName
                            )
                            val message = RestTimerRules.formatRestDoneNotificationText(
                                nextSet,
                                claim.exerciseName
                            )
                            platform.postRestDoneHeadsUpNotification(message)
                            if (currentSettings.vibrateOnRestEnd) {
                                platform.triggerRestCompleteVibration()
                            }
                            if (currentSettings.soundEnabled) {
                                platform.playRestCompleteChime()
                            }
                        }
                    }
                }
            }
        }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _localState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2600L)
            _localState.update { it.copy(toastMessage = null) }
        }
    }

    fun selectTab(tab: PrimaryTab) {
        _localState.update { it.copy(selectedTab = tab) }
    }

    fun dismissUnreadableRecordsNotice() {
        _localState.update { it.copy(dismissedUnreadableCount = _unreadableRecordCount.value) }
    }

    fun openHistoryForRoutine(routineId: String?, routineName: String) {
        _localState.update {
            it.copy(
                selectedTab = PrimaryTab.HISTORY,
                historyFilter = HistoryRoutineFilter(routineId, routineName)
            )
        }
    }

    fun setHistoryFilter(filter: HistoryRoutineFilter?) {
        _localState.update { it.copy(historyFilter = filter) }
    }

    fun openSettings() {
        _localState.update { it.copy(showSettingsSheet = true) }
    }

    fun closeSettings() {
        _localState.update { it.copy(showSettingsSheet = false) }
    }

    fun openPlateCalculator(seedWeight: Double? = null) {
        _localState.update {
            it.copy(showPlateCalculator = true, plateCalcSeedWeight = seedWeight)
        }
    }

    fun closePlateCalculator() {
        _localState.update { it.copy(showPlateCalculator = false) }
    }

    // ---- Routines CRUD & Sorting ----

    fun updateRoutineSortField(field: RoutineSortField) {
        mutateSettings { current ->
            val sort = current.routineSort
            val newConfig = if (sort.field == field) {
                sort.copy(
                    direction = if (sort.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                )
            } else {
                val defaultDir = if (field == RoutineSortField.NAME) SortDirection.ASC else SortDirection.DESC
                sort.copy(field = field, direction = defaultDir)
            }
            current.copy(routineSort = newConfig)
        }
    }

    fun toggleRoutineSortDirection() {
        mutateSettings { current ->
            val sort = current.routineSort
            val toggled = if (sort.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
            current.copy(routineSort = sort.copy(direction = toggled))
        }
    }

    fun openNewRoutineEditor() {
        _localState.update { it.copy(routineEditor = RoutineEditorRequest()) }
    }

    fun openEditRoutineEditor(routine: Routine) {
        _localState.update { it.copy(routineEditor = RoutineEditorRequest(initialRoutine = routine)) }
    }

    fun openDuplicateRoutineEditor(routine: Routine) {
        val copyName = RepDayRules.nextDuplicateName(routine.name, _routines.value.map { it.name })
        val nowMs = clock()
        val now = RepDayRules.formatIso(nowMs)
        val draft = routine.copy(
            id = RepDayRules.newId("rt"),
            name = copyName,
            createdAt = now,
            updatedAt = now,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
        _localState.update {
            it.copy(routineEditor = RoutineEditorRequest(initialRoutine = draft, isDuplicateDraft = true))
        }
    }

    fun openBuildRoutineForPlanSlot(planId: String, blockId: String, slotId: String, placeholderLabel: String) {
        _localState.update {
            it.copy(
                routineEditor = RoutineEditorRequest(
                    prefilledName = placeholderLabel,
                    linkToPlanId = planId,
                    linkToBlockId = blockId,
                    linkToSlotId = slotId
                )
            )
        }
    }

    fun openSaveHistoryAsRoutineEditor(workout: CompletedWorkout) {
        val draft = RepDayRules.buildRoutineDraftFromHistory(
            workout = workout,
            defaultRest = _settings.value.defaultRestSeconds,
            prefillName = !RepDayRules.isLiveWorkoutName(workout.routineName)
        )
        _localState.update {
            it.copy(
                routineEditor = RoutineEditorRequest(
                    initialRoutine = draft,
                    isFromHistoryDraft = true,
                    linkToHistoryWorkoutId = workout.id
                )
            )
        }
    }

    fun closeRoutineEditor() {
        _localState.update { it.copy(routineEditor = null) }
    }

    fun saveRoutine(routine: Routine, request: RoutineEditorRequest) {
        viewModelScope.launch {
            repository.saveRoutine(routine)
            if (request.linkToHistoryWorkoutId != null) {
                val targetHistory = _history.value.find { it.id == request.linkToHistoryWorkoutId }
                if (targetHistory != null) {
                    repository.saveCompletedWorkout(
                        targetHistory.copy(
                            routineId = routine.id,
                            routineName = routine.name
                        )
                    )
                }
                showToast("Saved \"${routine.name}\" as a routine")
            } else if (request.linkToPlanId != null && request.linkToBlockId != null && request.linkToSlotId != null) {
                val targetPlan = _plans.value.find { it.id == request.linkToPlanId }
                if (targetPlan != null) {
                    val updatedBlocks = targetPlan.blocks.map { block ->
                        if (block.id == request.linkToBlockId) {
                            block.copy(
                                workouts = block.workouts.map { slot ->
                                    if (slot.id == request.linkToSlotId) {
                                        slot.copy(routineId = routine.id, label = routine.name)
                                    } else slot
                                }
                            )
                        } else block
                    }
                    repository.savePlan(targetPlan.copy(blocks = updatedBlocks))
                    showToast("Created & linked \"${routine.name}\"")
                } else {
                    showToast("Saved \"${routine.name}\"")
                }
            } else {
                showToast("Saved \"${routine.name}\"")
            }
            _localState.update { it.copy(routineEditor = null) }
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            repository.deleteRoutine(routine.id)
            showToast("Routine deleted")
        }
    }

    // ---- Plans CRUD ----

    fun openNewPlanEditor() {
        _localState.update { it.copy(planEditor = PlanEditorRequest()) }
    }

    fun openEditPlanEditor(plan: TrainingPlan) {
        _localState.update { it.copy(planEditor = PlanEditorRequest(initialPlan = plan)) }
    }

    fun openDuplicatePlanEditor(plan: TrainingPlan) {
        val copyName = RepDayRules.nextDuplicateName(plan.name, _plans.value.map { it.name })
        val nowMs = clock()
        val now = RepDayRules.formatIso(nowMs)
        val draft = plan.copy(
            id = RepDayRules.newId("plan"),
            name = copyName,
            isActive = false,
            startedAt = null,
            pausedAt = null,
            pausedDurationMs = 0L,
            createdAt = now,
            updatedAt = now,
            updatedAtMs = nowMs
        )
        _localState.update {
            it.copy(planEditor = PlanEditorRequest(initialPlan = draft, isDuplicateDraft = true))
        }
    }

    fun closePlanEditor() {
        _localState.update { it.copy(planEditor = null) }
    }

    fun savePlan(plan: TrainingPlan) {
        viewModelScope.launch {
            repository.savePlan(plan)
            showToast("Saved \"${plan.name}\"")
            _localState.update { it.copy(planEditor = null) }
        }
    }

    fun deletePlan(plan: TrainingPlan) {
        viewModelScope.launch {
            repository.deletePlan(plan.id)
            showToast("Plan deleted")
        }
    }

    fun togglePlanActive(plan: TrainingPlan) {
        viewModelScope.launch {
            val all = _plans.value
            val nowMs = clock()
            if (plan.isActive) {
                repository.savePlan(RepDayRules.pausePlan(plan, nowMs))
                showToast("Paused \"${plan.name}\"")
            } else {
                val wasPaused = plan.startedAt != null
                val updated = all.map { p ->
                    when {
                        p.id == plan.id -> RepDayRules.resumeOrActivatePlan(p, nowMs)
                        p.isActive -> RepDayRules.pausePlan(p, nowMs)
                        else -> p
                    }
                }
                repository.saveAllPlans(updated)
                showToast(if (wasPaused) "Resumed \"${plan.name}\"" else "Activated \"${plan.name}\"")
            }
        }
    }

    fun restartPlanFromWeekOne(plan: TrainingPlan) {
        viewModelScope.launch {
            val all = _plans.value
            val nowMs = clock()
            val updated = all.map { p ->
                when {
                    p.id == plan.id -> RepDayRules.restartPlan(p, nowMs)
                    p.isActive -> RepDayRules.pausePlan(p, nowMs)
                    else -> p
                }
            }
            if (updated.any { it.id == plan.id }) {
                repository.saveAllPlans(updated)
            } else {
                repository.savePlan(RepDayRules.restartPlan(plan, nowMs))
            }
            showToast("Plan restarted at Week 1")
        }
    }

    fun linkRoutineToPlanSlot(planId: String, blockId: String, slotId: String, routine: Routine) {
        viewModelScope.launch {
            val plan = _plans.value.find { it.id == planId } ?: return@launch
            val updatedBlocks = plan.blocks.map { block ->
                if (block.id == blockId) {
                    block.copy(
                        workouts = block.workouts.map { slot ->
                            if (slot.id == slotId) {
                                slot.copy(routineId = routine.id, label = routine.name)
                            } else slot
                        }
                    )
                } else block
            }
            repository.savePlan(plan.copy(blocks = updatedBlocks))
            showToast("Linked \"${routine.name}\"")
        }
    }

    // ---- Active Workout Lifecycle ----

    fun requestStartLiveWorkout() {
        if (_activeWorkout.value != null) {
            _localState.update { it.copy(pendingReplaceWorkout = PendingWorkoutStart.LiveWorkout) }
        } else {
            startWorkoutImmediately(
                RepDayRules.buildEmptyLiveWorkout(startedAtMs = clock(), unit = _settings.value.unit),
                flushPrevious = false
            )
        }
    }

    fun requestStartRoutine(routine: Routine) {
        if (_activeWorkout.value != null) {
            _localState.update { it.copy(pendingReplaceWorkout = PendingWorkoutStart.FromRoutine(routine)) }
        } else {
            val workout = RepDayRules.buildActiveWorkoutFromRoutine(
                routine = routine,
                defaultRest = _settings.value.defaultRestSeconds,
                unit = _settings.value.unit
            )
            startWorkoutImmediately(workout, flushPrevious = false)
        }
    }

    fun requestRepeatHistoryWorkout(workout: CompletedWorkout) {
        val matchingSaved = _routines.value.find {
            (workout.routineId != null && it.id == workout.routineId) ||
                RepDayRules.exerciseKey(it.name) == RepDayRules.exerciseKey(workout.routineName)
        }
        val target = matchingSaved ?: RepDayRules.buildSyntheticRoutineFromHistory(
            workout,
            _settings.value.defaultRestSeconds
        )
        requestStartRoutine(target)
    }

    fun confirmReplaceActiveWorkout() {
        val pending = _localState.value.pendingReplaceWorkout ?: return
        _localState.update { it.copy(pendingReplaceWorkout = null) }
        val replacement = when (pending) {
            is PendingWorkoutStart.FromRoutine -> RepDayRules.buildActiveWorkoutFromRoutine(
                routine = pending.routine,
                defaultRest = _settings.value.defaultRestSeconds,
                unit = _settings.value.unit
            )
            PendingWorkoutStart.LiveWorkout -> RepDayRules.buildEmptyLiveWorkout(
                startedAtMs = clock(),
                unit = _settings.value.unit
            )
        }
        startWorkoutImmediately(replacement, flushPrevious = true)
    }

    fun cancelReplaceActiveWorkout() {
        _localState.update { it.copy(pendingReplaceWorkout = null) }
    }

    private fun startWorkoutImmediately(workout: ActiveWorkout, flushPrevious: Boolean) {
        activeWorkoutDebounceJob?.cancel()
        activeWorkoutDebounceJob = null
        val previousPending = if (flushPrevious && lastPersistedWorkoutVersion < activeWorkoutVersion) {
            _activeWorkout.value
        } else {
            null
        }

        hasLocalActiveWorkoutMutation = true
        _activeWorkout.value = workout
        activeWorkoutVersion += 1L
        val targetVersion = activeWorkoutVersion

        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        val emptyRest = RestTimerSnapshot()
        _restSnapshot.value = emptyRest
        syncRestUiFromSnapshot(emptyRest)
        _localState.update {
            it.copy(
                isActiveWorkoutFullScreen = true,
                pendingEffortPrompt = null
            )
        }

        viewModelScope.launch {
            activeWorkoutPersistMutex.withLock {
                if (previousPending != null) {
                    repository.saveActiveWorkout(previousPending)
                }
                repository.saveRestTimer(emptyRest)
                val latestVersion = activeWorkoutVersion
                repository.saveActiveWorkout(_activeWorkout.value)
                lastPersistedWorkoutVersion = maxOf(targetVersion, latestVersion)
            }
            // Start (or re-sync) the service only after the new workout is on disk, so the
            // service's first read sees it. The service also tolerates an early empty read.
            platform.startOrSyncWorkoutService()
            maybePromptNotificationPermissionOnWorkoutStart()
        }
    }

    private suspend fun maybePromptNotificationPermissionOnWorkoutStart() {
        if (!platform.shouldPromptNotificationPermission()) return
        if (repository.hasAskedNotificationPermission()) return
        _localState.update { it.copy(showNotificationPermissionPrompt = true) }
    }

    fun dismissNotificationPermissionPrompt() {
        _localState.update { it.copy(showNotificationPermissionPrompt = false) }
        viewModelScope.launch {
            repository.markAskedNotificationPermission()
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _localState.update { it.copy(showNotificationPermissionPrompt = false) }
        viewModelScope.launch {
            repository.markAskedNotificationPermission()
            if (granted && _activeWorkout.value != null) {
                platform.startOrSyncWorkoutService()
            }
        }
    }

    fun openActiveWorkoutFromNotification() {
        if (_activeWorkout.value != null) {
            _localState.update {
                it.copy(
                    isActiveWorkoutFullScreen = true,
                    pendingOpenActiveWorkoutFromNotification = false
                )
            }
        } else {
            _localState.update { it.copy(pendingOpenActiveWorkoutFromNotification = true) }
        }
    }

    fun minimizeActiveWorkout() {
        _localState.update { it.copy(isActiveWorkoutFullScreen = false) }
    }

    fun resumeActiveWorkout() {
        if (_activeWorkout.value != null) {
            _localState.update { it.copy(isActiveWorkoutFullScreen = true) }
        }
    }

    fun discardActiveWorkout() {
        activeWorkoutDebounceJob?.cancel()
        activeWorkoutDebounceJob = null
        val previousPending = if (lastPersistedWorkoutVersion < activeWorkoutVersion) {
            _activeWorkout.value
        } else {
            null
        }

        hasLocalActiveWorkoutMutation = true
        _activeWorkout.value = null
        activeWorkoutVersion += 1L
        val targetVersion = activeWorkoutVersion

        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        platform.stopWorkoutService()
        val emptyRest = RestTimerSnapshot()
        _restSnapshot.value = emptyRest
        syncRestUiFromSnapshot(emptyRest)
        _localState.update {
            it.copy(
                isActiveWorkoutFullScreen = false,
                pendingEffortPrompt = null,
                pendingUnitChange = null
            )
        }
        showToast("Workout discarded")

        viewModelScope.launch {
            activeWorkoutPersistMutex.withLock {
                if (previousPending != null) {
                    repository.saveActiveWorkout(previousPending)
                }
                val latestVersion = activeWorkoutVersion
                repository.saveActiveWorkout(_activeWorkout.value)
                lastPersistedWorkoutVersion = maxOf(targetVersion, latestVersion)
            }
            repository.saveRestTimer(emptyRest)
        }
    }

    // ---- Synchronous In-Memory Active Workout Mutations (Debounced Persistence) ----

    fun updateActiveWorkoutNotes(notes: String) {
        mutateActiveWorkout { ActiveWorkoutMutations.updateNotes(it, notes) }
    }

    fun updateSetWeight(exerciseId: String, setId: String, weight: String) {
        mutateActiveWorkout { ActiveWorkoutMutations.updateSetWeight(it, exerciseId, setId, weight) }
    }

    fun updateSetReps(exerciseId: String, setId: String, reps: String) {
        mutateActiveWorkout { ActiveWorkoutMutations.updateSetReps(it, exerciseId, setId, reps) }
    }

    fun fillSetFromPrevious(exerciseId: String, setId: String, prevWeight: Double, prevReps: Int) {
        if (_activeWorkout.value == null) return
        platform.triggerLightTick()
        mutateActiveWorkout {
            ActiveWorkoutMutations.fillSetFromPrevious(it, exerciseId, setId, prevWeight, prevReps)
        }
    }

    /**
     * Toggles a set's completion status synchronously in memory. Returns true if the exercise
     * just became 100% completed so the UI can auto-collapse and auto-advance.
     */
    fun toggleSetCompleted(
        exerciseId: String,
        setId: String,
        prevSet: CompletedSet?,
        prevSetUnit: WeightUnit? = null
    ): Boolean {
        val currentSettings = _settings.value
        val defaultRest = currentSettings.defaultRestSeconds
        val now = clock()
        var outcome: ToggleSetOutcome? = null

        _activeWorkout.update { current ->
            if (current == null) {
                null
            } else {
                val res = ActiveWorkoutMutations.toggleSetCompleted(
                    workout = current,
                    exerciseId = exerciseId,
                    setId = setId,
                    prevSet = prevSet,
                    defaultRestSeconds = defaultRest,
                    nowMs = now,
                    prevSetUnit = prevSetUnit,
                    sessionUnit = current.resolvedUnit(currentSettings.unit)
                )
                outcome = res
                res?.workout ?: current
            }
        }

        val resolved = outcome ?: return false
        hasLocalActiveWorkoutMutation = true
        activeWorkoutVersion += 1L
        scheduleActiveWorkoutPersist()

        platform.triggerLightTick()

        if (resolved.shouldDismissRest) {
            dismissRestTimer()
        } else if (resolved.startRestSeconds != null) {
            startRestTimer(resolved.startRestSeconds, resolved.exerciseName)
            // Skip the prompt when an effort value was already chosen for this set.
            if (RepDayRules.shouldPromptEffortAfterSet(currentSettings.askEffortAfterSet, resolved.currentRpe)) {
                _localState.update {
                    it.copy(
                        pendingEffortPrompt = EffortPromptTarget(
                            exerciseId = exerciseId,
                            setId = setId,
                            setNumber = resolved.setNumber,
                            exerciseName = resolved.exerciseName,
                            targetRpe = resolved.targetRpe,
                            currentRpe = resolved.currentRpe
                        )
                    )
                }
            } else {
                _localState.update { it.copy(pendingEffortPrompt = null) }
            }
        } else if (!resolved.nowCompleted) {
            _localState.update { state ->
                if (state.pendingEffortPrompt?.setId == setId) {
                    state.copy(pendingEffortPrompt = null)
                } else {
                    state
                }
            }
        }

        return resolved.justCompletedExercise
    }

    fun setActiveSetRpe(
        exerciseId: String,
        setId: String,
        rpe: Double?,
        dismissEffortPrompt: Boolean = false
    ) {
        if (_activeWorkout.value == null) return
        platform.triggerLightTick()
        mutateActiveWorkout {
            ActiveWorkoutMutations.updateSetRpe(it, exerciseId, setId, rpe)
        }
        if (dismissEffortPrompt || _localState.value.pendingEffortPrompt?.setId == setId) {
            _localState.update { it.copy(pendingEffortPrompt = null) }
        }
    }

    fun dismissEffortPrompt() {
        _localState.update { it.copy(pendingEffortPrompt = null) }
    }

    fun updateSetTagAndRest(
        exerciseId: String,
        setId: String,
        tag: SetTag?,
        workingSubTag: WorkingSubTag?,
        restSeconds: Int
    ) {
        val defaultRest = _settings.value.defaultRestSeconds
        mutateActiveWorkout { workout ->
            val currentRpe = workout.exercises
                .find { it.id == exerciseId }
                ?.sets
                ?.find { it.id == setId }
                ?.rpe
            ActiveWorkoutMutations.updateSetOptions(
                workout = workout,
                exerciseId = exerciseId,
                setId = setId,
                tag = tag,
                workingSubTag = workingSubTag,
                rpe = currentRpe,
                restSeconds = restSeconds,
                defaultRestSeconds = defaultRest
            )
        }
    }

    fun updateSetOptions(
        exerciseId: String,
        setId: String,
        tag: SetTag?,
        workingSubTag: WorkingSubTag?,
        rpe: Double?,
        restSeconds: Int
    ) {
        val defaultRest = _settings.value.defaultRestSeconds
        mutateActiveWorkout {
            ActiveWorkoutMutations.updateSetOptions(
                workout = it,
                exerciseId = exerciseId,
                setId = setId,
                tag = tag,
                workingSubTag = workingSubTag,
                rpe = rpe,
                restSeconds = restSeconds,
                defaultRestSeconds = defaultRest
            )
        }
    }

    fun cycleExerciseRest(exerciseId: String) {
        if (_activeWorkout.value == null) return
        val defaultRest = _settings.value.defaultRestSeconds
        platform.triggerLightTick()
        mutateActiveWorkout { ActiveWorkoutMutations.cycleExerciseRest(it, exerciseId, defaultRest) }
    }

    fun addSetToActiveExercise(exerciseId: String) {
        val defaultRest = _settings.value.defaultRestSeconds
        mutateActiveWorkout { ActiveWorkoutMutations.addSetToExercise(it, exerciseId, defaultRest) }
    }

    fun removeLastSetFromActiveExercise(exerciseId: String) {
        val defaultRest = _settings.value.defaultRestSeconds
        mutateActiveWorkout { ActiveWorkoutMutations.removeLastSetFromExercise(it, exerciseId, defaultRest) }
    }

    fun reorderActiveExercises(fromIndex: Int, toIndex: Int) {
        val current = _activeWorkout.value ?: return
        if (fromIndex == toIndex) return
        if (fromIndex !in current.exercises.indices || toIndex !in current.exercises.indices) return
        platform.triggerLightTick()
        mutateActiveWorkout { ActiveWorkoutMutations.reorderExercises(it, fromIndex, toIndex) }
    }

    fun addExerciseToActiveWorkout(rawName: String) {
        val name = RepDayRules.normalizeExerciseName(rawName)
        if (name.isEmpty()) return
        if (_activeWorkout.value == null) return
        val lastByExercise = _lastSessionByExercise.value
        val currentSettings = _settings.value
        mutateActiveWorkout {
            ActiveWorkoutMutations.addExercise(
                workout = it,
                normalizedName = name,
                lastSessionByExercise = lastByExercise,
                defaultRestSeconds = currentSettings.defaultRestSeconds,
                fallbackUnit = currentSettings.unit
            )
        }
        viewModelScope.launch {
            repository.registerCustomExercises(listOf(name))
        }
    }

    fun swapActiveExercise(exerciseId: String, rawNewName: String) {
        val newName = RepDayRules.normalizeExerciseName(rawNewName)
        if (newName.isEmpty()) return
        if (_activeWorkout.value == null) return
        platform.triggerLightTick()
        mutateActiveWorkout { ActiveWorkoutMutations.swapExercise(it, exerciseId, newName) }
        viewModelScope.launch {
            repository.registerCustomExercises(listOf(newName))
        }
    }

    fun swapActiveExerciseKeepCompleted(exerciseId: String, rawNewName: String) {
        val newName = RepDayRules.normalizeExerciseName(rawNewName)
        if (newName.isEmpty()) return
        if (_activeWorkout.value == null) return
        val defaultRest = _settings.value.defaultRestSeconds
        platform.triggerLightTick()
        mutateActiveWorkout {
            ActiveWorkoutMutations.swapExerciseKeepCompleted(it, exerciseId, newName, defaultRest)
        }
        viewModelScope.launch {
            repository.registerCustomExercises(listOf(newName))
        }
    }

    fun removeExerciseFromActiveWorkout(exerciseId: String) {
        mutateActiveWorkout { ActiveWorkoutMutations.removeExercise(it, exerciseId) }
    }

    fun finishActiveWorkout(
        notes: String,
        saveAsRoutine: Boolean = false,
        newRoutineName: String = ""
    ) {
        val current = _activeWorkout.value ?: return
        val finalActiveSnapshot = current.copy(notes = notes)
        val completedExercises = finalActiveSnapshot.exercises.mapNotNull { ex ->
            val doneSets = ex.sets.filter { it.completed }.map { s ->
                CompletedSet(
                    weight = s.weight.toDoubleOrNull() ?: 0.0,
                    reps = s.reps.toIntOrNull() ?: 0,
                    tag = s.tag,
                    workingSubTag = s.workingSubTag,
                    rpe = s.rpe
                )
            }
            if (doneSets.isEmpty()) null
            else CompletedExercise(
                name = ex.name,
                targetSets = ex.targetSets,
                repRange = ex.repRange.ifBlank { null },
                sets = doneSets
            )
        }
        if (completedExercises.isEmpty()) return

        val currentSettings = _settings.value
        val isLiveWorkout = finalActiveSnapshot.routineId == null
        val trimmedRoutineName = newRoutineName.trim()
        val createdRoutine = if (isLiveWorkout && saveAsRoutine && trimmedRoutineName.isNotEmpty()) {
            RepDayRules.buildRoutineFromActiveWorkout(
                workout = finalActiveSnapshot,
                routineName = trimmedRoutineName,
                defaultRest = currentSettings.defaultRestSeconds
            )
        } else {
            null
        }

        val resolvedRoutineId = createdRoutine?.id ?: finalActiveSnapshot.routineId
        val resolvedRoutineName = when {
            createdRoutine != null -> createdRoutine.name
            isLiveWorkout -> RepDayRules.formatLiveWorkoutDefaultName(finalActiveSnapshot.startedAt)
            else -> finalActiveSnapshot.routineName
        }

        val nowMs = clock()
        val durationSec = ((nowMs - finalActiveSnapshot.startedAt) / 1000L).toInt().coerceAtLeast(1)
        val totalVol = RepDayRules.computeCompletedVolume(completedExercises)
        val completed = CompletedWorkout(
            id = RepDayRules.completedWorkoutIdForActive(finalActiveSnapshot.id),
            routineId = resolvedRoutineId,
            routineName = resolvedRoutineName,
            startedAt = RepDayRules.formatIso(finalActiveSnapshot.startedAt),
            completedAt = RepDayRules.formatIso(nowMs),
            completedAtMs = nowMs,
            durationSeconds = durationSec,
            totalVolume = totalVol,
            unit = finalActiveSnapshot.resolvedUnit(currentSettings.unit),
            exercises = completedExercises,
            notes = notes.trim().ifBlank { null }
        )

        activeWorkoutDebounceJob?.cancel()
        activeWorkoutDebounceJob = null
        hasLocalActiveWorkoutMutation = true
        _activeWorkout.value = null
        activeWorkoutVersion += 1L
        val clearedVersion = activeWorkoutVersion

        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        platform.stopWorkoutService()
        val emptyRest = RestTimerSnapshot()
        _restSnapshot.value = emptyRest
        syncRestUiFromSnapshot(emptyRest)
        _localState.update {
            it.copy(
                isActiveWorkoutFullScreen = false,
                pendingEffortPrompt = null,
                pendingUnitChange = null,
                selectedTab = PrimaryTab.HISTORY,
                historyFilter = null
            )
        }
        if (createdRoutine != null) {
            showToast("Logged & saved \"${createdRoutine.name}\" routine")
        } else {
            showToast("Logged ${completed.routineName}")
        }

        viewModelScope.launch {
            activeWorkoutPersistMutex.withLock {
                // Flush the final active workout state before committing to history
                repository.saveActiveWorkout(finalActiveSnapshot)
            }
            if (createdRoutine != null) {
                repository.saveRoutine(createdRoutine)
            }
            repository.saveCompletedWorkout(completed)
            activeWorkoutPersistMutex.withLock {
                val latestVersion = activeWorkoutVersion
                repository.saveActiveWorkout(_activeWorkout.value)
                lastPersistedWorkoutVersion = maxOf(clearedVersion, latestVersion)
            }
            repository.saveRestTimer(emptyRest)
        }
    }

    // ---- Drift-Free Rest Timer ----

    fun startRestTimer(durationSeconds: Int, exerciseName: String = "") {
        val sec = durationSeconds.coerceIn(5, RestTimerRules.MAX_REST_DURATION_SEC)
        val endMs = clock() + sec * 1000L
        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        val snap = RestTimerSnapshot(
            endTimeMs = endMs,
            totalDurationSec = sec,
            exerciseName = exerciseName,
            completed = false
        )
        _restSnapshot.value = snap
        syncRestUiFromSnapshot(snap)
        viewModelScope.launch { repository.saveRestTimer(snap) }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        val cur = _restSnapshot.value
        val now = clock()
        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        val adjusted = RestTimerRules.adjustRestEndTime(
            currentEndTimeMs = cur.endTimeMs,
            currentTotalDurationSec = cur.totalDurationSec,
            completed = cur.completed,
            deltaSeconds = deltaSeconds,
            nowMs = now
        )
        val optimistic = RestTimerSnapshot(
            endTimeMs = adjusted.endTimeMs,
            totalDurationSec = adjusted.totalDurationSec,
            exerciseName = if (adjusted.endTimeMs == null) "" else cur.exerciseName,
            completed = adjusted.completed
        )
        _restSnapshot.value = optimistic
        syncRestUiFromSnapshot(optimistic)
        viewModelScope.launch {
            repository.adjustRestTimer(deltaSeconds = deltaSeconds, nowMs = now)
        }
    }

    fun selectRestTimerPresetAndSaveDefault(seconds: Int) {
        val name = _restSnapshot.value.exerciseName
        mutateSettings { it.copy(defaultRestSeconds = seconds) }
        startRestTimer(seconds, name)
    }

    fun dismissRestTimer() {
        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        val empty = RestTimerSnapshot()
        _restSnapshot.value = empty
        syncRestUiFromSnapshot(empty)
        _localState.update { it.copy(pendingEffortPrompt = null) }
        viewModelScope.launch { repository.saveRestTimer(empty) }
    }

    // ---- History CRUD & Editing ----

    fun openEditHistoryWorkout(workout: CompletedWorkout) {
        _localState.update { it.copy(editingHistoryWorkout = workout) }
    }

    fun closeEditHistoryWorkout() {
        _localState.update { it.copy(editingHistoryWorkout = null) }
    }

    fun saveEditedHistoryWorkout(updated: CompletedWorkout) {
        viewModelScope.launch {
            repository.saveCompletedWorkout(updated)
            _localState.update { it.copy(editingHistoryWorkout = null) }
            showToast("Workout log updated")
        }
    }

    fun deleteHistoryWorkout(workout: CompletedWorkout) {
        viewModelScope.launch {
            repository.deleteCompletedWorkout(workout.id)
            showToast("Workout log deleted")
        }
    }

    // ---- Backup Export / Import & CSV Export ----

    fun buildBackupJson(): String = RepDayBackupRules.encodeBackupJson(
        routines = _routines.value,
        plans = _plans.value,
        history = _history.value,
        customExercises = _customExercises.value,
        settings = _settings.value,
        nowMs = clock()
    )

    fun buildHistoryCsv(): String = RepDayBackupRules.exportHistoryToCsv(_history.value)

    fun stageBackupImportJson(rawJson: String) {
        viewModelScope.launch {
            val result = withContext(computeDispatcher) {
                RepDayBackupRules.parseAndValidateBackupJson(rawJson)
            }
            when (result) {
                is BackupValidationResult.Valid -> {
                    _localState.update {
                        it.copy(
                            pendingBackupImport = result,
                            backupImportError = null
                        )
                    }
                }
                is BackupValidationResult.Invalid -> {
                    _localState.update {
                        it.copy(
                            pendingBackupImport = null,
                            backupImportError = result.reason
                        )
                    }
                }
            }
        }
    }

    fun dismissBackupImport() {
        _localState.update {
            it.copy(
                pendingBackupImport = null,
                backupImportError = null
            )
        }
    }

    fun confirmBackupImport(mode: BackupImportMode) {
        val pending = _localState.value.pendingBackupImport ?: return
        _localState.update {
            it.copy(
                pendingBackupImport = null,
                backupImportError = null,
                showSettingsSheet = false
            )
        }
        viewModelScope.launch {
            val plan = withContext(computeDispatcher) {
                RepDayBackupRules.planBackupImport(
                    mode = mode,
                    backup = pending.backup,
                    existingRoutines = _routines.value,
                    existingPlans = _plans.value,
                    existingHistory = _history.value,
                    existingCustomExercises = _customExercises.value
                )
            }
            if (mode == BackupImportMode.REPLACE) {
                activeWorkoutDebounceJob?.cancel()
                activeWorkoutDebounceJob = null
                settingsDebounceJob?.cancel()
                settingsDebounceJob = null

                hasLocalActiveWorkoutMutation = true
                _activeWorkout.value = null
                activeWorkoutVersion += 1L
                val clearedVersion = activeWorkoutVersion

                lastChimedEndTimeMs = null
                platform.cancelRestDoneNotification()
                platform.stopWorkoutService()
                val emptyRest = RestTimerSnapshot()
                _restSnapshot.value = emptyRest
                syncRestUiFromSnapshot(emptyRest)
                _localState.update {
                    it.copy(
                        isActiveWorkoutFullScreen = false,
                        pendingEffortPrompt = null,
                        pendingUnitChange = null,
                        historyFilter = null
                    )
                }
                activeWorkoutPersistMutex.withLock {
                    repository.applyBackupImport(plan)
                    lastPersistedWorkoutVersion = maxOf(clearedVersion, activeWorkoutVersion)
                }
                plan.settingsToSave?.let { importedSettings ->
                    hasLocalSettingsMutation = true
                    _settings.value = importedSettings
                    settingsVersion += 1L
                    lastPersistedSettingsVersion = settingsVersion
                }
                showToast(
                    "Replaced data (${plan.routinesToSave.size} routines, ${plan.historyToSave.size} workouts)"
                )
            } else {
                repository.applyBackupImport(plan)
                val addedTotal = plan.routinesToSave.size + plan.plansToSave.size + plan.historyToSave.size
                if (addedTotal == 0 && plan.customExercisesToSave.isEmpty()) {
                    showToast("Nothing new to merge (all items already exist)")
                } else {
                    showToast(
                        "Merged ${plan.routinesToSave.size} routines, ${plan.plansToSave.size} plans, ${plan.historyToSave.size} workouts"
                    )
                }
            }
        }
    }

    // ---- Sample Data, Banner & Erase All Data ----

    fun loadSampleRoutinesAndPlan() {
        viewModelScope.launch {
            val (addedRoutines, addedPlans) = repository.loadSampleRoutinesAndPlan()
            when {
                addedRoutines == 0 && addedPlans == 0 -> showToast("Sample routines & plan already loaded")
                addedRoutines > 0 && addedPlans > 0 -> showToast("Loaded $addedRoutines sample routines & $addedPlans plan")
                addedRoutines > 0 -> showToast("Loaded $addedRoutines sample routines")
                else -> showToast("Loaded sample training plan")
            }
        }
    }

    fun removeSampleHistoryWorkouts() {
        val sampleIds = SampleDataRules.findSeedSampleHistoryIds(_history.value)
        viewModelScope.launch {
            if (sampleIds.isNotEmpty()) {
                repository.deleteCompletedWorkouts(sampleIds)
            }
            repository.markSampleHistoryBannerDismissed()
            showToast(
                if (sampleIds.size == 1) "Removed 1 sample workout"
                else "Removed ${sampleIds.size} sample workouts"
            )
        }
    }

    fun dismissSampleHistoryBanner() {
        viewModelScope.launch {
            repository.markSampleHistoryBannerDismissed()
        }
    }

    // ---- Settings & Preferences (Synchronous In-Memory Update + Background Save) ----

    fun updateSettings(settings: AppSettings) {
        mutateSettings { settings }
    }

    fun updateThemeConfig(theme: ThemeConfig) {
        mutateSettings { it.copy(theme = theme) }
    }

    fun updateWeightUnit(unit: WeightUnit) {
        mutateSettings { it.copy(unit = unit) }
    }

    fun requestWeightUnitChange(newUnit: WeightUnit) {
        val currentSettingsUnit = _settings.value.unit
        if (currentSettingsUnit == newUnit) return
        val active = _activeWorkout.value
        if (active != null && active.resolvedUnit(currentSettingsUnit) != newUnit) {
            _localState.update { it.copy(pendingUnitChange = newUnit) }
        } else {
            mutateSettings { it.copy(unit = newUnit) }
        }
    }

    fun confirmConvertActiveWorkoutUnit() {
        val targetUnit = _localState.value.pendingUnitChange ?: return
        val oldSettingsUnit = _settings.value.unit
        _localState.update { it.copy(pendingUnitChange = null) }
        mutateSettings { it.copy(unit = targetUnit) }
        mutateActiveWorkout { active ->
            ActiveWorkoutMutations.convertWorkoutUnit(
                workout = active,
                targetUnit = targetUnit,
                currentFallbackUnit = oldSettingsUnit
            )
        }
        showToast("Converted workout in progress to ${targetUnit.label}")
    }

    fun confirmKeepActiveWorkoutUnit() {
        val targetUnit = _localState.value.pendingUnitChange ?: return
        val oldSettingsUnit = _settings.value.unit
        _localState.update { it.copy(pendingUnitChange = null) }
        // Pin active workout unit to its current unit before changing global settings
        mutateActiveWorkout { active ->
            if (active.unit == null) active.copy(unit = oldSettingsUnit) else active
        }
        mutateSettings { it.copy(unit = targetUnit) }
    }

    fun dismissPendingUnitChange() {
        _localState.update { it.copy(pendingUnitChange = null) }
    }

    fun updatePlateCalcSettings(plateSettings: PlateCalculatorSettings) {
        mutateSettings { it.copy(plateCalculator = plateSettings) }
    }

    fun toggleSoundEnabled() {
        mutateSettings { it.copy(soundEnabled = !it.soundEnabled) }
    }

    fun eraseAllData() {
        activeWorkoutDebounceJob?.cancel()
        activeWorkoutDebounceJob = null
        settingsDebounceJob?.cancel()
        settingsDebounceJob = null

        hasLocalActiveWorkoutMutation = true
        _activeWorkout.value = null
        activeWorkoutVersion += 1L
        val clearedVersion = activeWorkoutVersion

        lastChimedEndTimeMs = null
        platform.cancelRestDoneNotification()
        platform.stopWorkoutService()
        val emptyRest = RestTimerSnapshot()
        _restSnapshot.value = emptyRest
        syncRestUiFromSnapshot(emptyRest)
        _localState.update {
            it.copy(
                isActiveWorkoutFullScreen = false,
                pendingEffortPrompt = null,
                pendingUnitChange = null,
                historyFilter = null,
                editingHistoryWorkout = null,
                pendingBackupImport = null,
                backupImportError = null,
                showSettingsSheet = false
            )
        }
        viewModelScope.launch {
            activeWorkoutPersistMutex.withLock {
                repository.eraseAllData()
                lastPersistedWorkoutVersion = maxOf(clearedVersion, activeWorkoutVersion)
            }
            val defaults = SeedData.defaultSettings()
            hasLocalSettingsMutation = true
            _settings.value = defaults
            settingsVersion += 1L
            lastPersistedSettingsVersion = settingsVersion
            showToast("All data erased")
        }
    }

    companion object {
        const val ACTIVE_WORKOUT_PERSIST_DEBOUNCE_MS = 400L
        const val SETTINGS_PERSIST_DEBOUNCE_MS = 150L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = RepDayRepository(
                        db = RepDayDatabase.getInstance(application),
                        dataStore = RepDayDataStore(application)
                    )
                    val hooks = AndroidWorkoutPlatformHooks(application)
                    return RepDayViewModel(
                        repository = repo,
                        platform = hooks
                    ) as T
                }
            }
    }
}
