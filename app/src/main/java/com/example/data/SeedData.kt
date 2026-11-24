package com.example.data

import com.example.data.model.AppSettings
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.PlannedSet
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.SetTag
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.domain.RepDayRules

object SeedData {

    val COMMON_EXERCISES: List<String> = listOf(
        "Barbell Bench Press",
        "Incline Dumbbell Press",
        "Dumbbell Bench Press",
        "Machine Chest Press",
        "Incline Smith Machine Press",
        "Chest Dip",
        "Pec Deck Flye",
        "Cable Flye",
        "Seated Overhead Press",
        "Dumbbell Shoulder Press",
        "Machine Shoulder Press",
        "Barbell Overhead Press",
        "Cable Lateral Raise",
        "Dumbbell Lateral Raise",
        "Machine Lateral Raise",
        "Triceps Rope Pushdown",
        "Single-Arm Cable Pushdown",
        "Overhead Triceps Extension",
        "EZ-Bar Skullcrusher",
        "Weighted Pull-Up",
        "Assisted Pull-Up",
        "Barbell Bent-Over Row",
        "Seated Cable Row",
        "Chest-Supported Row",
        "Single-Arm Dumbbell Row",
        "T-Bar Row",
        "Lat Pulldown",
        "Neutral-Grip Lat Pulldown",
        "Straight-Arm Pulldown",
        "Rear Delt Face Pull",
        "Reverse Pec Deck",
        "Incline Dumbbell Curl",
        "Barbell Curl",
        "Preacher Curl",
        "Cable Curl",
        "Hammer Curl",
        "Barbell Back Squat",
        "Hack Squat",
        "Smith Machine Squat",
        "Front Squat",
        "Romanian Deadlift",
        "Dumbbell Romanian Deadlift",
        "Conventional Deadlift",
        "Leg Press",
        "Bulgarian Split Squat",
        "Walking Lunges",
        "Lying Leg Curl",
        "Seated Leg Curl",
        "Seated Leg Extension",
        "Standing Calf Raise",
        "Seated Calf Raise",
        "Hanging Leg Raise",
        "Cable Crunch"
    )

    private fun epochOffsetDays(daysAgo: Long, hourOffset: Int = 0): Long =
        System.currentTimeMillis() - daysAgo * 86_400_000L + hourOffset * 3_600_000L

    private fun isoOffsetDays(daysAgo: Long, hourOffset: Int = 0): String =
        RepDayRules.formatIso(epochOffsetDays(daysAgo, hourOffset))

    const val ROUTINE_PUSH_ID = "seed-routine-push"
    const val ROUTINE_BACK_ID = "seed-routine-back"
    const val PLAN_PROGRESSIVE_ID = "seed-plan-12w"

