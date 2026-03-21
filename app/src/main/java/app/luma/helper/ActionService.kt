package app.luma.helper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference

class ActionService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var toolLaunchMaskView: View? = null
    private var shownAtUptimeMs: Long = 0L
    private var sawLightOsForegroundEvent = false
    private var hardTimeoutRunnable: Runnable? = null
    private var deferredHideRunnable: Runnable? = null

    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelToolLaunchMaskOnMain()
        instance = WeakReference(null)
        return super.onUnbind(intent)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    @RequiresApi(Build.VERSION_CODES.P)
    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun showToolLaunchMask(isDark: Boolean) {
        runOnMainThread {
            showToolLaunchMaskOnMain(isDark)
        }
    }

    fun cancelToolLaunchMask() {
        runOnMainThread {
            cancelToolLaunchMaskOnMain()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val event = event ?: return
        if (event.packageName?.toString() != LIGHT_OS_PACKAGE) return
        val eventType = event.eventType

        runOnMainThread {
            if (toolLaunchMaskView == null) return@runOnMainThread

            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                -> handleForegroundEvent()

                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentEvent()
            }
        }
    }

    override fun onInterrupt() {
        cancelToolLaunchMaskOnMain()
    }

    private fun showToolLaunchMaskOnMain(isDark: Boolean) {
        cancelPendingCallbacks()
        shownAtUptimeMs = SystemClock.uptimeMillis()
        sawLightOsForegroundEvent = false

        val view =
            toolLaunchMaskView ?: View(this).also {
                toolLaunchMaskView = it
            }
        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)

        if (!view.isAttachedToWindow) {
            try {
                windowManager.addView(view, createMaskLayoutParams())
            } catch (_: Exception) {
                toolLaunchMaskView = null
                shownAtUptimeMs = 0L
                return
            }
        }

        hardTimeoutRunnable =
            Runnable {
                cancelToolLaunchMaskOnMain()
            }.also { runnable ->
                mainHandler.postDelayed(runnable, HARD_TIMEOUT_MS)
            }
    }

    private fun cancelToolLaunchMaskOnMain() {
        cancelPendingCallbacks()
        shownAtUptimeMs = 0L
        sawLightOsForegroundEvent = false

        val view = toolLaunchMaskView ?: return
        if (!view.isAttachedToWindow) {
            toolLaunchMaskView = null
            return
        }

        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        } finally {
            toolLaunchMaskView = null
        }
    }

    private fun handleForegroundEvent() {
        if (!sawLightOsForegroundEvent) {
            // The first LightOS event is usually just the existing singleTask activity resurfacing.
            sawLightOsForegroundEvent = true
            return
        }

        hideMaskWhenReady()
    }

    private fun handleContentEvent() {
        if (!sawLightOsForegroundEvent) return
        hideMaskWhenReady()
    }

    private fun hideMaskWhenReady() {
        if (toolLaunchMaskView == null) return

        val remainingDelay = (MIN_MASK_VISIBILITY_MS - (SystemClock.uptimeMillis() - shownAtUptimeMs)).coerceAtLeast(0L)
        if (remainingDelay == 0L) {
            cancelToolLaunchMaskOnMain()
            return
        }

        deferredHideRunnable?.let(mainHandler::removeCallbacks)
        deferredHideRunnable =
            Runnable {
                cancelToolLaunchMaskOnMain()
            }.also { runnable ->
                mainHandler.postDelayed(runnable, remainingDelay)
            }
    }

    private fun cancelPendingCallbacks() {
        hardTimeoutRunnable?.let(mainHandler::removeCallbacks)
        deferredHideRunnable?.let(mainHandler::removeCallbacks)
        hardTimeoutRunnable = null
        deferredHideRunnable = null
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    private fun createMaskLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

    companion object {
        private const val LIGHT_OS_PACKAGE = "com.lightos"
        private const val MIN_MASK_VISIBILITY_MS = 200L
        private const val HARD_TIMEOUT_MS = 900L

        private var instance: WeakReference<ActionService> = WeakReference(null)

        fun instance(): ActionService? = instance.get()
    }
}
