package com.example.domain

import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.CompletedSet
import com.example.data.model.SetTag
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet

data class ToggleSetOutcome(
    val workout: ActiveWorkout,
    val justCompletedExercise: Boolean,
    val nowCompleted: Boolean,
    val startRestSeconds: Int?,
    val shouldDismissRest: Boolean,
    val exerciseName: String,
    val setNumber: Int = 1,
    val targetRpe: String? = null,
    val currentRpe: Double? = null
)

/**
 * Pure-Kotlin, synchronous mutations on [ActiveWorkout].
 * Kept side-effect-free so live workout state transitions are atomic and JVM unit-testable.
 */
object ActiveWorkoutMutations {

    fun sanitizeWeightInput(raw: String): String =
        RepDayRules.sanitizeDecimalInput(raw, maxLength = 8)

    fun sanitizeRepsInput(raw: String): String =
        raw.filter { it.isDigit() }.take(3)

    fun updateNotes(workout: ActiveWorkout, notes: String): ActiveWorkout =
        workout.copy(notes = notes)

    fun updateSetWeight(
        workout: ActiveWorkout,
        exerciseId: String,
        setId: String,
        rawWeight: String
    ): ActiveWorkout {
        val sanitized = sanitizeWeightInput(rawWeight)
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) {
                    ex.copy(sets = ex.sets.map { s -> if (s.id == setId) s.copy(weight = sanitized) else s })
                } else {
                    ex
                }
            }
        )
    }

    fun updateSetReps(
        workout: ActiveWorkout,
        exerciseId: String,
        setId: String,
        rawReps: String
    ): ActiveWorkout {
        val sanitized = sanitizeRepsInput(rawReps)
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) {
                    ex.copy(sets = ex.sets.map { s -> if (s.id == setId) s.copy(reps = sanitized) else s })
                } else {
                    ex
                }
            }
        )
    }

    fun fillSetFromPrevious(
        workout: ActiveWorkout,
        exerciseId: String,
        setId: String,
        prevWeight: Double,
        prevReps: Int
    ): ActiveWorkout {
        val wStr = RepDayRules.formatNumber(prevWeight)
        val rStr = prevReps.toString()
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) {
                    ex.copy(
                        sets = ex.sets.map { s ->
                            if (s.id == setId) s.copy(weight = wStr, reps = rStr) else s
                        }
                    )
                } else {
                    ex
                }
            }
        )
    }

    fun toggleSetCompleted(
        workout: ActiveWorkout,
        exerciseId: String,
        setId: String,
        prevSet: CompletedSet?,
        defaultRestSeconds: Int,
        nowMs: Long,
        prevSetUnit: WeightUnit? = null,
        sessionUnit: WeightUnit? = null
    ): ToggleSetOutcome? {
        val exercise = workout.exercises.find { it.id == exerciseId } ?: return null
        val setIndex = exercise.sets.indexOfFirst { it.id == setId }
        if (setIndex < 0) return null
        val targetSet = exercise.sets[setIndex]
        val nowCompleted = !targetSet.completed
        val wasAllDone = exercise.sets.all { it.completed }

        val newWeight = if (nowCompleted && targetSet.weight.isBlank()) {
            prevSet?.let { prev ->
                val resolvedWeight = if (prevSetUnit != null && sessionUnit != null && prevSetUnit != sessionUnit) {
                    RepDayRules.convertWeightForInput(prev.weight, prevSetUnit, sessionUnit)
                } else {
                    prev.weight
                }
                RepDayRules.formatNumber(resolvedWeight)
            } ?: "0"
        } else {
            targetSet.weight
        }

        val newReps = if (nowCompleted && targetSet.reps.isBlank()) {
            when {
                prevSet != null -> prevSet.reps.toString()
                !targetSet.targetRepRange.isNullOrBlank() -> {
                    RepDayRules.parseRepBounds(targetSet.targetRepRange)?.first?.toString() ?: ""
                }
                else -> ""
            }
        } else {
            targetSet.reps
        }

        val updatedSets = exercise.sets.mapIndexed { idx, s ->
            if (idx == setIndex) {
                s.copy(
                    weight = newWeight,
                    reps = newReps,
                    completed = nowCompleted,
                    completedAt = if (nowCompleted) nowMs else null
                )
            } else {
                s
            }
        }

        val updatedWorkout = workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) ex.copy(sets = updatedSets) else ex
            }
        )

        var startRestSec: Int? = null
        var shouldDismissRest = false
        if (nowCompleted) {
            val nextIsDrop = RepDayRules.isPrecedingDropSetActive(updatedSets, setIndex)
            val restSec = targetSet.restSeconds ?: exercise.restSeconds ?: defaultRestSeconds
            if (nextIsDrop || restSec <= 0) {
                shouldDismissRest = true
            } else {
                startRestSec = restSec
            }
        }

        val isNowAllDone = updatedSets.all { it.completed }
        return ToggleSetOutcome(
            workout = updatedWorkout,
            justCompletedExercise = !wasAllDone && isNowAllDone,
            nowCompleted = nowCompleted,
            startRestSeconds = startRestSec,
            shouldDismissRest = shouldDismissRest,
            exerciseName = exercise.name,
            setNumber = setIndex + 1,
            targetRpe = targetSet.targetRpe,
            currentRpe = targetSet.rpe
        )
    }

    fun updateSetRpe(
        workout: ActiveWorkout,
        exerciseId: String,
        setId: String,
        rpe: Double?
    ): ActiveWorkout {
        val snapped = rpe?.let { RepDayRules.snapRpe(it) }
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) {
                    ex.copy(
                        sets = ex.sets.map { s ->
                            if (s.id == setId) s.copy(rpe = snapped) else s
                        }
                    )
                } else {
                    ex
                }
            }
        )
    }

    fun convertWorkoutUnit(
        workout: ActiveWorkout,
        targetUnit: WeightUnit,
        currentFallbackUnit: WeightUnit
    ): ActiveWorkout {
        val sourceUnit = workout.resolvedUnit(currentFallbackUnit)
        if (sourceUnit == targetUnit) {
            return workout.copy(unit = targetUnit)
        }
        return workout.copy(
            unit = targetUnit,
            exercises = workout.exercises.map { ex ->
                ex.copy(
                    sets = ex.sets.map { s ->
                        val parsed = s.weight.trim().toDoubleOrNull()
                        if (parsed != null && s.weight.isNotBlank()) {
                            val converted = RepDayRules.convertWeightForInput(parsed, sourceUnit, targetUnit)
                            s.copy(weight = RepDayRules.formatNumber(converted))
                        } else {
                            s
                        }
                    }
                )
            }
        )
    }

    fun updateSetOptions(
        workout: ActiveWorkout,
        exerciseId: String,
        setId: String,
        tag: SetTag?,
        workingSubTag: WorkingSubTag?,
        rpe: Double?,
        restSeconds: Int,
        defaultRestSeconds: Int
    ): ActiveWorkout {
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) {
                    val prevSets = ex.sets
                    val exDefault = ex.restSeconds ?: defaultRestSeconds
                    val rawSets = ex.sets.map { s ->
                        if (s.id == setId) {
                            s.copy(
                                tag = tag,
                                workingSubTag = if (tag == SetTag.WORKING) workingSubTag else null,
                                rpe = rpe,
                                restSeconds = restSeconds.coerceIn(0, RestTimerRules.MAX_REST_DURATION_SEC)
                            )
                        } else {
                            s
                        }
                    }
                    ex.copy(sets = RepDayRules.enforceDropSetRestOnActiveSets(rawSets, exDefault, prevSets))
                } else {
                    ex
                }
            }
        )
    }

    fun cycleExerciseRest(
        workout: ActiveWorkout,
        exerciseId: String,
        defaultRestSeconds: Int
    ): ActiveWorkout {
        val ex = workout.exercises.find { it.id == exerciseId } ?: return workout
        val curRest = ex.restSeconds ?: defaultRestSeconds
        val cycle = RepDayRules.EXERCISE_REST_CYCLE
        val nextRest = cycle.firstOrNull { it > curRest } ?: cycle.first()

        return workout.copy(
            exercises = workout.exercises.map { e ->
                if (e.id == exerciseId) {
                    val updatedSets = e.sets.mapIndexed { idx, s ->
                        val nextIsDrop = RepDayRules.isPrecedingDropSetActive(e.sets, idx)
                        if (nextIsDrop) s.copy(restSeconds = 0)
                        else if ((s.restSeconds ?: curRest) == curRest) s.copy(restSeconds = nextRest)
                        else s
                    }
                    e.copy(restSeconds = nextRest, sets = updatedSets)
                } else {
                    e
                }
            }
        )
    }

    fun addSetToExercise(
        workout: ActiveWorkout,
        exerciseId: String,
        defaultRestSeconds: Int
    ): ActiveWorkout {
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId && ex.sets.size < 20) {
                    val last = ex.sets.lastOrNull()
                    val exRest = ex.restSeconds ?: defaultRestSeconds
                    val clonedTag = if (last?.tag == SetTag.DROP) SetTag.WORKING else last?.tag
                    val clonedRest = if ((last?.restSeconds ?: exRest) == 0) exRest else (last?.restSeconds ?: exRest)
                    val newSet = WorkoutSet(
                        id = RepDayRules.newId("ws"),
                        weight = last?.weight ?: "",
                        reps = last?.reps ?: "",
                        completed = false,
                        completedAt = null,
                        tag = clonedTag,
                        workingSubTag = if (clonedTag == SetTag.WORKING) last?.workingSubTag else null,
                        targetRepRange = last?.targetRepRange,
                        targetRpe = last?.targetRpe,
                        restSeconds = clonedRest,
                        rpe = null
                    )
                    val nextSets = RepDayRules.enforceDropSetRestOnActiveSets(ex.sets + newSet, exRest, ex.sets)
                    ex.copy(targetSets = nextSets.size, sets = nextSets)
                } else {
                    ex
                }
            }
        )
    }

    fun removeLastSetFromExercise(
        workout: ActiveWorkout,
        exerciseId: String,
        defaultRestSeconds: Int
    ): ActiveWorkout {
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId && ex.sets.size > 1) {
                    val exRest = ex.restSeconds ?: defaultRestSeconds
                    val nextSets = RepDayRules.enforceDropSetRestOnActiveSets(
                        ex.sets.dropLast(1),
                        exRest,
                        ex.sets
                    )
                    ex.copy(targetSets = nextSets.size, sets = nextSets)
                } else {
                    ex
                }
            }
        )
    }

    fun reorderExercises(
        workout: ActiveWorkout,
        fromIndex: Int,
        toIndex: Int
    ): ActiveWorkout {
        if (fromIndex == toIndex) return workout
        if (fromIndex !in workout.exercises.indices || toIndex !in workout.exercises.indices) return workout
        val mutable = workout.exercises.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return workout.copy(exercises = mutable)
    }

    fun addExercise(
        workout: ActiveWorkout,
        normalizedName: String,
        lastSessionByExercise: Map<String, PreviousExerciseSession>,
        defaultRestSeconds: Int,
        @Suppress("UNUSED_PARAMETER") fallbackUnit: WeightUnit = WeightUnit.LB
    ): ActiveWorkout {
        val prev = RepDayRules.findPreviousExerciseSession(normalizedName, lastSessionByExercise)
        val setsCount = prev?.sets?.size?.coerceIn(1, 20) ?: 3
        val sets = List(setsCount) { idx ->
            val p = prev?.sets?.getOrNull(idx)
            WorkoutSet(
                id = RepDayRules.newId("ws"),
                weight = "",
                reps = "",
                completed = false,
                completedAt = null,
                tag = p?.tag,
                workingSubTag = p?.workingSubTag,
                targetRepRange = null,
                targetRpe = null,
                restSeconds = defaultRestSeconds,
                rpe = null
            )
        }
        val newEx = ActiveExercise(
            id = RepDayRules.newId("ae"),
            name = normalizedName,
            targetSets = sets.size,
            repRange = "",
            targetRpe = null,
            restSeconds = defaultRestSeconds,
            sets = RepDayRules.enforceDropSetRestOnActiveSets(sets, defaultRestSeconds)
        )
        return workout.copy(exercises = workout.exercises + newEx)
    }

    fun swapExercise(
        workout: ActiveWorkout,
        exerciseId: String,
        normalizedNewName: String
    ): ActiveWorkout {
        return workout.copy(
            exercises = workout.exercises.map { ex ->
                if (ex.id == exerciseId) {
                    ex.copy(
                        name = normalizedNewName,
                        sets = ex.sets.map { s ->
                            if (s.completed) s else s.copy(weight = "", reps = "")
                        }
                    )
                } else {
                    ex
                }
            }
        )
    }

    /**
     * Splits an exercise mid-workout: keeps only completed sets on the original exercise,
     * and inserts a new exercise named [normalizedNewName] right after it carrying the
     * remaining unlogged sets (preserving tags, targets, and rest; clearing weight/reps).
     */
    fun swapExerciseKeepCompleted(
        workout: ActiveWorkout,
        exerciseId: String,
        normalizedNewName: String,
        defaultRestSeconds: Int
    ): ActiveWorkout {
        val idx = workout.exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return workout
        val ex = workout.exercises[idx]
        val completedSets = ex.sets.filter { it.completed }
        if (completedSets.isEmpty()) {
            return swapExercise(workout, exerciseId, normalizedNewName)
        }

        val exRest = ex.restSeconds ?: defaultRestSeconds
        val keptSets = RepDayRules.enforceDropSetRestOnActiveSets(completedSets, exRest, ex.sets)
        val keptOriginal = ex.copy(
            targetSets = keptSets.size,
            sets = keptSets
        )

        val remainingRaw = ex.sets.filterNot { it.completed }
        val carriedSetsRaw = if (remainingRaw.isNotEmpty()) {
            remainingRaw.mapIndexed { i, s ->
                s.copy(
                    id = RepDayRules.newId("ws$i"),
                    weight = "",
                    reps = "",
                    completed = false,
                    completedAt = null,
                    rpe = null
                )
            }
        } else {
            val last = completedSets.last()
            val nextTag = if (last.tag == SetTag.DROP) SetTag.WORKING else last.tag
            val nextRest = if ((last.restSeconds ?: exRest) == 0) exRest else (last.restSeconds ?: exRest)
            listOf(
                WorkoutSet(
                    id = RepDayRules.newId("ws0"),
                    weight = "",
                    reps = "",
                    completed = false,
                    completedAt = null,
                    tag = nextTag,
                    workingSubTag = if (nextTag == SetTag.WORKING) last.workingSubTag else null,
                    targetRepRange = last.targetRepRange?.takeIf { it.isNotBlank() }
                        ?: ex.repRange.takeIf { it.isNotBlank() },
                    targetRpe = last.targetRpe?.takeIf { it.isNotBlank() }
                        ?: ex.targetRpe?.takeIf { it.isNotBlank() },
                    restSeconds = nextRest,
                    rpe = null
                )
            )
        }
        val enforcedCarried = RepDayRules.enforceDropSetRestOnActiveSets(carriedSetsRaw, exRest)
        val replacementEx = ActiveExercise(
            id = RepDayRules.newId("ae"),
            name = normalizedNewName,
            targetSets = enforcedCarried.size,
            repRange = ex.repRange,
            targetRpe = ex.targetRpe,
            restSeconds = ex.restSeconds,
            sets = enforcedCarried
        )

        val updatedList = workout.exercises.toMutableList()
        updatedList[idx] = keptOriginal
        updatedList.add(idx + 1, replacementEx)
        return workout.copy(exercises = updatedList)
    }

    fun removeExercise(
        workout: ActiveWorkout,
        exerciseId: String
    ): ActiveWorkout {
        if (workout.routineId != null && workout.exercises.size <= 1) return workout
        return workout.copy(exercises = workout.exercises.filterNot { it.id == exerciseId })
    }
}
