package com.tyejaedon.coverscreenos.ime

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36])
class ImeUiCoverageTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val permissionContext = ContactsPermissionContext(context)

    @After
    fun clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(ContactsSuggestionConsent.PREF_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `text defaults to QWERTY while number phone and datetime default to numeric`() {
        assertEquals(CoverImeLayout.QWERTY, CoverImePreferences.defaultLayout(context, null))
        assertEquals(CoverImeLayout.QWERTY, layoutFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(CoverImeLayout.NUMBER, layoutFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(CoverImeLayout.NUMBER, layoutFor(InputType.TYPE_CLASS_PHONE))
        assertEquals(CoverImeLayout.NUMBER, layoutFor(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun `settings select independent layouts for every editor field class`() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(CoverImeFieldClass.TEXT.preferenceKey, CoverImeLayout.T9.name)
            .putString(CoverImeFieldClass.NUMBER.preferenceKey, CoverImeLayout.QWERTY.name)
            .putString(CoverImeFieldClass.PHONE.preferenceKey, CoverImeLayout.SYMBOLS.name)
            .putString(CoverImeFieldClass.DATETIME.preferenceKey, CoverImeLayout.T9.name)
            .commit()

        assertEquals(CoverImeLayout.T9, layoutFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(CoverImeLayout.QWERTY, layoutFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(CoverImeLayout.SYMBOLS, layoutFor(InputType.TYPE_CLASS_PHONE))
        assertEquals(CoverImeLayout.T9, layoutFor(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun `editor variations use their field class default and invalid saved layout falls back`() {
        assertEquals(CoverImeLayout.NUMBER, layoutFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL))
        assertEquals(CoverImeLayout.QWERTY, layoutFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(CoverImeFieldClass.TEXT.preferenceKey, "not-a-layout")
            .commit()
        assertEquals(CoverImeLayout.QWERTY, layoutFor(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun `action key reflects editor action except when newline is required`() {
        assertEquals("SEARCH", imeActionLabel(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }))
        assertEquals("NEXT", imeActionLabel(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_NEXT }))
        assertEquals("ENTER", imeActionLabel(EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }))
    }

    @Test
    fun `contact suggestions require explicit permission and are optional`() {
        val preferences = context.getSharedPreferences(ContactsSuggestionConsent.PREF_FILE, Context.MODE_PRIVATE)
        assertFalse(ContactsSuggestionConsent.isEnabled(permissionContext))
        assertFalse(ContactsSuggestionConsent.setEnabled(permissionContext, true))
        assertFalse(preferences.contains(ContactsSuggestionConsent.KEY))

        permissionContext.granted = true
        assertTrue(ContactsSuggestionConsent.setEnabled(permissionContext, true))
        assertTrue(preferences.getBoolean(ContactsSuggestionConsent.KEY, false))

        ContactsSuggestionConsent.setEnabled(permissionContext, false)
        assertFalse(preferences.contains(ContactsSuggestionConsent.KEY))
    }

    @Test
    fun `revoked contacts permission clears previous opt-in`() {
        val preferences = context.getSharedPreferences(ContactsSuggestionConsent.PREF_FILE, Context.MODE_PRIVATE)
        permissionContext.granted = true
        ContactsSuggestionConsent.setEnabled(permissionContext, true)
        permissionContext.granted = false

        assertFalse(ContactsSuggestionConsent.isEnabled(permissionContext))
        assertFalse(preferences.contains(ContactsSuggestionConsent.KEY))
    }

    private fun layoutFor(inputType: Int): CoverImeLayout =
        CoverImePreferences.defaultLayout(context, EditorInfo().apply { this.inputType = inputType })

    private class ContactsPermissionContext(base: Context) : ContextWrapper(base) {
        var granted: Boolean = false

        override fun checkSelfPermission(permission: String): Int =
            if (permission == Manifest.permission.READ_CONTACTS) {
                if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            } else {
                super.checkSelfPermission(permission)
            }
    }
}
