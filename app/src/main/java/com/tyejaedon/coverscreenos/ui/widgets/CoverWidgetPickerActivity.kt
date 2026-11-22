package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compact One UI-styled widget picker hosted on the cover display (or main display)
 * to allocate, bind, configure, and persist [AppWidgetHost] widgets for Cover Screen OS.
 */
class CoverWidgetPickerActivity : ComponentActivity() {

    // Host helper used to list installed widget providers, allocate IDs, and bind widgets.
    private val widgetHostManager by lazy { CoverAppWidgetHostManager(this) }
    // DataStore handle where finalized pinned widget IDs are saved permanently.
    private val settingsStore by lazy { LauncherSettingsStore(applicationContext) }

    // Pending widget state retained across the external bind-permission and widget-configure subflows.
    // Newly allocated widget slot ID awaiting bind permission and/or provider configuration.
    private var pendingAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    // Provider metadata for the widget currently going through bind/configure.
    private var pendingProviderInfo: AppWidgetProviderInfo? = null
    // Re-entrancy guard ignoring duplicate card taps while an OS permission/config dialog is open.
    private var isAwaitingExternalFlow: Boolean = false

    /**
     * Handles the system [AppWidgetManager.ACTION_APPWIDGET_BIND] consent prompt result.
     * Continues to widget configuration on [RESULT_OK], or deletes the allocated slot on cancel.
     */
    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingAppWidgetId
        val providerInfo = pendingProviderInfo
        if (result.resultCode == RESULT_OK &&
            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            providerInfo != null
        ) {
            handleBoundWidget(widgetId, providerInfo)
        } else {
            cleanupPendingWidget()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Current theme and corner-radius preferences so the picker matches the cover launcher.
            val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())

            CoverOSTheme(
                themePreference = settings.themePreference,
                accentColor = settings.accentColor,
                panelCornerRadiusDp = settings.panelCornerRadiusDp
            ) {
                CoverWidgetPickerScreen(
                    widgetHostManager = widgetHostManager,
                    onSelectProvider = ::onProviderSelected,
                    onClose = {
                        cleanupPendingWidget()
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    /**
     * Receives the result of the provider's configuration activity started via
     * `AppWidgetHost.startAppWidgetConfigureActivityForResult`, finalizing or cleaning up the slot.
     */
    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val widgetId = pendingAppWidgetId
            if (resultCode == RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finalizeAddedWidget(widgetId)
            } else {
                cleanupPendingWidget()
            }
        }
    }

    /** Cleans up any unfinalized `appWidgetId` unless destroying for a configuration/fold change. */
    override fun onDestroy() {
        if (!isChangingConfigurations) {
            cleanupPendingWidget()
        }
        super.onDestroy()
    }

    /**
     * Step 1 & 2 of widget placement: allocates a host ID and attempts silent binding; if the
     * system denies silent binding, opens the OS bind-permission dialog via [bindWidgetLauncher].
     */
    private fun onProviderSelected(info: AppWidgetProviderInfo) {
        if (isAwaitingExternalFlow) return
        cleanupPendingWidget()

        // Reserve a fresh numeric widget slot ID from Android's AppWidgetHost.
        val appWidgetId = widgetHostManager.allocateAppWidgetId()
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, "Unable to allocate widget slot", Toast.LENGTH_SHORT).show()
            return
        }

        pendingAppWidgetId = appWidgetId
        pendingProviderInfo = info
        isAwaitingExternalFlow = true

