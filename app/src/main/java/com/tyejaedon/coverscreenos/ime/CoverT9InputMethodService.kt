package com.tyejaedon.coverscreenos.ime

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
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
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.keyboard.floris.ClamshellDockAnchor
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisKeyboardCustomization
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.launch

/**
 * Cover-screen software keyboard hosted via Android's [InputMethodService].
 *
 * Bridges the legacy IME view/input lifecycle to a Jetpack Compose surface
 * ([CoverFlorisKeyboardSurface]) and drives [CoverEditorBridge] + [CoverKeyboardNlpManager]
 * for T9 predictive, QWERTY, numeric, and symbol input on compact clamshell cover displays.
 */
class CoverT9InputMethodService : InputMethodService() {

    // Services do not expose ViewTree owners by default, so Compose requires a synthetic owner.
    private val composeOwner = ImeComposeOwner()
    private var fieldProfile = EditorFieldProfile(isNumeric = false, isSensitive = false)
    private var activeEditorInfo: EditorInfo? = null
    private var uiState by mutableStateOf(CoverImeUiState())
    private var activeBridge: CoverEditorBridge? = null

    /**
     * Advances the Compose lifecycle to `CREATED` and warms up the NLP dictionaries asynchronously.
     */
    override fun onCreate() {
        super.onCreate()
        composeOwner.create()
        CoverKeyboardNlpManager.initializeFromContext(applicationContext)
    }

