package com.example.data

import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.CompletedWorkoutEntity
import com.example.data.local.CustomExerciseEntity
import com.example.data.local.RepDayDataStore
import com.example.data.local.RepDayDatabase
import com.example.data.local.RestTimerSnapshot
import com.example.data.local.RoutineEntity
import com.example.data.local.TrainingPlanEntity
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedWorkout
import com.example.data.model.Routine
import com.example.data.model.TrainingPlan
import com.example.domain.BackupImportMode
import com.example.domain.BackupImportPlan
import com.example.domain.RecordDecodeRules
import com.example.domain.RepDayRules
import com.example.domain.SampleDataRules
import com.example.domain.UnreadableRecord
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RepDayRepository(
    private val db: RepDayDatabase,
    private val dataStore: RepDayDataStore
) : RepDayDataSource {
    private val moshi: Moshi = Moshi.Builder().build()
    private val routineAdapter = moshi.adapter(Routine::class.java)
    private val planAdapter = moshi.adapter(TrainingPlan::class.java)
    private val completedAdapter = moshi.adapter(CompletedWorkout::class.java)

    private val _unreadableRoutineIds = MutableStateFlow<Set<String>>(emptySet())
    private val _unreadablePlanIds = MutableStateFlow<Set<String>>(emptySet())
    private val _unreadableHistoryIds = MutableStateFlow<Set<String>>(emptySet())

    private fun logDecodeError(record: UnreadableRecord, error: Throwable?) {
        Log.e(TAG, "Unreadable ${record.source} record id=${record.recordId}: ${record.errorMessage}", error)
    }

    override val routinesFlow: Flow<List<Routine>> = db.routineDao().observeAll().map { list ->
        val batch = RecordDecodeRules.decodeBatch(
            source = "routine",
            rawItems = list,
            idOf = { it.id },
            decode = { entity ->
                routineAdapter.fromJson(entity.payloadJson)?.let { decoded ->
                    val resolvedUpdatedMs = when {
                        entity.updatedAtMs > 0L -> entity.updatedAtMs
                        decoded.updatedAtMs > 0L -> decoded.updatedAtMs
                        else -> RepDayRules.parseIsoToMillis(decoded.updatedAt) ?: 0L
                    }
                    val resolvedCreatedMs = when {
                        entity.createdAtMs > 0L -> entity.createdAtMs
                        decoded.createdAtMs > 0L -> decoded.createdAtMs
                        else -> RepDayRules.parseIsoToMillis(decoded.createdAt) ?: resolvedUpdatedMs
                    }
                    if (decoded.createdAtMs == resolvedCreatedMs && decoded.updatedAtMs == resolvedUpdatedMs) {
                        decoded
                    } else {
                        decoded.copy(createdAtMs = resolvedCreatedMs, updatedAtMs = resolvedUpdatedMs)
                    }
                }
            },
            onError = ::logDecodeError
        )
        _unreadableRoutineIds.value = batch.unreadable.map { "routine:${it.recordId}" }.toSet()
        batch.items
    }.flowOn(Dispatchers.Default)

    override val plansFlow: Flow<List<TrainingPlan>> = db.trainingPlanDao().observeAll().map { list ->
        val batch = RecordDecodeRules.decodeBatch(
            source = "plan",
            rawItems = list,
            idOf = { it.id },
            decode = { entity ->
                planAdapter.fromJson(entity.payloadJson)?.let { decoded ->
                    val resolvedUpdatedMs = when {
                        entity.updatedAtMs > 0L -> entity.updatedAtMs
                        decoded.updatedAtMs > 0L -> decoded.updatedAtMs
                        else -> RepDayRules.parseIsoToMillis(decoded.updatedAt) ?: 0L
                    }
                    if (decoded.updatedAtMs == resolvedUpdatedMs) {
                        decoded
                    } else {
                        decoded.copy(updatedAtMs = resolvedUpdatedMs)
                    }
                }
            },
            onError = ::logDecodeError
        )
        _unreadablePlanIds.value = batch.unreadable.map { "plan:${it.recordId}" }.toSet()
        batch.items
    }.flowOn(Dispatchers.Default)

    override val historyFlow: Flow<List<CompletedWorkout>> = db.completedWorkoutDao().observeAll().map { list ->
        val batch = RecordDecodeRules.decodeBatch(
            source = "history",
            rawItems = list,
            idOf = { it.id },
            decode = { entity ->
                completedAdapter.fromJson(entity.payloadJson)?.let { decoded ->
                    val resolvedCompletedMs = when {
                        entity.completedAtMs > 0L -> entity.completedAtMs
                        decoded.completedAtMs > 0L -> decoded.completedAtMs
                        else -> RepDayRules.parseIsoToMillis(decoded.completedAt) ?: 0L
                    }
                    if (decoded.completedAtMs == resolvedCompletedMs) {
                        decoded
                    } else {
                        decoded.copy(completedAtMs = resolvedCompletedMs)
                    }
                }
            },
            onError = ::logDecodeError
        )
        _unreadableHistoryIds.value = batch.unreadable.map { "history:${it.recordId}" }.toSet()
        batch.items
    }.flowOn(Dispatchers.Default)

    override val customExercisesFlow: Flow<List<String>> = db.customExerciseDao().observeAll().map { list ->
        list.map { it.displayName }
    }.flowOn(Dispatchers.Default)

    override val settingsFlow: Flow<AppSettings> = dataStore.settingsFlow
    override val activeWorkoutFlow: Flow<ActiveWorkout?> = dataStore.activeWorkoutFlow
    override val restTimerFlow: Flow<RestTimerSnapshot> = dataStore.restTimerFlow
    override val dismissedSampleHistoryBannerFlow: Flow<Boolean> = dataStore.dismissedSampleHistoryBannerFlow

    override val unreadableRecordCountFlow: Flow<Int> = combine(
        _unreadableRoutineIds,
        _unreadablePlanIds,
        _unreadableHistoryIds,
        dataStore.unreadableKeysFlow
    ) { r, p, h, ds ->
        r.size + p.size + h.size + ds.size
    }.distinctUntilChanged()

    override suspend fun initializeAndBackfill() {
        if (!dataStore.hasSeeded()) {
            // Fresh install starts empty: never insert fake workout history or pre-seeded templates
            dataStore.saveSettings(SeedData.defaultSettings())
            dataStore.markSeeded()
        }
        dataStore.backupCorruptEntriesIfNeeded()
        backfillCustomExercises()
    }

    override suspend fun getActiveWorkoutOnce(): ActiveWorkout? = dataStore.activeWorkoutFlow.first()

    override suspend fun getSettingsOnce(): AppSettings = dataStore.settingsFlow.first()

    private suspend fun backfillCustomExercises() {
        val routines = RecordDecodeRules.decodeBatch(
            source = "routine",
            rawItems = db.routineDao().getAllOnce(),
            idOf = { it.id },
            decode = { routineAdapter.fromJson(it.payloadJson) },
            onError = ::logDecodeError
        ).items
        val history = RecordDecodeRules.decodeBatch(
            source = "history",
            rawItems = db.completedWorkoutDao().getAllOnce(),
            idOf = { it.id },
            decode = { completedAdapter.fromJson(it.payloadJson) },
            onError = ::logDecodeError
        ).items
        val active = dataStore.activeWorkoutFlow.first()

        val allNames = ArrayList<String>()
        routines.forEach { r -> r.exercises.forEach { allNames.add(it.name) } }
        history.forEach { w -> w.exercises.forEach { allNames.add(it.name) } }
        active?.exercises?.forEach { allNames.add(it.name) }

        registerCustomExercises(allNames)
    }

    override suspend fun registerCustomExercises(names: Iterable<String>) {
        val existing = db.customExerciseDao().getAllOnce().map { it.displayName }
        val toAdd = RepDayRules.extractNewCustomExercises(names, existing)
        if (toAdd.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = toAdd.mapIndexed { idx, name ->
            CustomExerciseEntity(
                normalizedKey = RepDayRules.exerciseKey(name),
                displayName = name,
                createdAt = now + idx
            )
        }
        db.customExerciseDao().insertAll(entities)
    }

    override suspend fun saveRoutine(routine: Routine) {
        val nowMs = System.currentTimeMillis()
        val createdMs = if (routine.createdAtMs > 0L) {
            routine.createdAtMs
        } else {
            RepDayRules.parseIsoToMillis(routine.createdAt) ?: nowMs
        }
        val updated = routine.copy(
            updatedAt = RepDayRules.formatIso(nowMs),
            createdAtMs = createdMs,
            updatedAtMs = nowMs
        )
        db.routineDao().upsert(updated.toEntity())
        registerCustomExercises(updated.exercises.map { it.name })
        // Sync linked plan slot labels whenever a routine is renamed
        val plans = RecordDecodeRules.decodeBatch(
            source = "plan",
            rawItems = db.trainingPlanDao().getAllOnce(),
            idOf = { it.id },
            decode = { planAdapter.fromJson(it.payloadJson) },
            onError = ::logDecodeError
        ).items
        for (plan in plans) {
            var changed = false
            val updatedBlocks = plan.blocks.map { block ->
                block.copy(
                    workouts = block.workouts.map { slot ->
                        if (slot.routineId == updated.id && slot.label != updated.name) {
                            changed = true
                            slot.copy(label = updated.name)
                        } else slot
                    }
                )
            }
            if (changed) {
                db.trainingPlanDao().upsert(plan.copy(blocks = updatedBlocks).toEntity())
            }
        }
    }

    override suspend fun deleteRoutine(routineId: String) {
        db.routineDao().deleteById(routineId)
        // Detach routineId from plan slots while preserving labels
        val plans = RecordDecodeRules.decodeBatch(
            source = "plan",
            rawItems = db.trainingPlanDao().getAllOnce(),
            idOf = { it.id },
            decode = { planAdapter.fromJson(it.payloadJson) },
            onError = ::logDecodeError
        ).items
        for (plan in plans) {
            var changed = false
            val updatedBlocks = plan.blocks.map { block ->
                block.copy(
                    workouts = block.workouts.map { slot ->
                        if (slot.routineId == routineId) {
                            changed = true
                            slot.copy(routineId = null)
                        } else slot
                    }
                )
            }
            if (changed) {
                db.trainingPlanDao().upsert(plan.copy(blocks = updatedBlocks).toEntity())
            }
        }
    }

    override suspend fun savePlan(plan: TrainingPlan) {
        val nowMs = System.currentTimeMillis()
        val updated = plan.copy(
            updatedAt = RepDayRules.formatIso(nowMs),
            updatedAtMs = nowMs
        )
        db.trainingPlanDao().upsert(updated.toEntity())
    }

    override suspend fun saveAllPlans(plans: List<TrainingPlan>) {
        db.trainingPlanDao().upsertAll(plans.map { it.toEntity() })
    }

    override suspend fun deletePlan(planId: String) {
        db.trainingPlanDao().deleteById(planId)
    }

    override suspend fun saveCompletedWorkout(workout: CompletedWorkout) {
        db.completedWorkoutDao().upsert(workout.toEntity())
        registerCustomExercises(workout.exercises.map { it.name })
    }

    override suspend fun deleteCompletedWorkout(workoutId: String) {
        db.completedWorkoutDao().deleteById(workoutId)
    }

    override suspend fun deleteCompletedWorkouts(workoutIds: List<String>) {
        for (id in workoutIds) {
            db.completedWorkoutDao().deleteById(id)
        }
    }

    override suspend fun saveActiveWorkout(workout: ActiveWorkout?) {
        dataStore.saveActiveWorkout(workout)
    }

    override suspend fun saveSettings(settings: AppSettings) {
        dataStore.saveSettings(settings)
    }

    override suspend fun saveRestTimer(snapshot: RestTimerSnapshot) {
        dataStore.saveRestTimer(snapshot)
    }

    override suspend fun claimRestTimerCompletion(
        expectedEndTimeMs: Long,
        nowMs: Long
    ) = dataStore.claimRestTimerCompletion(expectedEndTimeMs, nowMs)

    override suspend fun adjustRestTimer(
        deltaSeconds: Int,
        nowMs: Long
    ): RestTimerSnapshot = dataStore.adjustRestTimer(deltaSeconds, nowMs)

    override suspend fun hasAskedNotificationPermission(): Boolean =
        dataStore.hasAskedNotificationPermission()

    override suspend fun markAskedNotificationPermission() {
        dataStore.markAskedNotificationPermission()
    }

    override suspend fun markSampleHistoryBannerDismissed() {
        dataStore.markSampleHistoryBannerDismissed()
    }

    override suspend fun loadSampleRoutinesAndPlan(): Pair<Int, Int> {
        val existingRoutines = RecordDecodeRules.decodeBatch(
            source = "routine",
            rawItems = db.routineDao().getAllOnce(),
            idOf = { it.id },
            decode = { routineAdapter.fromJson(it.payloadJson) },
            onError = ::logDecodeError
        ).items
        val existingPlans = RecordDecodeRules.decodeBatch(
            source = "plan",
            rawItems = db.trainingPlanDao().getAllOnce(),
            idOf = { it.id },
            decode = { planAdapter.fromJson(it.payloadJson) },
            onError = ::logDecodeError
        ).items

        val plan = SampleDataRules.planLoadSampleRoutinesAndPlan(existingRoutines, existingPlans)
        if (plan.routinesToAdd.isNotEmpty()) {
            db.routineDao().upsertAll(plan.routinesToAdd.map { it.toEntity() })
            val exNames = plan.routinesToAdd.flatMap { r -> r.exercises.map { it.name } }
            registerCustomExercises(exNames)
        }
        if (plan.plansToAdd.isNotEmpty()) {
            db.trainingPlanDao().upsertAll(plan.plansToAdd.map { it.toEntity() })
        }
        return plan.routinesToAdd.size to plan.plansToAdd.size
    }

    override suspend fun applyBackupImport(plan: BackupImportPlan) {
        // All database changes succeed or fail together, so a crash mid-import can't leave
        // the database empty or half-replaced. DataStore writes run after the commit.
        db.withTransaction {
            if (plan.mode == BackupImportMode.REPLACE) {
                db.routineDao().deleteAll()
                db.trainingPlanDao().deleteAll()
                db.completedWorkoutDao().deleteAll()
                db.customExerciseDao().deleteAll()
            }
            if (plan.routinesToSave.isNotEmpty()) {
                db.routineDao().upsertAll(plan.routinesToSave.map { it.toEntity() })
            }
            if (plan.plansToSave.isNotEmpty()) {
                db.trainingPlanDao().upsertAll(plan.plansToSave.map { it.toEntity() })
            }
            if (plan.historyToSave.isNotEmpty()) {
                db.completedWorkoutDao().upsertAll(plan.historyToSave.map { it.toEntity() })
            }
            if (plan.customExercisesToSave.isNotEmpty()) {
                registerCustomExercises(plan.customExercisesToSave)
            }
        }
        if (plan.mode == BackupImportMode.REPLACE) {
            dataStore.saveActiveWorkout(null)
            dataStore.saveRestTimer(RestTimerSnapshot())
        }
        if (plan.settingsToSave != null) {
            dataStore.saveSettings(plan.settingsToSave)
        }
    }

    override suspend fun eraseAllData() {
        db.withTransaction {
            db.routineDao().deleteAll()
            db.trainingPlanDao().deleteAll()
            db.completedWorkoutDao().deleteAll()
            db.customExerciseDao().deleteAll()
        }
        dataStore.saveActiveWorkout(null)
        dataStore.saveRestTimer(RestTimerSnapshot())
        dataStore.saveSettings(SeedData.defaultSettings())
    }

    private fun Routine.toEntity(): RoutineEntity {
        val resolvedUpdatedMs = if (updatedAtMs > 0L) updatedAtMs else (RepDayRules.parseIsoToMillis(updatedAt) ?: 0L)
        val resolvedCreatedMs = if (createdAtMs > 0L) createdAtMs else (RepDayRules.parseIsoToMillis(createdAt) ?: resolvedUpdatedMs)
        val normalized = if (createdAtMs == resolvedCreatedMs && updatedAtMs == resolvedUpdatedMs) {
            this
        } else {
            copy(createdAtMs = resolvedCreatedMs, updatedAtMs = resolvedUpdatedMs)
        }
        return RoutineEntity(
            id = normalized.id,
            name = normalized.name,
            updatedAt = normalized.updatedAt,
            payloadJson = routineAdapter.toJson(normalized),
            createdAtMs = resolvedCreatedMs,
            updatedAtMs = resolvedUpdatedMs
        )
    }

    private fun TrainingPlan.toEntity(): TrainingPlanEntity {
        val resolvedUpdatedMs = if (updatedAtMs > 0L) updatedAtMs else (RepDayRules.parseIsoToMillis(updatedAt) ?: 0L)
        val normalized = if (updatedAtMs == resolvedUpdatedMs) this else copy(updatedAtMs = resolvedUpdatedMs)
        return TrainingPlanEntity(
            id = normalized.id,
            name = normalized.name,
            isActive = normalized.isActive,
            updatedAt = normalized.updatedAt,
            payloadJson = planAdapter.toJson(normalized),
            updatedAtMs = resolvedUpdatedMs
        )
    }

    private fun CompletedWorkout.toEntity(): CompletedWorkoutEntity {
        val resolvedCompletedMs = if (completedAtMs > 0L) completedAtMs else (RepDayRules.parseIsoToMillis(completedAt) ?: 0L)
        val normalized = if (completedAtMs == resolvedCompletedMs) this else copy(completedAtMs = resolvedCompletedMs)
        return CompletedWorkoutEntity(
            id = normalized.id,
            routineId = normalized.routineId,
            routineName = normalized.routineName,
            completedAt = normalized.completedAt,
            payloadJson = completedAdapter.toJson(normalized),
            completedAtMs = resolvedCompletedMs
        )
    }

    companion object {
        private const val TAG = "RepDayRepository"
    }
}
