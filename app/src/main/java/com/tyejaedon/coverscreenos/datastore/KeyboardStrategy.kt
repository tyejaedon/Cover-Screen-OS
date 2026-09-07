package com.tyejaedon.coverscreenos.datastore

/**
 * Determines how text input is collected for the cover-screen app drawer search.
 *
 * - [T9]: the built-in on-cover T9 keypad. Ideal for narrow cover displays and does not
 *   require a system IME to be resized/routed into the cover overlay window.
 * - [SYSTEM_IME]: delegate to the user's default IME (Gboard, Samsung Keyboard, etc.).
 *   Requires IME insets/padding handling on the overlay.
 */
enum class KeyboardStrategy {
	T9,
	SYSTEM_IME;

	val isSystemIme: Boolean
		get() = this == SYSTEM_IME

	/** Returns the opposite strategy, used by the overlay's quick toggle button. */
	fun toggled(): KeyboardStrategy = if (this == T9) SYSTEM_IME else T9

	companion object {
		/**
		 * Resolves a persisted preference value back into a [KeyboardStrategy],
		 * falling back to [DEFAULT_KEYBOARD_STRATEGY] for unknown/legacy values.
		 */
		fun fromStorageValue(value: String?): KeyboardStrategy {
			if (value.isNullOrBlank()) return DEFAULT_KEYBOARD_STRATEGY
			return entries.firstOrNull { it.name == value } ?: DEFAULT_KEYBOARD_STRATEGY
		}
	}
}

/** Default cover-screen input strategy: the built-in T9 keypad. */
val DEFAULT_KEYBOARD_STRATEGY: KeyboardStrategy = KeyboardStrategy.T9

