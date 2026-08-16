package com.tyejaedon.coverscreenos.ui.settings

import android.widget.ImageView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.tyejaedon.coverscreenos.datastore.COVER_DOCK_SLOT_COUNT
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding

@Composable
internal fun DockCustomizationCard(
    dockPackages: List<String?>,
    resolveLabel: (String?) -> String,
    onPreviewReorder: (List<String?>) -> Unit,
    onReorderCommitted: (List<String?>) -> Unit,
    onPickSlot: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(CoverOSCornerRadiusMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dock", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pin only the apps you open most. Drag to reorder, choose per slot, or clear what you do not need.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DockReorderStrip(
                dockPackages = dockPackages,
                resolveLabel = resolveLabel,
                onPreviewReorder = onPreviewReorder,
                onReorderCommitted = onReorderCommitted
            )

            dockPackages.forEachIndexed { index, packageName ->
                DockSlotEditorRow(
                    slotIndex = index,
                    selectedAppName = packageName?.let(resolveLabel) ?: "Empty",
                    canClear = packageName != null,
                    onChoose = { onPickSlot(index) },
                    onClear = { onClearSlot(index) }
                )
            }
        }
    }
}

@Composable
private fun DockSlotEditorRow(
    slotIndex: Int,
    selectedAppName: String,
    canClear: Boolean,
    onChoose: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Slot ${slotIndex + 1}", style = MaterialTheme.typography.labelLarge)
            Text(
                text = selectedAppName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(onClick = onChoose, modifier = Modifier.coverMinimumTouchTarget()) {
            Text("Choose")
        }
        OutlinedButton(
            onClick = onClear,
            enabled = canClear,
            modifier = Modifier.coverMinimumTouchTarget()
        ) {
            Text("Clear")
        }
    }
}

