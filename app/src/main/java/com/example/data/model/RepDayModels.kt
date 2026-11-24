package com.example.data.model

import com.squareup.moshi.JsonClass

enum class WeightUnit(val label: String) {
    LB("lb"),
    KG("kg")
}

enum class EffortScale(val label: String) {
    RPE("RPE"),
    RIR("RIR")
}

enum class SetTag(val shortCode: String, val displayName: String) {
    WARMUP("W", "Warm-up"),
    WORKING("WK", "Working"),
    DROP("D", "Drop Set"),
    MYO("M", "Myo-Reps"),
    PARTIALS("LP", "Long-Length Partials")
}

enum class WorkingSubTag(val shortCode: String, val displayName: String, val fullLabel: String) {
    TOP("TOP", "Top Set", "Working · Top"),
    BACKOFF("BO", "Backoff Set", "Working · Backoff")
}

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("Auto")
}

enum class ThemePresetId(val displayName: String, val subtitle: String) {
    OBSIDIAN_GOLD("Obsidian Gold", "Black, gold, silver & crisp white"),
    VOLT_IRON("Volt Iron", "Charcoal, athletic lime & slate"),
    CRIMSON_STEEL("Crimson Steel", "Carbon black, red & brushed silver"),
    COBALT_TITANIUM("Cobalt Titanium", "Deep navy slate, blue & titanium"),
    EMERALD_CARBON("Emerald Carbon", "Matte black, jade green & pearl"),
    MONOCHROME_STARK("Stark Silver", "Pure black, silver & stark white")
}

enum class RoutineSortField(val label: String) {
    NAME("Name"),
    CREATED_AT("Created"),
    LAST_COMPLETED_AT("Last Done")
}

enum class SortDirection {
    ASC,
    DESC
}

@JsonClass(generateAdapter = true)
data class PlannedSet(
    val id: String,
    val tag: SetTag? = null,
    val workingSubTag: WorkingSubTag? = null,
    val repRange: String? = null,
    val targetRpe: String? = null,
    val restSeconds: Int = 90
)

@JsonClass(generateAdapter = true)
data class RoutineExercise(
    val id: String,
    val name: String,
    val targetSets: Int,
    val repRange: String,
    val targetRpe: String? = null,
    val restSeconds: Int? = null,
    val plannedSets: List<PlannedSet>? = null
)

@JsonClass(generateAdapter = true)
data class Routine(
    val id: String,
    val name: String,
    val focus: String? = null,
    val description: String? = null,
    val exercises: List<RoutineExercise>,
    val createdAt: String,
    val updatedAt: String,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class WorkoutSet(
    val id: String,
    val weight: String = "",
    val reps: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val tag: SetTag? = null,
    val workingSubTag: WorkingSubTag? = null,
    val targetRepRange: String? = null,
    val targetRpe: String? = null,
    val restSeconds: Int? = null,
    val rpe: Double? = null
)

@JsonClass(generateAdapter = true)
data class ActiveExercise(
    val id: String,
    val name: String,
    val targetSets: Int,
    val repRange: String,
    val targetRpe: String? = null,
    val restSeconds: Int? = null,
    val sets: List<WorkoutSet>
)

@JsonClass(generateAdapter = true)
data class ActiveWorkout(
    val id: String,
    val routineId: String? = null,
    val routineName: String,
    val startedAt: Long,
    val exercises: List<ActiveExercise>,
    val notes: String = "",
    val unit: WeightUnit? = null
) {
    fun resolvedUnit(fallback: WeightUnit): WeightUnit = unit ?: fallback
}

@JsonClass(generateAdapter = true)
data class CompletedSet(
    val weight: Double,
    val reps: Int,
    val tag: SetTag? = null,
    val workingSubTag: WorkingSubTag? = null,
    val rpe: Double? = null
)

@JsonClass(generateAdapter = true)
data class CompletedExercise(
    val name: String,
    val targetSets: Int? = null,
    val repRange: String? = null,
    val sets: List<CompletedSet>
)

@JsonClass(generateAdapter = true)
data class CompletedWorkout(
    val id: String,
    val routineId: String? = null,
    val routineName: String,
    val startedAt: String,
    val completedAt: String,
    val durationSeconds: Int,
    val totalVolume: Int,
    val unit: WeightUnit,
    val exercises: List<CompletedExercise>,
    val notes: String? = null,
    val completedAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class PlanWorkoutSlot(
    val id: String,
    val routineId: String? = null,
    val label: String,
    val notes: String? = null,
    val timesPerWeek: Int = 1
)

@JsonClass(generateAdapter = true)
data class PlanBlock(
    val id: String,
    val name: String,
    val weeks: Int = 4,
    val notes: String? = null,
    val workouts: List<PlanWorkoutSlot>
)

@JsonClass(generateAdapter = true)
data class PauseSpan(
    val startMs: Long,
    val endMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class TrainingPlan(
    val id: String,
    val name: String,
    val description: String? = null,
    val useBlocks: Boolean = true,
    val blocks: List<PlanBlock>,
    val startedAt: String? = null,
    val isActive: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val updatedAtMs: Long = 0L,
    val pausedAt: String? = null,
    val pausedDurationMs: Long = 0L,
    val pauses: List<PauseSpan> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CustomPalette(
    val canvas: String,
    val surface: String,
    val accent: String,
    val text: String
)

@JsonClass(generateAdapter = true)
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.DARK,
    val presetId: ThemePresetId = ThemePresetId.OBSIDIAN_GOLD,
    val useCustomColors: Boolean = false,
    val customDark: CustomPalette? = null,
    val customLight: CustomPalette? = null
)

@JsonClass(generateAdapter = true)
data class PlateCalculatorSettings(
    val includeBar: Boolean = true,
    val barWeightLb: Double = 45.0,
    val barWeightKg: Double = 20.0,
    val availablePlatesLb: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
    val availablePlatesKg: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    val perSideMode: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RoutineSortConfig(
    val field: RoutineSortField = RoutineSortField.NAME,
    val direction: SortDirection = SortDirection.ASC
)

@JsonClass(generateAdapter = true)
data class AppSettings(
    val unit: WeightUnit = WeightUnit.LB,
    val defaultRestSeconds: Int = 90,
    val soundEnabled: Boolean = true,
    val vibrateOnRestEnd: Boolean = true,
    val keepScreenOnDuringWorkout: Boolean = false,
    val effortScale: EffortScale = EffortScale.RPE,
    val askEffortAfterSet: Boolean = true,
    val theme: ThemeConfig = ThemeConfig(),
    val plateCalculator: PlateCalculatorSettings = PlateCalculatorSettings(),
    val routineSort: RoutineSortConfig = RoutineSortConfig()
)
