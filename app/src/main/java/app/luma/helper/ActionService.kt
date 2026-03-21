package app.luma.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.luma.R
import app.luma.data.Prefs
import java.lang.ref.WeakReference

class ActionService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val keyguardManager by lazy { getSystemService(KEYGUARD_SERVICE) as KeyguardManager }
    private val prefs by lazy { Prefs.getInstance(this) }

    private var toolLaunchMaskView: View? = null
    private var shownAtUptimeMs: Long = 0L
    private var sawLightOsForegroundEvent = false
    private var hardTimeoutRunnable: Runnable? = null
    private var deferredHideRunnable: Runnable? = null
    private var unlockGateView: View? = null
    private var unlockGateShownAtUptimeMs: Long = 0L
    private var unlockGateDismissRunnable: Runnable? = null
    private var unlockGateReceiver: BroadcastReceiver? = null
    private var repeatedHomeGateEligible = false
    private var ignoreNextHomeUpForUnlockGate = false
    private var wakeUnlockGateArmed = false

    override fun onServiceConnected() {
        configureServiceInfo()
        registerUnlockGateReceiver()
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        unregisterUnlockGateReceiver()
        instance = WeakReference(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        unregisterUnlockGateReceiver()
        instance = WeakReference(null)
        super.onDestroy()
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

    fun setRepeatedHomeGateEligible(isEligible: Boolean) {
        runOnMainThread {
            repeatedHomeGateEligible = isEligible
        }
    }

    fun handleLauncherIntent() {
        runOnMainThread {
            hideUnlockGateWhenReady(UNLOCK_GATE_HIDE_DELAY_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val event = event ?: return
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        runOnMainThread {
            if (toolLaunchMaskView != null && packageName == LIGHT_OS_PACKAGE) {
                when (eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                    -> handleForegroundEvent()

                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentEvent()
                }
            }
        }
    }

    override fun onInterrupt() {
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        repeatedHomeGateEligible = false
        ignoreNextHomeUpForUnlockGate = false
        wakeUnlockGateArmed = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        if (unlockGateView != null) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (ignoreNextHomeUpForUnlockGate) {
                    ignoreNextHomeUpForUnlockGate = false
                    return true
                }
                runOnMainThread {
                    hideUnlockGateWhenReady(UNLOCK_GATE_HIDE_DELAY_MS)
                }
            }
            return false
        }

        if (!repeatedHomeGateEligible) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount != 0) return true

        runOnMainThread {
            ignoreNextHomeUpForUnlockGate = true
            showUnlockGateOnMain()
        }
        return true
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
                windowManager.addView(view, createOverlayLayoutParams(TOOL_LAUNCH_MASK_WINDOW_TITLE))
            } catch (exception: Exception) {
                Log.e(TAG, "showToolLaunchMaskOnMain: addView failed", exception)
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
        } catch (exception: Exception) {
            Log.e(TAG, "cancelToolLaunchMaskOnMain: removeView failed", exception)
        } finally {
            toolLaunchMaskView = null
        }
    }

    private fun showUnlockGateOnMain() {
        cancelUnlockGateCallbacks()
        unlockGateShownAtUptimeMs = SystemClock.uptimeMillis()

        val isDark = prefs.isDarkTheme()
        val view =
            unlockGateView ?: LayoutInflater.from(this).inflate(R.layout.unlock_gate_overlay, null).also {
                unlockGateView = it
            }

        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        view.findViewById<TextView>(R.id.unlockGateMessage).setTextColor(if (isDark) Color.WHITE else Color.BLACK)

        if (!view.isAttachedToWindow) {
            try {
                windowManager.addView(view, createOverlayLayoutParams(UNLOCK_GATE_WINDOW_TITLE))
            } catch (exception: Exception) {
                Log.e(TAG, "showUnlockGateOnMain: addView failed", exception)
                unlockGateView = null
                unlockGateShownAtUptimeMs = 0L
                return
            }
        }
    }

    private fun hideUnlockGateWhenReady(minDelayMs: Long = 0L) {
        if (unlockGateView == null) return

        val remainingDelay =
            maxOf(
                minDelayMs,
                (UNLOCK_GATE_MIN_VISIBILITY_MS - (SystemClock.uptimeMillis() - unlockGateShownAtUptimeMs)).coerceAtLeast(0L),
            )

        if (remainingDelay == 0L) {
            cancelUnlockGateOnMain()
            return
        }

        unlockGateDismissRunnable?.let(mainHandler::removeCallbacks)
        unlockGateDismissRunnable =
            Runnable {
                cancelUnlockGateOnMain()
            }.also { runnable ->
                mainHandler.postDelayed(runnable, remainingDelay)
            }
    }

    private fun cancelUnlockGateOnMain() {
        cancelUnlockGateCallbacks()
        unlockGateShownAtUptimeMs = 0L
        ignoreNextHomeUpForUnlockGate = false

        val view = unlockGateView ?: return
        if (!view.isAttachedToWindow) {
            unlockGateView = null
            return
        }

        try {
            windowManager.removeView(view)
        } catch (exception: Exception) {
            Log.e(TAG, "cancelUnlockGateOnMain: removeView failed", exception)
        } finally {
            unlockGateView = null
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

    private fun cancelUnlockGateCallbacks() {
        unlockGateDismissRunnable?.let(mainHandler::removeCallbacks)
        unlockGateDismissRunnable = null
    }

    private fun registerUnlockGateReceiver() {
        if (unlockGateReceiver != null) return

        unlockGateReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF ->
                            runOnMainThread {
                                wakeUnlockGateArmed = true
                                cancelUnlockGateOnMain()
                            }

                        Intent.ACTION_SCREEN_ON ->
                            runOnMainThread {
                                maybeShowUnlockGateOnWake()
                            }

                        Intent.ACTION_USER_PRESENT ->
                            runOnMainThread {
                                if (wakeUnlockGateArmed) {
                                    wakeUnlockGateArmed = false
                                    showUnlockGateOnMain()
                                }
                            }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }

        ContextCompat.registerReceiver(this, unlockGateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterUnlockGateReceiver() {
        val receiver = unlockGateReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (exception: Exception) {
            Log.e(TAG, "unregisterUnlockGateReceiver: failed", exception)
        } finally {
            unlockGateReceiver = null
        }
    }

    private fun configureServiceInfo() {
        serviceInfo =
            serviceInfo?.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
    }

    private fun maybeShowUnlockGateOnWake() {
        if (!wakeUnlockGateArmed) return
        if (keyguardManager.isDeviceLocked) return

        wakeUnlockGateArmed = false
        showUnlockGateOnMain()
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    private fun createOverlayLayoutParams(title: String): WindowManager.LayoutParams =
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
            this.title = title
        }

    companion object {
        private const val TAG = "LumaActionService"
        private const val LIGHT_OS_PACKAGE = "com.lightos"
        private const val MIN_MASK_VISIBILITY_MS = 200L
        private const val HARD_TIMEOUT_MS = 900L
        private const val TOOL_LAUNCH_MASK_WINDOW_TITLE = "Luma Tool Launch Mask"
        private const val UNLOCK_GATE_MIN_VISIBILITY_MS = 150L
        private const val UNLOCK_GATE_HIDE_DELAY_MS = 100L
        private const val UNLOCK_GATE_WINDOW_TITLE = "Luma Unlock Gate"

        private var instance: WeakReference<ActionService> = WeakReference(null)

        fun instance(): ActionService? = instance.get()
    }
}
