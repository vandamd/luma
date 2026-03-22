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
    private var unlockGateStateMachine = UnlockGateStateMachine(UNLOCK_GATE_MIN_VISIBILITY_MS)
    private var unlockGateDismissRunnable: Runnable? = null
    private var unlockGateClockRunnable: Runnable? = null
    private var unlockGateReceiver: BroadcastReceiver? = null
    private var unlockGateBatteryReceiver: BroadcastReceiver? = null
    private var unlockGateBluetoothReceiver: BroadcastReceiver? = null
    private var unlockGateWifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var unlockGateTelephonyCallback: TelephonyCallback? = null
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
        secureLockMaskGestureAttempt = 0
        unregisterUnlockGateReceiver()
        instance = WeakReference(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
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
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.SetRepeatedHomeGateEligible(isEligible),
            )
        }
    }

    fun handleLauncherIntent() {
        runOnMainThread {
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.LauncherIntentConsumed(
                    nowUptimeMs = SystemClock.uptimeMillis(),
                    minDelayMs = UNLOCK_GATE_HIDE_DELAY_MS,
                ),
            )
        }
    }

    fun setUnlockGateHomeContentTop(contentTopPx: Int) {
        runOnMainThread {
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.SetHomeContentTop(contentTopPx),
            )
        }
    }

    fun dismissUnlockGateForStatusBarAction(delayMs: Long = 0L) {
        runOnMainThread {
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.DismissRequested(
                    nowUptimeMs = SystemClock.uptimeMillis(),
                    minDelayMs = delayMs,
                ),
            )
        }
    }

    fun isUnlockGateShowingHomeStatusBar(): Boolean =
        unlockGateStateMachine.snapshot.showingHomeStatusBar

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
        cancelUnlockGateOnMain(clearRepeatedHomeGateEligibility = true)
        secureLockMaskGestureAttempt = 0
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        val unlockGatePhase = unlockGateStateMachine.state.phase
        if (unlockGatePhase != UnlockGatePhase.Idle) {
            if (unlockGatePhase == UnlockGatePhase.SecureMask || unlockGatePhase == UnlockGatePhase.AwaitingCredential) {
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                val shouldConsume = unlockGateStateMachine.state.ignoreNextHomeUp
                runOnMainThread {
                    dispatchUnlockGateEventOnMain(
                        UnlockGateEvent.HomeKeyUp(
                            nowUptimeMs = SystemClock.uptimeMillis(),
                            minDelayMs = UNLOCK_GATE_HIDE_DELAY_MS,
                        ),
                    )
                }
                if (shouldConsume) return true
            }
            return false
        }

        if (!prefs.lockscreenGateEnabled) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount != 0) return true

        runOnMainThread {
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.HomeKeyDown(
                    nowUptimeMs = SystemClock.uptimeMillis(),
                    gateEnabled = prefs.lockscreenGateEnabled,
                ),
            )
        }
        return unlockGateStateMachine.state.repeatedHomeGateEligible
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

    private fun cancelUnlockGateOnMain(clearRepeatedHomeGateEligibility: Boolean = false) {
        secureLockMaskGestureAttempt = 0
        unlockGateStateMachine.forceIdle(clearRepeatedHomeGateEligibility)
        cancelUnlockGateCallbacks()
        stopUnlockGateStatusBarMonitors()
        removeUnlockGateViewOnMain()
        publishUnlockGateState()
    }

    private fun dismissSecureLockMaskWithGestureOnMain() {
        if (unlockGateStateMachine.state.phase != UnlockGatePhase.SecureMask) return
        performAppTapHapticFeedback(this)
        dispatchUnlockGateEventOnMain(UnlockGateEvent.SecureMaskTapped)
    }

    private fun dispatchSecureLockMaskGestureAttempt(
        sourceView: View,
        attempt: Int,
    ) {
        secureLockMaskGestureAttempt = attempt
        mainHandler.postDelayed(
            {
                if (unlockGateStateMachine.state.phase != UnlockGatePhase.AwaitingCredential || unlockGateView !== sourceView) {
                    return@postDelayed
                }

                val dispatched =
                    dispatchGesture(
                        createSecureLockMaskDismissGesture(),
                        object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription) {
                                Log.d(TAG, "dismissSecureLockMaskWithGestureOnMain: gesture completed attempt=$attempt")
                                secureLockMaskGestureAttempt = 0
                                dispatchUnlockGateEventOnMain(UnlockGateEvent.SecureGestureCompleted)
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
        if (unlockGateStateMachine.state.phase != UnlockGatePhase.AwaitingCredential || unlockGateView !== sourceView) {
            secureLockMaskGestureAttempt = 0
            return
        }

        if (attempt < SECURE_LOCK_MASK_GESTURE_MAX_RETRIES) {
            dispatchSecureLockMaskGestureAttempt(sourceView, attempt + 1)
            return
        }

        secureLockMaskGestureAttempt = 0
        dispatchUnlockGateEventOnMain(UnlockGateEvent.SecureGestureFailed)
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
        cancelUnlockGateDismissCallback()
        cancelUnlockGateClockTick()
    }

    private fun cancelUnlockGateDismissCallback() {
        unlockGateDismissRunnable?.let(mainHandler::removeCallbacks)
        unlockGateDismissRunnable = null
    }

    private fun cancelUnlockGateClockTick() {
        unlockGateClockRunnable?.let(mainHandler::removeCallbacks)
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
                                dispatchUnlockGateEventOnMain(
                                    UnlockGateEvent.ScreenOff(SystemClock.uptimeMillis()),
                                )
                            }

                        Intent.ACTION_SCREEN_ON ->
                            runOnMainThread {
                                dispatchUnlockGateEventOnMain(
                                    UnlockGateEvent.ScreenOn(
                                        nowUptimeMs = SystemClock.uptimeMillis(),
                                        gateEnabled = prefs.lockscreenGateEnabled,
                                        deviceLocked = keyguardManager.isDeviceLocked,
                                    ),
                                )
                            }

                        Intent.ACTION_USER_PRESENT ->
                            runOnMainThread {
                                dispatchUnlockGateEventOnMain(
                                    UnlockGateEvent.UserPresent(
                                        nowUptimeMs = SystemClock.uptimeMillis(),
                                        gateEnabled = prefs.lockscreenGateEnabled,
                                        deviceLocked = keyguardManager.isDeviceLocked,
                                    ),
                                )
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

    private fun dispatchUnlockGateEventOnMain(event: UnlockGateEvent) {
        val effects = unlockGateStateMachine.dispatch(event)
        val rendered = renderUnlockGateStateOnMain()
        publishUnlockGateState()
        if (!rendered) return
        effects.forEach(::handleUnlockGateEffectOnMain)
    }

    private fun handleUnlockGateEffectOnMain(effect: UnlockGateEffect) {
        when (effect) {
            UnlockGateEffect.BringLumaToFront -> bringLumaToFrontUnderUnlockGate()

            UnlockGateEffect.CancelDismiss -> cancelUnlockGateDismissCallback()

            is UnlockGateEffect.ScheduleDismiss -> {
                cancelUnlockGateDismissCallback()
                unlockGateDismissRunnable =
                    Runnable {
                        dispatchUnlockGateEventOnMain(
                            UnlockGateEvent.DismissTimeout(SystemClock.uptimeMillis()),
                        )
                    }.also { runnable ->
                        mainHandler.postDelayed(runnable, effect.delayMs)
                    }
            }

            UnlockGateEffect.StartSecureDismissGesture -> {
                val view = unlockGateView ?: return
                secureLockMaskGestureAttempt = 0
                dispatchSecureLockMaskGestureAttempt(view, 0)
            }
        }
    }

    private fun renderUnlockGateStateOnMain(): Boolean {
        val state = unlockGateStateMachine.state
        if (state.phase == UnlockGatePhase.Idle) {
            cancelUnlockGateCallbacks()
            stopUnlockGateStatusBarMonitors()
            removeUnlockGateViewOnMain()
            return true
        }

        val isDark = prefs.isDarkTheme()
        val view =
            unlockGateView ?: overlayInflater.inflate(R.layout.unlock_gate_overlay, null).also {
                unlockGateView = it
            }

        resetUnlockGateViewState(view)
        updateUnlockGateTextAppearance(view, isDark)
        updateSecureLockMaskStatusBar(view)
        updateUnlockGateText(view)
        updateUnlockGateContentLayout(view)
        scheduleNextUnlockGateClockTick(view)

        when (state.phase) {
            UnlockGatePhase.SecureMask -> updateUnlockGateSecureMode(view, isDark, blank = false)
            UnlockGatePhase.AwaitingCredential -> updateUnlockGateAwaitingCredentialMode(view, isDark)
            UnlockGatePhase.UnlockGateVisible, UnlockGatePhase.Dismissing -> updateUnlockGateVisibleMode(view, isDark)
            UnlockGatePhase.Idle -> Unit
        }

        val layoutParams =
            when (state.phase) {
                UnlockGatePhase.SecureMask ->
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = true,
                    )

                UnlockGatePhase.AwaitingCredential ->
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = false,
                    )

                UnlockGatePhase.UnlockGateVisible,
                UnlockGatePhase.Dismissing,
                ->
                    createUnlockGateLayoutParams(
                        title = UNLOCK_GATE_WINDOW_TITLE,
                        topInsetPx = currentUnlockGateTopInsetPx(),
                    )

                UnlockGatePhase.Idle -> return true
            }

        return if (!view.isAttachedToWindow) {
            try {
                windowManager.addView(view, layoutParams)
                true
            } catch (exception: Exception) {
                Log.e(TAG, "renderUnlockGateStateOnMain: addView failed", exception)
                unlockGateView = null
                unlockGateStateMachine.forceIdle(clearRepeatedHomeGateEligibility = false)
                cancelUnlockGateCallbacks()
                stopUnlockGateStatusBarMonitors()
                publishUnlockGateState()
                false
            }
        } else {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (exception: Exception) {
                Log.e(TAG, "renderUnlockGateStateOnMain: updateViewLayout failed", exception)
            }
            true
        }
    }

    private fun updateUnlockGateVisibleMode(
        view: View,
        isDark: Boolean,
    ) {
        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        restoreUnlockGatePrimaryContent(view)
        setUnlockGatePatternGridVisible(view, visible = false)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener(null)
        view.findViewById<TextView>(R.id.unlockGateDate).setOnClickListener {
            performAppTapHapticFeedback(this@ActionService)
            if (dispatchLockscreenDateTap()) {
                dispatchUnlockGateEventOnMain(
                    UnlockGateEvent.DismissRequested(
                        nowUptimeMs = SystemClock.uptimeMillis(),
                        minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
                    ),
                )
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
                dispatchUnlockGateEventOnMain(
                    UnlockGateEvent.DismissRequested(
                        nowUptimeMs = SystemClock.uptimeMillis(),
                        minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
                    ),
                )
            }
        }
    }

    private fun updateUnlockGateSecureMode(
        view: View,
        isDark: Boolean,
        blank: Boolean,
    ) {
        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        if (blank) {
            hideUnlockGatePrimaryContent(view)
        } else {
            restoreUnlockGatePrimaryContent(view)
        }
        setUnlockGatePatternGridVisible(view, visible = false)
        view.isClickable = !blank
        view.isFocusable = !blank
        view.setOnClickListener(
            if (blank) {
                null
            } else {
                View.OnClickListener {
                    dismissSecureLockMaskWithGestureOnMain()
                }
            },
        )
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
    }

    private fun updateUnlockGateAwaitingCredentialMode(
        view: View,
        isDark: Boolean,
    ) {
        view.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        hideUnlockGatePrimaryContent(view)
        setUnlockGatePatternGridVisible(view, visible = true)
        updateUnlockGatePatternGridAppearance(view, isDark)
        view.isClickable = false
        view.isFocusable = false
        view.setOnClickListener(null)
    }

    private fun hideUnlockGatePrimaryContent(
        view: View,
    ) {
        view.findViewById<View>(R.id.unlockGateStatusBar).visibility = View.GONE
        view.findViewById<TextView>(R.id.unlockGateClock).visibility = View.GONE
        view.findViewById<TextView>(R.id.unlockGateDate).visibility = View.GONE
        view.findViewById<View>(R.id.unlockGateHomeButton).visibility = View.GONE
    }

    private fun restoreUnlockGatePrimaryContent(view: View) {
        view.findViewById<TextView>(R.id.unlockGateClock).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.unlockGateDate).visibility =
            if (prefs.lockscreenDateEnabled) {
                View.VISIBLE
            } else {
                View.GONE
            }
        view.findViewById<View>(R.id.unlockGateHomeButton).visibility = View.VISIBLE
    }

    private fun setUnlockGatePatternGridVisible(
        view: View,
        visible: Boolean,
    ) {
        view.findViewById<View>(R.id.unlockGatePatternGrid).visibility =
            if (visible) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun updateUnlockGatePatternGridAppearance(
        view: View,
        isDark: Boolean,
    ) {
        val grid = view.findViewById<LinearLayout>(R.id.unlockGatePatternGrid)
        val dotBackground = createUnlockGatePatternDotBackground(isDark)
        for (rowIndex in 0 until grid.childCount) {
            val row = grid.getChildAt(rowIndex) as? LinearLayout ?: continue
            for (columnIndex in 0 until row.childCount) {
                row.getChildAt(columnIndex).background = dotBackground.constantState?.newDrawable()?.mutate() ?: createUnlockGatePatternDotBackground(isDark)
            }
        }
    }

    private fun removeUnlockGateViewOnMain() {
        val view = unlockGateView ?: return
        view.animate().setListener(null)
        view.animate().cancel()
        resetUnlockGateViewState(view)
        if (!view.isAttachedToWindow) {
            unlockGateView = null
            return
        }

        try {
            windowManager.removeView(view)
        } catch (exception: Exception) {
            Log.e(TAG, "removeUnlockGateViewOnMain: removeView failed", exception)
        } finally {
            unlockGateView = null
        }
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

    private fun currentUnlockGateTopInsetPx(): Int =
        unlockGateStateMachine.state.let { state ->
            if (state.prefersHomeStatusBar) {
                state.homeContentTopPx.coerceAtLeast(0)
            } else {
                0
            }
        }

    private fun currentAppliedUnlockGateTopInsetPx(): Int {
        val layoutParams = unlockGateView?.layoutParams as? WindowManager.LayoutParams
        if (unlockGateView?.isAttachedToWindow == true && layoutParams != null) {
            return layoutParams.y.coerceAtLeast(0)
        }
        return currentUnlockGateTopInsetPx()
    }

    private fun shouldHoldUnlockGateInsetDuringDismiss(): Boolean =
        unlockGateView?.isAttachedToWindow == true &&
            unlockGateStateMachine.state.phase == UnlockGatePhase.Dismissing &&
            unlockGateStateMachine.state.prefersHomeStatusBar

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
        val phase = unlockGateStateMachine.state.phase
        val enabled =
            (phase == UnlockGatePhase.UnlockGateVisible || phase == UnlockGatePhase.Dismissing) &&
                canHandleStatusBarSectionTap(section)
        target.isClickable = enabled
        target.isFocusable = enabled
        target.setOnClickListener(
            if (enabled) {
                View.OnClickListener {
                    performStatusBarPressHapticFeedback(this)
                    if (dispatchStatusBarSectionTap(section)) {
                        dispatchUnlockGateEventOnMain(
                            UnlockGateEvent.DismissRequested(
                                nowUptimeMs = SystemClock.uptimeMillis(),
                                minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
                            ),
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    private fun publishUnlockGateState() {
        val snapshot = unlockGateStateMachine.snapshot
        _unlockGateState.value = snapshot
        _unlockGateVisible.value = snapshot.visible
        _unlockGateShowingHomeStatusBar.value = snapshot.showingHomeStatusBar
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
        val phase = unlockGateStateMachine.state.phase
        val isInteractive =
            phase == UnlockGatePhase.UnlockGateVisible || phase == UnlockGatePhase.Dismissing
        val clockView = view.findViewById<TextView>(R.id.unlockGateClock)
        val dateView = view.findViewById<TextView>(R.id.unlockGateDate)

        clockView.text =
            formatClockText(
                prefs = prefs,
                appendNotificationIndicator = prefs.lockscreenClockNotificationIndicator,
            )
        if (phase == UnlockGatePhase.AwaitingCredential) {
            clockView.visibility = View.GONE
            dateView.visibility = View.GONE
            return
        }

        if (clockView.visibility == View.GONE) {
            clockView.visibility = View.VISIBLE
        }

        dateView.apply {
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
        unlockGateStateMachine.snapshot.visible &&
            unlockGateStateMachine.state.phase != UnlockGatePhase.AwaitingCredential &&
            !unlockGateStateMachine.state.prefersHomeStatusBar &&
            prefs.isStatusBarVisibleOnLockscreen()

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

    private fun createUnlockGatePatternDotBackground(isDark: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isDark) Color.WHITE else Color.BLACK)
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
        private const val SECURE_LOCK_MASK_GESTURE_DURATION_MS = 320L
        private const val SECURE_LOCK_MASK_GESTURE_START_Y_RATIO = 0.90f
        private const val SECURE_LOCK_MASK_GESTURE_END_Y_RATIO = 0.14f
        private const val SECURE_LOCK_MASK_GESTURE_MAX_RETRIES = 1

        private var instance: WeakReference<ActionService> = WeakReference(null)
        private val _unlockGateState = MutableStateFlow(UnlockGateUiSnapshot())
        val unlockGateState: StateFlow<UnlockGateUiSnapshot> = _unlockGateState.asStateFlow()
        private val _unlockGateVisible = MutableStateFlow(false)
        val unlockGateVisible: StateFlow<Boolean> = _unlockGateVisible.asStateFlow()
        private val _unlockGateShowingHomeStatusBar = MutableStateFlow(false)
        val unlockGateShowingHomeStatusBar: StateFlow<Boolean> = _unlockGateShowingHomeStatusBar.asStateFlow()

        fun instance(): ActionService? = instance.get()
    }
}
