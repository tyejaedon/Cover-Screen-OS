package com.tyejaedon.coverscreenos.ui.customize

import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.tyejaedon.coverscreenos.BuildConfig
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.ui.appshell.CustomizeCategory
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardPermissions
import com.tyejaedon.coverscreenos.ui.permissions.FeatureDegradedNotice
import com.tyejaedon.coverscreenos.ui.customize.appearance.AppearanceScreen
import com.tyejaedon.coverscreenos.ui.customize.dock.DockScreen
import com.tyejaedon.coverscreenos.ui.customize.input.InputScreen
import com.tyejaedon.coverscreenos.ui.customize.wallpaper.WallpaperScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

private const val LOG_TAG = "CustomizeScreen"
private const val MAX_SETTINGS_JSON_BYTES = 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomizeScreen(
    category: CustomizeCategory,
    expanded: Boolean,
    actionScope: CoroutineScope,
    snackbar: SnackbarHostState,
    initialScrollPosition: Int,
    onScrollPositionChanged: (CustomizeCategory, Int) -> Unit,
    onCategorySelected: (CustomizeCategory) -> Unit,
    permissions: DashboardPermissions,
    onPermissions: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember(appContext) { LauncherSettingsStore(appContext) }
    val settings by store.settings.collectAsState(initial = LauncherSettings())
    val scope = actionScope
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var keyboardRefreshNonce by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState(initial = initialScrollPosition)
    DisposableEffect(category, scroll) {
        onDispose { onScrollPositionChanged(category, scroll.value) }
    }

    fun reportFailure(error: Exception) {
        Log.w(LOG_TAG, "Customize action failed", error)
        scope.launch { snackbar.showSnackbar(error.message ?: "Unable to update settings") }
    }

    fun runAction(success: String? = null, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                if (success != null) snackbar.showSnackbar(success)
            } catch (error: IOException) {
                reportFailure(error)
            } catch (error: SecurityException) {
                reportFailure(error)
            } catch (error: IllegalArgumentException) {
                reportFailure(error)
            } catch (error: JSONException) {
                reportFailure(error)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runAction("Settings exported") {
            val json = store.exportSettingsJson()
            withContext(Dispatchers.IO) {
                val stream = appContext.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Unable to open settings file")
                stream.bufferedWriter().use { it.write(json) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runAction("Settings imported") {
            val json = withContext(Dispatchers.IO) {
                val stream = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open settings file")
                stream.use {
                    val bytes = it.readNBytes(MAX_SETTINGS_JSON_BYTES + 1)
                    require(bytes.size <= MAX_SETTINGS_JSON_BYTES) { "Settings file is too large" }
                    bytes.toString(Charsets.UTF_8)
                }
            }
            store.importSettingsJson(json)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Customize options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Reset to defaults") },
                            onClick = { menuExpanded = false; confirmReset = true }
                        )
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(
                                text = { Text("Export settings") },
                                onClick = {
                                    menuExpanded = false
                                    runCatching { exportLauncher.launch("coverscreenos-settings.json") }
                                        .onFailure { error ->
                                            Log.w(LOG_TAG, "Unable to open export document", error)
                                            scope.launch { snackbar.showSnackbar("Unable to open file picker") }
                                        }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import settings") },
                                onClick = {
                                    menuExpanded = false
                                    runCatching { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                                        .onFailure { error ->
                                            Log.w(LOG_TAG, "Unable to open import document", error)
                                            scope.launch { snackbar.showSnackbar("Unable to open file picker") }
                                        }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!expanded) {
                PrimaryTabRow(selectedTabIndex = category.ordinal) {
                    CustomizeCategory.entries.forEach { tab ->
                        Tab(
                            selected = category == tab,
                            onClick = { onCategorySelected(tab) },
                            text = { Text(tab.title) }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxSize()) {
                if (expanded) {
                    Column(
                        modifier = Modifier.width(200.dp).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CustomizeCategory.entries.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.title) },
                                selected = item == category,
                                onClick = { onCategorySelected(item) }
                            )
                        }
                    }
                    VerticalDivider()
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(scroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (permissions.missing.isNotEmpty()) {
                        FeatureDegradedNotice(
                            "Changes can be saved, but the cover launcher cannot show them until " +
                                "${permissions.missing.joinToString()} are enabled.",
                            onPermissions
                        )
                    }
                    if (category == CustomizeCategory.INPUT && !permissions.inputAccessibility) {
                        FeatureDegradedNotice(
                            "Cover keyboard fallback and text injection need the separate input accessibility service.",
                            onPermissions
                        )
                    }
                    when (category) {
                        CustomizeCategory.WALLPAPER -> WallpaperScreen(store, settings, ::runAction)
                        CustomizeCategory.DOCK -> DockScreen(store, settings, ::runAction)
                        CustomizeCategory.APPEARANCE -> AppearanceScreen(store, settings, ::runAction)
                        CustomizeCategory.INPUT -> InputScreen(
                            settings = settings,
                            onStrategy = { preference ->
                                runAction { store.setKeyboardStrategy(preference) }
                            },
                            onPicker = {
                                val manager = appContext.getSystemService<InputMethodManager>()
                                if (manager == null) {
                                    scope.launch { snackbar.showSnackbar("Keyboard picker unavailable on this device") }
                                } else {
                                    runCatching { manager.showInputMethodPicker() }
                                        .onFailure { error ->
                                            Log.w(LOG_TAG, "IME picker unavailable", error)
                                            scope.launch { snackbar.showSnackbar("Keyboard picker unavailable on this device") }
                                        }
                                }
                                keyboardRefreshNonce++
                            },
                            onSettings = {
                                runCatching {
                                    appContext.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                }.onFailure { error ->
                                    Log.w(LOG_TAG, "IME settings unavailable", error)
                                    scope.launch { snackbar.showSnackbar("Keyboard settings unavailable on this device") }
                                }
                                keyboardRefreshNonce++
                            },
                            onResetModes = { runAction("Saved keyboard preferences reset") { store.clearSavedKeyboardModes() } },
                            refreshNonce = keyboardRefreshNonce
                        )
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ${category.title.lowercase()}?") },
            text = { Text("This restores this category's supported settings to their defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    runAction("${category.title} reset") {
                        when (category) {
                            CustomizeCategory.WALLPAPER -> store.resetWallpaperCustomization()
                            CustomizeCategory.DOCK -> store.resetDockCustomization()
                            CustomizeCategory.APPEARANCE -> store.resetAppearanceCustomization()
                            CustomizeCategory.INPUT -> store.resetInputCustomization()
                        }
                    }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}
