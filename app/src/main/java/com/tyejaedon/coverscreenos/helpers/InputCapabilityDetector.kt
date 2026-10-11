package com.tyejaedon.coverscreenos.helpers

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import com.tyejaedon.coverscreenos.ime.CoverT9InputMethodService

private const val GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin"

data class InputMethodCapabilityEntry(
    val id: String,
    val label: String,
    val packageName: String
)

data class InputCapabilitySnapshot(
    val enabledInputMethods: List<InputMethodCapabilityEntry>,
    val defaultInputMethod: InputMethodCapabilityEntry?,
    val isImePickerLikelyAvailable: Boolean,
    val isCoverT9Enabled: Boolean,
    val isCoverT9Default: Boolean,
    val isGboardEnabled: Boolean,
    val isGboardDefault: Boolean,
    val oemRestrictionHint: String?,
    val availabilityEstimatePercent: Int,
    val availabilityEstimateTier: InputAvailabilityTier
)

enum class InputAvailabilityTier {
    HIGH,
    MODERATE,
    LOW
}

class InputCapabilityDetector(context: Context) {

    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val packageManager = appContext.packageManager
    private val inputMethodManager = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private val coverT9ImeIds: Set<String> = run {
        val componentName = ComponentName(appContext, CoverT9InputMethodService::class.java)
        setOf(componentName.flattenToString(), componentName.flattenToShortString())
            .map(::normalizeImeId)
            .toSet()
    }

    fun detect(): InputCapabilitySnapshot {
        val allInputMethods = inputMethodManager?.inputMethodList.orEmpty()
        val inputMethodById = allInputMethods.associateBy { normalizeImeId(it.id) }

        val enabledIds = readEnabledInputMethodIds()
        val enabledEntries = enabledIds.map { imeId ->
            toInputMethodEntry(
                imeId = imeId,
                inputMethodInfo = inputMethodById[normalizeImeId(imeId)]
            )
        }

        val defaultImeId = readDefaultInputMethodId()
        val defaultImeEntry = defaultImeId?.let { imeId ->
            toInputMethodEntry(
                imeId = imeId,
                inputMethodInfo = inputMethodById[normalizeImeId(imeId)]
            )
        }

        val normalizedEnabledIds = enabledIds.map(::normalizeImeId)
        val normalizedDefaultId = defaultImeId?.let(::normalizeImeId)
        val isCoverT9Enabled = normalizedEnabledIds.any { it in coverT9ImeIds }
        val isCoverT9Default = normalizedDefaultId != null && normalizedDefaultId in coverT9ImeIds
        val isGboardEnabled = enabledEntries.any { it.packageName == GBOARD_PACKAGE_NAME }
        val isGboardDefault = defaultImeEntry?.packageName == GBOARD_PACKAGE_NAME

        val isPickerAvailable = inputMethodManager != null && allInputMethods.isNotEmpty()
        val hasSamsungOemRisk = isSamsungDevice()
        val hasAlternativeImeEnabled = enabledEntries.any { entry ->
            entry.packageName != appContext.packageName
        }
        val oemRestrictionHint = if (isSamsungDevice()) {
            "Samsung One UI may block some third-party keyboards on cover screen. If picker opens but keyboard does not appear, this is likely an OEM restriction."
        } else {
            null
        }

        val availabilityPercent = estimateInputAvailabilityPercent(
            enabledInputMethodCount = enabledEntries.size,
            hasDefaultInputMethod = defaultImeEntry != null,
            isImePickerLikelyAvailable = isPickerAvailable,
            isCoverT9Enabled = isCoverT9Enabled,
            isCoverT9Default = isCoverT9Default,
            hasAlternativeImeEnabled = hasAlternativeImeEnabled,
            hasSamsungOemRisk = hasSamsungOemRisk
        )

        return InputCapabilitySnapshot(
            enabledInputMethods = enabledEntries,
            defaultInputMethod = defaultImeEntry,
            isImePickerLikelyAvailable = isPickerAvailable,
            isCoverT9Enabled = isCoverT9Enabled,
            isCoverT9Default = isCoverT9Default,
            isGboardEnabled = isGboardEnabled,
            isGboardDefault = isGboardDefault,
            oemRestrictionHint = oemRestrictionHint,
            availabilityEstimatePercent = availabilityPercent,
            availabilityEstimateTier = availabilityTierForPercent(availabilityPercent)
        )
    }

