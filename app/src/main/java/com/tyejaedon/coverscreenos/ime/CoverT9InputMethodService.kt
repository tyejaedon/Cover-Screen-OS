package com.tyejaedon.coverscreenos.ime

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.SystemClock
import android.preference.ListPreference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.preference.SwitchPreference
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKeyColors
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme

class CoverT9InputMethodService : InputMethodService() {

    private val t9Engine = T9Engine()
    private val composeOwner = ImeComposeOwner()
    private var fieldProfile = EditorFieldProfile(isNumeric = false, isSensitive = false)
    private var activeEditorInfo: EditorInfo? = null
    private var uiState by mutableStateOf(CoverImeUiState())

    override fun onCreate() {
        super.onCreate()
        composeOwner.create()
    }

    override fun onCreateInputView(): View =
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewTreeViewModelStoreOwner(composeOwner)
            setContent {
                CoverOSTheme {
                    CoverKeyboardHost(
                        state = uiState,
                        onLayoutSelected = ::selectLayout,
                        onShiftPressed = { uiState = uiState.copy(shifted = !uiState.shifted) },
                        onCharacterPressed = ::handleCharacterPressed,
                        onDigitPressed = ::handleDigitPressed,
                        onBackspacePressed = ::handleBackspacePressed,
                        onClearPressed = ::handleClearPressed,
                        onEnterPressed = ::handleEnterPressed,
                        onHidePressed = { requestHideSelf(0) }
                    )
                }
            }
        }

    // Cover displays have limited vertical space; never switch into extract/fullscreen mode.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composeOwner.resume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        composeOwner.pause()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        composeOwner.destroy()
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        activeEditorInfo = attribute
        fieldProfile = EditorFieldProfile.from(attribute)
        t9Engine.reset()
        uiState = CoverImeUiState(
            layout = CoverImePreferences.defaultLayout(this, attribute),
            isNumeric = fieldProfile.isNumeric,
            isSensitive = fieldProfile.isSensitive,
            actionLabel = imeActionLabel(attribute)
        )
    }

    override fun onFinishInput() {
        super.onFinishInput()
        t9Engine.reset()
        activeEditorInfo = null
        fieldProfile = EditorFieldProfile(isNumeric = false, isSensitive = false)
        uiState = CoverImeUiState()
    }

    private fun selectLayout(layout: CoverImeLayout) {
        if (layout != uiState.layout) {
            t9Engine.reset()
            uiState = uiState.copy(layout = layout, shifted = false)
        }
    }

    private fun handleCharacterPressed(text: String) {
        t9Engine.reset()
        currentInputConnection?.commitText(text, 1)
        if (uiState.shifted) uiState = uiState.copy(shifted = false)
    }

    private fun handleDigitPressed(digit: Char, letters: String) {
        val action = t9Engine.onTap(
            digit = digit,
            letters = letters,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            forceDigit = fieldProfile.isNumeric
        )
        applyCommitAction(action)
    }

    private fun handleBackspacePressed() {
        t9Engine.reset()
        val connection = currentInputConnection ?: return
        if (!connection.getSelectedText(0).isNullOrEmpty()) {
            connection.commitText("", 1)
        } else if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
            sendKey(connection, KeyEvent.KEYCODE_DEL)
        }
    }

    private fun handleClearPressed() {
        t9Engine.reset()
        val connection = currentInputConnection ?: return
        if (!connection.getSelectedText(0).isNullOrEmpty()) {
            connection.commitText("", 1)
        } else {
            connection.deleteSurroundingText(CLEAR_BEFORE_CURSOR_LIMIT_CHARS, 0)
        }
    }

    private fun handleEnterPressed() {
        t9Engine.reset()
        val connection = currentInputConnection ?: return
        val imeAction = activeEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val allowsAction = activeEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        val performed = allowsAction && imeAction != EditorInfo.IME_ACTION_NONE &&
            imeAction != EditorInfo.IME_ACTION_UNSPECIFIED &&
            connection.performEditorAction(imeAction)
        if (!performed) sendKey(connection, KeyEvent.KEYCODE_ENTER)
    }

    private fun sendKey(connection: android.view.inputmethod.InputConnection, code: Int) {
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun applyCommitAction(action: T9CommitAction) {
        val connection = currentInputConnection
        if (connection == null) {
            Log.w(IME_LOG_TAG, "No active input connection while committing T9 action.")
            t9Engine.reset()
            return
        }
        when (action) {
            is T9CommitAction.Append -> connection.commitText(action.text, 1)
            is T9CommitAction.ReplacePrevious -> {
                if (connection.getSelectedText(0).isNullOrEmpty()) {
                    connection.deleteSurroundingText(1, 0)
                }
                connection.commitText(action.text, 1)
            }
        }
    }
}

