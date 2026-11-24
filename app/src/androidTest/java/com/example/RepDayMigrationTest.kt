package com.example

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.RepDayDatabase
import com.example.domain.RepDayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real 1 -> 2 migration against a database built from the exported v1 schema
 * (app/schemas/.../1.json), then checks Room's validation and the backfilled columns.
 * Runs on a device or emulator: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class RepDayMigrationTest {

    private val testDbName = "repday-migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RepDayDatabase::class.java
    )

    @Test
    fun migrate1To2_backfillsEpochMillisAndKeepsAllRows() {
        // Whole seconds, because v1 stored ISO timestamps without milliseconds.
        val completedMs = 1_700_000_000_000L
        val routineCreatedMs = 1_690_000_000_000L
        val routineUpdatedMs = 1_695_000_000_000L
        val planUpdatedMs = 1_698_000_000_000L

        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO completed_workouts (id, routineId, routineName, completedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("cw_ok", "rt_1", "Push", RepDayRules.formatIso(completedMs), "{}")
            )
            execSQL(
                "INSERT INTO completed_workouts (id, routineId, routineName, completedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("cw_bad_date", null, "Live", "not a date", "{}")
            )
            execSQL(
                "INSERT INTO routines (id, name, updatedAt, payloadJson) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(
                    "rt_1",
                    "Push",
                    RepDayRules.formatIso(routineUpdatedMs),
                    "{\"id\":\"rt_1\",\"createdAt\":\"${RepDayRules.formatIso(routineCreatedMs)}\"}"
                )
            )
            execSQL(
                "INSERT INTO routines (id, name, updatedAt, payloadJson) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("rt_no_created", "Pull", RepDayRules.formatIso(routineUpdatedMs), "{\"id\":\"rt_no_created\"}")
            )
            execSQL(
                "INSERT INTO training_plans (id, name, isActive, updatedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("pl_1", "Block", 1, RepDayRules.formatIso(planUpdatedMs), "{}")
            )
            execSQL(
                "INSERT INTO custom_exercises (normalizedKey, displayName, createdAt) VALUES (?, ?, ?)",
                arrayOf<Any?>("landmine press", "Landmine Press", 123L)
            )
            close()
        }

        // Room checks the migrated tables, columns and index against the v2 schema here.
        val db = helper.runMigrationsAndValidate(testDbName, 2, true, RepDayDatabase.MIGRATION_1_2)

        fun longFor(sql: String): Long = db.query(sql).use { c ->
            assertTrue("No row for: $sql", c.moveToFirst())
            c.getLong(0)
        }

        assertEquals(completedMs, longFor("SELECT completedAtMs FROM completed_workouts WHERE id = 'cw_ok'"))
        assertEquals(0L, longFor("SELECT completedAtMs FROM completed_workouts WHERE id = 'cw_bad_date'"))
        assertEquals(routineCreatedMs, longFor("SELECT createdAtMs FROM routines WHERE id = 'rt_1'"))
        assertEquals(routineUpdatedMs, longFor("SELECT updatedAtMs FROM routines WHERE id = 'rt_1'"))
        // No createdAt in the payload: falls back to updatedAt.
        assertEquals(routineUpdatedMs, longFor("SELECT createdAtMs FROM routines WHERE id = 'rt_no_created'"))
        assertEquals(planUpdatedMs, longFor("SELECT updatedAtMs FROM training_plans WHERE id = 'pl_1'"))

        assertEquals(2L, longFor("SELECT COUNT(*) FROM completed_workouts"))
        assertEquals(2L, longFor("SELECT COUNT(*) FROM routines"))
        assertEquals(1L, longFor("SELECT COUNT(*) FROM training_plans"))
        assertEquals(1L, longFor("SELECT COUNT(*) FROM custom_exercises"))
        db.close()
    }

    @Test
    fun migrate1To2_thenAppDatabaseOpensNormally() {
        helper.createDatabase(testDbName, 1).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(context, RepDayDatabase::class.java, testDbName)
            .addMigrations(RepDayDatabase.MIGRATION_1_2)
            .build()
        // Opening forces the migration and Room's own schema check to run.
        roomDb.openHelper.writableDatabase
        roomDb.close()
    }
}