@Composable
private fun DockReorderStrip(
    dockPackages: List<String?>,
    resolveLabel: (String?) -> String,
    onPreviewReorder: (List<String?>) -> Unit,
    onReorderCommitted: (List<String?>) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestDockPackages by rememberUpdatedState(newValue = dockPackages)

    var draggingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var slotWidthPx by remember { mutableFloatStateOf(1f) }
    var workingDockPackages by remember { mutableStateOf(normalizeDockPackageSlots(dockPackages)) }
    var dragStartDockSnapshot by remember { mutableStateOf<List<String?>?>(null) }
    var lastReorderedDockSnapshot by remember { mutableStateOf<List<String?>?>(null) }

    LaunchedEffect(dockPackages, draggingSlotIndex) {
        if (draggingSlotIndex == null) {
            workingDockPackages = normalizeDockPackageSlots(dockPackages)
        }
    }

    fun commitReorderIfChanged() {
        val startSnapshot = dragStartDockSnapshot
        val reorderedSnapshot = lastReorderedDockSnapshot
        if (startSnapshot != null && reorderedSnapshot != null && reorderedSnapshot != startSnapshot) {
            onReorderCommitted(reorderedSnapshot)
        }
    }

    val neighborSnapDistance = 10.dp
    val dragProgress = if (slotWidthPx > 0f) {
        (dragDeltaX / slotWidthPx).coerceIn(-1f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(COVER_DOCK_SLOT_COUNT) { index ->
            val packageName = dockPackages.getOrNull(index)
            val label = resolveLabel(packageName)
            val isDragging = draggingSlotIndex == index
            val activeDragIndex = draggingSlotIndex
            val neighborSnapOffsetTarget = when {
                activeDragIndex == null || isDragging -> 0.dp
                dragProgress > 0f && index == activeDragIndex + 1 -> -(neighborSnapDistance * dragProgress)
                dragProgress < 0f && index == activeDragIndex - 1 -> neighborSnapDistance * -dragProgress
                else -> 0.dp
            }
            val neighborSnapOffset by animateDpAsState(
                targetValue = neighborSnapOffsetTarget,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dockSlotNeighborSnap$index"
            )
            val slotScale by animateFloatAsState(
                targetValue = if (isDragging) 1.06f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dockSlotScale$index"
            )
            val slotLiftOffset by animateDpAsState(
                targetValue = if (isDragging) (-6).dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dockSlotLift$index"
            )
            val slotShadow by animateDpAsState(
                targetValue = if (isDragging) 12.dp else 2.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "dockSlotShadow$index"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .shadow(
                        elevation = slotShadow,
                        shape = RoundedCornerShape(12.dp),
                        clip = false
                    )
                    .graphicsLayer {
                        scaleX = slotScale
                        scaleY = slotScale
                        translationX = with(density) { neighborSnapOffset.toPx() }
                        translationY = with(density) { slotLiftOffset.toPx() }
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .coverMinimumTouchTarget()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDragging) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .onSizeChanged { size ->
                        if (size.width > 0) {
                            slotWidthPx = size.width.toFloat()
                        }
                    }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingSlotIndex = index
                                dragDeltaX = 0f
                                dragStartDockSnapshot = latestDockPackages
                                workingDockPackages = normalizeDockPackageSlots(latestDockPackages)
                                lastReorderedDockSnapshot = null
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val activeIndex = draggingSlotIndex ?: return@detectDragGesturesAfterLongPress
                                if (slotWidthPx <= 0f) return@detectDragGesturesAfterLongPress

                                dragDeltaX += dragAmount.x
                                val slotShift = (dragDeltaX / slotWidthPx).toInt()
                                if (slotShift == 0) return@detectDragGesturesAfterLongPress

                                val targetIndex = (activeIndex + slotShift)
                                    .coerceIn(0, COVER_DOCK_SLOT_COUNT - 1)
                                if (targetIndex != activeIndex) {
                                    val reorderedDock = moveDockSlot(
                                        dockPackages = workingDockPackages,
                                        fromIndex = activeIndex,
                                        toIndex = targetIndex
                                    )
                                    workingDockPackages = reorderedDock
                                    lastReorderedDockSnapshot = reorderedDock
                                    onPreviewReorder(reorderedDock)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    draggingSlotIndex = targetIndex
                                    val consumedShift = (targetIndex - activeIndex) * slotWidthPx
                                    dragDeltaX -= consumedShift
                                }
                            },
                            onDragEnd = {
                                commitReorderIfChanged()
                                draggingSlotIndex = null
                                dragDeltaX = 0f
                                dragStartDockSnapshot = null
                                lastReorderedDockSnapshot = null
                            },
                            onDragCancel = {
                                commitReorderIfChanged()
                                draggingSlotIndex = null
                                dragDeltaX = 0f
                                dragStartDockSnapshot = null
                                lastReorderedDockSnapshot = null
                            }
                        )
                    }
                    .coverScreenPadding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun DockAppPickerDialog(
    apps: List<AppModel>,
    onDismiss: () -> Unit,
    onAppSelected: (AppModel) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(query, apps) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.name.contains(normalizedQuery, ignoreCase = true) ||
                    app.packageName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose dock app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Text(
                                text = "No apps match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredApps, key = { it.packageName }) { app ->
                            TextButton(
                                onClick = { onAppSelected(app) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .coverMinimumTouchTarget()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AndroidView(
                                        factory = { viewContext ->
                                            ImageView(viewContext).apply {
                                                scaleType = ImageView.ScaleType.FIT_CENTER
                                            }
                                        },
                                        update = { imageView ->
                                            if (imageView.tag !== app.iconDrawable) {
                                                imageView.setImageDrawable(app.iconDrawable)
                                                imageView.tag = app.iconDrawable
                                            }
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Text(
                                        text = app.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

internal fun normalizeDockPackageSlots(dockPackages: List<String?>): List<String?> {
    val normalized = MutableList<String?>(COVER_DOCK_SLOT_COUNT) { null }
    val seenPackages = linkedSetOf<String>()

    repeat(COVER_DOCK_SLOT_COUNT) { index ->
        val packageName = dockPackages.getOrNull(index)?.trim().takeUnless { it.isNullOrEmpty() }
        if (packageName != null && seenPackages.add(packageName)) {
            normalized[index] = packageName
        }
    }

    return normalized
}

internal fun moveDockSlot(
    dockPackages: List<String?>,
    fromIndex: Int,
    toIndex: Int
): List<String?> {
    if (fromIndex == toIndex) return normalizeDockPackageSlots(dockPackages)
    if (fromIndex !in 0 until COVER_DOCK_SLOT_COUNT || toIndex !in 0 until COVER_DOCK_SLOT_COUNT) {
        return normalizeDockPackageSlots(dockPackages)
    }

    val reordered = normalizeDockPackageSlots(dockPackages).toMutableList()
    val movedPackage = reordered.removeAt(fromIndex)
    reordered.add(toIndex, movedPackage)
    return normalizeDockPackageSlots(reordered)
}

internal fun updateDockSlotSelection(
    dockPackages: List<String?>,
    slotIndex: Int,
    packageName: String?
): List<String?> {
    if (slotIndex !in 0 until COVER_DOCK_SLOT_COUNT) {
        return normalizeDockPackageSlots(dockPackages)
    }

    val updated = normalizeDockPackageSlots(dockPackages).toMutableList()
    val normalizedPackage = packageName?.trim().takeUnless { it.isNullOrEmpty() }
    if (normalizedPackage != null) {
        repeat(updated.size) { index ->
            if (index != slotIndex && updated[index] == normalizedPackage) {
                updated[index] = null
            }
        }
    }

    updated[slotIndex] = normalizedPackage
    return normalizeDockPackageSlots(updated)
}

