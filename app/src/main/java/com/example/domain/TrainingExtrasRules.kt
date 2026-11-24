package com.example.domain

import com.example.data.model.ActiveWorkout
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.LoadType
import com.example.data.model.SetTag
import com.example.data.model.WeightUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

// =====================================================================================
// Weight type per exercise (total / per hand / bodyweight / assisted)
// =====================================================================================

object LoadTypeRules {

    private val bodyweightPhrases = listOf(
        "pull-up", "pull up", "pullup", "chin-up", "chin up", "chinup",
        "push-up", "push up", "pushup", "muscle-up", "muscle up",
        "inverted row", "hanging leg raise", "hanging knee raise",
        "pistol squat", "nordic", "sit-up", "sit up", "situp"
    )
    private val singleArmPhrases = listOf(
        "single-arm", "single arm", "one-arm", "one arm", "1-arm", "unilateral"
    )
    // Dumbbell exercises that use a single dumbbell held with both hands.
    private val singleImplementPhrases = listOf("pullover", "goblet")

    /** Best guess from the exercise name. The user can override it per exercise. */
    fun infer(name: String): LoadType {
        val n = " " + RepDayRules.exerciseKey(name) + " "
        if (n.isBlank()) return LoadType.STANDARD
        if (n.contains("assisted")) return LoadType.ASSISTED
        val words = n.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        if (bodyweightPhrases.any { n.contains(it) } || "dip" in words || "dips" in words) {
            return LoadType.BODYWEIGHT
        }
        val isDumbbell = n.contains("dumbbell") || "db" in words || n.contains("hammer curl")
        if (isDumbbell &&
            singleArmPhrases.none { n.contains(it) } &&
            singleImplementPhrases.none { n.contains(it) }
        ) {
            return LoadType.PER_HAND
        }
        return LoadType.STANDARD
    }

    /** The user's choice for this exercise if they made one, otherwise the guess. */
    fun resolve(name: String, overrides: Map<String, LoadType>): LoadType =
        overrides[RepDayRules.exerciseKey(name)] ?: infer(name)

    /**
     * Returns the override map with [type] saved for [name]. Choices that match the guess are
     * still stored, so a later change to the guessing rules can't silently change them.
     */
    fun withOverride(overrides: Map<String, LoadType>, name: String, type: LoadType): Map<String, LoadType> {
        val key = RepDayRules.exerciseKey(name)
        if (key.isEmpty()) return overrides
        return overrides + (key to type)
    }

    fun needsBodyWeight(type: LoadType): Boolean =
        type == LoadType.BODYWEIGHT || type == LoadType.ASSISTED

    /**
     * The weight actually moved per rep. [bodyWeight] is in the same unit as [entered].
     * Without a body weight, bodyweight sets count only the added weight and assisted sets count 0.
     */
    fun effectiveLoad(entered: Double, type: LoadType, bodyWeight: Double?): Double {
        val w = max(0.0, entered)
        return when (type) {
            LoadType.STANDARD -> w
            LoadType.PER_HAND -> w * 2.0
            LoadType.BODYWEIGHT -> (bodyWeight ?: 0.0) + w
            LoadType.ASSISTED -> if (bodyWeight == null) 0.0 else max(0.0, bodyWeight - w)
        }
    }

    fun setVolume(weight: Double, reps: Int, type: LoadType, bodyWeight: Double?): Double =
        if (reps <= 0) 0.0 else effectiveLoad(weight, type, bodyWeight) * reps

    /** Column header above the weight boxes, e.g. "lb", "lb ea", "+lb", "−lb". */
    fun weightColumnLabel(unit: WeightUnit, type: LoadType): String = when (type) {
        LoadType.STANDARD -> unit.label
        LoadType.PER_HAND -> "${unit.label} ea"
        LoadType.BODYWEIGHT -> "+${unit.label}"
        LoadType.ASSISTED -> "−${unit.label}"
    }

