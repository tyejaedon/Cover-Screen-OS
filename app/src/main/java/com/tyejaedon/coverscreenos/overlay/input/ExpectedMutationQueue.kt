package com.tyejaedon.coverscreenos.overlay.input

/**
 * Tracks expected text mutations injected by the accessibility overlay so incoming
 * `TYPE_VIEW_TEXT_CHANGED` events can be deduplicated by content rather than a brittle
 * fixed-duration timer.
 */
internal data class ExpectedMutation(
    val text: String,
    val caret: Int,
    val timestampMs: Long
)

internal class ExpectedMutationQueue(
    private val staleThresholdMs: Long = 1200L
) {
    private val queue = ArrayDeque<ExpectedMutation>()

    fun enqueue(text: String, caret: Int, nowMs: Long) {
        pruneStale(nowMs)
        queue.addLast(ExpectedMutation(text = text, caret = caret, timestampMs = nowMs))
    }

    /**
     * Returns true if [observedText] matches a pending expected mutation (consuming it
     * and any older superseded entries). Returns false if it does not match (external mutation).
     */
    fun consumeIfExpected(observedText: String, nowMs: Long): Boolean {
        pruneStale(nowMs)
        val matchIndex = queue.indexOfFirst { it.text == observedText }
        if (matchIndex < 0) return false
        repeat(matchIndex + 1) { queue.removeFirst() }
        return true
    }

    fun clear() {
        queue.clear()
    }

    private fun pruneStale(nowMs: Long) {
        while (queue.isNotEmpty() && nowMs - queue.first().timestampMs > staleThresholdMs) {
            queue.removeFirst()
        }
    }
}
