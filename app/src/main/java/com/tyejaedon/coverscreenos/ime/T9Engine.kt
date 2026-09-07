package com.tyejaedon.coverscreenos.ime

private const val DEFAULT_MULTI_TAP_WINDOW_MS = 900L

internal data class T9CycleState(
    val lastDigit: Char? = null,
    val lastTapElapsedMs: Long = 0L,
    val cycleIndex: Int = 0
)

internal sealed interface T9CommitAction {
    data class Append(val text: String) : T9CommitAction
    data class ReplacePrevious(val text: String) : T9CommitAction
}

internal class T9Engine(
    private val multiTapWindowMs: Long = DEFAULT_MULTI_TAP_WINDOW_MS
) {
    private var cycleState: T9CycleState = T9CycleState()

    fun reset() {
        cycleState = T9CycleState()
    }

    fun onTap(
        digit: Char,
        letters: String,
        nowElapsedMs: Long,
        forceDigit: Boolean
    ): T9CommitAction {
        if (forceDigit || letters.isBlank()) {
            cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = 0)
            return T9CommitAction.Append(digit.toString())
        }

        val normalizedLetters = letters.lowercase()
        val shouldCycle = cycleState.lastDigit == digit &&
            (nowElapsedMs - cycleState.lastTapElapsedMs) in 0..multiTapWindowMs

        if (!shouldCycle) {
            cycleState = T9CycleState(lastDigit = digit, lastTapElapsedMs = nowElapsedMs, cycleIndex = 0)
            return T9CommitAction.Append(normalizedLetters.first().toString())
        }

        val nextCycleIndex = (cycleState.cycleIndex + 1) % normalizedLetters.length
        cycleState = T9CycleState(
            lastDigit = digit,
            lastTapElapsedMs = nowElapsedMs,
            cycleIndex = nextCycleIndex
        )
        return T9CommitAction.ReplacePrevious(normalizedLetters[nextCycleIndex].toString())
    }
}

