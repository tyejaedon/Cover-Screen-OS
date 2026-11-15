package com.tyejaedon.coverscreenos.overlay.input

internal fun previousWordLength(text: String): Int {
    val trimmed = text.trimEnd()
    if (trimmed.isEmpty()) return text.length
    val lastSpace = trimmed.lastIndexOf(' ')
    return if (lastSpace == -1) text.length else text.length - lastSpace - 1
}