    /**
     * Builds the [ComposeView] input window, wires ViewTree lifecycle/saved-state/store owners,
     * and binds editor text mutations to candidate predictions and DataStore-backed preferences.
     */
    override fun onCreateInputView(): View =
        ComposeView(this).apply {
            // Dispose composition if the IME window detaches rather than waiting for process death.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewTreeViewModelStoreOwner(composeOwner)
            setContent {
                CoverOSTheme {
                    val settingsStore = remember { LauncherSettingsStore(applicationContext) }
                    val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())
                    val coroutineScope = rememberCoroutineScope()

                    // Resolve currentInputConnection lazily because Android swaps it per focused editor.
                    val bridge = remember {
                        CoverEditorBridge(
                            inputConnectionProvider = { currentInputConnection },
                            editorActionHandler = ::handleEnterPressed
                        ).also { activeBridge = it }
                    }
                    val mutationRevision by bridge.mutationRevision.collectAsState()

                    var activeLayoutMode by remember(uiState.layout) {
                        mutableStateOf(uiState.layout.toCoverLayoutMode())
                    }
                    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
                    var textBeforeCursor by remember { mutableStateOf("") }

                    LaunchedEffect(uiState.layout) {
                        activeLayoutMode = uiState.layout.toCoverLayoutMode()
                    }

                    // Recompute prefix/T9 or bigram next-word candidates whenever text or mode changes.
                    LaunchedEffect(uiState.layout, activeLayoutMode, mutationRevision, uiState.isSensitive) {
                        val surrounding = bridge.getSurroundingText(50, 0)
                        textBeforeCursor = surrounding.textBefore
                        if (uiState.isSensitive || uiState.isNumeric) {
                            // Never surface or learn dictionary suggestions for passwords or numeric/PIN inputs.
                            candidates = emptyList()
                        } else {
                            val word = surrounding.textBefore.takeLastWhile { it.isLetterOrDigit() }
                            candidates = when {
                                word.isNotBlank() -> CoverKeyboardNlpManager.getCandidates(activeLayoutMode, word)
                                surrounding.textBefore.isNotBlank() -> {
                                    // Trailing whitespace after a completed word triggers bigram prediction.
                                    val prev = surrounding.textBefore.trimEnd().takeLastWhile { it.isLetter() }
                                    if (prev.isNotEmpty()) {
                                        CoverKeyboardNlpManager.getNextWordSuggestions(prev)
                                    } else emptyList()
                                }
                                else -> emptyList()
                            }
                        }
                    }

                    val dockAnchor = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Right) {
                        ClamshellDockAnchor.RIGHT
                    } else {
                        ClamshellDockAnchor.LEFT
                    }

                    CoverFlorisKeyboardSurface(
                        bridge = bridge,
                        initialMode = activeLayoutMode,
                        candidates = candidates,
                        onCandidateSelected = { candidate ->
                            bridge.commitCandidate(candidate, trailingSpace = true)
                        },
                        onDismissRequest = { requestHideSelf(0) },
                        onModeChanged = { newMode ->
                            activeLayoutMode = newMode
                            uiState = uiState.copy(layout = newMode.toCoverImeLayout())
                        },
                        customization = FlorisKeyboardCustomization(
                            extraHeightDp = settings.keyboardExtraHeightDp,
                            onExtraHeightChanged = { newDp ->
                                coroutineScope.launch { settingsStore.setKeyboardExtraHeightDp(newDp) }
                            },
                            oneHandedEnabled = settings.keyboardOneHandedEnabled,
                            onOneHandedToggle = {
                                coroutineScope.launch {
                                    settingsStore.setKeyboardOneHandedEnabled(!settings.keyboardOneHandedEnabled)
                                }
                            },
                            dockAnchor = dockAnchor,
                            onAnchorToggle = {
                                val next = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Left) {
                                    KeyboardAnchor.Right
                                } else {
                                    KeyboardAnchor.Left
                                }
                                coroutineScope.launch { settingsStore.setKeyboardOneHandedAnchor(next) }
                            },
                            suggestionStripVisible = settings.suggestionStripVisible,
                            suggestionsEnabled = !uiState.isSensitive && !uiState.isNumeric,
                            autocorrectEnabled = settings.autocorrectEnabled,
                            popupKeyPreviewEnabled = settings.popupKeyPreviewEnabled && !uiState.isSensitive,
                            imeOptions = activeEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_DONE
                        ),
                        textBeforeCursor = textBeforeCursor,
                        selectedCandidateIndex = if (textBeforeCursor.takeLastWhile { it.isLetter() }.isNotEmpty()) 0 else -1,
                        onLearnCandidate = { _, word ->
                            CoverKeyboardNlpManager.learnWord(word, applicationContext)
                            bridge.notifyExternalMutation()
                        },
                        onSuppressCandidate = { _, word ->
                            CoverKeyboardNlpManager.suppressWord(word, applicationContext)
                            bridge.notifyExternalMutation()
                        },
                        onAutocorrectWord = CoverKeyboardNlpManager::autocorrectWord,
                        t9Engine = CoverKeyboardNlpManager.t9Engine,
                        glideDecoder = CoverKeyboardNlpManager.glideDecoder
                    )
                }
            }
        }

    /**
     * Notifies [CoverEditorBridge] when cursor/selection changes originate outside the IME
     * (e.g., tapping a different position in the target text field) so suggestions stay in sync.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        activeBridge?.notifyExternalMutation()
    }

    // Cover displays have limited vertical space; never switch into extract/fullscreen mode.
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Resumes the synthetic Compose lifecycle when the soft keyboard window becomes visible. */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composeOwner.resume()
    }

    /** Pauses Compose animations and effects when the soft keyboard window hides. */
    override fun onFinishInputView(finishingInput: Boolean) {
        composeOwner.pause()
        super.onFinishInputView(finishingInput)
    }

    /** Tears down the synthetic Compose lifecycle and clears the [ViewModelStore] on service destroy. */
    override fun onDestroy() {
        composeOwner.destroy()
        super.onDestroy()
    }

    /**
     * Inspects the newly focused editor's [EditorInfo] to configure layout, sensitivity flags,
     * and the Enter/action key label before input begins.
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        activeEditorInfo = attribute
        fieldProfile = EditorFieldProfile.from(attribute)
        uiState = CoverImeUiState(
            layout = CoverImePreferences.defaultLayout(this, attribute),
            isNumeric = fieldProfile.isNumeric,
            isSensitive = fieldProfile.isSensitive,
            actionLabel = imeActionLabel(attribute)
        )
    }

    /** Resets editor-scoped state when focus leaves the target input field. */
    override fun onFinishInput() {
        super.onFinishInput()
        activeEditorInfo = null
        fieldProfile = EditorFieldProfile(isNumeric = false, isSensitive = false)
        uiState = CoverImeUiState()
    }

    /**
     * Triggers the editor's requested IME action (Go/Search/Send/Next/Done) via
     * [android.view.inputmethod.InputConnection.performEditorAction], falling back to a hardware
     * `KEYCODE_ENTER` key event sequence when no explicit action is active or handled.
     */
    private fun handleEnterPressed() {
        val connection = currentInputConnection ?: return
        val imeAction = activeEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val allowsAction = activeEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        val performed = allowsAction && imeAction != EditorInfo.IME_ACTION_NONE &&
            imeAction != EditorInfo.IME_ACTION_UNSPECIFIED &&
            connection.performEditorAction(imeAction)
        if (!performed) sendKey(connection)
    }

    /** Dispatches a synthetic `ACTION_DOWN` + `ACTION_UP` pair for [KeyEvent.KEYCODE_ENTER]. */
    private fun sendKey(connection: android.view.inputmethod.InputConnection) {
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}

/**
 * Synthetic Jetpack Compose host owner for [CoverT9InputMethodService].
 *
 * Android `InputMethodService` instances are `Service` subclasses rather than Activities, so this
 * class manually drives [LifecycleRegistry], [SavedStateRegistryController], and [ViewModelStore]
 * across IME input-view visibility transitions.
 */
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
        // Guard against repeated onFinishInputView callbacks before ON_START runs.
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