    fun createSeedRoutines(): List<Routine> {
        val pushCreatedMs = epochOffsetDays(18)
        val pushCreated = RepDayRules.formatIso(pushCreatedMs)
        val backCreatedMs = epochOffsetDays(16)
        val backCreated = RepDayRules.formatIso(backCreatedMs)

        val benchSets = RepDayRules.enforceDropSetRestRules(
            listOf(
                PlannedSet("ps-b1", SetTag.WARMUP, null, "8", "6", 60),
                PlannedSet("ps-b2", SetTag.WARMUP, null, "5", "7", 90),
                PlannedSet("ps-b3", SetTag.WORKING, WorkingSubTag.TOP, "4-6", "8.5-9", 180),
                PlannedSet("ps-b4", SetTag.WORKING, WorkingSubTag.BACKOFF, "8-10", "8", 120),
                PlannedSet("ps-b5", SetTag.WORKING, WorkingSubTag.BACKOFF, "8-10", "8.5", 120)
            ),
            defaultRest = 120
        )
        val inclineSets = RepDayRules.enforceDropSetRestRules(
            listOf(
                PlannedSet("ps-i1", SetTag.WORKING, null, "8-10", "8", 90),
                PlannedSet("ps-i2", SetTag.WORKING, null, "8-10", "8.5", 90),
                PlannedSet("ps-i3", SetTag.WORKING, null, "8-10", "9", 90)
            ),
            defaultRest = 90
        )
        val ohpSets = RepDayRules.enforceDropSetRestRules(
            listOf(
                PlannedSet("ps-o1", SetTag.WORKING, null, "8-10", "8", 120),
                PlannedSet("ps-o2", SetTag.WORKING, null, "8-10", "8.5", 120),
                PlannedSet("ps-o3", SetTag.WORKING, null, "8-10", "9", 120)
            ),
            defaultRest = 120
        )
        val lateralSets = RepDayRules.enforceDropSetRestRules(
            listOf(
                PlannedSet("ps-l1", SetTag.WORKING, null, "12-15", "8.5", 60),
                PlannedSet("ps-l2", SetTag.WORKING, null, "12-15", "9", 60),
                PlannedSet("ps-l3", SetTag.DROP, null, "10-12", "9.5", 60)
            ),
            defaultRest = 60
        )
        val pushdownSets = RepDayRules.enforceDropSetRestRules(
            listOf(
                PlannedSet("ps-t1", SetTag.WORKING, null, "10-12", "8", 60),
                PlannedSet("ps-t2", SetTag.WORKING, null, "10-12", "8.5", 60),
                PlannedSet("ps-t3", SetTag.DROP, null, "10-12", "9.5", 60)
            ),
            defaultRest = 60
        )

        val pushExercises = listOf(
            RepDayRules.withDerivedSummary(
                RoutineExercise("ex-bench", "Barbell Bench Press", benchSets.size, "4-10", "6-9", 120, benchSets)
            ),
            RepDayRules.withDerivedSummary(
                RoutineExercise("ex-incline", "Incline Dumbbell Press", inclineSets.size, "8-10", "8-9", 90, inclineSets)
            ),
            RepDayRules.withDerivedSummary(
                RoutineExercise("ex-ohp", "Seated Overhead Press", ohpSets.size, "8-10", "8-9", 120, ohpSets)
            ),
            RepDayRules.withDerivedSummary(
                RoutineExercise("ex-lat", "Cable Lateral Raise", lateralSets.size, "10-15", "8.5-9.5", 60, lateralSets)
            ),
            RepDayRules.withDerivedSummary(
                RoutineExercise("ex-tri", "Triceps Rope Pushdown", pushdownSets.size, "10-12", "8-9.5", 60, pushdownSets)
            )
        )

        val backExercises = listOf(
            RoutineExercise("ex-pullup", "Weighted Pull-Up", 4, "6-8", "8.5", 120, null),
            RoutineExercise("ex-row", "Chest-Supported Row", 4, "8-10", "8.5", 90, null),
            RoutineExercise("ex-pulldown", "Neutral-Grip Lat Pulldown", 3, "10-12", "8.5", 90, null),
            RoutineExercise("ex-facepull", "Rear Delt Face Pull", 3, "12-15", "8", 60, null),
            RoutineExercise("ex-curl", "Incline Dumbbell Curl", 3, "10-12", "9", 60, null)
        )

        return listOf(
            Routine(
                id = ROUTINE_PUSH_ID,
                name = "Push Day",
                focus = "Chest, Front/Side Delts & Triceps",
                description = "Heavy bench top set with controlled backoff volume and metabolic drop-set finishers.",
                exercises = pushExercises,
                createdAt = pushCreated,
                updatedAt = pushCreated,
                createdAtMs = pushCreatedMs,
                updatedAtMs = pushCreatedMs
            ),
            Routine(
                id = ROUTINE_BACK_ID,
                name = "Back & Biceps",
                focus = "Vertical/Horizontal Pull & Arms",
                description = "Balanced upper-back thickness and lat width with strict eccentric elbow flexion.",
                exercises = backExercises,
                createdAt = backCreated,
                updatedAt = backCreated,
                createdAtMs = backCreatedMs,
                updatedAtMs = backCreatedMs
            )
        )
    }

