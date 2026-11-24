package com.example.data

import com.example.data.local.RestClaimResult
import com.example.data.local.RestTimerSnapshot
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedWorkout
import com.example.data.model.Routine
import com.example.data.model.TrainingPlan
import com.example.domain.BackupImportPlan
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over [RepDayRepository] so [com.example.ui.viewmodel.RepDayViewModel]
 * can be unit-tested on the JVM with a deterministic fake store and test dispatcher.
 */
interface RepDayDataSource {
    val routinesFlow: Flow<List<Routine>>
    val plansFlow: Flow<List<TrainingPlan>>
    val historyFlow: Flow<List<CompletedWorkout>>
    val customExercisesFlow: Flow<List<String>>
    val settingsFlow: Flow<AppSettings>
    val activeWorkoutFlow: Flow<ActiveWorkout?>
    val restTimerFlow: Flow<RestTimerSnapshot>
    val dismissedSampleHistoryBannerFlow: Flow<Boolean>
    val unreadableRecordCountFlow: Flow<Int>

    suspend fun initializeAndBackfill()
    suspend fun getActiveWorkoutOnce(): ActiveWorkout?
    suspend fun getSettingsOnce(): AppSettings

    suspend fun registerCustomExercises(names: Iterable<String>)

    suspend fun saveRoutine(routine: Routine)
    suspend fun deleteRoutine(routineId: String)

    suspend fun savePlan(plan: TrainingPlan)
    suspend fun saveAllPlans(plans: List<TrainingPlan>)
    suspend fun deletePlan(planId: String)

    suspend fun saveCompletedWorkout(workout: CompletedWorkout)
    suspend fun deleteCompletedWorkout(workoutId: String)
    suspend fun deleteCompletedWorkouts(workoutIds: List<String>)

    suspend fun saveActiveWorkout(workout: ActiveWorkout?)
    suspend fun saveSettings(settings: AppSettings)
    suspend fun saveRestTimer(snapshot: RestTimerSnapshot)

    suspend fun claimRestTimerCompletion(
        expectedEndTimeMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): RestClaimResult

    suspend fun adjustRestTimer(
        deltaSeconds: Int,
        nowMs: Long = System.currentTimeMillis()
    ): RestTimerSnapshot

    suspend fun hasAskedNotificationPermission(): Boolean
    suspend fun markAskedNotificationPermission()
    suspend fun markSampleHistoryBannerDismissed()

    suspend fun loadSampleRoutinesAndPlan(): Pair<Int, Int>
    suspend fun applyBackupImport(plan: BackupImportPlan)
    suspend fun eraseAllData()
}
