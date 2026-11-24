package com.example.domain

import com.example.data.SeedData
import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.EffortScale
import com.example.data.model.PauseSpan
import com.example.data.model.PlanBlock
import com.example.data.model.PlannedSet
import com.example.data.model.PlateCalculatorSettings
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.RoutineSortConfig
import com.example.data.model.RoutineSortField
import com.example.data.model.SetTag
import com.example.data.model.SortDirection
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PlateCount(val plate: Double, val count: Int)

data class PlateLoadOption(
    val totalWeight: Double,
    val platesPerSide: List<PlateCount>,
    val platesTotalWeight: Double,
    val diffFromTarget: Double
)

data class PlateCalcResult(
    val achievedTotal: Double,
    val platesPerSide: List<PlateCount>,
    val platesTotalWeight: Double,
    val barUsed: Double,
    val isBelowBar: Boolean,
    val isExactMatch: Boolean,
    val shortBy: Double,
    val overOption: PlateLoadOption? = null
)

data class BlockProgressInfo(
    val block: PlanBlock,
    val startWeek: Int,
    val endWeek: Int,
    val fillFraction: Float,
    val isCurrent: Boolean
)

data class PlanProgressSummary(
    val totalWeeks: Int,
    val totalSessions: Int,
    val currentWeek: Int,
    val allWeeksReached: Boolean,
    val isPaused: Boolean = false,
    val currentBlock: PlanBlock?,
    val blockInfos: List<BlockProgressInfo>,
    val weekSessionsDone: Int,
    val weekSessionsTarget: Int,
    val slotWeeklyDone: Map<String, Int>
)

data class PreviousExerciseSession(
    val completedAt: String,
    val unit: WeightUnit,
    val sets: List<CompletedSet>,
    val completedAtMs: Long = 0L
)

object RepDayRules {

    const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    const val KG_TO_LB = 2.20462262

