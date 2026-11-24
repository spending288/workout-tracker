package com.example.domain

import com.example.data.SeedData
import com.example.data.model.LoadType
import java.util.Locale
import com.example.data.model.AppSettings
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.Routine
import com.example.data.model.SetTag
import com.example.data.model.TrainingPlan
import com.example.data.model.WorkingSubTag
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlin.math.roundToInt

// ---- 4. Edit a Logged Workout ----

data class EditableCompletedSet(
    val id: String,
    val weightText: String,
    val repsText: String,
    val rpe: Double? = null,
    val tag: SetTag? = null,
    val workingSubTag: WorkingSubTag? = null
)

data class EditableCompletedExercise(
    val id: String,
    val name: String,
    val targetSets: Int? = null,
    val repRange: String? = null,
    val sets: List<EditableCompletedSet>,
    val loadType: LoadType = LoadType.STANDARD,
    val originalNameKey: String? = null,
    val originalLoadType: LoadType = loadType
)

data class HistoryWorkoutEditDraft(
    val original: CompletedWorkout,
    val routineName: String,
    val notes: String,
    val startedAtMs: Long,
    val durationMinutes: Int,
    val exercises: List<EditableCompletedExercise>
)

object HistoryWorkoutEditRules {

    fun fromCompletedWorkout(workout: CompletedWorkout): HistoryWorkoutEditDraft {
        val resolvedCompletedMs = workout.completedAtMs.takeIf { it > 0L }
            ?: RepDayRules.parseIsoToMillis(workout.completedAt)
            ?: System.currentTimeMillis()
        val resolvedStartedMs = RepDayRules.parseIsoToMillis(workout.startedAt)
            ?: (resolvedCompletedMs - workout.durationSeconds.coerceAtLeast(60) * 1000L).coerceAtLeast(0L)
        val durationMin = (workout.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)

        val editableExercises = workout.exercises.mapIndexed { exIdx, ex ->
            EditableCompletedExercise(
                id = "he-$exIdx-${RepDayRules.newId("ex")}",
                name = ex.name,
                targetSets = ex.targetSets,
                repRange = ex.repRange,
                loadType = ex.loadType,
                originalNameKey = RepDayRules.exerciseKey(ex.name),
                originalLoadType = ex.loadType,
                sets = ex.sets.mapIndexed { setIdx, s ->
                    EditableCompletedSet(
                        id = "hs-$exIdx-$setIdx-${RepDayRules.newId("s")}",
                        weightText = RepDayRules.formatNumber(s.weight),
                        repsText = s.reps.toString(),
                        rpe = s.rpe,
                        tag = s.tag,
                        workingSubTag = if (s.tag == SetTag.WORKING) s.workingSubTag else null
                    )
                }
            )
        }

        return HistoryWorkoutEditDraft(
            original = workout,
            routineName = workout.routineName,
            notes = workout.notes.orEmpty(),
            startedAtMs = resolvedStartedMs,
            durationMinutes = durationMin,
            exercises = editableExercises
        )
    }

    fun snapshotSignature(draft: HistoryWorkoutEditDraft): String = buildString {
        append(draft.routineName.trim()).append('|')
        append(draft.notes.trim()).append('|')
        append(draft.startedAtMs).append('|')
        append(draft.durationMinutes)
        for (ex in draft.exercises) {
            append("||").append(ex.name.trim())
            for (s in ex.sets) {
                append(';')
                    .append(s.weightText.trim()).append(',')
                    .append(s.repsText.trim()).append(',')
                    .append(s.rpe).append(',')
                    .append(s.tag).append(',')
                    .append(s.workingSubTag)
            }
        }
    }

    fun isDirty(draft: HistoryWorkoutEditDraft, initialSignature: String): Boolean =
        snapshotSignature(draft) != initialSignature

    fun isValidWeightText(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val parsed = trimmed.toDoubleOrNull() ?: return false
        return parsed.isFinite() && parsed >= 0.0
    }

    fun isValidRepsText(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val parsed = trimmed.toIntOrNull() ?: return false
        return parsed >= 1
    }

    fun validationError(draft: HistoryWorkoutEditDraft): String? {
        if (draft.durationMinutes < 1) {
            return "Duration must be at least 1 minute."
        }
        if (draft.exercises.isEmpty()) {
            return "At least one exercise with a set is required to save this workout log."
        }
        for (ex in draft.exercises) {
            val trimmedName = ex.name.trim()
            if (trimmedName.isEmpty()) {
                return "Every exercise needs a name before saving."
            }
            if (ex.sets.isEmpty()) {
                return "Add at least one set or remove \"$trimmedName\"."
            }
            for (s in ex.sets) {
                if (!isValidWeightText(s.weightText) || !isValidRepsText(s.repsText)) {
                    return "Enter weight (0 or more) and reps (1 or more) for every set."
                }
            }
        }
        return null
    }