    fun createSeedPlans(): List<TrainingPlan> {
        val createdMs = epochOffsetDays(14)
        val created = RepDayRules.formatIso(createdMs)
        return listOf(
            TrainingPlan(
                id = PLAN_PROGRESSIVE_ID,
                name = "12-Week Progressive Split",
                description = "Three-phase hypertrophy and strength progression moving from foundational volume to peak intensity.",
                useBlocks = true,
                blocks = listOf(
                    PlanBlock(
                        id = "blk-1",
                        name = "Block 1 · Base Hypertrophy",
                        weeks = 4,
                        notes = "Establish clean technique and repeatable RPE 8–8.5 working sets.",
                        workouts = listOf(
                            PlanWorkoutSlot("slot-1", ROUTINE_PUSH_ID, "Push Day", null, 2),
                            PlanWorkoutSlot("slot-2", ROUTINE_BACK_ID, "Back & Biceps", null, 2)
                        )
                    ),
                    PlanBlock(
                        id = "blk-2",
                        name = "Block 2 · Overload & Volume",
                        weeks = 4,
                        notes = "Add top-set intensity and dedicate lower-body hypertrophy work.",
                        workouts = listOf(
                            PlanWorkoutSlot("slot-3", null, "Upper Hypertrophy", null, 2),
                            PlanWorkoutSlot("slot-4", null, "Lower Hypertrophy", null, 2)
                        )
                    ),
                    PlanBlock(
                        id = "blk-3",
                        name = "Block 3 · Strength Peak",
                        weeks = 4,
                        notes = "Lower rep ranges on primary compounds with tapered accessory volume.",
                        workouts = listOf(
                            PlanWorkoutSlot("slot-5", null, "Heavy Upper Peak", null, 2),
                            PlanWorkoutSlot("slot-6", null, "Heavy Lower Peak", null, 2)
                        )
                    )
                ),
                startedAt = null,
                isActive = false,
                createdAt = created,
                updatedAt = created,
                updatedAtMs = createdMs
            )
        )
    }

