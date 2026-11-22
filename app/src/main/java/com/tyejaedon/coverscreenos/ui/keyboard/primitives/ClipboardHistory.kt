package com.tyejaedon.coverscreenos.ui.keyboard.primitives

/**
 * Most-recent-first ring buffer of copied/cut text snippets for the cover keyboard's
 * clipboard-history strip (Phase H). Pure Kotlin — no Android/Compose dependency — so it's
 * trivially unit-testable and can be driven from either the accessibility-overlay path (fed by
 * Android's `ClipboardManager` clip changes or in-app copy/cut actions) or a future IME-side
 * integration.
 *
 * Re-recording an already-present snippet moves it back to the front instead of adding a
 * duplicate entry, and the buffer never exceeds [maxSize]. Because entries are guaranteed unique,
 * Compose list keys in [ClipboardHistoryRow] can safely use the snippet text itself.
 * @property maxSize Maximum number of distinct clips kept before evicting the oldest entry.
 */
class ClipboardHistory(private val maxSize: Int = DEFAULT_MAX_SIZE) {

    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    // Backing list ordered newest-at-index-0 to oldest-at-tail, capped at maxSize.
    private val entries = mutableListOf<String>()

    /** Most-recent-first snapshot of the current history. */
    val snippets: List<String> get() = entries.toList()

    /** Records [text] as the newest snippet. Blank text is ignored. */
    fun record(text: String) {
        if (text.isBlank()) return
        // Deduplicate by removing any older occurrence before promoting the snippet to index 0.
        entries.remove(text)
        entries.add(0, text)
        // Evict oldest tail entries to stay within the bounded capacity.
        while (entries.size > maxSize) entries.removeAt(entries.size - 1)
    }

    /** Removes every recorded snippet. */
    fun clear() {
        entries.clear()
    }

    /** Default configuration constants for [ClipboardHistory]. */
    companion object {
        /** Default maximum number of distinct clipboard snippets retained in memory. */
        const val DEFAULT_MAX_SIZE: Int = 5
    }
}
