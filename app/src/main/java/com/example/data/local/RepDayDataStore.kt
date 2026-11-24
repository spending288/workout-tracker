package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.domain.RecordDecodeRules
import com.example.domain.RestCompletionOutcome
import com.example.domain.RestTimerRules
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val Context.repDayDataStore: DataStore<Preferences> by preferencesDataStore(name = "repday_prefs")

data class RestTimerSnapshot(
    val endTimeMs: Long? = null,
    val totalDurationSec: Int = 90,
    val exerciseName: String = "",
    val completed: Boolean = false
)

enum class RestClaimStatus {
    ALREADY_HANDLED,
    COMPLETED_WITH_ALERT,
    COMPLETED_SILENT_STALE
}

data class RestClaimResult(
    val status: RestClaimStatus,
    val exerciseName: String = ""
)

class RepDayDataStore(
    private val context: Context,
    private val moshi: Moshi = Moshi.Builder().build()
) {
    private val settingsAdapter = moshi.adapter(AppSettings::class.java)
    private val activeWorkoutAdapter = moshi.adapter(ActiveWorkout::class.java)

    private object Keys {
        val HAS_SEEDED = booleanPreferencesKey("has_seeded_v1")
        val HAS_ASKED_NOTIFICATION_PERM = booleanPreferencesKey("has_asked_notification_perm_v1")
        val DISMISSED_SAMPLE_HISTORY_BANNER = booleanPreferencesKey("dismissed_sample_history_banner_v1")
        val SETTINGS_JSON = stringPreferencesKey("settings_json")
        val SETTINGS_CORRUPT_BACKUP_JSON = stringPreferencesKey("settings_corrupt_backup_json")
        val ACTIVE_WORKOUT_JSON = stringPreferencesKey("active_workout_json")
        val ACTIVE_WORKOUT_CORRUPT_BACKUP_JSON = stringPreferencesKey("active_workout_corrupt_backup_json")
        val REST_END_TIME_MS = longPreferencesKey("rest_end_time_ms")
        val REST_TOTAL_SEC = intPreferencesKey("rest_total_sec")
        val REST_EXERCISE_NAME = stringPreferencesKey("rest_exercise_name")
        val REST_COMPLETED = booleanPreferencesKey("rest_completed")
    }

    private val _unreadableKeys = MutableStateFlow<Set<String>>(emptySet())
    val unreadableKeysFlow: Flow<Set<String>> = _unreadableKeys.asStateFlow()

    private fun markKeyUnreadable(key: String, unreadable: Boolean) {
        _unreadableKeys.update { current ->
            if (unreadable) current + key else current - key
        }
    }

    val settingsFlow: Flow<AppSettings> = context.repDayDataStore.data.map { prefs ->
        val json = prefs[Keys.SETTINGS_JSON]
        if (json.isNullOrBlank()) {
            markKeyUnreadable(KEY_SETTINGS_TAG, false)
            AppSettings()
        } else {
            val (decoded, err) = RecordDecodeRules.decodeSingle(
                source = "datastore",
                recordId = KEY_SETTINGS_TAG,
                rawJson = json,
                decode = { settingsAdapter.fromJson(it) },
                onError = { rec, t ->
                    Log.e(TAG, "Unreadable ${rec.source} record id=${rec.recordId}: ${rec.errorMessage}", t)
                }
            )
            markKeyUnreadable(KEY_SETTINGS_TAG, err != null)
            decoded ?: AppSettings()
        }
    }.flowOn(Dispatchers.Default)

    val activeWorkoutFlow: Flow<ActiveWorkout?> = context.repDayDataStore.data.map { prefs ->
        val json = prefs[Keys.ACTIVE_WORKOUT_JSON]
        if (json.isNullOrBlank()) {
            markKeyUnreadable(KEY_ACTIVE_WORKOUT_TAG, false)
            null
        } else {
            val (decoded, err) = RecordDecodeRules.decodeSingle(
                source = "datastore",
                recordId = KEY_ACTIVE_WORKOUT_TAG,
                rawJson = json,
                decode = { activeWorkoutAdapter.fromJson(it) },
                onError = { rec, t ->
                    Log.e(TAG, "Unreadable ${rec.source} record id=${rec.recordId}: ${rec.errorMessage}", t)
                }
            )
            markKeyUnreadable(KEY_ACTIVE_WORKOUT_TAG, err != null)
            decoded
        }
    }.flowOn(Dispatchers.Default)

    val restTimerFlow: Flow<RestTimerSnapshot> = context.repDayDataStore.data.map { prefs ->
        val endMs = prefs[Keys.REST_END_TIME_MS]
        RestTimerSnapshot(
            endTimeMs = if (endMs == null || endMs <= 0L) null else endMs,
            totalDurationSec = prefs[Keys.REST_TOTAL_SEC] ?: 90,
            exerciseName = prefs[Keys.REST_EXERCISE_NAME] ?: "",
            completed = prefs[Keys.REST_COMPLETED] ?: false
        )
    }.flowOn(Dispatchers.Default)

    val dismissedSampleHistoryBannerFlow: Flow<Boolean> = context.repDayDataStore.data.map { prefs ->
        prefs[Keys.DISMISSED_SAMPLE_HISTORY_BANNER] ?: false
    }.flowOn(Dispatchers.Default)

    suspend fun hasSeeded(): Boolean =
        context.repDayDataStore.data.first()[Keys.HAS_SEEDED] ?: false

    suspend fun markSeeded() {
        context.repDayDataStore.edit { it[Keys.HAS_SEEDED] = true }
    }

    suspend fun markSampleHistoryBannerDismissed() {
        context.repDayDataStore.edit { it[Keys.DISMISSED_SAMPLE_HISTORY_BANNER] = true }
    }

    suspend fun hasAskedNotificationPermission(): Boolean =
        context.repDayDataStore.data.first()[Keys.HAS_ASKED_NOTIFICATION_PERM] ?: false

    suspend fun markAskedNotificationPermission() {
        context.repDayDataStore.edit { it[Keys.HAS_ASKED_NOTIFICATION_PERM] = true }
    }

    suspend fun backupCorruptEntriesIfNeeded() {
        context.repDayDataStore.edit { prefs ->
            val rawActive = prefs[Keys.ACTIVE_WORKOUT_JSON]
            if (!rawActive.isNullOrBlank() && prefs[Keys.ACTIVE_WORKOUT_CORRUPT_BACKUP_JSON].isNullOrBlank()) {
                val (_, err) = RecordDecodeRules.decodeSingle(
                    source = "datastore",
                    recordId = KEY_ACTIVE_WORKOUT_TAG,
                    rawJson = rawActive,
                    decode = { activeWorkoutAdapter.fromJson(it) }
                )
                if (err != null) {
                    prefs[Keys.ACTIVE_WORKOUT_CORRUPT_BACKUP_JSON] = rawActive
                }
            }
            val rawSettings = prefs[Keys.SETTINGS_JSON]
            if (!rawSettings.isNullOrBlank() && prefs[Keys.SETTINGS_CORRUPT_BACKUP_JSON].isNullOrBlank()) {
                val (_, err) = RecordDecodeRules.decodeSingle(
                    source = "datastore",
                    recordId = KEY_SETTINGS_TAG,
                    rawJson = rawSettings,
                    decode = { settingsAdapter.fromJson(it) }
                )
                if (err != null) {
                    prefs[Keys.SETTINGS_CORRUPT_BACKUP_JSON] = rawSettings
                }
            }
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        val json = settingsAdapter.toJson(settings)
        context.repDayDataStore.edit { prefs ->
            val existing = prefs[Keys.SETTINGS_JSON]
            if (!existing.isNullOrBlank() && prefs[Keys.SETTINGS_CORRUPT_BACKUP_JSON].isNullOrBlank()) {
                val (_, err) = RecordDecodeRules.decodeSingle(
                    source = "datastore",
                    recordId = KEY_SETTINGS_TAG,
                    rawJson = existing,
                    decode = { settingsAdapter.fromJson(it) }
                )
                if (err != null) {
                    prefs[Keys.SETTINGS_CORRUPT_BACKUP_JSON] = existing
                }
            }
            prefs[Keys.SETTINGS_JSON] = json
        }
        markKeyUnreadable(KEY_SETTINGS_TAG, false)
    }

    suspend fun saveActiveWorkout(workout: ActiveWorkout?) {
        context.repDayDataStore.edit { prefs ->
            val existing = prefs[Keys.ACTIVE_WORKOUT_JSON]
            if (!existing.isNullOrBlank() && prefs[Keys.ACTIVE_WORKOUT_CORRUPT_BACKUP_JSON].isNullOrBlank()) {
                val (_, err) = RecordDecodeRules.decodeSingle(
                    source = "datastore",
                    recordId = KEY_ACTIVE_WORKOUT_TAG,
                    rawJson = existing,
                    decode = { activeWorkoutAdapter.fromJson(it) }
                )
                if (err != null) {
                    prefs[Keys.ACTIVE_WORKOUT_CORRUPT_BACKUP_JSON] = existing
                }
            }
            if (workout == null) {
                prefs.remove(Keys.ACTIVE_WORKOUT_JSON)
            } else {
                prefs[Keys.ACTIVE_WORKOUT_JSON] = activeWorkoutAdapter.toJson(workout)
            }
        }
        markKeyUnreadable(KEY_ACTIVE_WORKOUT_TAG, false)
    }

    suspend fun saveRestTimer(snapshot: RestTimerSnapshot) {
        context.repDayDataStore.edit { prefs ->
            if (snapshot.endTimeMs == null && !snapshot.completed) {
                prefs.remove(Keys.REST_END_TIME_MS)
                prefs.remove(Keys.REST_TOTAL_SEC)
                prefs.remove(Keys.REST_EXERCISE_NAME)
                prefs[Keys.REST_COMPLETED] = false
            } else {
                prefs[Keys.REST_END_TIME_MS] = snapshot.endTimeMs ?: 0L
                prefs[Keys.REST_TOTAL_SEC] = snapshot.totalDurationSec
                prefs[Keys.REST_EXERCISE_NAME] = snapshot.exerciseName
                prefs[Keys.REST_COMPLETED] = snapshot.completed
            }
        }
    }

    /**
     * Atomically transitions a running rest timer to completed so that at most one caller
     * (either the foreground service or the in-app ViewModel) fires the completion alert.
     */
    suspend fun claimRestTimerCompletion(
        expectedEndTimeMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): RestClaimResult {
        var result = RestClaimResult(RestClaimStatus.ALREADY_HANDLED)
        context.repDayDataStore.edit { prefs ->
            val storedEndMs = prefs[Keys.REST_END_TIME_MS] ?: 0L
            val alreadyCompleted = prefs[Keys.REST_COMPLETED] ?: false
            if (alreadyCompleted || storedEndMs <= 0L || storedEndMs != expectedEndTimeMs) {
                result = RestClaimResult(RestClaimStatus.ALREADY_HANDLED)
                return@edit
            }
            val outcome = RestTimerRules.evaluateRestTimer(
                endTimeMs = storedEndMs,
                completed = false,
                nowMs = nowMs
            )
            when (outcome) {
                RestCompletionOutcome.COMPLETE_WITH_ALERT -> {
                    prefs[Keys.REST_END_TIME_MS] = 0L
                    prefs[Keys.REST_COMPLETED] = true
                    result = RestClaimResult(
                        status = RestClaimStatus.COMPLETED_WITH_ALERT,
                        exerciseName = prefs[Keys.REST_EXERCISE_NAME] ?: ""
                    )
                }
                RestCompletionOutcome.COMPLETE_SILENT_STALE -> {
                    prefs[Keys.REST_END_TIME_MS] = 0L
                    prefs[Keys.REST_COMPLETED] = true
                    result = RestClaimResult(
                        status = RestClaimStatus.COMPLETED_SILENT_STALE,
                        exerciseName = prefs[Keys.REST_EXERCISE_NAME] ?: ""
                    )
                }
                else -> {
                    result = RestClaimResult(RestClaimStatus.ALREADY_HANDLED)
                }
            }
        }
        return result
    }

    /**
     * Atomically adjusts the stored rest timer end timestamp by [deltaSeconds].
     */
    suspend fun adjustRestTimer(
        deltaSeconds: Int,
        nowMs: Long = System.currentTimeMillis()
    ): RestTimerSnapshot {
        var updated = RestTimerSnapshot()
        context.repDayDataStore.edit { prefs ->
            val storedEndMs = prefs[Keys.REST_END_TIME_MS]?.takeIf { it > 0L }
            val storedTotal = prefs[Keys.REST_TOTAL_SEC] ?: 90
            val storedEx = prefs[Keys.REST_EXERCISE_NAME] ?: ""
            val storedCompleted = prefs[Keys.REST_COMPLETED] ?: false

            val adjusted = RestTimerRules.adjustRestEndTime(
                currentEndTimeMs = storedEndMs,
                currentTotalDurationSec = storedTotal,
                completed = storedCompleted,
                deltaSeconds = deltaSeconds,
                nowMs = nowMs
            )
            if (adjusted.endTimeMs == null && !adjusted.completed) {
                prefs.remove(Keys.REST_END_TIME_MS)
                prefs.remove(Keys.REST_TOTAL_SEC)
                prefs.remove(Keys.REST_EXERCISE_NAME)
                prefs[Keys.REST_COMPLETED] = false
                updated = RestTimerSnapshot()
            } else {
                prefs[Keys.REST_END_TIME_MS] = adjusted.endTimeMs ?: 0L
                prefs[Keys.REST_TOTAL_SEC] = adjusted.totalDurationSec
                prefs[Keys.REST_EXERCISE_NAME] = storedEx
                prefs[Keys.REST_COMPLETED] = adjusted.completed
                updated = RestTimerSnapshot(
                    endTimeMs = adjusted.endTimeMs,
                    totalDurationSec = adjusted.totalDurationSec,
                    exerciseName = storedEx,
                    completed = adjusted.completed
                )
            }
        }
        return updated
    }

    companion object {
        private const val TAG = "RepDayDataStore"
        const val KEY_SETTINGS_TAG = "datastore:settings_json"
        const val KEY_ACTIVE_WORKOUT_TAG = "datastore:active_workout_json"
    }
}
