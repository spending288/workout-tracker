package com.example.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.RepDayRules
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: String,
    val payloadJson: String,
    @ColumnInfo(defaultValue = "0") val createdAtMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val updatedAtMs: Long = 0L
)

@Entity(tableName = "training_plans")
data class TrainingPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val updatedAt: String,
    val payloadJson: String,
    @ColumnInfo(defaultValue = "0") val updatedAtMs: Long = 0L
)

@Entity(
    tableName = "completed_workouts",
    indices = [Index(value = ["completedAtMs"])]
)
data class CompletedWorkoutEntity(
    @PrimaryKey val id: String,
    val routineId: String?,
    val routineName: String,
    val completedAt: String,
    val payloadJson: String,
    @ColumnInfo(defaultValue = "0") val completedAtMs: Long = 0L
)

@Entity(tableName = "custom_exercises")
data class CustomExerciseEntity(
    @PrimaryKey val normalizedKey: String,
    val displayName: String,
    val createdAt: Long
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines")
    suspend fun getAllOnce(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RoutineEntity>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}

@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM training_plans ORDER BY isActive DESC, updatedAtMs DESC")
    fun observeAll(): Flow<List<TrainingPlanEntity>>

    @Query("SELECT * FROM training_plans")
    suspend fun getAllOnce(): List<TrainingPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrainingPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TrainingPlanEntity>)

    @Query("DELETE FROM training_plans WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM training_plans")
    suspend fun deleteAll()
}

@Dao
interface CompletedWorkoutDao {
    @Query("SELECT * FROM completed_workouts ORDER BY completedAtMs DESC")
    fun observeAll(): Flow<List<CompletedWorkoutEntity>>

    @Query("SELECT * FROM completed_workouts")
    suspend fun getAllOnce(): List<CompletedWorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompletedWorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CompletedWorkoutEntity>)

    @Query("DELETE FROM completed_workouts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM completed_workouts")
    suspend fun deleteAll()
}

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercises ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomExerciseEntity>>

    @Query("SELECT * FROM custom_exercises ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<CustomExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<CustomExerciseEntity>)

    @Query("DELETE FROM custom_exercises")
    suspend fun deleteAll()
}

@Database(
    entities = [
        RoutineEntity::class,
        TrainingPlanEntity::class,
        CompletedWorkoutEntity::class,
        CustomExerciseEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RepDayDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun completedWorkoutDao(): CompletedWorkoutDao
    abstract fun customExerciseDao(): CustomExerciseDao

    companion object {
        private val JSON_CREATED_AT_REGEX = Regex("\"createdAt\"\\s*:\\s*\"([^\"]+)\"")

        internal fun extractJsonCreatedAt(payloadJson: String?): String? {
            if (payloadJson.isNullOrBlank()) return null
            return JSON_CREATED_AT_REGEX.find(payloadJson)?.groupValues?.getOrNull(1)
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add epoch-millis columns with safe defaults
                db.execSQL(
                    "ALTER TABLE `completed_workouts` ADD COLUMN `completedAtMs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `routines` ADD COLUMN `createdAtMs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `routines` ADD COLUMN `updatedAtMs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `training_plans` ADD COLUMN `updatedAtMs` INTEGER NOT NULL DEFAULT 0"
                )

                // 2. Backfill completed_workouts.completedAtMs from completedAt
                val workoutUpdates = ArrayList<Pair<String, Long>>()
                db.query("SELECT id, completedAt FROM completed_workouts").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val completedAt = cursor.getString(1)
                        val ms = RepDayRules.parseIsoToMillis(completedAt) ?: 0L
                        workoutUpdates.add(id to ms)
                    }
                }
                for ((id, ms) in workoutUpdates) {
                    db.execSQL(
                        "UPDATE `completed_workouts` SET `completedAtMs` = ? WHERE `id` = ?",
                        arrayOf<Any>(ms, id)
                    )
                }

                // 3. Backfill routines.createdAtMs & routines.updatedAtMs from payloadJson/updatedAt
                val routineUpdates = ArrayList<Triple<String, Long, Long>>()
                db.query("SELECT id, updatedAt, payloadJson FROM routines").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val updatedAt = cursor.getString(1)
                        val payloadJson = cursor.getString(2)
                        val updatedAtMs = RepDayRules.parseIsoToMillis(updatedAt) ?: 0L
                        val createdAtIso = extractJsonCreatedAt(payloadJson)
                        val createdAtMs = RepDayRules.parseIsoToMillis(createdAtIso) ?: updatedAtMs
                        routineUpdates.add(Triple(id, createdAtMs, updatedAtMs))
                    }
                }
                for ((id, createdMs, updatedMs) in routineUpdates) {
                    db.execSQL(
                        "UPDATE `routines` SET `createdAtMs` = ?, `updatedAtMs` = ? WHERE `id` = ?",
                        arrayOf<Any>(createdMs, updatedMs, id)
                    )
                }

                // 4. Backfill training_plans.updatedAtMs from updatedAt
                val planUpdates = ArrayList<Pair<String, Long>>()
                db.query("SELECT id, updatedAt FROM training_plans").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val updatedAt = cursor.getString(1)
                        val ms = RepDayRules.parseIsoToMillis(updatedAt) ?: 0L
                        planUpdates.add(id to ms)
                    }
                }
                for ((id, ms) in planUpdates) {
                    db.execSQL(
                        "UPDATE `training_plans` SET `updatedAtMs` = ? WHERE `id` = ?",
                        arrayOf<Any>(ms, id)
                    )
                }

                // 5. Index completed_workouts.completedAtMs after backfill
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_completed_workouts_completedAtMs` ON `completed_workouts` (`completedAtMs`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: RepDayDatabase? = null

        fun getInstance(context: Context): RepDayDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RepDayDatabase::class.java,
                    "repday_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