    /** Placeholder for an empty weight box when there's no previous value to show. */
    fun emptyWeightPlaceholder(type: LoadType): String =
        if (type == LoadType.BODYWEIGHT) "BW" else "0"

    /** Full form with unit: "185 lb", "40 lb ea", "BW", "BW+25 lb", "BW−40 lb". */
    fun formatLoggedWeight(weight: Double, unit: WeightUnit, type: LoadType): String {
        val w = RepDayRules.formatNumber(weight)
        val u = unit.label
        return when (type) {
            LoadType.STANDARD -> "$w $u"
            LoadType.PER_HAND -> "$w $u ea"
            LoadType.BODYWEIGHT -> if (weight <= 0.0) "BW" else "BW+$w $u"
            LoadType.ASSISTED -> if (weight <= 0.0) "BW" else "BW−$w $u"
        }
    }

    /** Short form without unit for set lists such as "40 ea×10" or "BW+25×6". */
    fun formatCompactWeight(weightText: String, weight: Double, type: LoadType): String = when (type) {
        LoadType.STANDARD -> weightText
        LoadType.PER_HAND -> "$weightText ea"
        LoadType.BODYWEIGHT -> if (weight <= 0.0) "BW" else "BW+$weightText"
        LoadType.ASSISTED -> if (weight <= 0.0) "BW" else "BW−$weightText"
    }

    /** Same as [formatCompactWeight] but with the unit, e.g. "185lb", "40lb ea", "BW+25lb". */
    fun formatCompactWithUnit(weightText: String, weight: Double, unit: WeightUnit, type: LoadType): String = when (type) {
        LoadType.STANDARD -> "$weightText${unit.label}"
        LoadType.PER_HAND -> "$weightText${unit.label} ea"
        LoadType.BODYWEIGHT -> if (weight <= 0.0) "BW" else "BW+$weightText${unit.label}"
        LoadType.ASSISTED -> if (weight <= 0.0) "BW" else "BW−$weightText${unit.label}"
    }

    /** Short label for buttons. */
    fun shortLabel(type: LoadType): String = when (type) {
        LoadType.STANDARD -> "Total"
        LoadType.PER_HAND -> "Per hand"
        LoadType.BODYWEIGHT -> "Bodyweight"
        LoadType.ASSISTED -> "Assisted"
    }

    /** Body weight (stored in kg) in [unit], rounded to 0.1. */
    fun bodyWeightIn(bodyWeightKg: Double?, unit: WeightUnit): Double? =
        bodyWeightKg?.takeIf { it > 0.0 }?.let {
            RepDayRules.roundToTenth(RepDayRules.convertWeight(it, WeightUnit.KG, unit))
        }

    fun bodyWeightToKg(value: Double?, unit: WeightUnit): Double? =
        value?.takeIf { it > 0.0 }?.let { RepDayRules.convertWeight(it, unit, WeightUnit.KG) }
}

// =====================================================================================
// Finishing a workout: build history entries, then look for personal bests
// =====================================================================================

object FinishWorkoutRules {

    /** Completed sets of each exercise, with the weight type recorded. Exercises with no completed sets are left out. */
    fun buildCompletedExercises(
        active: ActiveWorkout,
        loadTypes: Map<String, LoadType>
    ): List<CompletedExercise> = active.exercises.mapNotNull { ex ->
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
            sets = doneSets,
            loadType = LoadTypeRules.resolve(ex.name, loadTypes)
        )
    }

    /** Sets of one exercise for the finish review, e.g. "185×8, 185×7 lb" or "BW×12, BW+25×6 lb". */
    fun describeSets(ex: CompletedExercise, unit: WeightUnit): String {
        val parts = ex.sets.joinToString(", ") { s ->
            LoadTypeRules.formatCompactWeight(RepDayRules.formatNumber(s.weight), s.weight, ex.loadType) + "×" + s.reps
        }
        val bodyweightOnly = LoadTypeRules.needsBodyWeight(ex.loadType) && ex.sets.all { it.weight <= 0.0 }
        return if (bodyweightOnly) parts else "$parts ${unit.label}"
    }
}

