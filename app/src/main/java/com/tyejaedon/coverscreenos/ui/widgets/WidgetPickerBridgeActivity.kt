package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridge activity that handles picking, binding (`ACTION_APPWIDGET_BIND`), and
 * configuring (`AppWidgetProviderInfo.configure`) widgets for the Cover Screen.
 */
class WidgetPickerBridgeActivity : ComponentActivity() {

    private data class WidgetProviderItem(
        val info: AppWidgetProviderInfo,
        val label: String,
        val appName: String,
        val icon: Drawable?,
        val sizeSummary: String
    )

    private lateinit var widgetHostManager: CoverAppWidgetHostManager
    private lateinit var launcherSettingsStore: LauncherSettingsStore

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProviderInfo: AppWidgetProviderInfo? = null
    private var isCommitted: Boolean = false

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingWidgetId
        val info = pendingProviderInfo
        if (result.resultCode == RESULT_OK &&
            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            info != null
        ) {
            proceedToConfigureOrCommit(widgetId, info)
        } else {
            cleanupPendingWidget()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHostManager = CoverAppWidgetHostManager(applicationContext)
        launcherSettingsStore = LauncherSettingsStore(applicationContext)

        val reconfigureWidgetId = intent.getIntExtra(
            EXTRA_RECONFIGURE_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (reconfigureWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val launched = widgetHostManager.launchConfigureActivity(
                activity = this,
                appWidgetId = reconfigureWidgetId,
                requestCode = REQUEST_CODE_RECONFIGURE
            )
            if (!launched) {
                finish()
            }
            return
        }

        setContent {
            CoverOSTheme {
                WidgetPickerScreen(
                    loadProviders = { loadProviderItems() },
                    onProviderSelected = { item -> handleProviderSelected(item.info) },
                    onClose = {
                        cleanupPendingWidget()
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Required for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_CONFIGURE -> {
                val widgetId = pendingWidgetId
                if (resultCode == RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    commitWidget(widgetId)
                } else {
                    cleanupPendingWidget()
                    finish()
                }
            }
            REQUEST_CODE_RECONFIGURE -> {
                finish()
            }
        }
    }

    override fun onDestroy() {
        if (!isFinishing && !isChangingConfigurations) {
            // Keep pending state across config changes.
        } else if (isFinishing && !isCommitted) {
            cleanupPendingWidget()
        }
        super.onDestroy()
    }

    private fun handleProviderSelected(info: AppWidgetProviderInfo) {
        cleanupPendingWidget()
        val allocatedId = widgetHostManager.allocateAppWidgetId()
        if (allocatedId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.w(LOG_TAG, "Unable to allocate appWidgetId")
            finish()
            return
        }

        pendingWidgetId = allocatedId
        pendingProviderInfo = info

        val bound = widgetHostManager.bindAppWidgetIdIfAllowed(
            appWidgetId = allocatedId,
            provider = info.provider,
            profile = info.profile
        )
        if (bound) {
            proceedToConfigureOrCommit(allocatedId, info)
        } else {
            val bindIntent = widgetHostManager.createBindIntent(
                appWidgetId = allocatedId,
                provider = info.provider,
                profile = info.profile
            )
            bindWidgetLauncher.launch(bindIntent)
        }
    }

    private fun proceedToConfigureOrCommit(widgetId: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            val launched = widgetHostManager.launchConfigureActivity(
                activity = this,
                appWidgetId = widgetId,
                requestCode = REQUEST_CODE_CONFIGURE
            )
            if (!launched) {
                commitWidget(widgetId)
            }
        } else {
            commitWidget(widgetId)
        }
    }

    private fun commitWidget(widgetId: Int) {
        isCommitted = true
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProviderInfo = null
        lifecycleScope.launch {
            runCatching {
                launcherSettingsStore.addWidgetId(widgetId)
            }.onFailure { error ->
                Log.w(LOG_TAG, "Failed to persist widgetId=$widgetId", error)
            }
            finish()
        }
    }

    private fun cleanupPendingWidget() {
        val widgetId = pendingWidgetId
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !isCommitted) {
            widgetHostManager.deleteAppWidgetId(widgetId)
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProviderInfo = null
    }

    private suspend fun loadProviderItems(): List<WidgetProviderItem> = withContext(Dispatchers.IO) {
        val pm = packageManager
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        widgetHostManager.getInstalledProviders().mapNotNull { info ->
            runCatching {
                val label = info.loadLabel(pm).toString().ifBlank { info.provider.shortClassName }
                val appInfo = pm.getApplicationInfo(info.provider.packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = runCatching { info.loadIcon(this@WidgetPickerBridgeActivity, resources.displayMetrics.densityDpi) }
                    .getOrNull() ?: runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                val heightDp = CoverAppWidgetHostManager.resolveWidgetHeightDp(info, density)
                val sizeSummary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    info.targetCellWidth > 0 && info.targetCellHeight > 0
                ) {
                    "${info.targetCellWidth}x${info.targetCellHeight} • ~${heightDp}dp"
                } else {
                    "~${heightDp}dp"
                }
                WidgetProviderItem(
                    info = info,
                    label = label,
                    appName = appName,
                    icon = icon,
                    sizeSummary = sizeSummary
                )
            }.getOrNull()
        }.sortedWith(compareBy({ it.appName.lowercase() }, { it.label.lowercase() }))
    }

    @Composable
    private fun WidgetPickerScreen(
        loadProviders: suspend () -> List<WidgetProviderItem>,
        onProviderSelected: (WidgetProviderItem) -> Unit,
        onClose: () -> Unit
    ) {
        var providers by remember { mutableStateOf<List<WidgetProviderItem>?>(null) }
        var query by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            providers = loadProviders()
        }

        val filtered = remember(providers, query) {
            val all = providers.orEmpty()
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                all
            } else {
                all.filter {
                    it.label.contains(trimmed, ignoreCase = true) ||
                        it.appName.contains(trimmed, ignoreCase = true)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1015))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Widget",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search widgets or apps",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                providers == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (query.isBlank()) "No widgets available" else "No matching widgets",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { "${it.info.provider.flattenToShortString()}_${it.info.profile}" }
                        ) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onProviderSelected(item) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    val bitmap = remember(item.icon) {
                                        runCatching { item.icon?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Widgets,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.label,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.appName} • ${item.sizeSummary}",
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val LOG_TAG = "WidgetPickerBridge"
        private const val REQUEST_CODE_CONFIGURE = 4001
        private const val REQUEST_CODE_RECONFIGURE = 4002
        const val EXTRA_RECONFIGURE_WIDGET_ID = "com.tyejaedon.coverscreenos.extra.RECONFIGURE_WIDGET_ID"

        fun createPickIntent(context: Context): Intent {
            return Intent(context, WidgetPickerBridgeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        fun createReconfigureIntent(context: Context, widgetId: Int): Intent {
            return Intent(context, WidgetPickerBridgeActivity::class.java).apply {
                putExtra(EXTRA_RECONFIGURE_WIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