    fun canSave(draft: HistoryWorkoutEditDraft): Boolean = validationError(draft) == null

    /**
     * Exercises kept under their original name preserve the weight type saved in history.
     * Added exercises (originalNameKey == null) or renamed exercises resolve via
     * [LoadTypeRules.resolve] using the user's current overrides.
     */
    fun resolvedLoadType(
        exercise: EditableCompletedExercise,
        loadTypes: Map<String, LoadType> = emptyMap()
    ): LoadType {
        val currentKey = RepDayRules.exerciseKey(exercise.name)
        if (exercise.originalNameKey != null && currentKey == exercise.originalNameKey) {
            return exercise.originalLoadType
        }
        return LoadTypeRules.resolve(exercise.name, loadTypes)
    }

    /**
     * Converts an edited history draft back into a [CompletedWorkout], recomputing totalVolume
     * and preserving original timestamps when start time and duration were not modified.
     * Returns null if validation fails.
     */
    fun applyEdit(
        draft: HistoryWorkoutEditDraft,
        loadTypes: Map<String, LoadType> = emptyMap()
    ): CompletedWorkout? {
        if (!canSave(draft)) return null
        val cleanedExercises = draft.exercises.mapNotNull { ex ->
            val normalizedName = RepDayRules.normalizeExerciseName(ex.name)
            if (normalizedName.isEmpty() || ex.sets.isEmpty()) return@mapNotNull null
            val mappedSets = ex.sets.map { s ->
                val parsedWeight = s.weightText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: return null
                val parsedReps = s.repsText.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
                val cleanSubTag = if (s.tag == SetTag.WORKING) s.workingSubTag else null
                val cleanRpe = s.rpe?.let { RepDayRules.snapRpe(it) }
                CompletedSet(
                    weight = parsedWeight,
                    reps = parsedReps,
                    tag = s.tag,
                    workingSubTag = cleanSubTag,
                    rpe = cleanRpe
                )
            }
            CompletedExercise(
                name = normalizedName,
                targetSets = ex.targetSets ?: mappedSets.size,
                repRange = ex.repRange,
                sets = mappedSets,
                loadType = resolvedLoadType(ex, loadTypes)
            )
        }
        if (cleanedExercises.isEmpty()) return null

        val orig = draft.original
        val origCompletedMs = orig.completedAtMs.takeIf { it > 0L }
            ?: RepDayRules.parseIsoToMillis(orig.completedAt)
            ?: (draft.startedAtMs + draft.durationMinutes.coerceIn(1, 1440) * 60_000L)
        val origStartedMs = RepDayRules.parseIsoToMillis(orig.startedAt)
            ?: (origCompletedMs - orig.durationSeconds.coerceAtLeast(60) * 1000L).coerceAtLeast(0L)
        val origDurationMin = (orig.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)

        val clampedMinutes = draft.durationMinutes.coerceIn(1, 1440)
        val timingChanged = draft.startedAtMs != origStartedMs || clampedMinutes != origDurationMin

        val newDurationSec = if (clampedMinutes == origDurationMin) {
            orig.durationSeconds.coerceAtLeast(60)
        } else {
            clampedMinutes * 60
        }
        val newStartedAtIso = if (timingChanged) RepDayRules.formatIso(draft.startedAtMs) else orig.startedAt
        val newCompletedAtMs = if (timingChanged) draft.startedAtMs + newDurationSec * 1000L else origCompletedMs
        val newCompletedAtIso = if (timingChanged) RepDayRules.formatIso(newCompletedAtMs) else orig.completedAt

        val recomputedVolume = RepDayRules.computeCompletedVolume(cleanedExercises, orig.bodyWeight)
        val resolvedName = draft.routineName.trim().ifEmpty { orig.routineName }

        return orig.copy(
            routineName = resolvedName,
            startedAt = newStartedAtIso,
            completedAt = newCompletedAtIso,
            completedAtMs = newCompletedAtMs,
            durationSeconds = newDurationSec,
            totalVolume = recomputedVolume,
            exercises = cleanedExercises,
            notes = draft.notes.trim().ifBlank { null }
        )
    }
}

// ---- 5. Export & Import Backup + History CSV ----

