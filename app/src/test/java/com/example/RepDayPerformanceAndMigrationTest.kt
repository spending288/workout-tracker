package com.example

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.RepDayDatabase
import com.example.data.model.CompletedExercise
import com.example.data.model.CompletedSet
import com.example.data.model.CompletedWorkout
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.Routine
import com.example.data.model.RoutineSortConfig
import com.example.data.model.RoutineSortField
import com.example.data.model.SortDirection
import com.example.data.model.TrainingPlan
import com.example.data.model.WeightUnit
import com.example.domain.RepDayRules
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets

class RepDayPerformanceAndMigrationTest {

    @Test
    fun buildLastSessionByExercise_1000WorkoutsAndResolve6Exercises_finishesWellUnder20Ms() {
        val exercisePool = List(30) { idx -> "Exercise #$idx Barbell Press" }
        val baseMs = 1_700_000_000_000L
        val workouts = List(1_000) { wIdx ->
            // Intentionally shuffled order so the builder must compare timestamps, not rely on list order
            val chronologicalIndex = (wIdx * 37) % 1_000
            val completedAtMs = baseMs + chronologicalIndex * 86_400_000L
            val iso = RepDayRules.formatIso(completedAtMs)
            val exercises = List(6) { slot ->
                val exName = exercisePool[(chronologicalIndex + slot * 5) % exercisePool.size]
                CompletedExercise(
                    name = exName,
                    targetSets = 3,
                    repRange = "8-10",
                    sets = listOf(
                        CompletedSet(weight = 100.0 + chronologicalIndex, reps = 8),
                        CompletedSet(weight = 100.0 + chronologicalIndex, reps = 8),
                        CompletedSet(weight = 100.0 + chronologicalIndex, reps = 7)
                    )
                )
            }
            CompletedWorkout(
                id = "cw_$wIdx",
                routineId = "rt_${wIdx % 4}",
                routineName = "Routine ${wIdx % 4}",
                startedAt = iso,
                completedAt = iso,
                completedAtMs = completedAtMs,
                durationSeconds = 3600,
                totalVolume = 5000,
                unit = WeightUnit.LB,
                exercises = exercises
            )
        }

        val targets = List(6) { i -> exercisePool[i] }

        // Light JIT warm-up on a small slice so we measure steady-state algorithmic speed
        val warmup = workouts.take(20)
        repeat(5) {
            val m = RepDayRules.buildLastSessionByExercise(warmup)
            targets.forEach { RepDayRules.findPreviousExerciseSession(it, m) }
        }

        val startNs = System.nanoTime()
        val lastSessionMap = RepDayRules.buildLastSessionByExercise(workouts)
        val resolved = targets.map { RepDayRules.findPreviousExerciseSession(it, lastSessionMap) }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertTrue(
            "Expected building lastSessionByExercise for 1,000 workouts and resolving 6 exercises to finish well under 20 ms, but took ${"%.2f".format(elapsedMs)} ms",
            elapsedMs < 20.0
        )

        // Verify correctness: every target resolved to its true latest workout timestamp
        targets.forEachIndexed { idx, name ->
            val session = resolved[idx]
            assertNotNull("Expected previous session for $name", session)
            val expectedLatestChrono = (999 downTo 0).first { chrono ->
                (0 until 6).any { slot -> (chrono + slot * 5) % exercisePool.size == idx }
            }
            assertEquals(baseMs + expectedLatestChrono * 86_400_000L, session!!.completedAtMs)
            assertEquals(100.0 + expectedLatestChrono, session.sets.first().weight, 0.001)
        }
    }

    @Test
    fun parseIsoToMillis_supportsAppFormatFirstAndFallbackFormats() {
        val epochSec = 1_764_009_900_000L // 2025-11-24T18:45:00Z
        val formatted = RepDayRules.formatIso(epochSec)
        assertEquals(epochSec, RepDayRules.parseIsoToMillis(formatted))
        assertEquals(epochSec + 345L, RepDayRules.parseIsoToMillis("2025-11-24T18:45:00.345Z"))
        assertEquals(epochSec, RepDayRules.parseIsoToMillis("2025-11-24T20:45:00+02:00"))
        assertNull(RepDayRules.parseIsoToMillis(null))
        assertNull(RepDayRules.parseIsoToMillis("   "))
        assertNull(RepDayRules.parseIsoToMillis("not-a-timestamp"))
    }