    fun createSeedHistory(): List<CompletedWorkout> {
        val pushStart = isoOffsetDays(2, -1)
        val pushEndMs = epochOffsetDays(2, 0)
        val pushEnd = RepDayRules.formatIso(pushEndMs)
        val backStart = isoOffsetDays(4, -1)
        val backEndMs = epochOffsetDays(4, 0)
        val backEnd = RepDayRules.formatIso(backEndMs)

        val pushExercises = listOf(
            CompletedExercise(
                name = "Barbell Bench Press",
                targetSets = 5,
                repRange = "4-10",
                sets = listOf(
                    CompletedSet(135.0, 8, SetTag.WARMUP, null, 6.0),
                    CompletedSet(175.0, 5, SetTag.WARMUP, null, 7.0),
                    CompletedSet(205.0, 5, SetTag.WORKING, WorkingSubTag.TOP, 9.0),
                    CompletedSet(185.0, 9, SetTag.WORKING, WorkingSubTag.BACKOFF, 8.0),
                    CompletedSet(185.0, 8, SetTag.WORKING, WorkingSubTag.BACKOFF, 8.5)
                )
            ),
            CompletedExercise(
                name = "Incline Dumbbell Press",
                targetSets = 3,
                repRange = "8-10",
                sets = listOf(
                    CompletedSet(70.0, 10, SetTag.WORKING, null, 8.0),
                    CompletedSet(70.0, 9, SetTag.WORKING, null, 8.5),
                    CompletedSet(70.0, 8, SetTag.WORKING, null, 9.0)
                )
            ),
            CompletedExercise(
                name = "Seated Overhead Press",
                targetSets = 3,
                repRange = "8-10",
                sets = listOf(
                    CompletedSet(115.0, 9, SetTag.WORKING, null, 8.0),
                    CompletedSet(115.0, 8, SetTag.WORKING, null, 8.5),
                    CompletedSet(115.0, 8, SetTag.WORKING, null, 9.0)
                )
            ),
            CompletedExercise(
                name = "Cable Lateral Raise",
                targetSets = 3,
                repRange = "10-15",
                sets = listOf(
                    CompletedSet(25.0, 14, SetTag.WORKING, null, 8.5),
                    CompletedSet(25.0, 13, SetTag.WORKING, null, 9.0),
                    CompletedSet(15.0, 12, SetTag.DROP, null, 9.5)
                )
            ),
            CompletedExercise(
                name = "Triceps Rope Pushdown",
                targetSets = 3,
                repRange = "10-12",
                sets = listOf(
                    CompletedSet(65.0, 12, SetTag.WORKING, null, 8.0),
                    CompletedSet(65.0, 11, SetTag.WORKING, null, 8.5),
                    CompletedSet(45.0, 12, SetTag.DROP, null, 9.5)
                )
            )
        )

        val backExercises = listOf(
            CompletedExercise(
                name = "Weighted Pull-Up",
                targetSets = 4,
                repRange = "6-8",
                sets = listOf(
                    CompletedSet(35.0, 8, SetTag.WORKING, null, 8.0),
                    CompletedSet(35.0, 7, SetTag.WORKING, null, 8.5),
                    CompletedSet(35.0, 7, SetTag.WORKING, null, 9.0),
                    CompletedSet(25.0, 8, SetTag.WORKING, WorkingSubTag.BACKOFF, 8.5)
                )
            ),
            CompletedExercise(
                name = "Chest-Supported Row",
                targetSets = 4,
                repRange = "8-10",
                sets = listOf(
                    CompletedSet(135.0, 10, SetTag.WORKING, null, 8.0),
                    CompletedSet(135.0, 10, SetTag.WORKING, null, 8.5),
                    CompletedSet(135.0, 9, SetTag.WORKING, null, 8.5),
                    CompletedSet(135.0, 9, SetTag.WORKING, null, 9.0)
                )
            ),
            CompletedExercise(
                name = "Neutral-Grip Lat Pulldown",
                targetSets = 3,
                repRange = "10-12",
                sets = listOf(
                    CompletedSet(140.0, 12, SetTag.WORKING, null, 8.0),
                    CompletedSet(140.0, 11, SetTag.WORKING, null, 8.5),
                    CompletedSet(140.0, 10, SetTag.WORKING, null, 9.0)
                )
            ),
            CompletedExercise(
                name = "Rear Delt Face Pull",
                targetSets = 3,
                repRange = "12-15",
                sets = listOf(
                    CompletedSet(50.0, 15, SetTag.WORKING, null, 8.0),
                    CompletedSet(50.0, 14, SetTag.WORKING, null, 8.0),
                    CompletedSet(50.0, 14, SetTag.WORKING, null, 8.5)
                )
            ),
            CompletedExercise(
                name = "Incline Dumbbell Curl",
                targetSets = 3,
                repRange = "10-12",
                sets = listOf(
                    CompletedSet(35.0, 12, SetTag.WORKING, null, 8.5),
                    CompletedSet(35.0, 11, SetTag.WORKING, null, 9.0),
                    CompletedSet(35.0, 10, SetTag.WORKING, null, 9.5)
                )
            )
        )

        return listOf(
            CompletedWorkout(
                id = "seed-hist-push",
                routineId = ROUTINE_PUSH_ID,
                routineName = "Push Day",
                startedAt = pushStart,
                completedAt = pushEnd,
                completedAtMs = pushEndMs,
                durationSeconds = 3120,
                totalVolume = RepDayRules.computeCompletedVolume(pushExercises),
                unit = WeightUnit.LB,
                exercises = pushExercises,
                notes = "Top bench set moved smoothly. Ready for 210 next week."
            ),
            CompletedWorkout(
                id = "seed-hist-back",
                routineId = ROUTINE_BACK_ID,
                routineName = "Back & Biceps",
                startedAt = backStart,
                completedAt = backEnd,
                completedAtMs = backEndMs,
                durationSeconds = 2880,
                totalVolume = RepDayRules.computeCompletedVolume(backExercises),
                unit = WeightUnit.LB,
                exercises = backExercises,
                notes = "Strong lat lock-in on neutral pulldowns."
            )
        )
    }

    fun defaultSettings(): AppSettings = AppSettings()
}
