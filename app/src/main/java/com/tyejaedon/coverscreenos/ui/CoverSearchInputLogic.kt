package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.models.AppModel

internal const val T9_MULTI_TAP_WINDOW_MS = 900L

internal data class T9CycleState(
    val lastDigit: Char? = null,
    val lastTapElapsedMs: Long = 0L,
    val cycleIndex: Int = 0
)

internal data class T9TapResult(
    val query: String,
    val cycleState: T9CycleState
)

internal fun filterAppsForSearchQuery(apps: List<AppModel>, query: String): List<AppModel> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return apps

    return apps.filter { app ->
        matchesSearchQuery(app.name, app.packageName, normalizedQuery)
    }
}

internal fun matchesSearchQuery(appName: String, packageName: String, query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true

    return appName.contains(normalizedQuery, ignoreCase = true) ||
        packageName.contains(normalizedQuery, ignoreCase = true)
}

internal fun applyT9CharacterTap(
    currentQuery: String,
    digit: Char,
    letters: String,
    cycleState: T9CycleState,
    nowElapsedMs: Long
): T9TapResult {
    if (letters.isEmpty()) {
        return T9TapResult(
            query = currentQuery + digit,
            cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = 0)
        )
    }

    val normalizedLetters = letters.lowercase()
    val shouldCycle = cycleState.lastDigit == digit &&
        (nowElapsedMs - cycleState.lastTapElapsedMs) in 0..T9_MULTI_TAP_WINDOW_MS &&
        currentQuery.isNotEmpty()

    if (!shouldCycle) {
        return T9TapResult(
            query = currentQuery + normalizedLetters.first(),
            cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = 0)
        )
    }

    val nextCycleIndex = (cycleState.cycleIndex + 1) % normalizedLetters.length
    val replacementChar = normalizedLetters[nextCycleIndex]
    val updatedQuery = currentQuery.dropLast(1) + replacementChar

    return T9TapResult(
        query = updatedQuery,
        cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = nextCycleIndex)
    )
}


