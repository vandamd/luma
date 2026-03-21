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
import android.graphics.drawable.GradientDrawable
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
import app.luma.MainActivity
import app.luma.R
import app.luma.data.Prefs
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var unlockGateClockRunnable: Runnable? = null
    private var unlockGateReceiver: BroadcastReceiver? = null
    private var repeatedHomeGateEligible = false
    private var ignoreNextHomeUpForUnlockGate = false
    private var wakeUnlockGateArmed = false
    private var unlockGateVisible = false
    private var unlockGateHomeContentTopPx = 0
    private var unlockGatePrefersHomeStatusBar = false

    override fun onServiceConnected() {
        configureServiceInfo()
        registerUnlockGateReceiver()
        instance = WeakReference(this)
        publishUnlockGateState()
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

    fun setUnlockGateHomeContentTop(contentTopPx: Int) {
        runOnMainThread {
            unlockGateHomeContentTopPx = contentTopPx.coerceAtLeast(0)
            if (shouldHoldUnlockGateInsetDuringDismiss()) {
                publishUnlockGateState()
                return@runOnMainThread
            }
            updateUnlockGateLayoutOnMain()
            publishUnlockGateState()
        }
    }

    fun dismissUnlockGateForStatusBarAction(delayMs: Long = 0L) {
        runOnMainThread {
            hideUnlockGateWhenReady(delayMs)
        }
    }

    fun isUnlockGateShowingHomeStatusBar(): Boolean =
        unlockGateVisible &&
            unlockGatePrefersHomeStatusBar &&
            currentAppliedUnlockGateTopInsetPx() > 0

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

        if (!prefs.lockscreenGateEnabled) return false
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
                windowManager.addView(view, createToolLaunchMaskLayoutParams(TOOL_LAUNCH_MASK_WINDOW_TITLE))
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
        if (!prefs.lockscreenGateEnabled) return

        cancelUnlockGateCallbacks()
        unlockGateShownAtUptimeMs = SystemClock.uptimeMillis()
        unlockGateVisible = true
        unlockGatePrefersHomeStatusBar = true

        val isDark = prefs.isDarkTheme()
        val view =
            unlockGateView ?: LayoutInflater.from(this).inflate(R.layout.unlock_gate_overlay, null).also {
                unlockGateView = it
            }

        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        view.findViewById<TextView>(R.id.unlockGateClock).setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        view.findViewById<View>(R.id.unlockGateHomeButton).apply {
            background = createUnlockGateHomeButtonBackground(isDark)
            setOnClickListener {
                performAppTapHapticFeedback(this@ActionService)
                dispatchLockscreenShortcut()
                hideUnlockGateWhenReady(UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS)
            }
        }
        updateUnlockGateClock(view)
        updateUnlockGateContentLayout(view)
        scheduleNextUnlockGateClockTick(view)

        if (!view.isAttachedToWindow) {
            try {
                windowManager.addView(
                    view,
                    createUnlockGateLayoutParams(
                        title = UNLOCK_GATE_WINDOW_TITLE,
                        topInsetPx = currentUnlockGateTopInsetPx(),
                    ),
                )
            } catch (exception: Exception) {
                Log.e(TAG, "showUnlockGateOnMain: addView failed", exception)
                unlockGateView = null
                unlockGateShownAtUptimeMs = 0L
                unlockGateVisible = false
                unlockGatePrefersHomeStatusBar = false
                publishUnlockGateState()
                return
            }
        } else {
            updateUnlockGateLayoutOnMain()
        }
        publishUnlockGateState()
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
        unlockGateVisible = false
        unlockGatePrefersHomeStatusBar = false

        val view = unlockGateView ?: return
        if (!view.isAttachedToWindow) {
            unlockGateView = null
            publishUnlockGateState()
            return
        }

        try {
            windowManager.removeView(view)
        } catch (exception: Exception) {
            Log.e(TAG, "cancelUnlockGateOnMain: removeView failed", exception)
        } finally {
            unlockGateView = null
            publishUnlockGateState()
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
        unlockGateClockRunnable?.let(mainHandler::removeCallbacks)
        unlockGateDismissRunnable = null
        unlockGateClockRunnable = null
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
                                    if (prefs.lockscreenGateEnabled) {
                                        showUnlockGateOnMain()
                                    }
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
        if (!prefs.lockscreenGateEnabled) {
            wakeUnlockGateArmed = false
            return
        }
        if (keyguardManager.isDeviceLocked) return

        wakeUnlockGateArmed = false
        showUnlockGateOnMain()
        bringLumaToFrontUnderUnlockGate()
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    private fun createToolLaunchMaskLayoutParams(title: String): WindowManager.LayoutParams =
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

    private fun createUnlockGateLayoutParams(
        title: String,
        topInsetPx: Int,
    ): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            resolveUnlockGateHeightPx(topInsetPx),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = topInsetPx
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            this.title = title
        }

    private fun updateUnlockGateLayoutOnMain() {
        val view = unlockGateView ?: return
        if (!view.isAttachedToWindow) return

        try {
            windowManager.updateViewLayout(
                view,
                createUnlockGateLayoutParams(
                    title = UNLOCK_GATE_WINDOW_TITLE,
                    topInsetPx = currentUnlockGateTopInsetPx(),
                ),
            )
        } catch (exception: Exception) {
            Log.e(TAG, "updateUnlockGateLayoutOnMain: updateViewLayout failed", exception)
        }
        updateUnlockGateContentLayout(view)
        publishUnlockGateState()
    }

    private fun currentUnlockGateTopInsetPx(): Int =
        if (unlockGatePrefersHomeStatusBar) unlockGateHomeContentTopPx.coerceAtLeast(0) else 0

    private fun currentAppliedUnlockGateTopInsetPx(): Int {
        val layoutParams = unlockGateView?.layoutParams as? WindowManager.LayoutParams
        if (unlockGateView?.isAttachedToWindow == true && layoutParams != null) {
            return layoutParams.y.coerceAtLeast(0)
        }
        return currentUnlockGateTopInsetPx()
    }

    private fun shouldHoldUnlockGateInsetDuringDismiss(): Boolean =
        unlockGateView?.isAttachedToWindow == true &&
            unlockGateDismissRunnable != null &&
            unlockGatePrefersHomeStatusBar

    private fun resolveUnlockGateHeightPx(topInsetPx: Int): Int {
        val displayHeight =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.height()
            } else {
                @Suppress("DEPRECATION")
                resources.displayMetrics.heightPixels
            }
        return (displayHeight - topInsetPx).coerceAtLeast(1)
    }

    private fun bringLumaToFrontUnderUnlockGate() {
        try {
            startActivity(MainActivity.createUnlockGateHomeIntent(this))
        } catch (exception: Exception) {
            Log.e(TAG, "bringLumaToFrontUnderUnlockGate: startActivity failed", exception)
        }
    }

    private fun dispatchLockscreenShortcut() {
        try {
            startActivity(MainActivity.createLockscreenShortcutIntent(this))
        } catch (exception: Exception) {
            Log.e(TAG, "dispatchLockscreenShortcut: startActivity failed", exception)
        }
    }

    private fun publishUnlockGateState() {
        _unlockGateVisible.value = unlockGateVisible
        _unlockGateShowingHomeStatusBar.value = isUnlockGateShowingHomeStatusBar()
    }

    private fun updateUnlockGateClock(view: View) {
        view.findViewById<TextView>(R.id.unlockGateClock).text =
            formatClockText(
                prefs = prefs,
                appendNotificationIndicator = prefs.lockscreenClockNotificationIndicator,
            )
    }

    private fun updateUnlockGateContentLayout(view: View) {
        val donutOffsetPx = resources.displayMetrics.density * UNLOCK_GATE_HOME_BUTTON_SIZE_DP
        view.findViewById<TextView>(R.id.unlockGateClock).translationY =
            -(currentUnlockGateTopInsetPx() / 2f) - donutOffsetPx
    }

    private fun scheduleNextUnlockGateClockTick(view: View) {
        unlockGateClockRunnable?.let(mainHandler::removeCallbacks)
        unlockGateClockRunnable =
            Runnable {
                if (!view.isAttachedToWindow) {
                    unlockGateClockRunnable = null
                    return@Runnable
                }
                updateUnlockGateClock(view)
                scheduleNextUnlockGateClockTick(view)
            }.also { runnable ->
                val now = System.currentTimeMillis()
                mainHandler.postDelayed(runnable, 1000 - (now % 1000))
            }
    }

    private fun createUnlockGateHomeButtonBackground(isDark: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(6, if (isDark) Color.WHITE else Color.BLACK)
        }

    companion object {
        private const val TAG = "LumaActionService"
        private const val LIGHT_OS_PACKAGE = "com.lightos"
        private const val MIN_MASK_VISIBILITY_MS = 200L
        private const val HARD_TIMEOUT_MS = 900L
        private const val TOOL_LAUNCH_MASK_WINDOW_TITLE = "Luma Tool Launch Mask"
        private const val UNLOCK_GATE_MIN_VISIBILITY_MS = 150L
        private const val UNLOCK_GATE_HIDE_DELAY_MS = 100L
        private const val UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS = 150L
        private const val UNLOCK_GATE_HOME_BUTTON_SIZE_DP = 30f
        private const val UNLOCK_GATE_WINDOW_TITLE = "Luma Unlock Gate"

        private var instance: WeakReference<ActionService> = WeakReference(null)
        private val _unlockGateVisible = MutableStateFlow(false)
        val unlockGateVisible: StateFlow<Boolean> = _unlockGateVisible.asStateFlow()
        private val _unlockGateShowingHomeStatusBar = MutableStateFlow(false)
        val unlockGateShowingHomeStatusBar: StateFlow<Boolean> = _unlockGateShowingHomeStatusBar.asStateFlow()

        fun instance(): ActionService? = instance.get()
    }
}