    @Test
    fun sortRoutinesAndComputePlanProgress_usePrecomputedEpochMillis() {
        // Deliberately give misleading ISO strings to prove sorting/filtering relies on precomputed millis
        val rOld = Routine(
            id = "r_old",
            name = "Alpha",
            exercises = emptyList(),
            createdAt = "9999-12-31T00:00:00Z",
            updatedAt = "9999-12-31T00:00:00Z",
            createdAtMs = 1_000L,
            updatedAtMs = 1_000L
        )
        val rNew = Routine(
            id = "r_new",
            name = "Beta",
            exercises = emptyList(),
            createdAt = "1970-01-01T00:00:00Z",
            updatedAt = "1970-01-01T00:00:00Z",
            createdAtMs = 9_000L,
            updatedAtMs = 9_000L
        )
        val h1 = CompletedWorkout(
            id = "w1",
            routineId = "r_old",
            routineName = "Alpha",
            startedAt = "bad-iso",
            completedAt = "bad-iso",
            completedAtMs = 50_000L,
            durationSeconds = 600,
            totalVolume = 1000,
            unit = WeightUnit.LB,
            exercises = emptyList()
        )
        val h2 = CompletedWorkout(
            id = "w2",
            routineId = "r_new",
            routineName = "Beta",
            startedAt = "bad-iso",
            completedAt = "bad-iso",
            completedAtMs = 20_000L,
            durationSeconds = 600,
            totalVolume = 1000,
            unit = WeightUnit.LB,
            exercises = emptyList()
        )

        val byCreatedDesc = RepDayRules.sortRoutines(
            routines = listOf(rOld, rNew),
            history = listOf(h1, h2),
            config = RoutineSortConfig(RoutineSortField.CREATED_AT, SortDirection.DESC)
        )
        assertEquals(listOf("r_new", "r_old"), byCreatedDesc.map { it.id })

        val byLastDoneDesc = RepDayRules.sortRoutines(
            routines = listOf(rOld, rNew),
            history = listOf(h1, h2),
            config = RoutineSortConfig(RoutineSortField.LAST_COMPLETED_AT, SortDirection.DESC)
        )
        assertEquals(listOf("r_old", "r_new"), byLastDoneDesc.map { it.id })

        // Plan progress counts workouts within Week 1 window strictly via completedAtMs
        val planStartMs = 1_700_000_000_000L
        val plan = TrainingPlan(
            id = "p1",
            name = "Test Plan",
            useBlocks = false,
            blocks = listOf(
                PlanBlock(
                    id = "b1",
                    name = "Block 1",
                    weeks = 4,
                    workouts = listOf(PlanWorkoutSlot("s1", "r_old", "Alpha", null, 2))
                )
            ),
            startedAt = RepDayRules.formatIso(planStartMs),
            isActive = true,
            createdAt = RepDayRules.formatIso(planStartMs),
            updatedAt = RepDayRules.formatIso(planStartMs),
            updatedAtMs = planStartMs
        )
        val inWeek = h1.copy(completedAtMs = planStartMs + 3_600_000L)
        val outsideWeek = h1.copy(id = "w_old", completedAtMs = planStartMs - 3_600_000L)
        val progress = RepDayRules.computePlanProgress(
            plan = plan,
            history = listOf(inWeek, outsideWeek),
            routines = listOf(rOld),
            nowMs = planStartMs + 86_400_000L
        )
        assertEquals(1, progress.weekSessionsDone)
        assertEquals(2, progress.weekSessionsTarget)
    }