    val REST_PRESETS_SECONDS = listOf(30, 60, 90, 120, 150, 180, 210, 240)
    val EXERCISE_REST_CYCLE = listOf(60, 90, 120, 180)
    val RPE_VALUES = listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)
    val RIR_CHOICES = listOf(4, 3, 2, 1, 0)
    val RIR_RANGE_PRESETS = listOf(0 to 1, 1 to 2, 2 to 3, 1 to 3)
    val REP_EXACT_PRESETS = listOf(5, 6, 8, 10, 12, 15)
    val REP_RANGE_PRESETS = listOf("4-6", "6-8", "8-10", "10-12", "12-15")
    val RPE_EXACT_PRESETS = listOf("7", "7.5", "8", "8.5", "9", "9.5", "10")
    val RPE_RANGE_PRESETS = listOf("6-7", "7-8", "7.5-8.5", "8-9", "9-10")
    val BAR_PRESETS_LB = listOf(45.0, 35.0, 25.0, 15.0)
    val BAR_PRESETS_KG = listOf(20.0, 15.0, 10.0, 7.5)
    val ALL_PLATES_LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    val ALL_PLATES_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    private val whitespaceRegex = Regex("\\s+")

    private val isoSecondsUtcFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val isoMillisUtcFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val isoOffsetFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val liveWorkoutDateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("EEEE, MMMM d", Locale.US)
    }

    private val shortMonthDayFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d", Locale.US)
    }

    private val completedDateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, MMM d", Locale.US)
    }

    private val completedTimeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("h:mm a", Locale.US)
    }

    fun newId(prefix: String = "id"): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    fun formatIso(epochMs: Long): String =
        isoSecondsUtcFormat.get()!!.format(Date(epochMs))

    fun nowIso(): String = formatIso(System.currentTimeMillis())

    fun parseIsoToMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val trimmed = iso.trim()
        val primary = when {
            trimmed.endsWith("Z") && !trimmed.contains('.') -> isoSecondsUtcFormat
            trimmed.endsWith("Z") -> isoMillisUtcFormat
            else -> isoOffsetFormat
        }
        try {
            primary.get()?.parse(trimmed)?.let { return it.time }
        } catch (_: Exception) {
        }
        val fallbacks = arrayOf(isoSecondsUtcFormat, isoMillisUtcFormat, isoOffsetFormat)
        for (tl in fallbacks) {
            if (tl === primary) continue
            try {
                tl.get()?.parse(trimmed)?.let { return it.time }
            } catch (_: Exception) {
            }
        }
        return null
    }

    // ---- Exercise Library Normalization & Search ----

    fun normalizeExerciseName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        var needsCollapse = false
        var prevSpace = false
        for (i in 0 until trimmed.length) {
            val c = trimmed[i]
            if (c.isWhitespace()) {
                if (c != ' ' || prevSpace) {
                    needsCollapse = true
                    break
                }
                prevSpace = true
            } else {
                prevSpace = false
            }
        }
        return if (needsCollapse) trimmed.replace(whitespaceRegex, " ") else trimmed
    }

    fun exerciseKey(raw: String): String = normalizeExerciseName(raw).lowercase(Locale.US)

    private val builtInKeys: Set<String> by lazy {
        SeedData.COMMON_EXERCISES.map { exerciseKey(it) }.toSet()
    }

    fun isBuiltInExercise(name: String): Boolean = builtInKeys.contains(exerciseKey(name))

    fun isKnownInLibrary(name: String, customExercises: List<String>): Boolean {
        val key = exerciseKey(name)
        if (key.isEmpty()) return true
        if (builtInKeys.contains(key)) return true
        return customExercises.any { exerciseKey(it) == key }
    }

    fun buildUnifiedLibrary(customExercises: List<String>): List<String> {
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (name in customExercises + SeedData.COMMON_EXERCISES) {
            val normalized = normalizeExerciseName(name)
            val key = normalized.lowercase(Locale.US)
            if (key.isNotEmpty() && seen.add(key)) {
                result.add(normalized)
            }
        }
        return result
    }

    fun extractNewCustomExercises(
        candidateNames: Iterable<String>,
        existingCustom: List<String>
    ): List<String> {
        val existingKeys = HashSet<String>(builtInKeys)
        existingCustom.forEach { existingKeys.add(exerciseKey(it)) }
        val toAdd = ArrayList<String>()
        for (raw in candidateNames) {
            val normalized = normalizeExerciseName(raw)
            val key = normalized.lowercase(Locale.US)
            if (key.isNotEmpty() && existingKeys.add(key)) {
                toAdd.add(normalized)
            }
        }
        return toAdd
    }

    fun searchExercises(
        query: String,
        library: List<String>,
        excludeNames: Set<String> = emptySet(),
        limit: Int = 8,
        omitExactMatch: Boolean = true
    ): List<String> {
        val q = exerciseKey(query)
        val excludedKeys = excludeNames.map { exerciseKey(it) }.toSet()
        val available = library.filter { !excludedKeys.contains(exerciseKey(it)) }
        if (q.isEmpty()) {
            return available.take(limit)
        }
        val prefixMatches = ArrayList<String>()
        val substringMatches = ArrayList<String>()
        for (item in available) {
            val key = exerciseKey(item)
            if (omitExactMatch && key == q) continue
            if (key.startsWith(q)) {
                prefixMatches.add(item)
            } else if (key.contains(q)) {
                substringMatches.add(item)
            }
        }
        return (prefixMatches + substringMatches).take(limit)
    }

    private val movementGroups: List<List<String>> = listOf(
        listOf("bench", "chest", "press", "dip", "flye", "pec"),
        listOf("overhead", "shoulder", "lateral", "delt"),
        listOf("tricep", "pushdown", "skullcrusher", "extension"),
        listOf("pull-up", "pulldown", "lat"),
        listOf("row", "pull", "face pull", "reverse pec"),
        listOf("curl", "bicep", "hammer", "preacher"),
        listOf("squat", "leg press", "lunge", "split squat", "leg extension"),
        listOf("deadlift", "leg curl", "romanian", "hamstring"),
        listOf("calf", "raise"),
        listOf("crunch", "leg raise", "ab")
    )

    fun suggestSwapAlternatives(
        currentName: String,
        query: String,
        library: List<String>,
        excludeNames: Set<String>,
        limit: Int = 10
    ): List<String> {
        val q = exerciseKey(query)
        if (q.isNotEmpty()) {
            return searchExercises(query, library, excludeNames, limit, omitExactMatch = true)
        }
        val currentKey = exerciseKey(currentName)
        val excludedKeys = excludeNames.map { exerciseKey(it) }.toSet() + currentKey
        val activeGroups = movementGroups.filter { group -> group.any { kw -> currentKey.contains(kw) } }
        val scored = library.filter { !excludedKeys.contains(exerciseKey(it)) }.map { candidate ->
            val ck = exerciseKey(candidate)
            var score = 0
            for (group in activeGroups) {
                for (kw in group) {
                    if (ck.contains(kw)) score += 2
                }
            }
            val words = currentKey.split(" ").filter { it.length >= 4 }
            for (w in words) {
                if (ck.contains(w)) score += 1
            }
            candidate to score
        }
        return scored
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    // ---- Drop Set Zero-Rest Rules (ID-based) ----

    fun enforceDropSetRestRules(
        sets: List<PlannedSet>,
        defaultRest: Int,
        previousSets: List<PlannedSet>? = null
    ): List<PlannedSet> {
        val wasPrecedingDropIds = previousSets?.let { prev ->
            buildSet {
                for (i in 0 until prev.size - 1) {
                    if (prev[i + 1].tag == SetTag.DROP) add(prev[i].id)
                }
            }
        }.orEmpty()
        val safeDefault = defaultRest.coerceIn(0, 600)
        return sets.mapIndexed { index, set ->
            val nextIsDrop = index + 1 < sets.size && sets[index + 1].tag == SetTag.DROP
            val cleanSubTag = if (set.tag == SetTag.WORKING) set.workingSubTag else null
            val resolvedRest = when {
                nextIsDrop -> 0
                set.restSeconds == 0 && set.id in wasPrecedingDropIds -> safeDefault
                else -> set.restSeconds.coerceIn(0, 600)
            }
            set.copy(workingSubTag = cleanSubTag, restSeconds = resolvedRest)
        }
    }

    /**
     * Compatibility overload for callers passing previous drop-set indices directly.
     */
    fun enforceDropSetRestRules(
        sets: List<PlannedSet>,
        defaultRest: Int,
        previousDropIndices: Set<Int>
    ): List<PlannedSet> {
        val safeDefault = defaultRest.coerceIn(0, 600)
        return sets.mapIndexed { index, set ->
            val nextIsDrop = index + 1 < sets.size && sets[index + 1].tag == SetTag.DROP
            val cleanSubTag = if (set.tag == SetTag.WORKING) set.workingSubTag else null
            val resolvedRest = when {
                nextIsDrop -> 0
                set.restSeconds == 0 && previousDropIndices.contains(index + 1) -> safeDefault
                else -> set.restSeconds.coerceIn(0, 600)
            }
            set.copy(workingSubTag = cleanSubTag, restSeconds = resolvedRest)
        }
    }

    fun enforceDropSetRestOnActiveSets(
        sets: List<WorkoutSet>,
        defaultRest: Int,
        previousSets: List<WorkoutSet>? = null
    ): List<WorkoutSet> {
        val wasPrecedingDropIds = previousSets?.let { prev ->
            buildSet {
                for (i in 0 until prev.size - 1) {
                    if (prev[i + 1].tag == SetTag.DROP) add(prev[i].id)
                }
            }
        }.orEmpty()
        val safeDefault = defaultRest.coerceIn(0, 600)
        return sets.mapIndexed { index, set ->
            val nextIsDrop = index + 1 < sets.size && sets[index + 1].tag == SetTag.DROP
            val cleanSubTag = if (set.tag == SetTag.WORKING) set.workingSubTag else null
            val resolvedRest = when {
                nextIsDrop -> 0
                set.restSeconds == 0 && set.id in wasPrecedingDropIds -> safeDefault
                else -> (set.restSeconds ?: safeDefault).coerceIn(0, 600)
            }
            set.copy(workingSubTag = cleanSubTag, restSeconds = resolvedRest)
        }
    }

    fun isPrecedingDropSet(sets: List<PlannedSet>, index: Int): Boolean =
        index + 1 < sets.size && sets[index + 1].tag == SetTag.DROP

    fun isPrecedingDropSetActive(sets: List<WorkoutSet>, index: Int): Boolean =
        index + 1 < sets.size && sets[index + 1].tag == SetTag.DROP

    // ---- Rep Range & RPE Normalization + Derived Summary ----

    fun parseRepBounds(repRange: String?): Pair<Int, Int>? {
        if (repRange.isNullOrBlank()) return null
        val nums = Regex("\\d+").findAll(repRange).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.isEmpty()) return null
        val first = nums[0].coerceIn(1, 100)
        val second = (nums.getOrNull(1) ?: first).coerceIn(1, 100)
        return min(first, second) to max(first, second)
    }

    fun normalizeRepRange(minVal: Int, maxVal: Int?): String {
        val lo = minVal.coerceIn(1, 100)
        if (maxVal == null) return lo.toString()
        val hi = maxVal.coerceIn(1, 100)
        val a = min(lo, hi)
        val b = max(lo, hi)
        return if (a == b) "$a" else "$a-$b"
    }

    fun snapRpe(value: Double): Double {
        val snapped = (value * 2.0).roundToInt() / 2.0
        return snapped.coerceIn(6.0, 10.0)
    }

    fun formatRpeNumber(value: Double): String {
        val s = snapRpe(value)
        return if (s % 1.0 == 0.0) s.toInt().toString() else String.format(Locale.US, "%.1f", s)
    }

    fun parseRpeBounds(targetRpe: String?): Pair<Double, Double>? {
        if (targetRpe.isNullOrBlank()) return null
        val nums = Regex("\\d+(?:\\.\\d+)?").findAll(targetRpe).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (nums.isEmpty()) return null
        val first = snapRpe(nums[0])
        val second = snapRpe(nums.getOrNull(1) ?: first)
        return min(first, second) to max(first, second)
    }

    fun normalizeRpeTarget(minVal: Double, maxVal: Double?): String {
        val lo = snapRpe(minVal)
        if (maxVal == null) return formatRpeNumber(lo)
        val hi = snapRpe(maxVal)
        val a = min(lo, hi)
        val b = max(lo, hi)
        return if (abs(a - b) < 0.01) formatRpeNumber(a) else "${formatRpeNumber(a)}-${formatRpeNumber(b)}"
    }

    fun rpeEffortDescription(rpe: Double): String = when (snapRpe(rpe)) {
        6.0 -> "Light effort · 4+ RIR"
        6.5 -> "Moderate-light · 3–4 RIR"
        7.0 -> "Moderate · 3 RIR"
        7.5 -> "Challenging · 2–3 RIR"
        8.0 -> "Hard · 2 RIR"
        8.5 -> "Very hard · 1–2 RIR"
        9.0 -> "Near failure · 1 RIR"
        9.5 -> "Maybe 1 rep left · 0–1 RIR"
        else -> "Max effort · 0 RIR"
    }

    fun formatEffortDescription(rpe: Double, scale: EffortScale): String {
        val s = snapRpe(rpe)
        if (scale == EffortScale.RIR) return rpeEffortDescription(s)
        val prefix = when (s) {
            6.0 -> "Light effort"
            6.5 -> "Moderate-light"
            7.0 -> "Moderate"
            7.5 -> "Challenging"
            8.0 -> "Hard"
            8.5 -> "Very hard"
            9.0 -> "Near failure"
            9.5 -> "Maybe 1 rep left"
            else -> "Max effort"
        }
        return "$prefix · RPE ${formatRpeNumber(s)}"
    }

    fun rirToRpe(rir: Int): Double = (10 - rir.coerceIn(0, 4)).toDouble()

    fun rpeToRirLabel(rpe: Double, compact: Boolean = false): String {
        val core = when (snapRpe(rpe)) {
            6.0 -> "4+"
            6.5 -> "3–4"
            7.0 -> "3"
            7.5 -> "2–3"
            8.0 -> "2"
            8.5 -> "1–2"
            9.0 -> "1"
            9.5 -> "0–1"
            else -> "0"
        }
        return if (compact) core else "$core RIR"
    }

    fun formatEffortValue(rpe: Double, scale: EffortScale, compact: Boolean = false): String {
        return when (scale) {
            EffortScale.RPE -> if (compact) formatRpeNumber(rpe) else "RPE ${formatRpeNumber(rpe)}"
            EffortScale.RIR -> rpeToRirLabel(rpe, compact = compact)
        }
    }

    fun rpeTargetToRirBounds(targetRpe: String?): Pair<Int, Int>? {
        val (minRpe, maxRpe) = parseRpeBounds(targetRpe) ?: return null
        val minRir = floor(10.0 - maxRpe).toInt().coerceIn(0, 4)
        val maxRir = ceil(10.0 - minRpe).toInt().coerceIn(0, 4)
        return min(minRir, maxRir) to max(minRir, maxRir)
    }

    fun rirTargetToRpeString(minRir: Int, maxRir: Int? = null): String {
        val lo = minRir.coerceIn(0, 4)
        if (maxRir == null) {
            return formatRpeNumber((10 - lo).toDouble())
        }
        val hi = maxRir.coerceIn(0, 4)
        val a = min(lo, hi) // lower RIR -> higher RPE
        val b = max(lo, hi) // higher RIR -> lower RPE
        return normalizeRpeTarget((10 - b).toDouble(), (10 - a).toDouble())
    }

    fun formatRirBound(rir: Int): String = if (rir >= 4) "4+" else rir.coerceAtLeast(0).toString()

    fun formatEffortTarget(
        targetRpe: String?,
        scale: EffortScale,
        compact: Boolean = false
    ): String? {
        if (targetRpe.isNullOrBlank()) return null
        if (scale == EffortScale.RPE) {
            return if (compact) "@$targetRpe" else "RPE $targetRpe"
        }
        val (minRpe, maxRpe) = parseRpeBounds(targetRpe) ?: return null
        if (abs(minRpe - maxRpe) < 0.01) {
            return rpeToRirLabel(minRpe, compact = compact)
        }
        val minRir = floor(10.0 - maxRpe).toInt().coerceIn(0, 4)
        val maxRir = ceil(10.0 - minRpe).toInt().coerceIn(0, 4)
        val core = if (minRir == maxRir) {
            formatRirBound(minRir)
        } else {
            "${formatRirBound(minRir)}–${formatRirBound(maxRir)}"
        }
        return if (compact) core else "$core RIR"
    }

    fun matchesEffortTargetChip(chipRpe: Double, targetRpe: String?): Boolean {
        val (lo, hi) = parseRpeBounds(targetRpe) ?: return false
        return chipRpe >= lo - 0.01 && chipRpe <= hi + 0.01
    }

    // ---- Weight Unit Conversion Helpers ----

    fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
        if (from == to) return value
        return if (from == WeightUnit.KG && to == WeightUnit.LB) {
            value * KG_TO_LB
        } else {
            value / KG_TO_LB
        }
    }

    fun roundToTenth(value: Double): Double = (value * 10.0).roundToInt() / 10.0

    fun roundToHalf(value: Double): Double = (value * 2.0).roundToInt() / 2.0

    fun formatConvertedWeight(value: Double, from: WeightUnit, to: WeightUnit): String {
        if (from == to) return formatNumber(value)
        return formatNumber(roundToTenth(convertWeight(value, from, to)))
    }

    fun convertWeightForInput(value: Double, from: WeightUnit, to: WeightUnit): Double {
        if (from == to) return value
        return roundToHalf(convertWeight(value, from, to))
    }

    fun convertVolume(volume: Int, from: WeightUnit, to: WeightUnit): Int {
        if (from == to) return volume
        return convertWeight(volume.toDouble(), from, to).roundToInt()
    }

    fun computeTotalHistoryVolume(history: List<CompletedWorkout>, displayUnit: WeightUnit): Int {
        if (history.isEmpty()) return 0
        return history.sumOf { w ->
            convertWeight(w.totalVolume.toDouble(), w.unit, displayUnit)
        }.roundToInt()
    }

    fun expandPlannedSets(exercise: RoutineExercise, defaultRest: Int): List<PlannedSet> {
        val existing = exercise.plannedSets
        val exRest = exercise.restSeconds ?: defaultRest
        if (!existing.isNullOrEmpty()) {
            return enforceDropSetRestRules(existing, exRest)
        }
        val count = exercise.targetSets.coerceIn(1, 20)
        return List(count) { idx ->
            PlannedSet(
                id = "${exercise.id}-s${idx + 1}",
                tag = null,
                workingSubTag = null,
                repRange = exercise.repRange.ifBlank { null },
                targetRpe = exercise.targetRpe,
                restSeconds = exRest
            )
        }
    }

    fun withDerivedSummary(exercise: RoutineExercise): RoutineExercise {
        val sets = exercise.plannedSets
        if (sets.isNullOrEmpty()) return exercise
        val repBounds = sets.mapNotNull { parseRepBounds(it.repRange) }
        val derivedReps = if (repBounds.isEmpty()) {
            ""
        } else {
            val lo = repBounds.minOf { it.first }
            val hi = repBounds.maxOf { it.second }
            if (lo == hi) "$lo" else "$lo-$hi"
        }
        val rpeBounds = sets.mapNotNull { parseRpeBounds(it.targetRpe) }
        val derivedRpe = if (rpeBounds.isEmpty()) {
            null
        } else {
            val lo = rpeBounds.minOf { it.first }
            val hi = rpeBounds.maxOf { it.second }
            normalizeRpeTarget(lo, hi)
        }
        return exercise.copy(
            targetSets = sets.size.coerceIn(1, 20),
            repRange = derivedReps,
            targetRpe = derivedRpe
        )
    }

    fun formatExerciseTargetSummary(
        targetSets: Int,
        repRange: String,
        targetRpe: String?,
        effortScale: EffortScale = EffortScale.RPE
    ): String {
        val hasReps = repRange.isNotBlank()
        val hasRpe = !targetRpe.isNullOrBlank()
        if (effortScale == EffortScale.RIR && hasRpe) {
            val rirLabel = formatEffortTarget(targetRpe, EffortScale.RIR) ?: "RPE $targetRpe"
            return if (hasReps) "$targetSets × $repRange · $rirLabel" else "$targetSets × $rirLabel"
        }
        return when {
            hasReps && hasRpe -> "$targetSets × $repRange @$targetRpe"
            hasReps -> "$targetSets × $repRange"
            hasRpe -> "$targetSets × RPE $targetRpe"
            else -> "$targetSets sets"
        }
    }

    fun formatSetTargetLabel(
        repRange: String?,
        targetRpe: String?,
        effortScale: EffortScale = EffortScale.RPE
    ): String {
        val hasReps = !repRange.isNullOrBlank()
        val hasRpe = !targetRpe.isNullOrBlank()
        if (effortScale == EffortScale.RIR && hasRpe) {
            val rirLabel = formatEffortTarget(targetRpe, EffortScale.RIR) ?: "RPE $targetRpe"
            return if (hasReps) "$repRange · $rirLabel" else rirLabel
        }
        return when {
            hasReps && hasRpe -> "$repRange @$targetRpe"
            hasReps -> "$repRange reps"
            hasRpe -> "RPE $targetRpe"
            else -> "Open"
        }
    }

    fun summarizeSetTags(sets: List<PlannedSet>?): String {
        if (sets.isNullOrEmpty()) return ""
        var warmup = 0
        var top = 0
        var backoff = 0
        var working = 0
        var drop = 0
        var myo = 0
        var partials = 0
        for (s in sets) {
            when (s.tag) {
                SetTag.WARMUP -> warmup++
                SetTag.WORKING -> when (s.workingSubTag) {
                    WorkingSubTag.TOP -> top++
                    WorkingSubTag.BACKOFF -> backoff++
                    null -> working++
                }
                SetTag.DROP -> drop++
                SetTag.MYO -> myo++
                SetTag.PARTIALS -> partials++
                null -> Unit
            }
        }
        val parts = ArrayList<String>()
        if (warmup > 0) parts.add("$warmup W")
        if (top > 0) parts.add("$top Top")
        if (backoff > 0) parts.add("$backoff Backoff")
        if (working > 0) parts.add("$working WK")
        if (drop > 0) parts.add("$drop Drop")
        if (myo > 0) parts.add("$myo Myo")
        if (partials > 0) parts.add("$partials LP")
        return parts.joinToString(" · ")
    }

    fun tagShortCode(tag: SetTag?, subTag: WorkingSubTag?): String? {
        if (tag == null) return null
        if (tag == SetTag.WORKING && subTag != null) return subTag.shortCode
        return tag.shortCode
    }

    fun tagFullLabel(tag: SetTag?, subTag: WorkingSubTag?): String? {
        if (tag == null) return null
        if (tag == SetTag.WORKING && subTag != null) return subTag.fullLabel
        return tag.displayName
    }

    // ---- Optimal Plate Calculator (exact DP in integer hundredths) ----

    private fun gcdInt(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    fun calculatePlates(
        targetTotal: Double,
        unit: WeightUnit,
        settings: PlateCalculatorSettings
    ): PlateCalcResult {
        val barWeight = if (settings.includeBar) {
            (if (unit == WeightUnit.LB) settings.barWeightLb else settings.barWeightKg).coerceIn(0.0, 200.0)
        } else {
            0.0
        }
        val enabledPlates = (if (unit == WeightUnit.LB) settings.availablePlatesLb else settings.availablePlatesKg)
            .filter { it > 0.0 }
            .distinct()
            .sortedDescending()
            .ifEmpty { listOf(if (unit == WeightUnit.LB) 2.5 else 1.25) }

        val targetHundredths = (targetTotal.coerceIn(0.0, 5000.0) * 100.0).roundToInt()
        val barHundredths = (barWeight * 100.0).roundToInt()

        if (targetHundredths < barHundredths) {
            return PlateCalcResult(
                achievedTotal = barWeight,
                platesPerSide = emptyList(),
                platesTotalWeight = 0.0,
                barUsed = barWeight,
                isBelowBar = true,
                isExactMatch = false,
                shortBy = (barHundredths - targetHundredths) / 100.0,
                overOption = null
            )
        }

        val sides = if (settings.perSideMode) 2 else 1
        val neededPlatesTotal = targetHundredths - barHundredths
        if (neededPlatesTotal == 0) {
            return PlateCalcResult(
                achievedTotal = barWeight,
                platesPerSide = emptyList(),
                platesTotalWeight = 0.0,
                barUsed = barWeight,
                isBelowBar = false,
                isExactMatch = true,
                shortBy = 0.0,
                overOption = null
            )
        }

        val plateHundredths = enabledPlates.map { (it * 100.0).roundToInt() }.filter { it > 0 }
        val g = plateHundredths.reduce(::gcdInt).coerceAtLeast(1)
        val unitPlates = plateHundredths.map { it / g }
        val maxUnitPlate = unitPlates.first()

        val floorPerSideHundredths = neededPlatesTotal / sides
        val floorUnits = floorPerSideHundredths / g
        val maxSearchUnits = floorUnits + maxUnitPlate + 1

        val inf = Int.MAX_VALUE / 4
        val minCount = IntArray(maxSearchUnits + 1) { inf }
        val firstPlateIdx = IntArray(maxSearchUnits + 1) { -1 }
        minCount[0] = 0

        // Iterating plates heaviest-first and updating on strict `<` guarantees:
        // (1) fewest total plates, and (2) lexicographically heaviest plates on ties.
        for (w in 1..maxSearchUnits) {
            var bestCount = inf
            var bestIdx = -1
            for (pIdx in unitPlates.indices) {
                val pUnits = unitPlates[pIdx]
                if (w >= pUnits) {
                    val prev = minCount[w - pUnits]
                    if (prev + 1 < bestCount) {
                        bestCount = prev + 1
                        bestIdx = pIdx
                    }
                }
            }
            minCount[w] = bestCount
            firstPlateIdx[w] = bestIdx
        }

        fun reconstruct(units: Int): List<PlateCount> {
            if (units <= 0) return emptyList()
            val counts = IntArray(enabledPlates.size)
            var rem = units
            while (rem > 0) {
                val idx = firstPlateIdx[rem]
                if (idx < 0) break
                counts[idx] += 1
                rem -= unitPlates[idx]
            }
            val result = ArrayList<PlateCount>()
            for (i in enabledPlates.indices) {
                if (counts[i] > 0) {
                    result.add(PlateCount(enabledPlates[i], counts[i]))
                }
            }
            return result
        }

        // Exact match check: only possible if neededPlatesTotal is divisible by (sides * g)
        val exactUnits = if (neededPlatesTotal % (sides * g) == 0) {
            val candidate = neededPlatesTotal / (sides * g)
            if (candidate in 0..maxSearchUnits && minCount[candidate] < inf) candidate else -1
        } else {
            -1
        }

        if (exactUnits >= 0) {
            val plates = reconstruct(exactUnits)
            val platesTotalHundredths = exactUnits * g * sides
            val achievedHundredths = barHundredths + platesTotalHundredths
            return PlateCalcResult(
                achievedTotal = achievedHundredths / 100.0,
                platesPerSide = plates,
                platesTotalWeight = platesTotalHundredths / 100.0,
                barUsed = barWeight,
                isBelowBar = false,
                isExactMatch = true,
                shortBy = 0.0,
                overOption = null
            )
        }

        // Closest achievable load below target
        var bestBelowUnits = 0
        for (w in floorUnits.coerceAtMost(maxSearchUnits) downTo 0) {
            if (minCount[w] < inf && w * g * sides <= neededPlatesTotal) {
                bestBelowUnits = w
                break
            }
        }
        val belowPlates = reconstruct(bestBelowUnits)
        val belowPlatesTotalHundredths = bestBelowUnits * g * sides
        val belowAchievedHundredths = barHundredths + belowPlatesTotalHundredths
        val shortHundredths = (targetHundredths - belowAchievedHundredths).coerceAtLeast(0)

        // Closest achievable load strictly above target
        var bestAboveUnits = -1
        val startAbove = (neededPlatesTotal / (sides * g)) + 1
        for (w in startAbove.coerceAtLeast(1)..maxSearchUnits) {
            if (minCount[w] < inf && w * g * sides > neededPlatesTotal) {
                bestAboveUnits = w
                break
            }
        }
        val overOption = if (bestAboveUnits > 0) {
            val overPlatesTotalHundredths = bestAboveUnits * g * sides
            val overAchievedHundredths = barHundredths + overPlatesTotalHundredths
            PlateLoadOption(
                totalWeight = overAchievedHundredths / 100.0,
                platesPerSide = reconstruct(bestAboveUnits),
                platesTotalWeight = overPlatesTotalHundredths / 100.0,
                diffFromTarget = (overAchievedHundredths - targetHundredths) / 100.0
            )
        } else {
            null
        }

        return PlateCalcResult(
            achievedTotal = belowAchievedHundredths / 100.0,
            platesPerSide = belowPlates,
            platesTotalWeight = belowPlatesTotalHundredths / 100.0,
            barUsed = barWeight,
            isBelowBar = false,
            isExactMatch = false,
            shortBy = shortHundredths / 100.0,
            overOption = overOption
        )
    }

    // ---- Plan Validation & Block-Drop Helpers ----

    fun isPlanValidToSave(
        name: String,
        useBlocks: Boolean,
        blocks: List<PlanBlock>
    ): Boolean {
        if (name.trim().isEmpty()) return false
        val effectiveBlocks = if (useBlocks) blocks else blocks.take(1)
        if (effectiveBlocks.isEmpty()) return false
        return effectiveBlocks.all { block ->
            block.workouts.isNotEmpty() &&
                block.workouts.all { slot -> slot.routineId != null || slot.label.trim().isNotEmpty() }
        }
    }

    fun droppedBlocksForSingleRepeating(
        useBlocks: Boolean,
        blocks: List<PlanBlock>
    ): List<String> {
        if (useBlocks || blocks.size <= 1) return emptyList()
        return blocks.drop(1).mapIndexed { idx, block ->
            block.name.trim().ifEmpty { "Block ${idx + 2}" }
        }
    }

    // ---- Plan Pause, Resume, Restart & Progress ----

    fun pausePlan(plan: TrainingPlan, nowMs: Long): TrainingPlan {
        val nowIso = formatIso(nowMs)
        if (plan.startedAt == null) {
            return plan.copy(
                isActive = false,
                pausedAt = null,
                updatedAt = nowIso,
                updatedAtMs = nowMs
            )
        }
        val existingPausedMs = parseIsoToMillis(plan.pausedAt)
        val hasOpenSpan = plan.pauses.any { it.endMs == null }
        val updatedPauses = when {
            hasOpenSpan -> plan.pauses
            existingPausedMs != null -> plan.pauses + PauseSpan(startMs = existingPausedMs, endMs = null)
            else -> plan.pauses + PauseSpan(startMs = nowMs, endMs = null)
        }
        return plan.copy(
            isActive = false,
            pausedAt = plan.pausedAt ?: nowIso,
            pauses = updatedPauses,
            updatedAt = nowIso,
            updatedAtMs = nowMs
        )
    }

    fun resumeOrActivatePlan(plan: TrainingPlan, nowMs: Long): TrainingPlan {
        val nowIso = formatIso(nowMs)
        if (plan.startedAt == null) {
            return plan.copy(
                isActive = true,
                startedAt = nowIso,
                pausedAt = null,
                pausedDurationMs = 0L,
                pauses = emptyList(),
                updatedAt = nowIso,
                updatedAtMs = nowMs
            )
        }
        var addedPause = 0L
        val hasOpenSpan = plan.pauses.any { it.endMs == null }
        val updatedPauses = if (hasOpenSpan) {
            plan.pauses.map { span ->
                if (span.endMs == null) {
                    val closedEnd = nowMs.coerceAtLeast(span.startMs)
                    addedPause += (closedEnd - span.startMs)
                    span.copy(endMs = closedEnd)
                } else {
                    span
                }
            }
        } else {
            val pausedMs = parseIsoToMillis(plan.pausedAt)
            if (pausedMs != null) {
                val closedEnd = nowMs.coerceAtLeast(pausedMs)
                addedPause = closedEnd - pausedMs
                plan.pauses + PauseSpan(startMs = pausedMs, endMs = closedEnd)
            } else {
                plan.pauses
            }
        }
        return plan.copy(
            isActive = true,
            pausedAt = null,
            pausedDurationMs = (plan.pausedDurationMs + addedPause).coerceAtLeast(0L),
            pauses = updatedPauses,
            updatedAt = nowIso,
            updatedAtMs = nowMs
        )
    }

    fun restartPlan(plan: TrainingPlan, nowMs: Long): TrainingPlan {
        val nowIso = formatIso(nowMs)
        return plan.copy(
            isActive = true,
            startedAt = nowIso,
            pausedAt = null,
            pausedDurationMs = 0L,
            pauses = emptyList(),
            updatedAt = nowIso,
            updatedAtMs = nowMs
        )
    }

    private fun buildMergedPauseSpans(
        plan: TrainingPlan,
        startedMs: Long,
        effectiveNowMs: Long
    ): Pair<List<Pair<Long, Long>>, Long> {
        val raw = ArrayList<Pair<Long, Long>>()
        var recordedClosedTotal = 0L
        var hasOpenSpan = false

        for (span in plan.pauses) {
            val s = span.startMs.coerceAtLeast(startedMs)
            val e = (span.endMs ?: effectiveNowMs).coerceAtLeast(s)
            if (span.endMs == null) {
                hasOpenSpan = true
            } else {
                recordedClosedTotal += (span.endMs - span.startMs).coerceAtLeast(0L)
            }
            if (e > s) {
                raw.add(s to e)
            }
        }

        if (!hasOpenSpan) {
            val legacyPausedMs = parseIsoToMillis(plan.pausedAt)
            if (legacyPausedMs != null) {
                val s = legacyPausedMs.coerceAtLeast(startedMs)
                val e = effectiveNowMs.coerceAtLeast(s)
                if (e > s) raw.add(s to e)
            }
        }

        val merged = if (raw.isEmpty()) {
            emptyList()
        } else {
            val sorted = raw.sortedBy { it.first }
            val out = ArrayList<Pair<Long, Long>>()
            var curStart = sorted[0].first
            var curEnd = sorted[0].second
            for (i in 1 until sorted.size) {
                val (s, e) = sorted[i]
                if (s <= curEnd) {
                    curEnd = maxOf(curEnd, e)
                } else {
                    out.add(curStart to curEnd)
                    curStart = s
                    curEnd = e
                }
            }
            out.add(curStart to curEnd)
            out
        }

        // Preserve any legacy accumulated pausedDurationMs recorded before PauseSpan intervals existed
        val legacyUnrecordedMs = (plan.pausedDurationMs.coerceAtLeast(0L) - recordedClosedTotal).coerceAtLeast(0L)
        return merged to legacyUnrecordedMs
    }

    private fun activeElapsedMsAt(
        timestampMs: Long,
        startedMs: Long,
        mergedPauseSpans: List<Pair<Long, Long>>,
        legacyUnrecordedPauseMs: Long
    ): Long {
        if (timestampMs <= startedMs) return 0L
        var pausedBefore = 0L
        for ((spanStart, spanEnd) in mergedPauseSpans) {
            if (spanStart >= timestampMs) break
            val overlapStart = maxOf(spanStart, startedMs)
            val overlapEnd = minOf(spanEnd, timestampMs)
            if (overlapEnd > overlapStart) {
                pausedBefore += (overlapEnd - overlapStart)
            }
        }
        return (timestampMs - startedMs - pausedBefore - legacyUnrecordedPauseMs).coerceAtLeast(0L)
    }

    fun computePlanProgress(
        plan: TrainingPlan,
        history: List<CompletedWorkout>,
        routines: List<Routine>,
        nowMs: Long = System.currentTimeMillis()
    ): PlanProgressSummary {
        val effectiveBlocks = if (plan.useBlocks) plan.blocks else plan.blocks.take(1)
        val totalWeeks = effectiveBlocks.sumOf { it.weeks.coerceAtLeast(1) }.coerceAtLeast(1)
        val totalSessions = effectiveBlocks.sumOf { block ->
            block.weeks.coerceAtLeast(1) * block.workouts.sumOf { it.timesPerWeek.coerceAtLeast(1) }
        }

        val startedMs = parseIsoToMillis(plan.startedAt)
        val isPaused = !plan.isActive && startedMs != null
        val openSpanStartMs = plan.pauses.lastOrNull { it.endMs == null }?.startMs
        val pausedAtMs = openSpanStartMs ?: parseIsoToMillis(plan.pausedAt)
        val effectiveNowMs = if (isPaused) (pausedAtMs ?: nowMs) else nowMs

        val (mergedPauseSpans, legacyUnrecordedPauseMs) = if (startedMs != null) {
            buildMergedPauseSpans(plan, startedMs, effectiveNowMs)
        } else {
            emptyList<Pair<Long, Long>>() to 0L
        }

        val elapsedWeeks = if ((plan.isActive || isPaused) && startedMs != null) {
            val activeElapsedMs = activeElapsedMsAt(
                timestampMs = effectiveNowMs,
                startedMs = startedMs,
                mergedPauseSpans = mergedPauseSpans,
                legacyUnrecordedPauseMs = legacyUnrecordedPauseMs
            )
            floor(activeElapsedMs.toDouble() / SEVEN_DAYS_MS.toDouble()).toInt()
        } else {
            0
        }
        val allWeeksReached = (plan.isActive || isPaused) && startedMs != null && elapsedWeeks >= totalWeeks
        val currentWeek = (elapsedWeeks + 1).coerceIn(1, totalWeeks)

        val blockInfos = ArrayList<BlockProgressInfo>()
        var cursorWeek = 1
        var activeBlock: PlanBlock? = null
        for (block in effectiveBlocks) {
            val w = block.weeks.coerceAtLeast(1)
            val startW = cursorWeek
            val endW = cursorWeek + w - 1
            val isCurr = plan.isActive && !allWeeksReached && currentWeek in startW..endW
            if (isCurr) activeBlock = block
            val fill = when {
                !plan.isActive -> 0f
                allWeeksReached -> 1f
                currentWeek > endW -> 1f
                currentWeek < startW -> 0f
                else -> ((currentWeek - startW + 1).toFloat() / w.toFloat()).coerceIn(0f, 1f)
            }
            blockInfos.add(BlockProgressInfo(block, startW, endW, fill, isCurr))
            cursorWeek += w
        }
        if (activeBlock == null && plan.isActive) {
            activeBlock = effectiveBlocks.lastOrNull()
        }

        val slotWeeklyDone = HashMap<String, Int>()
        var weekDone = 0
        var weekTarget = 0
        if (plan.isActive && startedMs != null && activeBlock != null) {
            val weekWorkouts = history.filter { w ->
                val t = w.completedAtMs
                if (t <= 0L || t < startedMs || t > effectiveNowMs) {
                    false
                } else {
                    val workoutActiveElapsed = activeElapsedMsAt(
                        timestampMs = t,
                        startedMs = startedMs,
                        mergedPauseSpans = mergedPauseSpans,
                        legacyUnrecordedPauseMs = legacyUnrecordedPauseMs
                    )
                    val workoutWeek = floor(workoutActiveElapsed.toDouble() / SEVEN_DAYS_MS.toDouble()).toInt() + 1
                    workoutWeek == currentWeek
                }
            }
            for (slot in activeBlock.workouts) {
                val linkedRoutine = routines.find { it.id == slot.routineId }
                val count = weekWorkouts.count { completed ->
                    if (linkedRoutine != null) {
                        matchesRoutineHistory(completed, linkedRoutine)
                    } else if (slot.routineId != null) {
                        completed.routineId == slot.routineId
                    } else {
                        exerciseKey(completed.routineName) == exerciseKey(slot.label)
                    }
                }
                slotWeeklyDone[slot.id] = count
                val target = slot.timesPerWeek.coerceAtLeast(1)
                weekTarget += target
                weekDone += min(count, target)
            }
        }

        return PlanProgressSummary(
            totalWeeks = totalWeeks,
            totalSessions = totalSessions,
            currentWeek = currentWeek,
            allWeeksReached = allWeeksReached,
            isPaused = isPaused,
            currentBlock = activeBlock,
            blockInfos = blockInfos,
            weekSessionsDone = weekDone,
            weekSessionsTarget = weekTarget,
            slotWeeklyDone = slotWeeklyDone
        )
    }

    // ---- History Matching, Previous Session & Sorting ----

    fun matchesRoutineHistory(workout: CompletedWorkout, routine: Routine): Boolean {
        if (workout.routineId != null && workout.routineId == routine.id) return true
        return exerciseKey(workout.routineName) == exerciseKey(routine.name)
    }

    fun matchesRoutineFilter(
        workout: CompletedWorkout,
        filterRoutineId: String?,
        filterRoutineName: String?
    ): Boolean {
        if (filterRoutineId == null && filterRoutineName == null) return true
        if (filterRoutineId != null && workout.routineId == filterRoutineId) return true
        if (filterRoutineName != null && exerciseKey(workout.routineName) == exerciseKey(filterRoutineName)) return true
        return false
    }

    fun buildLastSessionByExercise(
        history: List<CompletedWorkout>
    ): Map<String, PreviousExerciseSession> {
        if (history.isEmpty()) return emptyMap()
        val bestByKey = HashMap<String, PreviousExerciseSession>()
        for (workout in history) {
            val workoutMs = workout.completedAtMs
            for (exercise in workout.exercises) {
                if (exercise.sets.isEmpty()) continue
                val key = exerciseKey(exercise.name)
                if (key.isEmpty()) continue
                val existing = bestByKey[key]
                if (existing == null || workoutMs > existing.completedAtMs) {
                    bestByKey[key] = PreviousExerciseSession(
                        completedAt = workout.completedAt,
                        unit = workout.unit,
                        sets = exercise.sets,
                        completedAtMs = workoutMs
                    )
                }
            }
        }
        return bestByKey
    }

    fun findPreviousExerciseSession(
        exerciseName: String,
        lastSessionByExercise: Map<String, PreviousExerciseSession>
    ): PreviousExerciseSession? {
        val key = exerciseKey(exerciseName)
        if (key.isEmpty()) return null
        return lastSessionByExercise[key]
    }

    fun findPreviousExerciseSession(
        exerciseName: String,
        history: List<CompletedWorkout>
    ): PreviousExerciseSession? {
        val key = exerciseKey(exerciseName)
        if (key.isEmpty()) return null
        var best: PreviousExerciseSession? = null
        for (workout in history) {
            val workoutMs = workout.completedAtMs
            if (best != null && workoutMs <= best.completedAtMs) continue
            val match = workout.exercises.find { exerciseKey(it.name) == key && it.sets.isNotEmpty() }
            if (match != null) {
                best = PreviousExerciseSession(
                    completedAt = workout.completedAt,
                    unit = workout.unit,
                    sets = match.sets,
                    completedAtMs = workoutMs
                )
            }
        }
        return best
    }

    fun sortRoutines(
        routines: List<Routine>,
        history: List<CompletedWorkout>,
        config: RoutineSortConfig
    ): List<Routine> {
        val lastDoneMap = routines.associate { r ->
            var latest: Long? = null
            for (workout in history) {
                if (matchesRoutineHistory(workout, r)) {
                    val ms = workout.completedAtMs
                    if (latest == null || ms > latest) {
                        latest = ms
                    }
                }
            }
            r.id to latest
        }
        val nameCmp = Comparator<Routine> { a, b ->
            a.name.lowercase(Locale.US).compareTo(b.name.lowercase(Locale.US))
        }
        val comparator = Comparator<Routine> { a, b ->
            val primary = when (config.field) {
                RoutineSortField.NAME -> nameCmp.compare(a, b)
                RoutineSortField.CREATED_AT -> a.createdAtMs.compareTo(b.createdAtMs)
                RoutineSortField.LAST_COMPLETED_AT -> {
                    val la = lastDoneMap[a.id]
                    val lb = lastDoneMap[b.id]
                    when {
                        la == null && lb == null -> 0
                        la == null -> if (config.direction == SortDirection.DESC) -1 else 1
                        lb == null -> if (config.direction == SortDirection.DESC) 1 else -1
                        else -> la.compareTo(lb)
                    }
                }
            }
            val directed = if (config.direction == SortDirection.ASC) primary else -primary
            if (directed != 0) directed else nameCmp.compare(a, b)
        }
        return routines.sortedWith(comparator)
    }

    fun nextDuplicateName(baseName: String, existingNames: Collection<String>): String {
        val cleanBase = baseName.trim().replace(Regex("\\s*\\(Copy(?:\\s+\\d+)?\\)$", RegexOption.IGNORE_CASE), "")
        val existingKeys = existingNames.map { it.trim().lowercase(Locale.US) }.toSet()
        var candidate = "$cleanBase (Copy)"
        if (!existingKeys.contains(candidate.lowercase(Locale.US))) return candidate
        var n = 2
        while (true) {
            candidate = "$cleanBase (Copy $n)"
            if (!existingKeys.contains(candidate.lowercase(Locale.US))) return candidate
            n++
        }
    }

    // ---- Active Workout Creation & Volume ----

    /**
     * Derives a deterministic [CompletedWorkout.id] from an [ActiveWorkout.id] so finishing
     * the same session twice (e.g., after a crash between saving history and clearing DataStore)
     * upserts cleanly instead of creating duplicate history rows.
     */
    fun completedWorkoutIdForActive(activeWorkoutId: String): String {
        val clean = activeWorkoutId.trim()
        return if (clean.isEmpty()) newId("cw") else "cw-$clean"
    }

    fun formatLiveWorkoutDefaultName(startedAtMs: Long = System.currentTimeMillis()): String {
        return "${liveWorkoutDateFormat.get()!!.format(Date(startedAtMs))}, live workout"
    }

    fun isLiveWorkoutName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.endsWith(", live workout", ignoreCase = true) ||
            trimmed.equals("Live Workout", ignoreCase = true)
    }

    fun buildEmptyLiveWorkout(
        startedAtMs: Long = System.currentTimeMillis(),
        unit: WeightUnit = WeightUnit.LB
    ): ActiveWorkout {
        return ActiveWorkout(
            id = newId("aw"),
            routineId = null,
            routineName = "Live Workout",
            startedAt = startedAtMs,
            exercises = emptyList(),
            notes = "",
            unit = unit
        )
    }

    fun buildActiveWorkoutFromRoutine(
        routine: Routine,
        defaultRest: Int,
        unit: WeightUnit = WeightUnit.LB
    ): ActiveWorkout {
        val exercises = routine.exercises.map { ex ->
            val planned = expandPlannedSets(ex, defaultRest)
            val sets = planned.mapIndexed { idx, ps ->
                WorkoutSet(
                    id = newId("ws$idx"),
                    weight = "",
                    reps = "",
                    completed = false,
                    completedAt = null,
                    tag = ps.tag,
                    workingSubTag = ps.workingSubTag,
                    targetRepRange = ps.repRange,
                    targetRpe = ps.targetRpe,
                    restSeconds = ps.restSeconds,
                    rpe = null
                )
            }
            ActiveExercise(
                id = newId("ae"),
                name = ex.name,
                targetSets = sets.size,
                repRange = ex.repRange,
                targetRpe = ex.targetRpe,
                restSeconds = ex.restSeconds ?: defaultRest,
                sets = enforceDropSetRestOnActiveSets(sets, ex.restSeconds ?: defaultRest)
            )
        }
        return ActiveWorkout(
            id = newId("aw"),
            routineId = routine.id,
            routineName = routine.name,
            startedAt = System.currentTimeMillis(),
            exercises = exercises,
            notes = "",
            unit = unit
        )
    }

    fun buildRoutineFromActiveWorkout(
        workout: ActiveWorkout,
        routineName: String,
        defaultRest: Int
    ): Routine {
        val nowMs = System.currentTimeMillis()
        val now = formatIso(nowMs)
        val exercises = workout.exercises.mapNotNull { ex ->
            val completedSets = ex.sets.filter { it.completed }
            if (completedSets.isEmpty()) return@mapNotNull null
            val exRest = ex.restSeconds ?: defaultRest
            val planned = completedSets.mapIndexed { idx, s ->
                val repVal = s.reps.toIntOrNull()?.takeIf { it > 0 }?.toString()
                    ?: s.targetRepRange?.ifBlank { null }
                    ?: ex.repRange.ifBlank { null }
                val rpeVal = s.rpe?.let { formatRpeNumber(it) } ?: s.targetRpe?.ifBlank { null }
                PlannedSet(
                    id = newId("ps$idx"),
                    tag = s.tag,
                    workingSubTag = if (s.tag == SetTag.WORKING) s.workingSubTag else null,
                    repRange = repVal,
                    targetRpe = rpeVal,
                    restSeconds = (s.restSeconds ?: exRest).coerceIn(0, 600)
                )
            }
            withDerivedSummary(
                RoutineExercise(
                    id = newId("ex"),
                    name = normalizeExerciseName(ex.name),
                    targetSets = planned.size,
                    repRange = "",
                    targetRpe = null,
                    restSeconds = exRest,
                    plannedSets = enforceDropSetRestRules(planned, exRest)
                )
            )
        }
        return Routine(
            id = newId("rt"),
            name = routineName.trim(),
            focus = null,
            description = null,
            exercises = exercises,
            createdAt = now,
            updatedAt = now,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
    }

    fun buildRoutineDraftFromHistory(
        workout: CompletedWorkout,
        defaultRest: Int,
        prefillName: Boolean = false
    ): Routine {
        val nowMs = System.currentTimeMillis()
        val now = formatIso(nowMs)
        val exercises = workout.exercises.map { ex ->
            val planned = ex.sets.mapIndexed { idx, s ->
                val repVal = s.reps.takeIf { it > 0 }?.toString() ?: ex.repRange?.ifBlank { null }
                PlannedSet(
                    id = newId("ps$idx"),
                    tag = s.tag,
                    workingSubTag = if (s.tag == SetTag.WORKING) s.workingSubTag else null,
                    repRange = repVal,
                    targetRpe = s.rpe?.let { formatRpeNumber(it) },
                    restSeconds = defaultRest
                )
            }.ifEmpty {
                listOf(PlannedSet(newId("ps0"), null, null, "8-10", null, defaultRest))
            }
            withDerivedSummary(
                RoutineExercise(
                    id = newId("ex"),
                    name = normalizeExerciseName(ex.name),
                    targetSets = planned.size,
                    repRange = ex.repRange ?: "",
                    targetRpe = null,
                    restSeconds = defaultRest,
                    plannedSets = enforceDropSetRestRules(planned, defaultRest)
                )
            )
        }
        val suggestedName = if (prefillName && !isLiveWorkoutName(workout.routineName)) {
            workout.routineName
        } else {
            ""
        }
        return Routine(
            id = newId("rt"),
            name = suggestedName,
            focus = null,
            description = null,
            exercises = exercises,
            createdAt = now,
            updatedAt = now,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
    }

    fun buildSyntheticRoutineFromHistory(workout: CompletedWorkout, defaultRest: Int): Routine {
        val nowMs = System.currentTimeMillis()
        val now = formatIso(nowMs)
        val exercises = workout.exercises.map { ex ->
            val planned = ex.sets.mapIndexed { idx, s ->
                PlannedSet(
                    id = newId("syn$idx"),
                    tag = s.tag,
                    workingSubTag = s.workingSubTag,
                    repRange = s.reps.takeIf { it > 0 }?.toString() ?: ex.repRange?.ifBlank { null },
                    targetRpe = s.rpe?.let { formatRpeNumber(it) },
                    restSeconds = defaultRest
                )
            }.ifEmpty {
                listOf(PlannedSet(newId("syn0"), null, null, "8-10", null, defaultRest))
            }
            withDerivedSummary(
                RoutineExercise(
                    id = newId("synex"),
                    name = ex.name,
                    targetSets = planned.size,
                    repRange = ex.repRange ?: "",
                    targetRpe = null,
                    restSeconds = defaultRest,
                    plannedSets = enforceDropSetRestRules(planned, defaultRest)
                )
            )
        }
        return Routine(
            id = workout.routineId ?: newId("syn-rt"),
            name = workout.routineName,
            focus = null,
            description = null,
            exercises = exercises,
            createdAt = now,
            updatedAt = now,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
    }

    fun computeLiveVolume(workout: ActiveWorkout): Int {
        var sum = 0.0
        for (ex in workout.exercises) {
            for (s in ex.sets) {
                if (!s.completed) continue
                val w = s.weight.toDoubleOrNull() ?: 0.0
                val r = s.reps.toIntOrNull() ?: 0
                sum += w * r
            }
        }
        return sum.roundToInt()
    }

    fun computeCompletedVolume(exercises: List<CompletedExercise>): Int {
        var sum = 0.0
        for (ex in exercises) {
            for (s in ex.sets) {
                sum += s.weight * s.reps
            }
        }
        return sum.roundToInt()
    }

    // ---- Input Sanitization & Formatting Helpers ----

    /**
     * Accepts digits and either '.' or ',' as a decimal separator, normalizing ',' to '.'
     * and keeping at most one decimal point.
     */
    fun sanitizeDecimalInput(raw: String, maxLength: Int = 8): String {
        val normalized = raw.replace(',', '.')
        val sb = StringBuilder(normalized.length.coerceAtMost(maxLength))
        var seenDot = false
        for (ch in normalized) {
            when {
                ch.isDigit() -> sb.append(ch)
                ch == '.' && !seenDot -> {
                    seenDot = true
                    sb.append('.')
                }
            }
            if (sb.length >= maxLength) break
        }
        return sb.toString()
    }

    fun formatRestTime(seconds: Int): String {
        val clamped = seconds.coerceAtLeast(0)
        val m = clamped / 60
        val s = clamped % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatElapsed(seconds: Long): String {
        val clamped = seconds.coerceAtLeast(0L)
        val h = clamped / 3600
        val m = (clamped % 3600) / 60
        val s = clamped % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            val s = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
            s
        }
    }

    fun formatVolume(volume: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(volume)

    private fun localDateToEpochDay(epochMs: Long, timeZone: TimeZone): Long {
        val localCal = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                12,
                0,
                0
            )
        }
        return utcCal.timeInMillis / 86_400_000L
    }

    fun calendarDaysBetween(
        earlierMs: Long,
        laterMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        return (localDateToEpochDay(laterMs, timeZone) - localDateToEpochDay(earlierMs, timeZone)).toInt()
    }

    fun millisUntilNextLocalMidnight(
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return (cal.timeInMillis - nowMs).coerceAtLeast(1_000L)
    }

    fun formatRelativeDate(
        iso: String?,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val ms = parseIsoToMillis(iso) ?: return ""
        return formatRelativeDateMs(ms, nowMs, timeZone)
    }

    fun formatRelativeDateMs(
        epochMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (epochMs <= 0L) return ""
        val days = calendarDaysBetween(epochMs, nowMs, timeZone).coerceAtLeast(0)
        return when {
            days == 0 -> "Today"
            days == 1 -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "1 week ago"
            days < 30 -> "${days / 7} weeks ago"
            else -> {
                val fmt = SimpleDateFormat("MMM d", Locale.US).apply { this.timeZone = timeZone }
                fmt.format(Date(epochMs))
            }
        }
    }

    fun formatCompletedDateTime(iso: String): String {
        val ms = parseIsoToMillis(iso) ?: return iso
        return formatCompletedDateTimeMs(ms)
    }

    fun formatCompletedDateTimeMs(epochMs: Long): String {
        val date = Date(epochMs)
        return "${completedDateFormat.get()!!.format(date)} · ${completedTimeFormat.get()!!.format(date)}"
    }

    fun remainingRestSeconds(endTimeMs: Long?, nowMs: Long = System.currentTimeMillis()): Int {
        if (endTimeMs == null) return 0
        val diff = endTimeMs - nowMs
        if (diff <= 0L) return 0
        return ceil(diff / 1000.0).toInt()
    }
}