enum class PersonalBestKind(val label: String) {
    HEAVIEST("Heaviest weight"),
    EST_1RM("Best estimated 1-rep max"),
    MOST_REPS("Most reps")
}

data class PersonalBest(
    val exerciseName: String,
    val kind: PersonalBestKind,
    /** Weight for HEAVIEST, estimated 1RM for EST_1RM, reps for MOST_REPS. */
    val value: Double,
    val previousBest: Double,
    /** The set that set the record. */
    val weight: Double,
    val reps: Int,
    val unit: WeightUnit,
    val loadType: LoadType
)

object PersonalBestRules {

    /** Estimates above 12 reps aren't reliable, so those sets don't count toward 1RM records. */
    const val MAX_REPS_FOR_ESTIMATE = 12
    private const val EPS = 0.01

    /** Epley formula: weight × (1 + reps / 30). One rep returns the weight itself. */
    fun estimatedOneRepMax(weight: Double, reps: Int): Double = when {
        weight <= 0.0 || reps <= 0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1.0 + reps / 30.0)
    }

    private fun countsForRecords(s: CompletedSet): Boolean = s.tag != SetTag.WARMUP && s.reps > 0

    private data class Bests(var heaviest: Double = -1.0, var e1rm: Double = -1.0, var mostBwReps: Int = -1)

    private fun accumulate(b: Bests, weight: Double, reps: Int, type: LoadType) {
        when (type) {
            LoadType.STANDARD, LoadType.PER_HAND -> {
                b.heaviest = max(b.heaviest, weight)
                if (reps <= MAX_REPS_FOR_ESTIMATE) b.e1rm = max(b.e1rm, estimatedOneRepMax(weight, reps))
            }
            LoadType.BODYWEIGHT -> {
                if (weight > 0.0) b.heaviest = max(b.heaviest, weight) else b.mostBwReps = max(b.mostBwReps, reps)
            }
            LoadType.ASSISTED -> Unit // lower assistance is better; not tracked as a record
        }
    }

    /**
     * Records set by [current] compared with earlier [history]. Only exercises with earlier
     * history of the same weight type count, so a first-ever session isn't reported as a record.
     * At most one record per exercise: heaviest weight, else estimated 1RM, else most reps.
     */
    fun find(
        current: List<CompletedExercise>,
        currentUnit: WeightUnit,
        history: List<CompletedWorkout>,
        excludeWorkoutId: String? = null
    ): List<PersonalBest> {
        if (current.isEmpty() || history.isEmpty()) return emptyList()
        val wanted = current.associateBy({ RepDayRules.exerciseKey(it.name) }, { it.loadType })
        val baseline = HashMap<String, Bests>()
        for (w in history) {
            if (excludeWorkoutId != null && w.id == excludeWorkoutId) continue
            for (ex in w.exercises) {
                val key = RepDayRules.exerciseKey(ex.name)
                val type = wanted[key] ?: continue
                if (ex.loadType != type) continue
                val b = baseline.getOrPut(key) { Bests() }
                for (s in ex.sets) {
                    if (!countsForRecords(s)) continue
                    accumulate(b, RepDayRules.convertWeight(s.weight, w.unit, currentUnit), s.reps, type)
                }
            }
        }

        val result = ArrayList<PersonalBest>()
        for (ex in current) {
            val key = RepDayRules.exerciseKey(ex.name)
            val before = baseline[key] ?: continue
            val sets = ex.sets.filter(::countsForRecords)
            if (sets.isEmpty()) continue

            fun pb(kind: PersonalBestKind, value: Double, prev: Double, s: CompletedSet) = PersonalBest(
                exerciseName = ex.name, kind = kind, value = value, previousBest = prev,
                weight = s.weight, reps = s.reps, unit = currentUnit, loadType = ex.loadType
            )

            val record: PersonalBest? = when (ex.loadType) {
                LoadType.STANDARD, LoadType.PER_HAND -> {
                    val heaviestSet = sets.maxWithOrNull(compareBy<CompletedSet> { it.weight }.thenBy { it.reps })!!
                    val e1rmSet = sets.filter { it.reps <= MAX_REPS_FOR_ESTIMATE }
                        .maxByOrNull { estimatedOneRepMax(it.weight, it.reps) }
                    when {
                        before.heaviest >= 0 && heaviestSet.weight > before.heaviest + EPS ->
                            pb(PersonalBestKind.HEAVIEST, heaviestSet.weight, before.heaviest, heaviestSet)
                        e1rmSet != null && before.e1rm >= 0 &&
                            estimatedOneRepMax(e1rmSet.weight, e1rmSet.reps) > before.e1rm + EPS ->
                            pb(PersonalBestKind.EST_1RM, estimatedOneRepMax(e1rmSet.weight, e1rmSet.reps), before.e1rm, e1rmSet)
                        else -> null
                    }
                }
                LoadType.BODYWEIGHT -> {
                    val added = sets.filter { it.weight > 0.0 }.maxWithOrNull(compareBy<CompletedSet> { it.weight }.thenBy { it.reps })
                    val bwOnly = sets.filter { it.weight <= 0.0 }.maxByOrNull { it.reps }
                    when {
                        added != null && before.heaviest >= 0 && added.weight > before.heaviest + EPS ->
                            pb(PersonalBestKind.HEAVIEST, added.weight, before.heaviest, added)
                        bwOnly != null && before.mostBwReps >= 0 && bwOnly.reps > before.mostBwReps ->
                            pb(PersonalBestKind.MOST_REPS, bwOnly.reps.toDouble(), before.mostBwReps.toDouble(), bwOnly)
                        else -> null
                    }
                }
                LoadType.ASSISTED -> null
            }
            record?.let { result.add(it) }
        }
        return result
    }

    /** One line for the summary, e.g. "Bench Press · Heaviest weight: 195 lb (was 185)". */
    fun describe(pb: PersonalBest): String {
        val prev = RepDayRules.formatNumber(RepDayRules.roundToTenth(pb.previousBest))
        return when (pb.kind) {
            PersonalBestKind.HEAVIEST ->
                "${pb.exerciseName} · ${pb.kind.label}: ${LoadTypeRules.formatLoggedWeight(pb.weight, pb.unit, pb.loadType)} × ${pb.reps} (was $prev)"
            PersonalBestKind.EST_1RM ->
                "${pb.exerciseName} · ${pb.kind.label}: ${RepDayRules.formatNumber(RepDayRules.roundToTenth(pb.value))} ${pb.unit.label} " +
                    "from ${LoadTypeRules.formatLoggedWeight(pb.weight, pb.unit, pb.loadType)} × ${pb.reps} (was $prev)"
            PersonalBestKind.MOST_REPS ->
                "${pb.exerciseName} · ${pb.kind.label}: ${pb.reps} at body weight (was $prev)"
        }
    }
}