    private fun toInputMethodEntry(
        imeId: String,
        inputMethodInfo: InputMethodInfo?
    ): InputMethodCapabilityEntry {
        val packageName = inputMethodInfo?.packageName ?: extractImePackageName(imeId)
        val resolvedLabel = inputMethodInfo
            ?.loadLabel(packageManager)
            ?.toString()
            ?.trim()
            .orEmpty()
            .takeUnless { it.isEmpty() }
            ?: packageName

        return InputMethodCapabilityEntry(
            id = imeId,
            label = resolvedLabel,
            packageName = packageName
        )
    }

    // Uses the public API first. Reading Settings.Secure for enabled keyboards is blocked
    // on apps targeting SDK 34+ and throws SecurityException, so it is only a fallback
    // for older target SDKs.
    private fun readEnabledInputMethodIds(): List<String> {
        val fromManager = runCatching { inputMethodManager?.enabledInputMethodList.orEmpty() }
            .getOrDefault(emptyList())
            .map { it.id }
            .distinct()
        if (fromManager.isNotEmpty()) return fromManager
        if (!canReadEnabledInputMethodsSetting(appContext.applicationInfo.targetSdkVersion)) {
            return emptyList()
        }

        return runCatching {
            parseEnabledInputMethodIds(
                Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            )
        }.getOrDefault(emptyList())
    }

    // Default keyboard is read on a best-effort basis. Some Android versions block this
    // setting too, so a failed read returns null instead of crashing the screen.
    private fun readDefaultInputMethodId(): String? {
        return runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()
            ?.trim()
            ?.takeUnless { it.isEmpty() }
    }

    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
}

internal fun estimateInputAvailabilityPercent(
    enabledInputMethodCount: Int,
    hasDefaultInputMethod: Boolean,
    isImePickerLikelyAvailable: Boolean,
    isCoverT9Enabled: Boolean,
    isCoverT9Default: Boolean,
    hasAlternativeImeEnabled: Boolean,
    hasSamsungOemRisk: Boolean
): Int {
    if (enabledInputMethodCount <= 0) return 0

    var score = 35
    if (hasDefaultInputMethod) score += 10
    if (isImePickerLikelyAvailable) score += 20
    if (isCoverT9Enabled) score += 20
    if (isCoverT9Default) score += 5
    if (hasAlternativeImeEnabled) score += 10
    if (hasSamsungOemRisk) score -= 15
    return score.coerceIn(0, 100)
}

internal fun availabilityTierForPercent(percent: Int): InputAvailabilityTier {
    return when {
        percent >= 85 -> InputAvailabilityTier.HIGH
        percent >= 65 -> InputAvailabilityTier.MODERATE
        else -> InputAvailabilityTier.LOW
    }
}

// Android only lets apps targeting SDK 33 or lower read Settings.Secure.ENABLED_INPUT_METHODS.
internal fun canReadEnabledInputMethodsSetting(targetSdkVersion: Int): Boolean {
    return targetSdkVersion <= Build.VERSION_CODES.TIRAMISU
}

internal fun parseEnabledInputMethodIds(rawValue: String?): List<String> {
    if (rawValue.isNullOrBlank()) return emptyList()

    return rawValue
        .split(':')
        .map { section -> section.substringBefore(';').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

internal fun extractImePackageName(imeId: String): String {
    return imeId.substringBefore('/').ifBlank { imeId }
}

private fun normalizeImeId(imeId: String): String {
    return ComponentName.unflattenFromString(imeId)?.flattenToString() ?: imeId
}