    @Test
    fun roomMigration1To2_backfillsMillisCreatesIndexPreservesDataAndMatchesExportedV2Schema() {
        val schemaDir = locateSchemasDir()
        val v1SchemaFile = File(schemaDir, "1.json")
        val v2SchemaFile = File(schemaDir, "2.json")
        assertTrue("Expected v1 Room schema at ${v1SchemaFile.absolutePath}", v1SchemaFile.isFile)
        assertTrue("Expected exported v2 Room schema at ${v2SchemaFile.absolutePath}", v2SchemaFile.isFile)

        val moshi = Moshi.Builder().build()
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

        @Suppress("UNCHECKED_CAST")
        val v1Root = mapAdapter.fromJson(v1SchemaFile.readText())!!
        @Suppress("UNCHECKED_CAST")
        val v1DbSchema = v1Root["database"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val v1Entities = v1DbSchema["entities"] as List<Map<String, Any>>

        val sqliteBinary = resolveSqlite3Binary()
        val dbFile = File.createTempFile("repday_migration_1_2_", ".db")
        try {
            val driver = SqliteCliDriver(sqliteBinary, dbFile)
            val supportDb = driver.asSupportSQLiteDatabase()

            // 1. Create exact v1 schema from exported 1.json
            for (entity in v1Entities) {
                val tableName = entity["tableName"] as String
                val createSql = (entity["createSql"] as String).replace("\${TABLE_NAME}", tableName)
                driver.execSql(createSql)
            }

            // 2. Seed v1 rows with mixed ISO formats & distinct routine createdAt vs updatedAt
            val w1Iso = "2026-01-10T10:00:00Z"
            val w2Iso = "2026-01-10T11:30:00.500Z"
            val w3Iso = "2026-01-10T14:00:00+02:00" // 12:00:00Z -> strictly newer than w2, even though string ordering differs from +05:00 cases
            val w4Iso = "2026-01-10T09:00:00-05:00" // 14:00:00Z -> newest of all! Lexicographically "09:00" < "10:00" in SQL text sort!

            driver.execSql(
                "INSERT INTO completed_workouts (id, routineId, routineName, completedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf("cw1", "rt1", "Push's Day", w1Iso, """{"id":"cw1","completedAt":"$w1Iso"}""")
            )
            driver.execSql(
                "INSERT INTO completed_workouts (id, routineId, routineName, completedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf("cw2", "rt1", "Push Day", w2Iso, """{"id":"cw2","completedAt":"$w2Iso"}""")
            )
            driver.execSql(
                "INSERT INTO completed_workouts (id, routineId, routineName, completedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf("cw3", null, "Live Workout", w3Iso, """{"id":"cw3","completedAt":"$w3Iso"}""")
            )
            driver.execSql(
                "INSERT INTO completed_workouts (id, routineId, routineName, completedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf("cw4", "rt2", "Pull Day", w4Iso, """{"id":"cw4","completedAt":"$w4Iso"}""")
            )

            val rt1CreatedIso = "2025-12-01T08:00:00Z"
            val rt1UpdatedIso = "2026-01-15T12:00:00Z"
            val rt2UpdatedIso = "2026-01-18T09:30:00Z"
            driver.execSql(
                "INSERT INTO routines (id, name, updatedAt, payloadJson) VALUES (?, ?, ?, ?)",
                arrayOf("rt1", "Push Day", rt1UpdatedIso, """{"id":"rt1","name":"Push Day","createdAt":"$rt1CreatedIso","updatedAt":"$rt1UpdatedIso"}""")
            )
            driver.execSql(
                "INSERT INTO routines (id, name, updatedAt, payloadJson) VALUES (?, ?, ?, ?)",
                arrayOf("rt2", "Legacy Routine", rt2UpdatedIso, """{"id":"rt2","name":"Legacy Routine"}""")
            )

            val plan1UpdatedIso = "2026-01-05T07:00:00Z"
            val plan2UpdatedIso = "2026-01-20T07:00:00Z"
            driver.execSql(
                "INSERT INTO training_plans (id, name, isActive, updatedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("p1", "Active Plan", 1, plan1UpdatedIso, """{"id":"p1","isActive":true}""")
            )
            driver.execSql(
                "INSERT INTO training_plans (id, name, isActive, updatedAt, payloadJson) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("p2", "Paused Newer Plan", 0, plan2UpdatedIso, """{"id":"p2","isActive":false}""")
            )

            driver.execSql(
                "INSERT INTO custom_exercises (normalizedKey, displayName, createdAt) VALUES (?, ?, ?)",
                arrayOf<Any?>("jm press", "JM Press", 1_700_000_000_000L)
            )

            // 3. Run the real Room MIGRATION_1_2 against the SQLite database
            RepDayDatabase.MIGRATION_1_2.migrate(supportDb)

            // 4. Verify completed_workouts: all rows preserved, completedAtMs backfilled, SQL ORDER BY accurate
            val workoutsAfter = driver.queryRows(
                "SELECT id, routineName, completedAt, completedAtMs FROM completed_workouts ORDER BY completedAtMs DESC"
            )
            assertEquals(listOf("cw4", "cw3", "cw2", "cw1"), workoutsAfter.map { it[0] })
            assertEquals("Push's Day", workoutsAfter.last()[1])
            assertEquals(RepDayRules.parseIsoToMillis(w4Iso).toString(), workoutsAfter[0][3])
            assertEquals(RepDayRules.parseIsoToMillis(w3Iso).toString(), workoutsAfter[1][3])
            assertEquals(RepDayRules.parseIsoToMillis(w2Iso).toString(), workoutsAfter[2][3])
            assertEquals(RepDayRules.parseIsoToMillis(w1Iso).toString(), workoutsAfter[3][3])

            // 5. Verify index_completed_workouts_completedAtMs exists in sqlite_master
            val indexes = driver.queryRows(
                "SELECT name, tbl_name FROM sqlite_master WHERE type = 'index' AND name = 'index_completed_workouts_completedAtMs'"
            )
            assertEquals(1, indexes.size)
            assertEquals("completed_workouts", indexes[0][1])

            // 6. Verify routines: createdAtMs backfilled from payloadJson (and fallback to updatedAtMs), updatedAtMs from updatedAt
            val routinesAfter = driver.queryRows(
                "SELECT id, createdAtMs, updatedAtMs FROM routines ORDER BY updatedAtMs DESC"
            )
            assertEquals(listOf("rt2", "rt1"), routinesAfter.map { it[0] })
            assertEquals(RepDayRules.parseIsoToMillis(rt2UpdatedIso).toString(), routinesAfter[0][1]) // fallback to updatedAtMs
            assertEquals(RepDayRules.parseIsoToMillis(rt2UpdatedIso).toString(), routinesAfter[0][2])
            assertEquals(RepDayRules.parseIsoToMillis(rt1CreatedIso).toString(), routinesAfter[1][1]) // extracted from payloadJson
            assertEquals(RepDayRules.parseIsoToMillis(rt1UpdatedIso).toString(), routinesAfter[1][2])

            // 7. Verify training_plans: updatedAtMs backfilled & SQL ORDER BY isActive DESC, updatedAtMs DESC
            val plansAfter = driver.queryRows(
                "SELECT id, isActive, updatedAtMs FROM training_plans ORDER BY isActive DESC, updatedAtMs DESC"
            )
            assertEquals(listOf("p1", "p2"), plansAfter.map { it[0] })
            assertEquals(RepDayRules.parseIsoToMillis(plan1UpdatedIso).toString(), plansAfter[0][2])
            assertEquals(RepDayRules.parseIsoToMillis(plan2UpdatedIso).toString(), plansAfter[1][2])

            // 8. Verify custom_exercises untouched
            val customAfter = driver.queryRows("SELECT normalizedKey, displayName, createdAt FROM custom_exercises")
            assertEquals(1, customAfter.size)
            assertEquals("JM Press", customAfter[0][1])

            // 9. Verify migrated SQLite table columns & indexes match KSP-exported 2.json verbatim
            @Suppress("UNCHECKED_CAST")
            val v2Root = mapAdapter.fromJson(v2SchemaFile.readText())!!
            @Suppress("UNCHECKED_CAST")
            val v2DbSchema = v2Root["database"] as Map<String, Any>
            assertEquals(2, (v2DbSchema["version"] as Number).toInt())
            @Suppress("UNCHECKED_CAST")
            val v2Entities = v2DbSchema["entities"] as List<Map<String, Any>>

            for (entity in v2Entities) {
                val tableName = entity["tableName"] as String
                @Suppress("UNCHECKED_CAST")
                val expectedFields = entity["fields"] as List<Map<String, Any>>
                val actualCols = driver.queryRows("PRAGMA table_info(`$tableName`)")
                val actualByName = actualCols.associateBy { it[1] }
                assertEquals(
                    "Column count mismatch on migrated table $tableName vs 2.json",
                    expectedFields.size,
                    actualCols.size
                )
                for (field in expectedFields) {
                    val colName = field["columnName"] as String
                    val affinity = field["affinity"] as String
                    val notNull = (field["notNull"] as? Boolean) ?: false
                    val actual = actualByName[colName]
                    assertNotNull("Missing column $tableName.$colName after migration", actual)
                    assertEquals("Affinity mismatch for $tableName.$colName", affinity, actual!![2])
                    assertEquals("notNull mismatch for $tableName.$colName", if (notNull) "1" else "0", actual[3])
                }

                @Suppress("UNCHECKED_CAST")
                val expectedIndices = (entity["indices"] as? List<Map<String, Any>>).orEmpty()
                for (idx in expectedIndices) {
                    val idxName = idx["name"] as String
                    val idxRows = driver.queryRows(
                        "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$tableName' AND name = '$idxName'"
                    )
                    assertEquals("Missing index $idxName on $tableName after migration", 1, idxRows.size)
                }
            }
        } finally {
            dbFile.delete()
        }
    }

    private fun locateSchemasDir(): File {
        val candidates = listOf(
            File("schemas/com.example.data.local.RepDayDatabase"),
            File("app/schemas/com.example.data.local.RepDayDatabase")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate Room schemas directory from ${File(".").absolutePath}")
    }

    private fun resolveSqlite3Binary(): String {
        val candidates = listOf(
            "/opt/android/sdk/platform-tools/sqlite3",
            "/usr/bin/sqlite3",
            "sqlite3"
        )
        return candidates.firstOrNull { path ->
            path == "sqlite3" || File(path).canExecute()
        } ?: error("sqlite3 CLI not found")
    }

    /**
     * Real SQLite3-backed SupportSQLiteDatabase harness for offline JVM Room migration testing.
     */
    private class SqliteCliDriver(
        private val sqliteBinary: String,
        private val dbFile: File
    ) {
        private val fieldSep = "\u001F"
        private val rowSep = "\u001E"

        fun execSql(sql: String, bindArgs: Array<out Any?>? = null) {
            val rendered = bindParameters(sql, bindArgs)
            runSqlite(rendered, expectRows = false)
        }

        fun queryRows(sql: String): List<List<String>> {
            val output = runSqlite(sql, expectRows = true)
            if (output.isEmpty()) return emptyList()
            return output
                .split(rowSep)
                .filter { it.isNotEmpty() }
                .map { row -> row.split(fieldSep) }
        }

        fun asSupportSQLiteDatabase(): SupportSQLiteDatabase {
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        val sql = args!![0] as String
                        @Suppress("UNCHECKED_CAST")
                        val bindArgs = if (args.size >= 2) args[1] as? Array<out Any?> else null
                        execSql(sql, bindArgs)
                        null
                    }
                    "query" -> {
                        val sql = args!![0] as String
                        createCursor(queryRows(sql))
                    }
                    else -> throw UnsupportedOperationException("Unsupported SupportSQLiteDatabase method in test: ${method.name}")
                }
            } as SupportSQLiteDatabase
        }

        private fun createCursor(rows: List<List<String>>): Cursor {
            var pos = -1
            return Proxy.newProxyInstance(
                Cursor::class.java.classLoader,
                arrayOf(Cursor::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "moveToNext" -> {
                        pos += 1
                        pos < rows.size
                    }
                    "getCount" -> rows.size
                    "getPosition" -> pos
                    "getString" -> rows[pos][args!![0] as Int]
                    "getLong" -> rows[pos][args!![0] as Int].toLong()
                    "getInt" -> rows[pos][args!![0] as Int].toInt()
                    "isNull" -> rows[pos][args!![0] as Int].isEmpty()
                    "close" -> null
                    "isClosed" -> false
                    else -> throw UnsupportedOperationException("Unsupported Cursor method in test: ${method.name}")
                }
            } as Cursor
        }

        private fun bindParameters(sql: String, bindArgs: Array<out Any?>?): String {
            if (bindArgs == null || bindArgs.isEmpty()) return sql
            val out = StringBuilder(sql.length + 64)
            var argIdx = 0
            for (ch in sql) {
                if (ch == '?' && argIdx < bindArgs.size) {
                    val v = bindArgs[argIdx++]
                    when (v) {
                        null -> out.append("NULL")
                        is Number -> out.append(v.toString())
                        is Boolean -> out.append(if (v) "1" else "0")
                        else -> {
                            out.append('\'')
                            out.append(v.toString().replace("'", "''"))
                            out.append('\'')
                        }
                    }
                } else {
                    out.append(ch)
                }
            }
            return out.toString()
        }

        private fun runSqlite(sql: String, expectRows: Boolean): String {
            val cmd = if (expectRows) {
                listOf(sqliteBinary, "-batch", "-noheader", "-separator", fieldSep, "-newline", rowSep, dbFile.absolutePath)
            } else {
                listOf(sqliteBinary, "-batch", dbFile.absolutePath)
            }
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
            proc.outputStream.use { os ->
                os.write((sql.trimEnd(';') + ";\n").toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }
            val stdout = proc.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val stderr = proc.errorStream.readBytes().toString(StandardCharsets.UTF_8)
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException("sqlite3 failed (exit $exitCode) for SQL:\n$sql\nstderr:\n$stderr")
            }
            return stdout
        }
    }
}
