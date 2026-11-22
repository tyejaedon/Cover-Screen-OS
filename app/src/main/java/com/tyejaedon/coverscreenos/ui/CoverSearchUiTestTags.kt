package com.tyejaedon.coverscreenos.ui

/**
 * Stable tags for instrumentation tests that validate input-mode and IME-insets behavior.
 *
 * Applied via Compose `Modifier.testTag(...)` so AndroidX UI tests (`onNodeWithTag`) can target
 * specific overlay pages, search input modes (system IME vs. built-in T9 keypad), and inset
 * padding containers without coupling assertions to localized strings or layout hierarchy.
 */
object CoverSearchUiTestTags {
    // Overlay pager and primary cover-screen pages
    const val OVERLAY_PAGER = "cover_overlay_pager"
    const val LOCKSCREEN_TILE_PAGE = "cover_lockscreen_tile_page"
    const val WIDGET_HOST_PAGE = "cover_widget_host_page"
    const val SEARCH_WIDGET_GRID_PAGE = "cover_search_widget_grid_page"

    // Search header bar, input-mode switching, and IME picker controls
    const val SEARCH_INPUT_ROW = "cover_search_input_row"
    const val SEARCH_DISMISS_INPUT_BUTTON = "cover_search_dismiss_input_button"
    const val SEARCH_INPUT_MODE_LABEL = "cover_search_input_mode_label"
    const val SEARCH_INPUT_MODE_TOGGLE_BUTTON = "cover_search_mode_toggle_button"
    const val SEARCH_IME_PICKER_BUTTON = "cover_search_ime_picker_button"

    // Mode-specific input fields, embedded T9 keypad, and IME-inset container variants
    /** Text field shown when the user chooses Android's system keyboard instead of built-in T9. */
    const val SEARCH_SYSTEM_IME_FIELD = "cover_search_system_ime_field"
    /** Read-out field fed by the embedded cover T9 keypad. */
    const val SEARCH_T9_FIELD = "cover_search_t9_field"
    /** Root node of the embedded T9 keypad inside the search overlay. */
    const val SEARCH_T9_KEYPAD_ROOT = "cover_search_t9_keypad"
    /** Wrapper applying system IME bottom insets when the system keyboard is active. */
    const val SEARCH_CONTENT_CONTAINER_WITH_IME_PADDING = "cover_search_content_with_ime_padding"
    /** Wrapper omitting IME bottom insets when using the built-in T9 keypad (avoids double padding). */
    const val SEARCH_CONTENT_CONTAINER_NO_IME_PADDING = "cover_search_content_no_ime_padding"
    const val SEARCH_OPEN_AFFORDANCE_BUTTON = "cover_search_open_affordance_button"

    // Quick settings overlay panel
    const val QUICK_SETTINGS_PANEL = "cover_quick_settings_panel"
}

