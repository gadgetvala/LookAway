package co.tekonika.lookaway.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import co.tekonika.lookaway.R
import co.tekonika.lookaway.service.BreakConfig

/**
 * Full-screen break overlay drawn over every app via SYSTEM_ALERT_WINDOW: the Android counterpart
 * of the macOS borderless window at `.screenSaver` level. A plain native View on purpose: it lives
 * inside the service, so no second Flutter engine has to be kept alive in the background.
 */
class BreakOverlay(private val context: Context, private val onSkip: () -> Unit) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null

    /** Returns false when the overlay can't be drawn (permission missing or revoked). */
    fun show(endsAt: Long, config: BreakConfig): Boolean {
        if (root != null) return true
        if (!Settings.canDrawOverlays(context)) return false

        // Swallows Back so the break can only end through Skip (Home still works, like Cmd+Tab on macOS).
        val view = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent) =
                event.keyCode == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(event)
        }
        LayoutInflater.from(context).inflate(R.layout.overlay_break, view, true)
        view.findViewById<TextView>(R.id.message).text =
            context.getString(R.string.overlay_message, config.breakMs / 1_000)
        view.findViewById<Button>(R.id.skip).setOnClickListener { onSkip() }

        try {
            windowManager.addView(view, layoutParams())
        } catch (e: RuntimeException) {
            return false // BadTokenException / SecurityException if permission vanished mid-flight.
        }
        root = view
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(FADE_MS).start()
        countdown(view.findViewById(R.id.countdown), endsAt)
        return true
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        root?.let { windowManager.removeViewImmediate(it) }
        root = null
    }

    private fun countdown(label: TextView, endsAt: Long) {
        val seconds = ((endsAt - SystemClock.elapsedRealtime() + 999) / 1_000).coerceAtLeast(0)
        label.text = "%d".format(seconds) // Locale-aware digits.
        if (seconds > 0) handler.postDelayed({ countdown(label, endsAt) }, 1_000)
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        title = "LookAway break"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private companion object {
        const val FADE_MS = 300L
    }
}