private class ImeComposeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        stateController.performAttach()
        stateController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = stateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun create() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun pause() {
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

internal enum class CoverImeLayout(val label: String) {
    QWERTY("ABC"), T9("T9"), NUMBER("123"), SYMBOLS("#+=")
}

internal enum class CoverImeFieldClass(val preferenceKey: String, val defaultLayout: CoverImeLayout) {
    TEXT("ime_default_text", CoverImeLayout.QWERTY),
    NUMBER("ime_default_number", CoverImeLayout.NUMBER),
    PHONE("ime_default_phone", CoverImeLayout.NUMBER),
    DATETIME("ime_default_datetime", CoverImeLayout.NUMBER);

    companion object {
        fun from(editorInfo: EditorInfo?): CoverImeFieldClass =
            when (editorInfo?.inputType?.and(InputType.TYPE_MASK_CLASS)) {
                InputType.TYPE_CLASS_NUMBER -> NUMBER
                InputType.TYPE_CLASS_PHONE -> PHONE
                InputType.TYPE_CLASS_DATETIME -> DATETIME
                else -> TEXT
            }
    }
}

internal object CoverImePreferences {
    fun defaultLayout(context: android.content.Context, editorInfo: EditorInfo?): CoverImeLayout {
        val fieldClass = CoverImeFieldClass.from(editorInfo)
        val saved = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(fieldClass.preferenceKey, null)
        return CoverImeLayout.entries.firstOrNull { it.name == saved } ?: fieldClass.defaultLayout
    }
}

// The input-method settings shortcut in Android's keyboard picker opens this activity.
class CoverImeSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .replace(android.R.id.content, CoverImeSettingsFragment())
                .commit()
        }
    }
}

class CoverImeSettingsFragment : PreferenceFragment() {
    private lateinit var contactsPreference: SwitchPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = preferenceManager.createPreferenceScreen(activity)
        listOf(
            CoverImeFieldClass.TEXT to "Text fields",
            CoverImeFieldClass.NUMBER to "Number fields",
            CoverImeFieldClass.PHONE to "Phone fields",
            CoverImeFieldClass.DATETIME to "Date and time fields"
        ).forEach { (fieldClass, title) ->
            screen.addPreference(ListPreference(activity).apply {
                key = fieldClass.preferenceKey
                this.title = title
                dialogTitle = "Default keyboard for $title"
                entries = arrayOf("QWERTY", "T9 multi-tap", "Numeric", "Symbols")
                entryValues = CoverImeLayout.entries.map { it.name }.toTypedArray()
                setDefaultValue(fieldClass.defaultLayout.name)
                summary = "%s"
            })
        }
        contactsPreference = SwitchPreference(activity).apply {
            key = ContactsSuggestionConsent.KEY
            title = "Use contacts for suggestions"
            summary = "Optional. Contact names stay on this device."
            isPersistent = false
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !ContactsSuggestionConsent.hasPermission(activity)) {
                    ContactsSuggestionConsent.setEnabled(activity, false)
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_PERMISSION_REQUEST)
                    false
                } else {
                    val enabled = ContactsSuggestionConsent.setEnabled(activity, newValue == true)
                    refreshContactsPreference()
                    newValue != true || enabled
                }
            }
        }
        screen.addPreference(contactsPreference)
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        refreshContactsPreference()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CONTACTS_PERMISSION_REQUEST) return
        val context = activity
        if (context == null) {
            Log.w(IME_LOG_TAG, "Contacts permission result received after settings was detached.")
            return
        }
        val contactIndex = permissions.indexOf(Manifest.permission.READ_CONTACTS)
        val granted = contactIndex >= 0 &&
            grantResults.getOrNull(contactIndex) == PackageManager.PERMISSION_GRANTED
        ContactsSuggestionConsent.setEnabled(context, granted)
        refreshContactsPreference()
    }

    private fun refreshContactsPreference() {
        val enabled = ContactsSuggestionConsent.isEnabled(activity)
        contactsPreference.isChecked = enabled
        contactsPreference.summary = if (enabled) {
            "Contact names can appear in suggestions on this device."
        } else {
            "Optional. Grant Contacts access to include names in suggestions."
        }
    }
}

internal object ContactsSuggestionConsent {
    const val PREF_FILE = "keyboard_words"
    const val KEY = "include_contact_names"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY, false)) return false
        if (hasPermission(context)) return true
        preferences.edit().remove(KEY).apply()
        return false
    }

    fun setEnabled(context: Context, requested: Boolean): Boolean {
        val enabled = requested && hasPermission(context)
        val editor = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
        if (enabled) editor.putBoolean(KEY, true) else editor.remove(KEY)
        editor.apply()
        return enabled
    }
}

private const val CONTACTS_PERMISSION_REQUEST = 201

internal data class CoverImeUiState(
    val layout: CoverImeLayout = CoverImeLayout.QWERTY,
    val isNumeric: Boolean = false,
    val isSensitive: Boolean = false,
    val shifted: Boolean = false,
    val actionLabel: String = "ENTER"
)

