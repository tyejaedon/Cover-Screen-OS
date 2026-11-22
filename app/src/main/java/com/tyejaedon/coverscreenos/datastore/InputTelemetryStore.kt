package com.tyejaedon.coverscreenos.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Top-level Jetpack `preferencesDataStore` delegate backed by
 * `files/datastore/cover_input_telemetry.preferences_pb`.
 *
 * Declaring this delegate on [Context] guarantees a single [androidx.datastore.core.DataStore]
 * instance per process for the telemetry file.
 */
private val Context.coverInputTelemetryDataStore by preferencesDataStore(name = "cover_input_telemetry")
// Logcat tag for filtering input-telemetry read warnings.
private const val INPUT_TELEMETRY_LOG_TAG = "InputTelemetryStore"

/** Strongly-typed DataStore [Preferences.Key] definitions for cover-screen IME telemetry. */
private object InputTelemetryKeys {
    // Total times the cover overlay asked Android to show the keyboard/IME picker.
    val imeShowRequestCount = longPreferencesKey("cover_ime_show_request")
    // Subset of keyboard show attempts that succeeded.
    val imeShowSuccessCount = longPreferencesKey("cover_ime_show_success")
    // Subset of keyboard show attempts that failed or were suppressed by the system.
    val imeShowFailureCount = longPreferencesKey("cover_ime_show_failure")
    // Times we showed the user a fallback tip when an OEM blocked the system keyboard on the cover screen.
    val oemBlockedHintCount = longPreferencesKey("cover_ime_oem_blocked_hint")
}

/**
 * Immutable snapshot of persisted cover-screen input telemetry counters.
 *
 * @property imeShowRequestCount Total attempts to show the system IME or input method picker on the cover display.
 * @property imeShowSuccessCount Attempts that succeeded in displaying the IME UI.
 * @property imeShowFailureCount Attempts that failed or were suppressed (e.g. by OEM window restrictions).
 * @property oemBlockedHintShownCount Number of times the user was shown an OEM-restriction workaround hint.
 */
data class InputTelemetryCounters(
    val imeShowRequestCount: Long = 0L,
    val imeShowSuccessCount: Long = 0L,
    val imeShowFailureCount: Long = 0L,
    val oemBlockedHintShownCount: Long = 0L
) {
    /** Rounded integer percentage `[0, 100]` of successful IME show requests. */
    val imeShowSuccessRatePercent: Int
        get() = imePickerSuccessRatePercent(
            requestCount = imeShowRequestCount,
            successCount = imeShowSuccessCount
        )
}

/**
 * Jetpack DataStore `<Preferences>` repository that tracks cover-display IME reliability metrics.
 *
 * Uses [Flow] mapping for non-blocking reads and atomic [edit] transactions on [ioDispatcher]
 * for concurrent-safe counter increments.
 *
 * @param context Android context; only its [Context.getApplicationContext] is retained.
 * @param ioDispatcher Coroutine dispatcher used for DataStore disk writes (defaults to [Dispatchers.IO]).
 */
class InputTelemetryStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Application-scoped Context handle so storing this repository never leaks an Activity or Service.
    private val appContext = context.applicationContext

    /**
     * Reactive stream of [InputTelemetryCounters] backed by `DataStore.data`.
     *
     * Recovers from disk/unexpected read errors by logging and emitting `emptyPreferences()`
     * (so collectors receive zeroed defaults), while re-throwing [CancellationException] to
     * preserve structured coroutine cancellation.
     */
    val counters: Flow<InputTelemetryCounters> = appContext.coverInputTelemetryDataStore.data
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            if (throwable is IOException) {
                Log.w(INPUT_TELEMETRY_LOG_TAG, "Failed to read input telemetry counters.", throwable)
            } else {
                Log.w(INPUT_TELEMETRY_LOG_TAG, "Unexpected telemetry read error.", throwable)
            }
            emit(emptyPreferences())
        }
        .map { preferences ->
            InputTelemetryCounters(
                imeShowRequestCount = preferences[InputTelemetryKeys.imeShowRequestCount] ?: 0L,
                imeShowSuccessCount = preferences[InputTelemetryKeys.imeShowSuccessCount] ?: 0L,
                imeShowFailureCount = preferences[InputTelemetryKeys.imeShowFailureCount] ?: 0L,
                oemBlockedHintShownCount = preferences[InputTelemetryKeys.oemBlockedHintCount] ?: 0L
            )
        }

    /**
     * Atomically increments [InputTelemetryCounters.imeShowRequestCount] and either the success or
     * failure counter inside a single DataStore `edit` transaction.
     */
    suspend fun recordImeShowRequest(success: Boolean) {
        withContext(ioDispatcher) {
            appContext.coverInputTelemetryDataStore.edit { preferences ->
                val currentRequestCount = preferences[InputTelemetryKeys.imeShowRequestCount] ?: 0L
                preferences[InputTelemetryKeys.imeShowRequestCount] = currentRequestCount + 1L

                if (success) {
                    val currentSuccessCount = preferences[InputTelemetryKeys.imeShowSuccessCount] ?: 0L
                    preferences[InputTelemetryKeys.imeShowSuccessCount] = currentSuccessCount + 1L
                } else {
                    val currentFailureCount = preferences[InputTelemetryKeys.imeShowFailureCount] ?: 0L
                    preferences[InputTelemetryKeys.imeShowFailureCount] = currentFailureCount + 1L
                }
            }
        }
    }

    /** Atomically increments [InputTelemetryCounters.oemBlockedHintShownCount] in DataStore. */
    suspend fun recordOemBlockedHintShown() {
        withContext(ioDispatcher) {
            appContext.coverInputTelemetryDataStore.edit { preferences ->
                val currentCount = preferences[InputTelemetryKeys.oemBlockedHintCount] ?: 0L
                preferences[InputTelemetryKeys.oemBlockedHintCount] = currentCount + 1L
            }
        }
    }
}

/**
 * Pure helper computing a clamped `[0, 100]` rounded success percentage from raw counter totals,
 * returning `0` when [requestCount] is non-positive and guarding against out-of-range counts.
 */
internal fun imePickerSuccessRatePercent(
    requestCount: Long,
    successCount: Long
): Int {
    if (requestCount <= 0L) return 0
    val boundedSuccessCount = successCount.coerceIn(0L, requestCount)
    return ((boundedSuccessCount.toDouble() / requestCount.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