@JsonClass(generateAdapter = true)
data class RepDayBackupPayload(
    val formatVersion: Int = RepDayBackupRules.BACKUP_FORMAT_VERSION,
    val exportedAt: String = "",
    val app: String = "RepDay",
    val routines: List<Routine> = emptyList(),
    val plans: List<TrainingPlan> = emptyList(),
    val history: List<CompletedWorkout> = emptyList(),
    val customExercises: List<String> = emptyList(),
    val settings: AppSettings = AppSettings()
)

sealed class BackupValidationResult {
    data class Valid(
        val backup: RepDayBackupPayload,
        val routineCount: Int,
        val planCount: Int,
        val historyCount: Int,
        val customExerciseCount: Int
    ) : BackupValidationResult()

    data class Invalid(val reason: String) : BackupValidationResult()
}

enum class BackupImportMode {
    MERGE,
    REPLACE
}

data class BackupImportPlan(
    val mode: BackupImportMode,
    val routinesToSave: List<Routine>,
    val plansToSave: List<TrainingPlan>,
    val historyToSave: List<CompletedWorkout>,
    val customExercisesToSave: List<String>,
    val settingsToSave: AppSettings?,
    val skippedRoutines: Int = 0,
    val skippedPlans: Int = 0,
    val skippedHistory: Int = 0
)

object RepDayBackupRules {
    const val BACKUP_FORMAT_VERSION = 1

    private val moshi: Moshi = Moshi.Builder().build()
    private val backupAdapter = moshi.adapter(RepDayBackupPayload::class.java).indent("  ")
    private val rawMapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun encodeBackupJson(
        routines: List<Routine>,
        plans: List<TrainingPlan>,
        history: List<CompletedWorkout>,
        customExercises: List<String>,
        settings: AppSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val payload = RepDayBackupPayload(
            formatVersion = BACKUP_FORMAT_VERSION,
            exportedAt = RepDayRules.formatIso(nowMs),
            app = "RepDay",
            routines = routines,
            plans = plans,
            history = history,
            customExercises = customExercises,
            settings = settings
        )
        return backupAdapter.toJson(payload)
    }

    fun parseAndValidateBackupJson(rawJson: String): BackupValidationResult {
        if (rawJson.isBlank()) {
            return BackupValidationResult.Invalid("The selected file is empty.")
        }
        val rawMap = runCatching { rawMapAdapter.fromJson(rawJson) }.getOrNull()
            ?: return BackupValidationResult.Invalid("This file is not valid JSON.")

        val versionRaw = rawMap["formatVersion"]
            ?: return BackupValidationResult.Invalid("Missing formatVersion. Choose a RepDay backup JSON file.")
        val versionNum = (versionRaw as? Number)?.toInt()
            ?: return BackupValidationResult.Invalid("Invalid formatVersion in backup file.")
        if (versionNum < 1) {
            return BackupValidationResult.Invalid("Unsupported backup format version ($versionNum).")
        }
        if (versionNum > BACKUP_FORMAT_VERSION) {
            return BackupValidationResult.Invalid(
                "This backup uses newer format v$versionNum (app supports up to v$BACKUP_FORMAT_VERSION)."
            )
        }

        val parsed = runCatching { backupAdapter.fromJson(rawJson) }.getOrNull()
            ?: return BackupValidationResult.Invalid("Backup structure could not be read.")

        // Hydrate epoch millis on any imported items that predate v2 or omit millis fields
        val hydratedRoutines = parsed.routines.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map { r ->
            val updMs = r.updatedAtMs.takeIf { it > 0L } ?: (RepDayRules.parseIsoToMillis(r.updatedAt) ?: 0L)
            val crtMs = r.createdAtMs.takeIf { it > 0L } ?: (RepDayRules.parseIsoToMillis(r.createdAt) ?: updMs)
            if (r.createdAtMs == crtMs && r.updatedAtMs == updMs) r else r.copy(createdAtMs = crtMs, updatedAtMs = updMs)
        }
        val hydratedPlans = parsed.plans.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map { p ->
            val updMs = p.updatedAtMs.takeIf { it > 0L } ?: (RepDayRules.parseIsoToMillis(p.updatedAt) ?: 0L)
            if (p.updatedAtMs == updMs) p else p.copy(updatedAtMs = updMs)
        }
        val hydratedHistory = parsed.history.filter { it.id.isNotBlank() }.map { w ->
            val compMs = w.completedAtMs.takeIf { it > 0L } ?: (RepDayRules.parseIsoToMillis(w.completedAt) ?: 0L)
            val recomputedVol = if (w.totalVolume > 0) w.totalVolume else RepDayRules.computeCompletedVolume(w.exercises, w.bodyWeight)
            if (w.completedAtMs == compMs && w.totalVolume == recomputedVol) w
            else w.copy(completedAtMs = compMs, totalVolume = recomputedVol)
        }
        val normalizedCustom = RepDayRules.extractNewCustomExercises(parsed.customExercises, emptyList())

        val hydrated = parsed.copy(
            routines = hydratedRoutines,
            plans = hydratedPlans,
            history = hydratedHistory,
            customExercises = normalizedCustom
        )

        return BackupValidationResult.Valid(
            backup = hydrated,
            routineCount = hydratedRoutines.size,
            planCount = hydratedPlans.size,
            historyCount = hydratedHistory.size,
            customExerciseCount = normalizedCustom.size
        )
    }