internal fun imeActionLabel(editorInfo: EditorInfo?): String {
    val options = editorInfo?.imeOptions ?: return "ENTER"
    if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "ENTER"
    return when (options and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_GO -> "GO"
        EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
        EditorInfo.IME_ACTION_SEND -> "SEND"
        EditorInfo.IME_ACTION_NEXT -> "NEXT"
        EditorInfo.IME_ACTION_DONE -> "DONE"
        EditorInfo.IME_ACTION_PREVIOUS -> "PREV"
        else -> "ENTER"
    }
}

private const val IME_LOG_TAG = "CoverT9IME"
private const val CLEAR_BEFORE_CURSOR_LIMIT_CHARS = 4096

private val QWERTY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val NUMBER_ROWS = listOf("123", "456", "789", "-0.")
private val SYMBOL_ROWS = listOf("1234567890", "@#\$%&-+()/", "*\"':;!?_=", ".,")

@Composable
internal fun CoverKeyboardHost(
    state: CoverImeUiState,
    onLayoutSelected: (CoverImeLayout) -> Unit,
    onShiftPressed: () -> Unit,
    onCharacterPressed: (String) -> Unit,
    onDigitPressed: (Char, String) -> Unit,
    onBackspacePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onEnterPressed: () -> Unit,
    onHidePressed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            CoverImeLayout.entries.forEach { layout ->
                KeyboardKey(
                    label = layout.label,
                    onClick = { onLayoutSelected(layout) },
                    modifier = Modifier.weight(1f).height(36.dp).testTag("ime_layout_${layout.name.lowercase()}"),
                    colors = if (state.layout == layout) KeyboardKeyColors.accent() else KeyboardKeyColors.default()
                )
            }
        }
        when (state.layout) {
            CoverImeLayout.QWERTY -> {
                QWERTY_ROWS.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("ime_qwerty_row_$index"),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (index == 2) {
                            KeyboardKey(
                                label = "⇧",
                                contentDescription = "Shift",
                                onClick = onShiftPressed,
                                modifier = Modifier.weight(1.25f).height(40.dp),
                                colors = if (state.shifted) KeyboardKeyColors.accent() else KeyboardKeyColors.default()
                            )
                        }
                        row.forEach { char ->
                            val text = if (state.shifted) char.uppercaseChar().toString() else char.toString()
                            KeyboardKey(
                                label = text,
                                onClick = { onCharacterPressed(text) },
                                modifier = Modifier.weight(1f).height(40.dp),
                                showKeyPreview = !state.isSensitive
                            )
                        }
                        if (index == 2) {
                            KeyboardKey(
                                icon = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                onClick = onBackspacePressed,
                                repeatOnHold = true,
                                modifier = Modifier.weight(1.25f).height(40.dp)
                            )
                        }
                    }
                }
            }
            CoverImeLayout.T9 -> {
                T9_KEY_LAYOUT_ROWS.forEachIndexed { index, row ->
                    Row(modifier = Modifier.fillMaxWidth().testTag("ime_t9_row_$index"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { key ->
                            KeyboardKey(
                                label = if (state.isNumeric || key.letters.isEmpty()) {
                                    key.digit.toString()
                                } else {
                                    "${key.digit} ${key.letters}"
                                },
                                onClick = { onDigitPressed(key.digit, key.letters) },
                                modifier = Modifier.weight(1f).height(42.dp)
                            )
                        }
                    }
                }
            }
            CoverImeLayout.NUMBER, CoverImeLayout.SYMBOLS -> {
                val rows = if (state.layout == CoverImeLayout.NUMBER) NUMBER_ROWS else SYMBOL_ROWS
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("ime_${state.layout.name.lowercase()}_row_$index"),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        row.forEach { char ->
                            KeyboardKey(
                                label = char.toString(),
                                onClick = { onCharacterPressed(char.toString()) },
                                modifier = Modifier.weight(1f).height(40.dp),
                                showKeyPreview = !state.isSensitive
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            KeyboardKey(
                label = "CLR",
                onClick = onClearPressed,
                modifier = Modifier.weight(1f).height(42.dp)
            )
            KeyboardKey(
                label = if (state.isNumeric) "0" else "SPACE",
                onClick = { onCharacterPressed(if (state.isNumeric) "0" else " ") },
                modifier = Modifier.weight(2f).height(42.dp)
            )
            KeyboardKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                onClick = onBackspacePressed,
                repeatOnHold = true,
                modifier = Modifier.weight(1f).height(42.dp)
            )
            KeyboardKey(
                label = state.actionLabel,
                onClick = onEnterPressed,
                modifier = Modifier.weight(1.5f).height(42.dp),
                colors = KeyboardKeyColors.accent()
            )
            KeyboardKey(
                icon = Icons.Filled.KeyboardHide,
                contentDescription = "Hide keyboard",
                onClick = onHidePressed,
                modifier = Modifier.weight(1f).height(42.dp)
            )
        }
    }
}
