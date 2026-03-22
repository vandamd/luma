package app.luma.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.bluetooth.BluetoothAdapter
import android.content.res.ColorStateList
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.graphics.Path
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.luma.MainActivity
import app.luma.R
import app.luma.data.Constants.Action
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActionService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val keyguardManager by lazy { getSystemService(KEYGUARD_SERVICE) as KeyguardManager }
    private val prefs by lazy { Prefs.getInstance(this) }
    private val overlayInflater by lazy {
        LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme))
    }

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
    private var unlockGateBatteryReceiver: BroadcastReceiver? = null
    private var unlockGateBluetoothReceiver: BroadcastReceiver? = null
    private var unlockGateWifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var unlockGateTelephonyCallback: TelephonyCallback? = null
    private var repeatedHomeGateEligible = false
    private var ignoreNextHomeUpForUnlockGate = false
    private var wakeUnlockGateArmed = false
    private var unlockGateVisible = false
    private var unlockGateHomeContentTopPx = 0
    private var unlockGatePrefersHomeStatusBar = false
    private var secureLockMaskVisible = false
    private var pendingSecureUnlockHomeLaunch = false
    private var secureLockMaskGestureInFlight = false
    private var secureLockMaskGestureAttempt = 0

    override fun onServiceConnected() {
        configureServiceInfo()
        registerUnlockGateReceiver()
        instance = WeakReference(this)
        publishUnlockGateState()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        pendingSecureUnlockHomeLaunch = false
        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0
        unregisterUnlockGateReceiver()
        instance = WeakReference(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        pendingSecureUnlockHomeLaunch = false
        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0
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
        pendingSecureUnlockHomeLaunch = false
        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        if (unlockGateView != null) {
            if (secureLockMaskVisible) {
                return true
            }
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
        unlockGatePrefersHomeStatusBar = false
        secureLockMaskVisible = false
        pendingSecureUnlockHomeLaunch = false
        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0

        val isDark = prefs.isDarkTheme()
        val view =
            unlockGateView ?: overlayInflater.inflate(R.layout.unlock_gate_overlay, null).also {
                unlockGateView = it
            }

        resetUnlockGateViewState(view)
        setSecureLockMaskBlankState(view, blank = false)
        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener(null)
        updateUnlockGateTextAppearance(view, isDark)
        updateSecureLockMaskStatusBar(view)
        view.findViewById<TextView>(R.id.unlockGateDate).setOnClickListener {
            performAppTapHapticFeedback(this@ActionService)
            if (dispatchLockscreenDateTap()) {
                hideUnlockGateWhenReady(UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS)
            }
        }
        view.findViewById<ImageView>(R.id.unlockGateHomeButton).apply {
            updateUnlockGateHomeButtonAppearance(
                imageView = this,
                isDark = isDark,
                locked = false,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performAppTapHapticFeedback(this@ActionService)
                dispatchLockscreenShortcut()
                hideUnlockGateWhenReady(UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS)
            }
        }
        updateUnlockGateText(view)
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

    private fun showSecureLockMaskOnMain(): Boolean {
        if (!prefs.lockscreenGateEnabled) return false

        cancelUnlockGateCallbacks()
        unlockGateShownAtUptimeMs = SystemClock.uptimeMillis()
        unlockGateVisible = true
        unlockGatePrefersHomeStatusBar = false
        secureLockMaskVisible = true
        pendingSecureUnlockHomeLaunch = true
        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0

        val isDark = prefs.isDarkTheme()
        val view =
            unlockGateView ?: overlayInflater.inflate(R.layout.unlock_gate_overlay, null).also {
                unlockGateView = it
            }

        resetUnlockGateViewState(view)
        setSecureLockMaskBlankState(view, blank = false)
        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener {
            dismissSecureLockMaskWithGestureOnMain()
        }
        updateUnlockGateTextAppearance(view, isDark)
        updateSecureLockMaskStatusBar(view)
        view.findViewById<TextView>(R.id.unlockGateDate).apply {
            setOnClickListener(null)
            isClickable = false
            isFocusable = false
        }
        view.findViewById<ImageView>(R.id.unlockGateHomeButton).apply {
            updateUnlockGateHomeButtonAppearance(
                imageView = this,
                isDark = isDark,
                locked = true,
            )
            setOnClickListener(null)
            isClickable = false
            isFocusable = false
        }
        updateUnlockGateText(view)
        updateUnlockGateContentLayout(view)
        scheduleNextUnlockGateClockTick(view)

        if (!view.isAttachedToWindow) {
            try {
                windowManager.addView(
                    view,
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = true,
                    ),
                )
            } catch (exception: Exception) {
                Log.e(TAG, "showSecureLockMaskOnMain: addView failed", exception)
                unlockGateView = null
                unlockGateShownAtUptimeMs = 0L
                unlockGateVisible = false
                secureLockMaskVisible = false
                pendingSecureUnlockHomeLaunch = false
                publishUnlockGateState()
                return false
            }
        } else {
            updateUnlockGateLayoutOnMain()
        }
        publishUnlockGateState()
        return true
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
        secureLockMaskVisible = false
        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0
        stopUnlockGateStatusBarMonitors()

        val view = unlockGateView ?: return
        view.animate().setListener(null)
        view.animate().cancel()
        resetUnlockGateViewState(view)
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

    private fun dismissSecureLockMaskWithGestureOnMain() {
        val view = unlockGateView ?: return
        if (!secureLockMaskVisible || secureLockMaskGestureInFlight) return

        secureLockMaskGestureInFlight = true
        secureLockMaskGestureAttempt = 0
        view.isClickable = false
        view.isFocusable = false
        setSecureLockMaskBlankState(view, blank = true)
        updateSecureLockMaskTouchabilityOnMain(touchable = false)
        dispatchSecureLockMaskGestureAttempt(view, 0)
    }

    private fun dispatchSecureLockMaskGestureAttempt(
        sourceView: View,
        attempt: Int,
    ) {
        secureLockMaskGestureAttempt = attempt
        mainHandler.postDelayed(
            {
                if (!secureLockMaskVisible || unlockGateView !== sourceView) {
                    secureLockMaskGestureInFlight = false
                    return@postDelayed
                }

                val dispatched =
                    dispatchGesture(
                        createSecureLockMaskDismissGesture(),
                        object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription) {
                                Log.d(TAG, "dismissSecureLockMaskWithGestureOnMain: gesture completed attempt=$attempt")
                                secureLockMaskGestureInFlight = false
                                secureLockMaskGestureAttempt = 0
                                mainHandler.postDelayed(
                                    {
                                        if (secureLockMaskVisible && unlockGateView === sourceView) {
                                            cancelUnlockGateOnMain()
                                        }
                                    },
                                    SECURE_LOCK_MASK_GESTURE_COMPLETE_DISMISS_DELAY_MS,
                                )
                            }

                            override fun onCancelled(gestureDescription: GestureDescription) {
                                handleSecureLockMaskGestureFailure(
                                    sourceView = sourceView,
                                    attempt = attempt,
                                    reason = "gesture cancelled",
                                )
                            }
                        },
                        null,
                    )
                if (!dispatched) {
                    handleSecureLockMaskGestureFailure(
                        sourceView = sourceView,
                        attempt = attempt,
                        reason = "dispatchGesture returned false",
                    )
                }
            },
            if (attempt == 0) {
                SECURE_LOCK_MASK_GESTURE_DISPATCH_DELAY_MS
            } else {
                SECURE_LOCK_MASK_GESTURE_RETRY_DELAY_MS
            },
        )
    }

    private fun handleSecureLockMaskGestureFailure(
        sourceView: View,
        attempt: Int,
        reason: String,
    ) {
        Log.w(TAG, "dismissSecureLockMaskWithGestureOnMain: $reason attempt=$attempt")
        if (!secureLockMaskVisible || unlockGateView !== sourceView) {
            secureLockMaskGestureInFlight = false
            secureLockMaskGestureAttempt = 0
            return
        }

        if (attempt < SECURE_LOCK_MASK_GESTURE_MAX_RETRIES) {
            dispatchSecureLockMaskGestureAttempt(sourceView, attempt + 1)
            return
        }

        secureLockMaskGestureInFlight = false
        secureLockMaskGestureAttempt = 0
        updateSecureLockMaskTouchabilityOnMain(touchable = true)
        if (secureLockMaskVisible) {
            setSecureLockMaskBlankState(sourceView, blank = false)
            sourceView.isClickable = true
            sourceView.isFocusable = true
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
                                pendingSecureUnlockHomeLaunch = false
                                cancelUnlockGateOnMain()
                            }

                        Intent.ACTION_SCREEN_ON ->
                            runOnMainThread {
                                maybeShowUnlockGateOnWake()
                            }

                        Intent.ACTION_USER_PRESENT ->
                            runOnMainThread {
                                if (pendingSecureUnlockHomeLaunch && !keyguardManager.isDeviceLocked) {
                                    wakeUnlockGateArmed = false
                                    showUnlockGateOnMain()
                                    bringLumaToFrontUnderUnlockGate()
                                    return@runOnMainThread
                                }
                                if (wakeUnlockGateArmed) {
                                    wakeUnlockGateArmed = false
                                    if (prefs.lockscreenGateEnabled && !keyguardManager.isDeviceLocked) {
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
        if (keyguardManager.isDeviceLocked) {
            if (showSecureLockMaskOnMain()) {
                wakeUnlockGateArmed = false
            }
            return
        }

        pendingSecureUnlockHomeLaunch = false
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

    private fun createSecureLockMaskLayoutParams(
        title: String,
        touchable: Boolean,
    ): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            this.title = title
        }

    private fun updateUnlockGateLayoutOnMain() {
        val view = unlockGateView ?: return
        if (!view.isAttachedToWindow) return

        try {
            windowManager.updateViewLayout(
                view,
                if (secureLockMaskVisible) {
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = !secureLockMaskGestureInFlight,
                    )
                } else {
                    createUnlockGateLayoutParams(
                        title = UNLOCK_GATE_WINDOW_TITLE,
                        topInsetPx = currentUnlockGateTopInsetPx(),
                    )
                },
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

    private fun dispatchLockscreenDateTap(): Boolean {
        if (prefs.getLockscreenDateTapAction() == Action.Disabled) return false
        return try {
            startActivity(MainActivity.createLockscreenDateTapIntent(this))
            true
        } catch (exception: Exception) {
            Log.e(TAG, "dispatchLockscreenDateTap: startActivity failed", exception)
            false
        }
    }

    private fun canHandleStatusBarSectionTap(section: StatusBarSectionType): Boolean {
        val action = prefs.getSectionAction(section)
        if (action == Action.Disabled) return false
        if (action == Action.OpenApp && prefs.getSectionApp(section).appPackage.isEmpty()) return false
        return true
    }

    private fun dispatchStatusBarSectionTap(section: StatusBarSectionType): Boolean {
        if (!canHandleStatusBarSectionTap(section)) return false
        return try {
            startActivity(MainActivity.createStatusBarSectionIntent(this, section))
            true
        } catch (exception: Exception) {
            Log.e(TAG, "dispatchStatusBarSectionTap: startActivity failed", exception)
            false
        }
    }

    private fun bindStatusBarSectionTapAction(
        view: View,
        viewId: Int,
        section: StatusBarSectionType,
    ) {
        val target = view.findViewById<View>(viewId)
        val enabled = !secureLockMaskVisible && canHandleStatusBarSectionTap(section)
        target.isClickable = enabled
        target.isFocusable = enabled
        target.setOnClickListener(
            if (enabled) {
                View.OnClickListener {
                    performStatusBarPressHapticFeedback(this)
                    if (dispatchStatusBarSectionTap(section)) {
                        hideUnlockGateWhenReady(UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS)
                    }
                }
            } else {
                null
            },
        )
    }

    private fun publishUnlockGateState() {
        _unlockGateVisible.value = unlockGateVisible
        _unlockGateShowingHomeStatusBar.value = isUnlockGateShowingHomeStatusBar()
    }

    private fun updateUnlockGateTextAppearance(
        view: View,
        isDark: Boolean,
    ) {
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        view.findViewById<TextView>(R.id.unlockGateClock).setTextColor(textColor)
        view.findViewById<TextView>(R.id.unlockGateDate).setTextColor(textColor)
    }

    private fun updateUnlockGateText(view: View) {
        val isInteractive = !secureLockMaskVisible
        view.findViewById<TextView>(R.id.unlockGateClock).text =
            formatClockText(
                prefs = prefs,
                appendNotificationIndicator = prefs.lockscreenClockNotificationIndicator,
            )
        view.findViewById<TextView>(R.id.unlockGateDate).apply {
            if (prefs.lockscreenDateEnabled) {
                text = formatLockscreenDateText(prefs.lockscreenDateFormat)
                visibility = View.VISIBLE
                isClickable = isInteractive && prefs.getLockscreenDateTapAction() != Action.Disabled
                isFocusable = isClickable
            } else {
                visibility = View.GONE
                isClickable = false
                isFocusable = false
            }
        }
    }

    private fun updateUnlockGateContentLayout(view: View) {
        val clockView = view.findViewById<TextView>(R.id.unlockGateClock)
        val dateView = view.findViewById<TextView>(R.id.unlockGateDate)
        val donutOffsetPx = resources.displayMetrics.density * UNLOCK_GATE_HOME_BUTTON_SIZE_DP
        val baseTranslationY = -(currentUnlockGateTopInsetPx() / 2f) - donutOffsetPx
        val dateGapPx = resources.displayMetrics.density * UNLOCK_GATE_DATE_GAP_DP
        val dateCenterOffsetPx = ((clockView.lineHeight + dateView.lineHeight) / 2f) + dateGapPx
        clockView.translationY = baseTranslationY
        dateView.translationY = baseTranslationY - dateCenterOffsetPx
    }

    private fun resetUnlockGateViewState(view: View) {
        view.translationY = 0f
        view.alpha = 1f
    }

    private fun setSecureLockMaskBlankState(
        view: View,
        blank: Boolean,
    ) {
        val contentAlpha = if (blank) 0f else 1f
        view.findViewById<View>(R.id.unlockGateStatusBar).alpha = contentAlpha
        view.findViewById<TextView>(R.id.unlockGateClock).alpha = contentAlpha
        view.findViewById<TextView>(R.id.unlockGateDate).alpha = contentAlpha
        view.findViewById<View>(R.id.unlockGateHomeButton).alpha = contentAlpha
    }

    private fun updateSecureLockMaskTouchabilityOnMain(touchable: Boolean) {
        val view = unlockGateView ?: return
        if (!view.isAttachedToWindow || !secureLockMaskVisible) return

        try {
            windowManager.updateViewLayout(
                view,
                createSecureLockMaskLayoutParams(
                    title = SECURE_LOCK_MASK_WINDOW_TITLE,
                    touchable = touchable,
                ),
            )
        } catch (exception: Exception) {
            Log.e(TAG, "updateSecureLockMaskTouchabilityOnMain: updateViewLayout failed", exception)
        }
    }

    private fun createSecureLockMaskDismissGesture(): GestureDescription {
        val bounds =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Rect(
                    0,
                    0,
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels,
                )
            }

        val centerX = bounds.exactCenterX()
        val startY = bounds.height() * SECURE_LOCK_MASK_GESTURE_START_Y_RATIO
        val endY = bounds.height() * SECURE_LOCK_MASK_GESTURE_END_Y_RATIO
        val path =
            Path().apply {
                moveTo(centerX, startY)
                lineTo(centerX, endY)
            }

        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SECURE_LOCK_MASK_GESTURE_DURATION_MS))
            .build()
    }

    private fun updateSecureLockMaskStatusBar(view: View) {
        val statusBar = view.findViewById<View>(R.id.unlockGateStatusBar)
        val shouldShow = shouldShowUnlockGateStatusBar()
        statusBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow) {
            stopUnlockGateStatusBarMonitors()
            return
        }

        bindStatusBarSectionTapAction(view, R.id.statusConnectivityLayout, StatusBarSectionType.CELLULAR)
        bindStatusBarSectionTapAction(view, R.id.statusBatteryLayout, StatusBarSectionType.BATTERY)

        val textColor = unlockGateTextColor(view)
        view.findViewById<TextView>(R.id.statusNetworkType).setTextColor(textColor)
        view.findViewById<TextView>(R.id.statusBatteryText).setTextColor(textColor)

        updateSecureLockMaskStatusBarClock(view)
        updateSecureLockMaskBatteryStatus(view, textColor)
        updateSecureLockMaskConnectivityStatus(view, textColor)
        syncUnlockGateStatusBarMonitors()
    }

    private fun updateSecureLockMaskStatusBarClock(view: View) {
        val clockLayout = view.findViewById<View>(R.id.statusClockLayout)
        clockLayout.visibility = View.GONE
    }

    private fun updateSecureLockMaskBatteryStatus(
        view: View,
        textColor: Int,
        batteryIntent: Intent? = null,
    ) {
        val batteryLayout = view.findViewById<LinearLayout>(R.id.statusBatteryLayout)
        val batteryText = view.findViewById<TextView>(R.id.statusBatteryText)
        val batteryIcon = view.findViewById<ImageView>(R.id.statusBattery)

        if (!prefs.batteryPercentage && !prefs.batteryIcon) {
            batteryText.visibility = View.GONE
            batteryIcon.visibility = View.GONE
            batteryLayout.visibility = View.INVISIBLE
            return
        }

        val sticky = batteryIntent ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky == null) {
            batteryText.visibility = View.GONE
            batteryIcon.visibility = View.GONE
            batteryLayout.visibility = View.INVISIBLE
            return
        }

        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            batteryText.visibility = View.GONE
            batteryIcon.visibility = View.GONE
            batteryLayout.visibility = View.INVISIBLE
            return
        }

        val pct = level * 100 / scale
        val charging = LumaStatusBarUi.batteryIconRes(sticky) == R.drawable.battery_charging
        val iconRes = LumaStatusBarUi.batteryIconRes(sticky)

        batteryLayout.visibility = View.VISIBLE
        batteryText.visibility = if (prefs.batteryPercentage) View.VISIBLE else View.GONE
        batteryText.text = "$pct%"
        batteryIcon.visibility = if (prefs.batteryIcon) View.VISIBLE else View.GONE
        batteryIcon.setImageResource(iconRes)
        batteryIcon.scaleType = if (charging) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.FIT_END
        batteryIcon.scaleX = if (charging) 1f else -1f
        batteryIcon.setColorFilter(textColor)
        LumaStatusBarUi.updateSectionBaseline(batteryLayout)
    }

    private fun updateSecureLockMaskConnectivityStatus(
        view: View,
        textColor: Int,
    ) {
        val connectivityLayout = view.findViewById<LinearLayout>(R.id.statusConnectivityLayout)
        val networkType = view.findViewById<TextView>(R.id.statusNetworkType)
        val signalIcon = view.findViewById<ImageView>(R.id.statusSignal)
        val wifiIcon = view.findViewById<ImageView>(R.id.statusWifi)
        val bluetoothIcon = view.findViewById<ImageView>(R.id.statusBluetooth)

        if (prefs.cellularEnabled) {
            val level = prefs.lastCellularSignalLevel
            if (level != null) {
                LumaStatusBarUi.showTinted(signalIcon, LumaStatusBarUi.signalDrawableForLevel(level), textColor)
            } else {
                signalIcon.visibility = View.GONE
            }
            val label = LumaStatusBarUi.networkLabelForType(prefs.lastCellularNetworkType)
            networkType.visibility = if (label.isNotEmpty()) View.VISIBLE else View.GONE
            networkType.text = label
        } else {
            signalIcon.visibility = View.GONE
            networkType.visibility = View.GONE
        }

        if (prefs.wifiEnabled) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
            val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val level = wm.calculateSignalLevel(activeCaps.signalStrength)
                LumaStatusBarUi.showTinted(wifiIcon, LumaStatusBarUi.wifiDrawableForLevel(level), textColor)
            } else {
                wifiIcon.visibility = View.GONE
            }
        } else {
            wifiIcon.visibility = View.GONE
        }

        if (prefs.bluetoothEnabled) {
            val btOn = Settings.Global.getInt(contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0
            if (btOn) {
                LumaStatusBarUi.showTinted(bluetoothIcon, R.drawable.bluetooth, textColor)
            } else {
                bluetoothIcon.visibility = View.GONE
            }
        } else {
            bluetoothIcon.visibility = View.GONE
        }

        val anyVisible =
            networkType.visibility == View.VISIBLE ||
                signalIcon.visibility == View.VISIBLE ||
                wifiIcon.visibility == View.VISIBLE ||
                bluetoothIcon.visibility == View.VISIBLE
        connectivityLayout.visibility = if (anyVisible) View.VISIBLE else View.INVISIBLE
        LumaStatusBarUi.updateSectionBaseline(connectivityLayout)
    }

    private fun shouldShowUnlockGateStatusBar(): Boolean =
        unlockGateVisible && !unlockGatePrefersHomeStatusBar && prefs.isStatusBarVisibleOnLockscreen()

    private fun unlockGateTextColor(view: View): Int = view.findViewById<TextView>(R.id.unlockGateClock).currentTextColor

    private fun refreshUnlockGateBatteryStatus(batteryIntent: Intent? = null) {
        val view = unlockGateView ?: return
        if (!shouldShowUnlockGateStatusBar()) return
        updateSecureLockMaskBatteryStatus(view, unlockGateTextColor(view), batteryIntent)
    }

    private fun refreshUnlockGateConnectivityStatus() {
        val view = unlockGateView ?: return
        if (!shouldShowUnlockGateStatusBar()) return
        updateSecureLockMaskConnectivityStatus(view, unlockGateTextColor(view))
    }

    private fun syncUnlockGateStatusBarMonitors() {
        if (!shouldShowUnlockGateStatusBar()) {
            stopUnlockGateStatusBarMonitors()
            return
        }

        if (prefs.batteryPercentage || prefs.batteryIcon) {
            startUnlockGateBatteryMonitor()
        } else {
            stopUnlockGateBatteryMonitor()
        }

        if (prefs.cellularEnabled) {
            startUnlockGateCellularMonitor()
        } else {
            stopUnlockGateCellularMonitor()
        }

        if (prefs.wifiEnabled) {
            startUnlockGateWifiMonitor()
        } else {
            stopUnlockGateWifiMonitor()
        }

        if (prefs.bluetoothEnabled) {
            startUnlockGateBluetoothMonitor()
        } else {
            stopUnlockGateBluetoothMonitor()
        }
    }

    private fun stopUnlockGateStatusBarMonitors() {
        stopUnlockGateBatteryMonitor()
        stopUnlockGateCellularMonitor()
        stopUnlockGateWifiMonitor()
        stopUnlockGateBluetoothMonitor()
    }

    private fun startUnlockGateBatteryMonitor() {
        if (unlockGateBatteryReceiver != null) return

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent == null) return
                    runOnMainThread {
                        refreshUnlockGateBatteryStatus(intent)
                    }
                }
            }

        try {
            val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            unlockGateBatteryReceiver = receiver
            sticky?.let(::refreshUnlockGateBatteryStatus)
        } catch (exception: Exception) {
            Log.e(TAG, "startUnlockGateBatteryMonitor: registerReceiver failed", exception)
        }
    }

    private fun stopUnlockGateBatteryMonitor() {
        val receiver = unlockGateBatteryReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (exception: Exception) {
            Log.e(TAG, "stopUnlockGateBatteryMonitor: unregisterReceiver failed", exception)
        } finally {
            unlockGateBatteryReceiver = null
        }
    }

    private fun startUnlockGateCellularMonitor() {
        if (unlockGateTelephonyCallback != null) return

        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return
        updateUnlockGateCellularSnapshot(telephonyManager)
        refreshUnlockGateConnectivityStatus()

        val callback =
            object :
                TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DataConnectionStateListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val level = signalStrength.level.coerceIn(0, 4)
                    runOnMainThread {
                        prefs.lastCellularSignalLevel = level
                        refreshUnlockGateConnectivityStatus()
                    }
                }

                override fun onDataConnectionStateChanged(
                    state: Int,
                    networkType: Int,
                ) {
                    runOnMainThread {
                        prefs.lastCellularNetworkType = networkType
                        refreshUnlockGateConnectivityStatus()
                    }
                }
            }

        try {
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            unlockGateTelephonyCallback = callback
        } catch (exception: SecurityException) {
            Log.w(TAG, "startUnlockGateCellularMonitor: registerTelephonyCallback failed", exception)
        }
    }

    private fun stopUnlockGateCellularMonitor() {
        val callback = unlockGateTelephonyCallback ?: return
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        try {
            telephonyManager?.unregisterTelephonyCallback(callback)
        } catch (exception: Exception) {
            Log.e(TAG, "stopUnlockGateCellularMonitor: unregisterTelephonyCallback failed", exception)
        } finally {
            unlockGateTelephonyCallback = null
        }
    }

    private fun updateUnlockGateCellularSnapshot(telephonyManager: TelephonyManager) {
        try {
            telephonyManager.signalStrength?.let {
                prefs.lastCellularSignalLevel = it.level.coerceIn(0, 4)
            }
        } catch (_: SecurityException) {
        }
        try {
            prefs.lastCellularNetworkType = telephonyManager.dataNetworkType
        } catch (_: SecurityException) {
        }
    }

    private fun startUnlockGateWifiMonitor() {
        if (unlockGateWifiNetworkCallback != null) return

        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    runOnMainThread {
                        refreshUnlockGateConnectivityStatus()
                    }
                }

                override fun onLost(network: Network) {
                    runOnMainThread {
                        refreshUnlockGateConnectivityStatus()
                    }
                }
            }

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request =
            NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
            unlockGateWifiNetworkCallback = callback
            refreshUnlockGateConnectivityStatus()
        } catch (exception: Exception) {
            Log.e(TAG, "startUnlockGateWifiMonitor: registerNetworkCallback failed", exception)
        }
    }

    private fun stopUnlockGateWifiMonitor() {
        val callback = unlockGateWifiNetworkCallback ?: return
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (exception: Exception) {
            Log.e(TAG, "stopUnlockGateWifiMonitor: unregisterNetworkCallback failed", exception)
        } finally {
            unlockGateWifiNetworkCallback = null
        }
    }

    private fun startUnlockGateBluetoothMonitor() {
        if (unlockGateBluetoothReceiver != null) return

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    runOnMainThread {
                        refreshUnlockGateConnectivityStatus()
                    }
                }
            }

        try {
            registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            unlockGateBluetoothReceiver = receiver
            refreshUnlockGateConnectivityStatus()
        } catch (exception: Exception) {
            Log.e(TAG, "startUnlockGateBluetoothMonitor: registerReceiver failed", exception)
        }
    }

    private fun stopUnlockGateBluetoothMonitor() {
        val receiver = unlockGateBluetoothReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (exception: Exception) {
            Log.e(TAG, "stopUnlockGateBluetoothMonitor: unregisterReceiver failed", exception)
        } finally {
            unlockGateBluetoothReceiver = null
        }
    }

    private fun scheduleNextUnlockGateClockTick(view: View) {
        unlockGateClockRunnable?.let(mainHandler::removeCallbacks)
        unlockGateClockRunnable =
            Runnable {
                if (!view.isAttachedToWindow) {
                    unlockGateClockRunnable = null
                    return@Runnable
                }
                updateUnlockGateText(view)
                scheduleNextUnlockGateClockTick(view)
            }.also { runnable ->
                val now = System.currentTimeMillis()
                mainHandler.postDelayed(runnable, 1000 - (now % 1000))
            }
    }

    private fun updateUnlockGateHomeButtonAppearance(
        imageView: ImageView,
        isDark: Boolean,
        locked: Boolean,
    ) {
        val tint = if (isDark) Color.WHITE else Color.BLACK
        if (locked) {
            imageView.background = null
            imageView.setImageResource(R.drawable.ic_unlock_gate_lock)
            imageView.imageTintList = ColorStateList.valueOf(tint)
            @Suppress("DEPRECATION")
            imageView.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        } else {
            imageView.setImageDrawable(null)
            imageView.imageTintList = null
            imageView.clearColorFilter()
            imageView.background = createUnlockGateHomeButtonBackground(isDark)
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
        private const val UNLOCK_GATE_DATE_GAP_DP = 30f
        private const val UNLOCK_GATE_WINDOW_TITLE = "Luma Unlock Gate"
        private const val SECURE_LOCK_MASK_WINDOW_TITLE = "Luma Secure Lock Mask"
        private const val SECURE_LOCK_MASK_GESTURE_DISPATCH_DELAY_MS = 96L
        private const val SECURE_LOCK_MASK_GESTURE_RETRY_DELAY_MS = 160L
        private const val SECURE_LOCK_MASK_GESTURE_COMPLETE_DISMISS_DELAY_MS = 80L
        private const val SECURE_LOCK_MASK_GESTURE_DURATION_MS = 320L
        private const val SECURE_LOCK_MASK_GESTURE_START_Y_RATIO = 0.90f
        private const val SECURE_LOCK_MASK_GESTURE_END_Y_RATIO = 0.14f
        private const val SECURE_LOCK_MASK_GESTURE_MAX_RETRIES = 1

        private var instance: WeakReference<ActionService> = WeakReference(null)
        private val _unlockGateVisible = MutableStateFlow(false)
        val unlockGateVisible: StateFlow<Boolean> = _unlockGateVisible.asStateFlow()
        private val _unlockGateShowingHomeStatusBar = MutableStateFlow(false)
        val unlockGateShowingHomeStatusBar: StateFlow<Boolean> = _unlockGateShowingHomeStatusBar.asStateFlow()

        fun instance(): ActionService? = instance.get()
    }
}
