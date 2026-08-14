package com.tyejaedon.coverscreenos.services

import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Display
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.tyejaedon.coverscreenos.R
import androidx.core.graphics.toColorInt

class OverlayWindowController(private val context: Context) {

    private var overlayView: View? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayWindowContext: Context? = null
    private var activeDisplayId: Int? = null

    fun showOverlay(targetDisplay: Display? = null, forceReattach: Boolean = false): Boolean {
        val desiredDisplayId = targetDisplay?.displayId ?: Display.DEFAULT_DISPLAY
        if (overlayView != null && !forceReattach && activeDisplayId == desiredDisplayId) {
            return true
        }
        if (overlayView != null) {
            removeOverlay()
        }

        val windowContext = createWindowContext(targetDisplay)
        val windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(windowContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 22, 36, 22)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor("#CC1E1F28".toColorInt())
                setStroke(2, "#66818CF8".toColorInt())
            }
        }

        val title = TextView(windowContext).apply {
            text = windowContext.getString(R.string.overlay_window_title)
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(windowContext).apply {
            text = windowContext.getString(R.string.overlay_window_subtitle)
            setTextColor("#C9D0FF".toColorInt())
            textSize = 13f
        }

        container.addView(title)
        container.addView(subtitle)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        val added = runCatching {
            windowManager.addView(container, layoutParams)
            overlayView = container
            overlayWindowManager = windowManager
            overlayWindowContext = windowContext
            activeDisplayId = runCatching { windowContext.display.displayId }.getOrNull()
                ?: targetDisplay?.displayId
                ?: Display.DEFAULT_DISPLAY
            true
        }.getOrDefault(false)

        if (!added) {
            overlayView = null
            overlayWindowManager = null
            overlayWindowContext = null
            activeDisplayId = null
        }

        return added
    }

    fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { overlayWindowManager?.removeView(view) }
        overlayView = null
        overlayWindowManager = null
        overlayWindowContext = null
        activeDisplayId = null
    }

    fun getActiveDisplayId(): Int? {
        return activeDisplayId
    }

    fun isOverlayAttached(): Boolean {
        return overlayView != null
    }

    private fun createWindowContext(targetDisplay: Display?): Context {
        val displayContext = if (targetDisplay != null) {
            context.createDisplayContext(targetDisplay)
        } else {
            createDefaultDisplayContext()
        }

        return runCatching {
            displayContext.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                Bundle()
            )
        }.getOrElse {
            // Some OEM builds reject createWindowContext from service-derived contexts.
            Log.w("OverlayWindowController", "Falling back to display context: ${it.message}")
            displayContext
        }
    }

    private fun createDefaultDisplayContext(): Context {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return if (defaultDisplay != null) context.createDisplayContext(defaultDisplay) else context
    }
}

