package com.tyejaedon.coverscreenos.ime

internal data class T9KeySpec(
    val digit: Char,
    val letters: String
)

internal val T9_KEY_LAYOUT_ROWS: List<List<T9KeySpec>> = listOf(
    listOf(T9KeySpec('1', ""), T9KeySpec('2', "ABC"), T9KeySpec('3', "DEF")),
    listOf(T9KeySpec('4', "GHI"), T9KeySpec('5', "JKL"), T9KeySpec('6', "MNO")),
    listOf(T9KeySpec('7', "PQRS"), T9KeySpec('8', "TUV"), T9KeySpec('9', "WXYZ"))
)

