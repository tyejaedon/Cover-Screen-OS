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

private val Context.coverInputTelemetryDataStore by preferencesDataStore(name = "cover_input_telemetry")
private const val INPUT_TELEMETRY_LOG_TAG = "InputTelemetryStore"

private object InputTelemetryKeys {
    val imeShowRequestCount = longPreferencesKey("cover_ime_show_request")
    val imeShowSuccessCount = longPreferencesKey("cover_ime_show_success")
    val imeShowFailureCount = longPreferencesKey("cover_ime_show_failure")
    val oemBlockedHintCount = longPreferencesKey("cover_ime_oem_blocked_hint")
}

data class InputTelemetryCounters(
    val imeShowRequestCount: Long = 0L,
    val imeShowSuccessCount: Long = 0L,
    val imeShowFailureCount: Long = 0L,
    val oemBlockedHintShownCount: Long = 0L
) {
    val imeShowSuccessRatePercent: Int
        get() = imePickerSuccessRatePercent(
            requestCount = imeShowRequestCount,
            successCount = imeShowSuccessCount
        )
}

class InputTelemetryStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext

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

    suspend fun recordOemBlockedHintShown() {
        withContext(ioDispatcher) {
            appContext.coverInputTelemetryDataStore.edit { preferences ->
                val currentCount = preferences[InputTelemetryKeys.oemBlockedHintCount] ?: 0L
                preferences[InputTelemetryKeys.oemBlockedHintCount] = currentCount + 1L
            }
        }
    }
}

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

