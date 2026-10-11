package com.tyejaedon.coverscreenos.ui.keyboard.primitives

/**
 * Most-recent-first ring buffer of copied/cut text snippets for the cover keyboard's
 * clipboard-history strip (Phase H). Pure Kotlin — no Android/Compose dependency — so it's
 * trivially unit-testable and can be driven from either the accessibility-overlay path or a
 * future IME-side integration.
 *
 * Re-recording an already-present snippet moves it back to the front instead of adding a
 * duplicate entry, and the buffer never exceeds [maxSize].
 */
class ClipboardHistory(private val maxSize: Int = DEFAULT_MAX_SIZE) {

    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    private val entries = mutableListOf<String>()

    /** Most-recent-first snapshot of the current history. */
    val snippets: List<String> get() = entries.toList()

    /** Records [text] as the newest snippet. Blank text is ignored. */
    fun record(text: String) {
        if (text.isBlank()) return
        entries.remove(text)
        entries.add(0, text)
        while (entries.size > maxSize) entries.removeAt(entries.size - 1)
    }

    /** Removes every recorded snippet. */
    fun clear() {
        entries.clear()
    }

    companion object {
        const val DEFAULT_MAX_SIZE: Int = 5
    }
}
