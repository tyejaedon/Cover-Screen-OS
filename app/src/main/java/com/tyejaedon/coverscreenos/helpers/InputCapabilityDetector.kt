package com.tyejaedon.coverscreenos.helpers

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import com.tyejaedon.coverscreenos.ime.CoverT9InputMethodService

// Package name for Google's Gboard IME, tracked explicitly as a common alternative keyboard.
private const val GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin"

/**
 * Metadata describing an installed or enabled Input Method Editor (IME).
 *
 * @property id Flattened IME component identifier (for example `pkg/.Service`).
 * @property label Human-readable service label resolved via [android.content.pm.PackageManager].
 * @property packageName Hosting application package name.
 */
data class InputMethodCapabilityEntry(
    val id: String,
    val label: String,
    val packageName: String
)

/**
 * Diagnostic summary of keyboard / IME availability on the device and likelihood of reliable
 * text input on the secondary cover screen.
 */
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

/**
 * Coarse confidence tier derived from [InputCapabilitySnapshot.availabilityEstimatePercent].
 */
enum class InputAvailabilityTier {
    HIGH,
    MODERATE,
    LOW
}

/**
 * Probes [InputMethodManager] and [Settings.Secure] to build an [InputCapabilitySnapshot] for
 * onboarding diagnostics and cover-screen keyboard fallback guidance.
 *
 * Accounts for Android 14+ (API 34+) restrictions on reading `Settings.Secure.ENABLED_INPUT_METHODS`
 * as well as Samsung One UI cover-display policies that can block third-party IMEs.
 */
class InputCapabilityDetector(context: Context) {

    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val packageManager = appContext.packageManager
    private val inputMethodManager = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    // Accept both full and shortened ComponentName formats when matching CoverT9 IME IDs.
    private val coverT9ImeIds: Set<String> = run {
        val componentName = ComponentName(appContext, CoverT9InputMethodService::class.java)
        setOf(componentName.flattenToString(), componentName.flattenToShortString())
            .map(::normalizeImeId)
            .toSet()
    }

    /**
     * Collects current IME configuration from [InputMethodManager] and [Settings.Secure],
     * evaluates CoverT9/Gboard enablement and OEM risks, and computes an availability estimate.
     */
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

    /** Converts a raw IME ID and optional [InputMethodInfo] into an [InputMethodCapabilityEntry]. */
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

/**
 * Computes a heuristic 0..100 score representing how likely cover-screen keyboard input will work,
 * weighting CoverT9 enablement, alternative IME availability, and Samsung OEM restriction risk.
 */
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

/** Maps a percentage from [estimateInputAvailabilityPercent] to an [InputAvailabilityTier]. */
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

/**
 * Parses `Settings.Secure.ENABLED_INPUT_METHODS` entries formatted as
 * `componentId;subtype1;subtype2:otherComponentId;...`, stripping subtype suffixes.
 */
internal fun parseEnabledInputMethodIds(rawValue: String?): List<String> {
    if (rawValue.isNullOrBlank()) return emptyList()

    return rawValue
        .split(':')
        .map { section -> section.substringBefore(';').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

/** Extracts the package prefix from a flattened IME ID (`package/class`). */
internal fun extractImePackageName(imeId: String): String {
    return imeId.substringBefore('/').ifBlank { imeId }
}

/** Normalizes shortened (`pkg/.Service`) and full (`pkg/pkg.Service`) IME component IDs for comparison. */
private fun normalizeImeId(imeId: String): String {
    return ComponentName.unflattenFromString(imeId)?.flattenToString() ?: imeId
}