// =====================================================================================
// Automatic backups to a folder the user picked
// =====================================================================================

object AutoBackupRules {
    const val FILE_PREFIX = "RepDay-auto-backup-"
    const val FILE_SUFFIX = ".json"
    const val KEEP_COUNT = 10
    const val DAILY_INTERVAL_MS = 24L * 60L * 60L * 1000L

    /** Sortable name, e.g. RepDay-auto-backup-2026-11-24_0715.json (local time). */
    fun fileName(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).apply { timeZone = zone }
        return FILE_PREFIX + fmt.format(Date(nowMs)) + FILE_SUFFIX
    }

    /** Daily check when the app opens. Finishing a workout always backs up (if enabled). */
    fun isDailyBackupDue(enabled: Boolean, folderUri: String?, lastBackupMs: Long, nowMs: Long): Boolean =
        enabled && !folderUri.isNullOrBlank() && (lastBackupMs <= 0L || nowMs - lastBackupMs >= DAILY_INTERVAL_MS)

    /** Older automatic backups to remove, keeping the newest [keep]. Other files are never touched. */
    fun filesToDelete(existingNames: List<String>, keep: Int = KEEP_COUNT): List<String> =
        existingNames
            .filter { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) }
            .sortedDescending()
            .drop(keep)
}

// =====================================================================================
// Exercise search: recent and frequent first, tolerant of typos
// =====================================================================================