        if (widgetHostManager.bindAppWidgetIdIfAllowed(appWidgetId, info)) {
            handleBoundWidget(appWidgetId, info)
        } else {
            runCatching {
                bindWidgetLauncher.launch(widgetHostManager.createBindIntent(appWidgetId, info))
            }.onFailure {
                cleanupPendingWidget()
                Toast.makeText(this, "Unable to request widget permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Step 3 of widget placement: opens the provider's [AppWidgetProviderInfo.configure] activity
     * if declared, or immediately persists the newly bound widget.
     */
    private fun handleBoundWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            // Tracks whether Android launched the widget's optional setup screen successfully.
            val started = widgetHostManager.startConfigureActivityForResult(
                activity = this,
                appWidgetId = appWidgetId,
                requestCode = REQUEST_CONFIGURE_WIDGET
            )
            if (!started) {
                cleanupPendingWidget()
                Toast.makeText(this, "Unable to configure widget", Toast.LENGTH_SHORT).show()
            }
        } else {
            finalizeAddedWidget(appWidgetId)
        }
    }

    /** Persists the bound [appWidgetId] into [LauncherSettingsStore] and closes with [RESULT_OK]. */
    private fun finalizeAddedWidget(appWidgetId: Int) {
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProviderInfo = null
        isAwaitingExternalFlow = false

        lifecycleScope.launch {
            runCatching {
                settingsStore.addWidgetId(appWidgetId)
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    /** Releases any unconfirmed `appWidgetId` back to `AppWidgetHost` so cancelled flows do not leak IDs. */
    private fun cleanupPendingWidget() {
        // Snapshot before resetting pendingAppWidgetId so deleteAppWidgetId frees the right slot.
        val id = pendingAppWidgetId
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProviderInfo = null
        isAwaitingExternalFlow = false
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetHostManager.deleteAppWidgetId(id)
        }
    }

    companion object {
        // Legacy request code passed to AppWidgetHost.startAppWidgetConfigureActivityForResult.
        private const val REQUEST_CONFIGURE_WIDGET = 4102
    }
}

/** Display model summarizing a discoverable [AppWidgetProviderInfo] for the picker list. */
private data class WidgetProviderItem(
    val info: AppWidgetProviderInfo,
    val widgetLabel: String,
    val appLabel: String,
    val cellEstimate: String
)

/**
 * Searchable widget-selection screen that loads installed [AppWidgetProviderInfo] descriptors
 * off the main thread via [produceState] and renders them in a [LazyColumn].
 */
@Composable
private fun CoverWidgetPickerScreen(
    widgetHostManager: CoverAppWidgetHostManager,
    onSelectProvider: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Current text typed into the search bar to filter widgets by label or app name.
    var searchQuery by remember { mutableStateOf("") }

    // Installed widget list loaded asynchronously on Dispatchers.Default (null while still loading).
    // Resolve PackageManager app/widget labels asynchronously on Dispatchers.Default.
    val providers by produceState<List<WidgetProviderItem>?>(initialValue = null, widgetHostManager) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            widgetHostManager.getInstalledProviders()
                .map { info ->
                    val pkg = info.provider.packageName
                    val appLabel = runCatching {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    }.getOrDefault(pkg)
                    val widgetLabel = runCatching {
                        info.loadLabel(pm).trim().ifEmpty { appLabel }
                    }.getOrDefault(appLabel)
                    // Estimate launcher grid cells using Android's standard ~70dp-per-cell formula.
                    val cols = ((info.minWidth + 30) / 70).coerceAtLeast(1)
                    val rows = ((info.minHeight + 30) / 70).coerceAtLeast(1)
                    WidgetProviderItem(
                        info = info,
                        widgetLabel = widgetLabel,
                        appLabel = appLabel,
                        cellEstimate = "${cols}×${rows}"
                    )
                }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }))
        }
    }

    // Filtered subset of providers matching searchQuery across widget and app names.
    val filteredProviders = remember(providers, searchQuery) {
        val list = providers.orEmpty()
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            list
        } else {
            list.filter {
                it.widgetLabel.lowercase().contains(query) ||
                    it.appLabel.lowercase().contains(query)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Widget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Compact search input
            val cornerRadius = LocalCoverPanelCornerRadius.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search widgets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                providers == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                filteredProviders.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No widgets available" else "No matching widgets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredProviders,
                            // Include user profile hash so personal and work-profile providers receive unique keys.
                            key = { "${it.info.provider.flattenToShortString()}:${it.info.profile.hashCode()}" }
                        ) { item ->
                            WidgetProviderRow(
                                item = item,
                                onClick = { onSelectProvider(item.info) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Renders a single widget provider row, lazily decoding its launcher icon off the UI thread. */
@Composable
private fun WidgetProviderRow(
    item: WidgetProviderItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Theme panel corner radius keeping picker rows visually aligned with cover launcher cards.
    val cornerRadius = LocalCoverPanelCornerRadius.current
    // Preview/app icon rasterized off the UI thread at 72x72px; null shows a fallback widget glyph.
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, item.info) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val drawable = item.info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                drawable.toBitmap(width = 72, height = 72).asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = item.appLabel,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.widgetLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.appLabel} · ${item.cellEstimate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ${item.widgetLabel}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
