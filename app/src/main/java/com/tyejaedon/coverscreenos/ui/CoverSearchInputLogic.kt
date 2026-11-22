package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.models.AppModel

/**
 * Timeout window (in milliseconds) for T9 multi-tap character cycling on the same digit key.
 * Subsequent taps within this interval cycle through the digit's letter list instead of
 * appending a new character.
 */
internal const val T9_MULTI_TAP_WINDOW_MS = 900L

/**
 * Tracks the active multi-tap cycling state between consecutive T9 keypad presses.
 *
 * @property lastDigit Most recently tapped keypad digit, or `null` before any tap.
 * @property lastTapElapsedMs Monotonic elapsed timestamp (ms) of the previous tap.
 * @property cycleIndex Zero-based index into the digit's mapped letter list.
 */
internal data class T9CycleState(
    val lastDigit: Char? = null,
    val lastTapElapsedMs: Long = 0L,
    val cycleIndex: Int = 0
)

/**
 * Immutable output produced by [applyT9CharacterTap].
 *
 * @property query Updated search text after appending or cycling a character.
 * @property cycleState Updated multi-tap state to persist for the next keypad event.
 */
internal data class T9TapResult(
    val query: String,
    val cycleState: T9CycleState
)

/**
 * Filters [apps] for the cover launcher search UI using [matchesSearchQuery].
 * Returns [apps] unchanged when [query] contains only whitespace.
 */
internal fun filterAppsForSearchQuery(apps: List<AppModel>, query: String): List<AppModel> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return apps

    return apps.filter { app ->
        matchesSearchQuery(app.name, app.packageName, normalizedQuery)
    }
}

/**
 * Returns `true` when the trimmed [query] is empty or matches [appName] or [packageName]
 * (case-insensitive substring match).
 */
internal fun matchesSearchQuery(appName: String, packageName: String, query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true

    return appName.contains(normalizedQuery, ignoreCase = true) ||
        packageName.contains(normalizedQuery, ignoreCase = true)
}

/**
 * Pure state transition for classic T9 multi-tap keypad input.
 *
 * When [letters] is empty (e.g. `0` or `1` without letter mappings), appends the literal [digit].
 * Otherwise, tapping the same [digit] within [T9_MULTI_TAP_WINDOW_MS] replaces the last character
 * in [currentQuery] with the next lowercase letter in [letters] (wrapping modulo length);
 * any other tap starts a fresh cycle by appending `letters.first()`.
 */
internal fun applyT9CharacterTap(
    currentQuery: String,
    digit: Char,
    letters: String,
    cycleState: T9CycleState,
    nowElapsedMs: Long
): T9TapResult {
    // Keys without alphabetic mappings (such as numeric-only keys) insert the raw digit directly.
    if (letters.isEmpty()) {
        return T9TapResult(
            query = currentQuery + digit,
            cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = 0)
        )
    }

    val normalizedLetters = letters.lowercase()
    // Guard against negative elapsed deltas (e.g. test clocks) and empty buffers before cycling.
    val shouldCycle = cycleState.lastDigit == digit &&
        (nowElapsedMs - cycleState.lastTapElapsedMs) in 0..T9_MULTI_TAP_WINDOW_MS &&
        currentQuery.isNotEmpty()

    if (!shouldCycle) {
        return T9TapResult(
            query = currentQuery + normalizedLetters.first(),
            cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = 0)
        )
    }

    // Advance modulo the digit's letter list and overwrite the character placed by the prior tap.
    val nextCycleIndex = (cycleState.cycleIndex + 1) % normalizedLetters.length
    val replacementChar = normalizedLetters[nextCycleIndex]
    val updatedQuery = currentQuery.dropLast(1) + replacementChar

    return T9TapResult(
        query = updatedQuery,
        cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = nextCycleIndex)
    )
}