/** User-selectable keyboard layout modes exposed in IME settings and the keyboard switcher. */
internal enum class CoverImeLayout(val label: String) {
    QWERTY("ABC"), T9("T9"), NUMBER("123"), SYMBOLS("#+=")
}

/**
 * Coarse input-field categories mapped from [InputType.TYPE_MASK_CLASS] so users can configure
 * distinct default layouts per field type.
 */
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

internal fun CoverImeLayout.toCoverLayoutMode(): CoverLayoutMode = when (this) {
    CoverImeLayout.QWERTY -> CoverLayoutMode.QWERTY
    CoverImeLayout.T9 -> CoverLayoutMode.T9_PREDICTIVE
    CoverImeLayout.NUMBER -> CoverLayoutMode.NUMERIC
    CoverImeLayout.SYMBOLS -> CoverLayoutMode.SYMBOLS
}

internal fun CoverLayoutMode.toCoverImeLayout(): CoverImeLayout = when (this) {
    CoverLayoutMode.QWERTY -> CoverImeLayout.QWERTY
    CoverLayoutMode.T9_PREDICTIVE -> CoverImeLayout.T9
    CoverLayoutMode.NUMERIC -> CoverImeLayout.NUMBER
    CoverLayoutMode.SYMBOLS, CoverLayoutMode.SYMBOLS_SECONDARY -> CoverImeLayout.SYMBOLS
}

/** Resolves per-field-class default keyboard layouts stored in default app SharedPreferences. */
internal object CoverImePreferences {
    fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    }

    fun defaultLayout(context: Context, editorInfo: EditorInfo?): CoverImeLayout {
        val fieldClass = CoverImeFieldClass.from(editorInfo)
        val saved = getSharedPreferences(context)
            .getString(fieldClass.preferenceKey, null)
        return CoverImeLayout.entries.firstOrNull { it.name == saved } ?: fieldClass.defaultLayout
    }
}

// The input-method settings shortcut in Android's keyboard picker opens this activity.
class CoverImeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoverOSTheme {
                CoverImeSettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * Settings screen allowing users to customize default layouts per input class and opt in to
 * on-device contact-name suggestions via runtime `READ_CONTACTS` permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoverImeSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { CoverImePreferences.getSharedPreferences(context) }

    var contactsEnabled by remember { mutableStateOf(ContactsSuggestionConsent.isEnabled(context)) }
    // Request READ_CONTACTS only when the user explicitly opts into contact-name predictions.
    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        ContactsSuggestionConsent.setEnabled(context, granted)
        contactsEnabled = ContactsSuggestionConsent.isEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keyboard Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Default layouts by field type",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            listOf(
                CoverImeFieldClass.TEXT to "Text fields",
                CoverImeFieldClass.NUMBER to "Number fields",
                CoverImeFieldClass.PHONE to "Phone fields",
                CoverImeFieldClass.DATETIME to "Date and time fields"
            ).forEach { (fieldClass, title) ->
                var selectedLayout by remember {
                    val saved = sharedPrefs.getString(fieldClass.preferenceKey, null)
                    mutableStateOf(CoverImeLayout.entries.firstOrNull { it.name == saved } ?: fieldClass.defaultLayout)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = title, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CoverImeLayout.entries.forEach { layout ->
                                val isSelected = selectedLayout == layout
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedLayout = layout
                                        sharedPrefs.edit().putString(fieldClass.preferenceKey, layout.name).apply()
                                    },
                                    label = { Text(layout.name) }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Privacy & Suggestions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Use contacts for suggestions", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (contactsEnabled) "Contact names can appear in suggestions on this device."
                            else "Optional. Grant Contacts access to include names in suggestions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = contactsEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (ContactsSuggestionConsent.hasPermission(context)) {
                                    ContactsSuggestionConsent.setEnabled(context, true)
                                    contactsEnabled = true
                                } else {
                                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            } else {
                                ContactsSuggestionConsent.setEnabled(context, false)
                                contactsEnabled = false
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Coordinates persisted user consent in `keyboard_words` with runtime [Manifest.permission.READ_CONTACTS]
 * permission, automatically clearing stale consent if the user revokes permission in system settings.
 */
internal object ContactsSuggestionConsent {
    const val PREF_FILE = "keyboard_words"
    const val KEY = "include_contact_names"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY, false)) return false
        if (hasPermission(context)) return true
        // Clear persisted opt-in if permission was later revoked externally.
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

/** UI state derived from the focused editor's [EditorInfo] and user layout overrides. */
internal data class CoverImeUiState(
    val layout: CoverImeLayout = CoverImeLayout.QWERTY,
    val isNumeric: Boolean = false,
    val isSensitive: Boolean = false,
    val shifted: Boolean = false,
    val actionLabel: String = "ENTER"
)

/** Decodes [EditorInfo.imeOptions] action flags into a short label for the keyboard's action key. */
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