data class ExerciseUsage(val count: Int, val lastUsedMs: Long)

object ExerciseSearchRules {

    const val RECENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L

    fun buildUsage(history: List<CompletedWorkout>): Map<String, ExerciseUsage> {
        val map = HashMap<String, ExerciseUsage>()
        for (w in history) {
            val ms = if (w.completedAtMs > 0) w.completedAtMs else RepDayRules.parseIsoToMillis(w.completedAt) ?: 0L
            for (ex in w.exercises) {
                val key = RepDayRules.exerciseKey(ex.name)
                if (key.isEmpty()) continue
                val prev = map[key]
                map[key] = if (prev == null) ExerciseUsage(1, ms)
                else ExerciseUsage(prev.count + 1, max(prev.lastUsedMs, ms))
            }
        }
        return map
    }

    private fun tokens(s: String): List<String> =
        RepDayRules.exerciseKey(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** Optimal string alignment distance (Levenshtein plus swapped neighbours). */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }

    private fun allowedTypos(len: Int): Int = when {
        len <= 2 -> 0
        len <= 5 -> 1
        else -> 2
    }

    /** Distance from a typed word to a word in the name, allowing the typed word to be a partial prefix. */
    private fun tokenDistance(q: String, c: String): Int {
        if (c.startsWith(q)) return 0
        var best = editDistance(q, c)
        for (len in max(1, q.length - 1)..min(c.length, q.length + 1)) {
            best = min(best, editDistance(q, c.substring(0, len)))
        }
        return best
    }

    /** Higher is better; null means no match. */
    fun score(query: String, name: String): Int? {
        val q = RepDayRules.exerciseKey(query).trim()
        if (q.isEmpty()) return 0
        val n = RepDayRules.exerciseKey(name)
        if (n == q) return 1000
        if (n.startsWith(q)) return 900
        val qTokens = tokens(q)
        val nTokens = tokens(n)
        if (qTokens.isEmpty() || nTokens.isEmpty()) return null
        if (qTokens.all { qt -> nTokens.any { it.startsWith(qt) } }) return 800
        if (n.contains(q)) return 700
        var totalDistance = 0
        for (qt in qTokens) {
            val best = nTokens.minOf { tokenDistance(qt, it) }
            if (best > allowedTypos(qt.length)) return null
            totalDistance += best
        }
        return 500 - 25 * totalDistance
    }

    /**
     * Names ordered for a picker. Empty query: recently used (last 30 days) first, then most used,
     * then A–Z. With a query: best text match first, then the same usage ordering.
     */
    fun rank(
        query: String,
        library: List<String>,
        usage: Map<String, ExerciseUsage>,
        nowMs: Long
    ): List<String> {
        val seen = HashSet<String>()
        val unique = library.filter { it.isNotBlank() && seen.add(RepDayRules.exerciseKey(it)) }
        val scored = unique.mapNotNull { name -> score(query, name)?.let { name to it } }
        return scored.sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenByDescending { (name, _) ->
                    val u = usage[RepDayRules.exerciseKey(name)]
                    if (u != null && nowMs - u.lastUsedMs <= RECENT_WINDOW_MS) u.lastUsedMs else 0L
                }
                .thenByDescending { (name, _) -> usage[RepDayRules.exerciseKey(name)]?.count ?: 0 }
                .thenBy { (name, _) -> name.lowercase(Locale.US) }
        ).map { it.first }
    }
}