    fun planBackupImport(
        mode: BackupImportMode,
        backup: RepDayBackupPayload,
        existingRoutines: List<Routine>,
        existingPlans: List<TrainingPlan>,
        existingHistory: List<CompletedWorkout>,
        existingCustomExercises: List<String>,
        currentSettings: AppSettings = AppSettings()
    ): BackupImportPlan {
        return when (mode) {
            BackupImportMode.REPLACE -> {
                // Guarantee at most one active plan in replaced state
                var seenActive = false
                val cleanedPlans = backup.plans.map { p ->
                    if (p.isActive) {
                        if (!seenActive) {
                            seenActive = true
                            p
                        } else {
                            p.copy(isActive = false)
                        }
                    } else p
                }
                val allExerciseNames = ArrayList<String>(backup.customExercises)
                backup.routines.forEach { r -> r.exercises.forEach { allExerciseNames.add(it.name) } }
                backup.history.forEach { w -> w.exercises.forEach { allExerciseNames.add(it.name) } }
                val customToSave = RepDayRules.extractNewCustomExercises(allExerciseNames, emptyList())
                val mergedSettings = backup.settings.copy(
                    autoBackupEnabled = currentSettings.autoBackupEnabled,
                    autoBackupFolderUri = currentSettings.autoBackupFolderUri,
                    lastAutoBackupMs = currentSettings.lastAutoBackupMs
                )
                BackupImportPlan(
                    mode = BackupImportMode.REPLACE,
                    routinesToSave = backup.routines.distinctBy { it.id },
                    plansToSave = cleanedPlans.distinctBy { it.id },
                    historyToSave = backup.history.distinctBy { it.id },
                    customExercisesToSave = customToSave,
                    settingsToSave = mergedSettings
                )
            }

            BackupImportMode.MERGE -> {
                val existingRoutineIds = existingRoutines.map { it.id }.toSet()
                val existingPlanIds = existingPlans.map { it.id }.toSet()
                val existingHistoryIds = existingHistory.map { it.id }.toSet()
                var hasActivePlan = existingPlans.any { it.isActive }

                val newRoutines = ArrayList<Routine>()
                var skippedRoutines = 0
                for (r in backup.routines.distinctBy { it.id }) {
                    if (r.id in existingRoutineIds) skippedRoutines++ else newRoutines.add(r)
                }

                val newPlans = ArrayList<TrainingPlan>()
                var skippedPlans = 0
                for (p in backup.plans.distinctBy { it.id }) {
                    if (p.id in existingPlanIds) {
                        skippedPlans++
                    } else {
                        val adjusted = if (p.isActive && hasActivePlan) {
                            p.copy(isActive = false)
                        } else {
                            if (p.isActive) hasActivePlan = true
                            p
                        }
                        newPlans.add(adjusted)
                    }
                }

                val newHistory = ArrayList<CompletedWorkout>()
                var skippedHistory = 0
                for (w in backup.history.distinctBy { it.id }) {
                    if (w.id in existingHistoryIds) skippedHistory++ else newHistory.add(w)
                }

                val candidateCustom = ArrayList<String>(backup.customExercises)
                newRoutines.forEach { r -> r.exercises.forEach { candidateCustom.add(it.name) } }
                newHistory.forEach { w -> w.exercises.forEach { candidateCustom.add(it.name) } }
                val newCustom = RepDayRules.extractNewCustomExercises(candidateCustom, existingCustomExercises)

                BackupImportPlan(
                    mode = BackupImportMode.MERGE,
                    routinesToSave = newRoutines,
                    plansToSave = newPlans,
                    historyToSave = newHistory,
                    customExercisesToSave = newCustom,
                    settingsToSave = null,
                    skippedRoutines = skippedRoutines,
                    skippedPlans = skippedPlans,
                    skippedHistory = skippedHistory
                )
            }
        }
    }

    fun exportHistoryToCsv(history: List<CompletedWorkout>): String {
        val sb = StringBuilder()
        // Columns after "notes" were added later; earlier columns keep their order for existing sheets.
        sb.appendLine("date,workout name,exercise,set number,tag,weight,unit,reps,RPE,RIR,notes,weight type,effective load,volume,body weight")
        val ordered = history.sortedByDescending { it.completedAtMs }
        for (workout in ordered) {
            val notes = workout.notes.orEmpty()
            for (exercise in workout.exercises) {
                exercise.sets.forEachIndexed { idx, set ->
                    val tag = RepDayRules.tagFullLabel(set.tag, set.workingSubTag).orEmpty()
                    val weight = RepDayRules.formatNumber(set.weight)
                    val rpeStr = set.rpe?.let { RepDayRules.formatRpeNumber(it) }.orEmpty()
                    val rirStr = set.rpe?.let { rpeVal ->
                        val rir = (10.0 - RepDayRules.snapRpe(rpeVal)).coerceAtLeast(0.0)
                        RepDayRules.formatNumber(rir)
                    }.orEmpty()
                    // Bodyweight/assisted sets need a recorded body weight to give a real load.
                    val effective = if (LoadTypeRules.needsBodyWeight(exercise.loadType) && workout.bodyWeight == null) null
                    else LoadTypeRules.effectiveLoad(set.weight, exercise.loadType, workout.bodyWeight)
                    val effectiveStr = effective?.let { RepDayRules.formatNumber(RepDayRules.roundToTenth(it)) }.orEmpty()
                    val volumeStr = effective?.let { RepDayRules.formatNumber(RepDayRules.roundToTenth(it * set.reps.coerceAtLeast(0))) }.orEmpty()
                    val row = listOf(
                        workout.completedAt,
                        workout.routineName,
                        exercise.name,
                        (idx + 1).toString(),
                        tag,
                        weight,
                        workout.unit.label,
                        set.reps.toString(),
                        rpeStr,
                        rirStr,
                        notes,
                        exercise.loadType.name.lowercase(Locale.US),
                        effectiveStr,
                        volumeStr,
                        workout.bodyWeight?.let { RepDayRules.formatNumber(RepDayRules.roundToTenth(it)) }.orEmpty()
                    ).joinToString(",") { escapeCsvField(it) }
                    sb.appendLine(row)
                }
            }
        }
        return sb.toString()
    }

    fun escapeCsvField(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}

// ---- 6. Sample Data & Erase Confirmation Rules ----

data class SampleRestorePlan(
    val routinesToAdd: List<Routine>,
    val plansToAdd: List<TrainingPlan>
)

object SampleDataRules {
    val SEED_HISTORY_IDS: Set<String> = setOf("seed-hist-push", "seed-hist-back")
    const val ERASE_CONFIRMATION_KEYWORD = "ERASE"

    fun findSeedSampleHistoryIds(history: List<CompletedWorkout>): List<String> =
        history.filter { it.id in SEED_HISTORY_IDS }.map { it.id }

    fun shouldShowSampleHistoryBanner(
        history: List<CompletedWorkout>,
        bannerDismissed: Boolean
    ): Boolean = !bannerDismissed && history.any { it.id in SEED_HISTORY_IDS }

    /**
     * Plans adding the sample routines & training plan non-destructively (never deletes existing
     * user data, never inserts fake history, and avoids creating two active plans).
     */
    fun planLoadSampleRoutinesAndPlan(
        existingRoutines: List<Routine>,
        existingPlans: List<TrainingPlan>
    ): SampleRestorePlan {
        val existingRoutineIds = existingRoutines.map { it.id }.toSet()
        val existingPlanIds = existingPlans.map { it.id }.toSet()
        val alreadyHasActivePlan = existingPlans.any { it.isActive }

        val routinesToAdd = SeedData.createSeedRoutines().filter { it.id !in existingRoutineIds }
        val plansToAdd = SeedData.createSeedPlans()
            .filter { it.id !in existingPlanIds }
            .map { p -> if (alreadyHasActivePlan) p.copy(isActive = false) else p }

        return SampleRestorePlan(
            routinesToAdd = routinesToAdd,
            plansToAdd = plansToAdd
        )
    }

    fun isEraseConfirmationValid(input: String): Boolean =
        input.trim() == ERASE_CONFIRMATION_KEYWORD
}
