package com.vandam.luma.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.vandam.luma.MainActivity
import com.vandam.luma.R
import com.vandam.luma.data.AppEntryType
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants.Action
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.data.Tool
import com.vandam.luma.listener.SwipeTouchListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private data class UnlockGateCellularSnapshot(
    val serviceState: Int = ServiceState.STATE_OUT_OF_SERVICE,
    val signalLevel: Int? = null,
    val networkType: Int? = null,
)

class ActionService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val keyguardManager by lazy { getSystemService(KEYGUARD_SERVICE) as KeyguardManager }
    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
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
    private var incomingCallCallback: IncomingCallCallback? = null
    private var unlockGateCellularSnapshot = UnlockGateCellularSnapshot()
    private var unlockGateVolumeIndicatorVisible = false
    private var unlockGateVolumeIndicatorLabelRes = R.string.volume_indicator_ringer
    private var unlockGateVolumeIndicatorProgress = 0f
    private var unlockGateVolumeIndicatorHideRunnable: Runnable? = null
    private var volumeOnlyOverlayView: View? = null
    private var volumeOnlyOverlayHideRunnable: Runnable? = null
    private var secureLockMaskGestureAttempt = 0
    private val consumedMappedKeyUps = mutableSetOf<Int>()
    private var lastWriteSettingsPermissionPromptUptimeMs = 0L
    private var currentForegroundPackage: String? = null
    private var lastForegroundPackage: String? = null
    private var torchCameraId: String? = null
    private var torchEnabled = false
    private var cameraAvailabilityCallback: CameraManager.AvailabilityCallback? = null
    private var torchCallback: CameraManager.TorchCallback? = null
    private val unavailableCameraIds = mutableSetOf<String>()
    private var trackedCameraIds = emptySet<String>()
    private val activeCameraOwnerPackages = mutableMapOf<String, String>()
    private var pendingCameraOwnerPackage: String? = null
    private var pendingCameraOwnerExpiryUptimeMs = 0L
    private var cameraKeyDownTime = 0L
    private var scrollwheelKeyDownTime = 0L
    private var cameraLongPressFired = false
    private var cameraKeyPassThroughActive = false
    private var cameraKeyPassThroughInterrupted = false
    private var scrollwheelButtonPassThroughActive = false
    private var scrollwheelButtonPassThroughInterrupted = false
    private var scrollwheelLongPressFired = false
    private var homeKeyDownTime = 0L
    private var homeLongPressFired = false
    private var mediaInfoObserverJob: Job? = null

    override fun onServiceConnected() {
        configureServiceInfo()
        registerUnlockGateReceiver()
        registerCameraAvailabilityCallback()
        registerTorchCallback()
        MediaSessionHelper.init(this)
        startMediaInfoObserver()
        instance = WeakReference(this)
        publishUnlockGateState()
        startIncomingCallMonitor()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        consumedMappedKeyUps.clear()
        mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
        mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
        mainHandler.removeCallbacksAndMessages(HOME_LONG_PRESS_TOKEN)
        cameraKeyPassThroughActive = false
        cameraKeyPassThroughInterrupted = false
        scrollwheelButtonPassThroughActive = false
        scrollwheelButtonPassThroughInterrupted = false
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        hideVolumeOnlyOverlay()
        secureLockMaskGestureAttempt = 0
        unregisterCameraAvailabilityCallback()
        unregisterTorchCallback()
        unregisterUnlockGateReceiver()
        stopMediaInfoObserver()
        stopIncomingCallMonitor()
        instance = WeakReference(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        consumedMappedKeyUps.clear()
        mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
        mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
        mainHandler.removeCallbacksAndMessages(HOME_LONG_PRESS_TOKEN)
        cameraKeyPassThroughActive = false
        cameraKeyPassThroughInterrupted = false
        scrollwheelButtonPassThroughActive = false
        scrollwheelButtonPassThroughInterrupted = false
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain()
        hideVolumeOnlyOverlay()
        secureLockMaskGestureAttempt = 0
        unregisterCameraAvailabilityCallback()
        unregisterTorchCallback()
        unregisterUnlockGateReceiver()
        stopMediaInfoObserver()
        stopIncomingCallMonitor()
        instance = WeakReference(null)
        super.onDestroy()
    }

    private fun startMediaInfoObserver() {
        mediaInfoObserverJob?.cancel()
        mediaInfoObserverJob =
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                merge(
                    MediaSessionHelper.mediaInfo.map { Unit },
                    LumaNotificationListener.changeVersion.map { Unit },
                    PhoneSignalHelper.changeVersion.map { Unit },
                ).collect {
                    val view = unlockGateView ?: return@collect
                    if (!view.isAttachedToWindow) return@collect
                    updateUnlockGateText(view)
                }
            }
    }

    private fun stopMediaInfoObserver() {
        mediaInfoObserverJob?.cancel()
        mediaInfoObserverJob = null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

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

    fun restoreUnlockGate() {
        runOnMainThread {
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.RestoreRequested(
                    nowUptimeMs = SystemClock.uptimeMillis(),
                ),
            )
        }
    }

    fun showUnlockGateVolumeIndicator(
        labelRes: Int,
        progress: Float,
    ) {
        runOnMainThread {
            unlockGateVolumeIndicatorVisible = true
            unlockGateVolumeIndicatorLabelRes = labelRes
            unlockGateVolumeIndicatorProgress = progress.coerceIn(0f, 1f)
            unlockGateView?.let { view ->
                updateSecureLockMaskStatusBar(view)
                applyUnlockGateVolumeIndicator(view)
            }
            unlockGateVolumeIndicatorHideRunnable?.let { mainHandler.removeCallbacks(it) }
            val hideRunnable = Runnable { hideUnlockGateVolumeIndicator() }
            unlockGateVolumeIndicatorHideRunnable = hideRunnable
            mainHandler.postDelayed(hideRunnable, VOLUME_INDICATOR_HIDE_DELAY_MS)
        }
    }

    fun hideUnlockGateVolumeIndicator() {
        runOnMainThread {
            if (!unlockGateVolumeIndicatorVisible) return@runOnMainThread
            unlockGateVolumeIndicatorVisible = false
            unlockGateVolumeIndicatorHideRunnable?.let { mainHandler.removeCallbacks(it) }
            unlockGateVolumeIndicatorHideRunnable = null
            unlockGateView?.let { view ->
                updateSecureLockMaskStatusBar(view)
                applyUnlockGateVolumeIndicator(view)
            }
        }
    }

    fun isUnlockGateShowingHomeStatusBar(): Boolean = unlockGateStateMachine.snapshot.showingHomeStatusBar

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val event = event ?: return
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        runOnMainThread {
            if (
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                if (packageName != this.packageName) {
                    if (packageName != currentForegroundPackage && currentForegroundPackage != null) {
                        lastForegroundPackage = currentForegroundPackage
                    }
                    currentForegroundPackage = packageName
                } else if (MainActivity.isLumaForeground()) {
                    if (currentForegroundPackage != null) {
                        lastForegroundPackage = currentForegroundPackage
                    }
                }
                if (packageName != this.packageName) {
                    clearPendingCameraOwner()
                }
            }

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
        consumedMappedKeyUps.clear()
        mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
        mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
        if (cameraKeyPassThroughActive) {
            cameraKeyPassThroughInterrupted = true
        }
        if (scrollwheelButtonPassThroughActive) {
            scrollwheelButtonPassThroughInterrupted = true
        }
        cameraKeyPassThroughActive = false
        scrollwheelButtonPassThroughActive = false
        cancelToolLaunchMaskOnMain()
        cancelUnlockGateOnMain(clearRepeatedHomeGateEligibility = true)
        hideVolumeOnlyOverlay()
        secureLockMaskGestureAttempt = 0
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && consumedMappedKeyUps.remove(event.keyCode)) {
            return true
        }

        if (handleScrollwheelBrightnessKeyEvent(event)) {
            return true
        }

        if (handleScrollwheelButtonKeyEvent(event)) {
            return true
        }

        if (handleCameraKeyEvent(event)) {
            return true
        }

        if (handleVolumeKeyEvent(event)) {
            return true
        }

        if (event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        return handleHomeKeyEvent(event)
    }

    private fun handleHomeKeyEvent(event: KeyEvent): Boolean {
        val unlockGatePhase = unlockGateStateMachine.state.phase
        if (unlockGatePhase != UnlockGatePhase.Idle) {
            if (event.repeatCount != 0) {
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                consumedMappedKeyUps.remove(KeyEvent.KEYCODE_HOME)
                mainHandler.removeCallbacksAndMessages(HOME_LONG_PRESS_TOKEN)
                homeKeyDownTime = SystemClock.uptimeMillis()
                homeLongPressFired = false

                val downTime = homeKeyDownTime
                val runnable =
                    Runnable {
                        if (homeKeyDownTime == downTime) {
                            if (isInCall()) {
                                launchLightOsRoute(this@ActionService, "call")
                            } else {
                                val mediaPkg = MediaSessionHelper.getActiveMediaPackageName(forceRefresh = true)
                                if (mediaPkg != null) {
                                    val targetPkg = resolveMediaAppPackage(mediaPkg)
                                    openAppByPackage(targetPkg)
                                } else if (isLumaForeground()) {
                                    openLastUsedApp()
                                } else {
                                    launchLumaHome(suppressLauncherIntentHandling = true)
                                }
                            }
                            dispatchUnlockGateEventOnMain(
                                UnlockGateEvent.DismissRequested(
                                    nowUptimeMs = SystemClock.uptimeMillis(),
                                    minDelayMs = 150L,
                                ),
                            )
                            homeLongPressFired = true
                        }
                    }
                mainHandler.postDelayed(runnable, HOME_LONG_PRESS_TOKEN, KEYMAP_LONG_PRESS_MS)
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                mainHandler.removeCallbacksAndMessages(HOME_LONG_PRESS_TOKEN)
                homeKeyDownTime = 0L
                if (homeLongPressFired) {
                    return true
                }
            }

            if (unlockGatePhase == UnlockGatePhase.SecureMask) {
                if (event.action == KeyEvent.ACTION_UP) {
                    consumedMappedKeyUps.add(KeyEvent.KEYCODE_HOME)
                    runOnMainThread {
                        dismissSecureLockMaskWithGestureOnMain()
                    }
                }
                return true
            }
            if (unlockGatePhase == UnlockGatePhase.AwaitingCredential) {
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                // The gate can finish dismissing before HOME key up arrives. Remember to
                // consume that key up so the default launcher does not win the race.
                consumedMappedKeyUps.add(KeyEvent.KEYCODE_HOME)
                runOnMainThread {
                    bringLumaToFrontUnderUnlockGate()
                    dispatchUnlockGateEventOnMain(
                        UnlockGateEvent.DismissRequested(
                            nowUptimeMs = SystemClock.uptimeMillis(),
                            minDelayMs = UNLOCK_GATE_HIDE_DELAY_MS,
                        ),
                    )
                }
                return true
            }
            return true
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount != 0) return true
                consumedMappedKeyUps.remove(KeyEvent.KEYCODE_HOME)
                mainHandler.removeCallbacksAndMessages(HOME_LONG_PRESS_TOKEN)
                homeKeyDownTime = SystemClock.uptimeMillis()
                homeLongPressFired = false

                val downTime = homeKeyDownTime
                val runnable =
                    Runnable {
                        if (homeKeyDownTime == downTime) {
                            if (isInCall()) {
                                launchLightOsRoute(this@ActionService, "call")
                            } else {
                                val mediaPkg = MediaSessionHelper.getActiveMediaPackageName(forceRefresh = true)
                                if (mediaPkg != null) {
                                    val targetPkg = resolveMediaAppPackage(mediaPkg)
                                    openAppByPackage(targetPkg)
                                } else if (isLumaForeground()) {
                                    openLastUsedApp()
                                } else {
                                    launchLumaHome(suppressLauncherIntentHandling = true)
                                }
                            }
                            homeLongPressFired = true
                        }
                    }
                mainHandler.postDelayed(runnable, HOME_LONG_PRESS_TOKEN, KEYMAP_LONG_PRESS_MS)
                true
            }

            KeyEvent.ACTION_UP -> {
                mainHandler.removeCallbacksAndMessages(HOME_LONG_PRESS_TOKEN)
                homeKeyDownTime = 0L
                if (!homeLongPressFired) {
                    if (unlockGateStateMachine.state.repeatedHomeGateEligible) {
                        runOnMainThread {
                            dispatchUnlockGateEventOnMain(
                                UnlockGateEvent.HomeKeyDown(
                                    nowUptimeMs = SystemClock.uptimeMillis(),
                                    gateEnabled = true,
                                ),
                            )
                        }
                    } else if (shouldHandleLightHomeFix()) {
                        consumedMappedKeyUps.add(KeyEvent.KEYCODE_HOME)
                        runOnMainThread {
                            launchLumaHomeFromShortcut()
                        }
                    } else {
                        runOnMainThread {
                            dispatchUnlockGateEventOnMain(
                                UnlockGateEvent.HomeKeyDown(
                                    nowUptimeMs = SystemClock.uptimeMillis(),
                                    gateEnabled = true,
                                ),
                            )
                        }
                    }
                }
                true
            }

            else -> {
                false
            }
        }
    }

    private fun handleCameraKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != CAMERA_KEY_CODE) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            cameraKeyPassThroughInterrupted = false
        }

        if (cameraKeyPassThroughActive) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    false
                }

                KeyEvent.ACTION_UP -> {
                    mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
                    cameraKeyDownTime = 0L
                    cameraLongPressFired = false
                    cameraKeyPassThroughActive = false
                    cameraKeyPassThroughInterrupted = false
                    false
                }

                else -> {
                    false
                }
            }
        }

        if (cameraKeyPassThroughInterrupted && event.action == KeyEvent.ACTION_UP) {
            mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
            cameraKeyDownTime = 0L
            cameraLongPressFired = false
            cameraKeyPassThroughInterrupted = false
            return true
        }

        val pressAction = prefs.getCameraKeyPressAction()
        val longPressAction = prefs.getCameraKeyLongPressAction()

        if (pressAction == Action.Disabled && longPressAction == Action.Disabled) return false

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            shouldPassThroughCameraKey(pressAction, longPressAction)
        ) {
            consumedMappedKeyUps.remove(CAMERA_KEY_CODE)
            mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
            cameraKeyDownTime = 0L
            cameraLongPressFired = false
            cameraKeyPassThroughActive = true
            cameraKeyPassThroughInterrupted = false
            return false
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount != 0) return true
                consumedMappedKeyUps.remove(CAMERA_KEY_CODE)
                mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
                cameraKeyDownTime = SystemClock.uptimeMillis()
                cameraLongPressFired = false

                if (longPressAction != Action.Disabled) {
                    val downTime = cameraKeyDownTime
                    val runnable =
                        Runnable {
                            if (cameraKeyDownTime == downTime) {
                                executeKeymapAction(longPressAction, { prefs.getCameraKeyLongPressApp() }, CAMERA_KEY_CODE)
                                cameraLongPressFired = true
                            }
                        }
                    mainHandler.postAtTime(runnable, CAMERA_KEY_CODE, downTime + KEYMAP_LONG_PRESS_MS)
                }
                true
            }

            KeyEvent.ACTION_UP -> {
                mainHandler.removeCallbacksAndMessages(CAMERA_KEY_CODE)
                cameraKeyDownTime = 0L
                if (!cameraLongPressFired && pressAction != Action.Disabled) {
                    executeKeymapAction(pressAction, { prefs.getCameraKeyPressApp() }, CAMERA_KEY_CODE)
                } else {
                    true
                }
            }

            else -> {
                false
            }
        }
    }

    private fun handleScrollwheelButtonKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != SCROLLWHEEL_BUTTON_KEY_CODE) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            scrollwheelButtonPassThroughInterrupted = false
        }

        if (scrollwheelButtonPassThroughActive) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    false
                }

                KeyEvent.ACTION_UP -> {
                    mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
                    scrollwheelKeyDownTime = 0L
                    scrollwheelLongPressFired = false
                    scrollwheelButtonPassThroughActive = false
                    scrollwheelButtonPassThroughInterrupted = false
                    false
                }

                else -> {
                    false
                }
            }
        }

        if (scrollwheelButtonPassThroughInterrupted && event.action == KeyEvent.ACTION_UP) {
            mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
            scrollwheelKeyDownTime = 0L
            scrollwheelLongPressFired = false
            scrollwheelButtonPassThroughInterrupted = false
            return true
        }

        val pressAction = prefs.getScrollwheelButtonPressAction()
        val longPressAction = prefs.getScrollwheelButtonLongPressAction()

        if (pressAction == Action.Disabled && longPressAction == Action.Disabled) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount != 0) return true
                if (isKeyTargetForeground(pressAction, { prefs.getScrollwheelButtonPressApp() })) {
                    consumedMappedKeyUps.remove(SCROLLWHEEL_BUTTON_KEY_CODE)
                    mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
                    scrollwheelKeyDownTime = 0L
                    scrollwheelLongPressFired = false
                    scrollwheelButtonPassThroughActive = true
                    scrollwheelButtonPassThroughInterrupted = false
                    return false
                }
                consumedMappedKeyUps.remove(SCROLLWHEEL_BUTTON_KEY_CODE)
                mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
                scrollwheelKeyDownTime = SystemClock.uptimeMillis()
                scrollwheelLongPressFired = false

                if (longPressAction != Action.Disabled) {
                    val downTime = scrollwheelKeyDownTime
                    val runnable =
                        Runnable {
                            if (scrollwheelKeyDownTime == downTime) {
                                executeKeymapAction(
                                    longPressAction,
                                    { prefs.getScrollwheelButtonLongPressApp() },
                                    SCROLLWHEEL_BUTTON_KEY_CODE,
                                )
                                scrollwheelLongPressFired = true
                            }
                        }
                    mainHandler.postAtTime(runnable, SCROLLWHEEL_BUTTON_KEY_CODE, downTime + KEYMAP_LONG_PRESS_MS)
                }
                true
            }

            KeyEvent.ACTION_UP -> {
                mainHandler.removeCallbacksAndMessages(SCROLLWHEEL_BUTTON_KEY_CODE)
                scrollwheelKeyDownTime = 0L
                if (!scrollwheelLongPressFired && pressAction != Action.Disabled) {
                    executeKeymapAction(pressAction, { prefs.getScrollwheelButtonPressApp() }, SCROLLWHEEL_BUTTON_KEY_CODE)
                } else {
                    true
                }
            }

            else -> {
                false
            }
        }
    }

    private fun handleScrollwheelBrightnessKeyEvent(event: KeyEvent): Boolean {
        if (!prefs.scrollwheelBrightnessEnabled) return false

        val brightnessDelta =
            when (event.keyCode) {
                SCROLLWHEEL_BRIGHTNESS_UP_KEY_CODE -> BRIGHTNESS_STEP
                SCROLLWHEEL_BRIGHTNESS_DOWN_KEY_CODE -> -BRIGHTNESS_STEP
                else -> return false
            }

        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount != 0) return true

        val handled = adjustBrightness(brightnessDelta)
        if (handled) {
            consumedMappedKeyUps.add(event.keyCode)
        }
        return handled
    }

    private fun handleVolumeKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        if (event.action != KeyEvent.ACTION_DOWN) return false

        val phase = unlockGateStateMachine.state.phase
        if (phase == UnlockGatePhase.Idle && currentForegroundPackage == ZERO_PACKAGE) return false

        if (phase == UnlockGatePhase.Idle && !isLumaForeground() && currentForegroundPackage != LIGHT_OS_PACKAGE &&
            currentForegroundPackage != packageName
        ) {
            val isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val isMusicActive = audioManager.isMusicActive
            val labelRes: Int
            val progress: Float

            if (isMusicActive) {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val targetVolume =
                    if (isVolumeUp) {
                        (currentVolume + 1).coerceAtMost(maxVolume)
                    } else {
                        (currentVolume - 1).coerceAtLeast(0)
                    }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                labelRes = R.string.volume_indicator_media
                progress = if (maxVolume <= 0) 0f else targetVolume.toFloat() / maxVolume.toFloat()
            } else {
                val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)

                when (audioManager.ringerMode) {
                    AudioManager.RINGER_MODE_VIBRATE -> {
                        if (isVolumeUp) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0)
                            labelRes = R.string.volume_indicator_ringer
                            progress = 1f / maxRingVolume
                        } else {
                            if (notificationManager.isNotificationPolicyAccessGranted) {
                                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                            }
                            labelRes =
                                if (notificationManager.isNotificationPolicyAccessGranted) {
                                    R.string.volume_indicator_silent
                                } else {
                                    R.string.volume_indicator_vibrate
                                }
                            progress = 0f
                        }
                    }

                    AudioManager.RINGER_MODE_SILENT -> {
                        if (isVolumeUp) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                            labelRes = R.string.volume_indicator_vibrate
                        } else {
                            labelRes = R.string.volume_indicator_silent
                        }
                        progress = 0f
                    }

                    else -> {
                        val targetVolume =
                            if (isVolumeUp) {
                                if (currentVolume == 0) 1 else (currentVolume + 1).coerceAtMost(maxRingVolume)
                            } else {
                                if (currentVolume > 1) currentVolume - 1 else 0
                            }
                        if (targetVolume == 0 && !isVolumeUp) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                            labelRes = R.string.volume_indicator_vibrate
                            progress = 0f
                        } else {
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVolume, 0)
                            labelRes = R.string.volume_indicator_ringer
                            progress = targetVolume.toFloat() / maxRingVolume.toFloat()
                        }
                    }
                }
            }

            showVolumeOnlyOverlay(labelRes, progress)
            return true
        }

        if (phase != UnlockGatePhase.SecureMask && phase != UnlockGatePhase.AwaitingCredential) return false

        val isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isMusicActive = audioManager.isMusicActive

        if (isMusicActive) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetVolume =
                if (isVolumeUp) {
                    (currentVolume + 1).coerceAtMost(maxVolume)
                } else {
                    (currentVolume - 1).coerceAtLeast(0)
                }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            val progress = if (maxVolume <= 0) 0f else targetVolume.toFloat() / maxVolume.toFloat()
            showUnlockGateVolumeIndicator(R.string.volume_indicator_media, progress)
        } else {
            val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)

            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> {
                    if (isVolumeUp) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0)
                        showUnlockGateVolumeIndicator(R.string.volume_indicator_ringer, 1f / maxRingVolume)
                    } else {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                            showUnlockGateVolumeIndicator(R.string.volume_indicator_silent, 0f)
                        } else {
                            showUnlockGateVolumeIndicator(R.string.volume_indicator_vibrate, 0f)
                        }
                    }
                }

                AudioManager.RINGER_MODE_SILENT -> {
                    if (isVolumeUp) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        showUnlockGateVolumeIndicator(R.string.volume_indicator_vibrate, 0f)
                    } else {
                        showUnlockGateVolumeIndicator(R.string.volume_indicator_silent, 0f)
                    }
                }

                else -> {
                    val targetVolume =
                        if (isVolumeUp) {
                            if (currentVolume == 0) 1 else (currentVolume + 1).coerceAtMost(maxRingVolume)
                        } else {
                            if (currentVolume > 1) {
                                currentVolume - 1
                            } else {
                                0
                            }
                        }
                    if (targetVolume == 0 && !isVolumeUp) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        showUnlockGateVolumeIndicator(R.string.volume_indicator_vibrate, 0f)
                    } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVolume, 0)
                        val progress = targetVolume.toFloat() / maxRingVolume.toFloat()
                        showUnlockGateVolumeIndicator(R.string.volume_indicator_ringer, progress)
                    }
                }
            }
        }
        return true
    }

    private fun adjustBrightness(delta: Int): Boolean {
        if (!Settings.System.canWrite(this)) {
            promptForWriteSettingsPermission()
            return true
        }

        val currentBrightness =
            runCatching {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrDefault(DEFAULT_BRIGHTNESS)
        val targetBrightness = (currentBrightness + delta).coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

        return try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
        } catch (exception: Exception) {
            Log.e(TAG, "adjustBrightness: write failed", exception)
            false
        }
    }

    private fun promptForWriteSettingsPermission() {
        val now = SystemClock.uptimeMillis()
        if (now - lastWriteSettingsPermissionPromptUptimeMs < WRITE_SETTINGS_PROMPT_COOLDOWN_MS) return
        lastWriteSettingsPermissionPromptUptimeMs = now

        showToast(this, getString(R.string.toast_brightness_permission_required))

        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (exception: Exception) {
            Log.e(TAG, "promptForWriteSettingsPermission: startActivity failed", exception)
        }
    }

    private fun shouldHandleLightHomeFix(): Boolean =
        !keyguardManager.isDeviceLocked &&
            getDefaultLauncherPackage(this) == LIGHT_OS_PACKAGE

    private fun executeKeymapAction(
        action: Action,
        getApp: () -> AppModel,
        keyCode: Int,
    ): Boolean {
        val handled =
            when (action) {
                Action.OpenApp -> {
                    if (unlockGateStateMachine.state.phase == UnlockGatePhase.SecureMask) {
                        false
                    } else {
                        val appModel = getApp()
                        if (appModel.appPackage.isBlank() || isTargetAppForeground(appModel)) {
                            false
                        } else {
                            rememberPendingCameraOwner(appModel, keyCode)
                            val launched = launchAppModel(this, appModel)
                            if (!launched) clearPendingCameraOwner()
                            if (launched) dismissUnlockGateIfNeeded()
                            launched
                        }
                    }
                }

                else -> {
                    executeSecondaryAction(this, action)
                }
            }
        if (handled) {
            consumedMappedKeyUps.add(keyCode)
            if (prefs.hapticsKeymapsEnabled) {
                performHapticFeedback(this)
            }
        }
        return handled
    }

    private fun registerCameraAvailabilityCallback() {
        val callback =
            object : CameraManager.AvailabilityCallback() {
                override fun onCameraAvailable(cameraId: String) {
                    updateCameraAvailability(cameraId, available = true)
                }

                override fun onCameraUnavailable(cameraId: String) {
                    updateCameraAvailability(cameraId, available = false)
                }
            }
        try {
            trackedCameraIds = cameraManager.cameraIdList.toSet()
            unavailableCameraIds.clear()
            activeCameraOwnerPackages.clear()
            clearPendingCameraOwner()
            cameraManager.registerAvailabilityCallback(callback, mainHandler)
            cameraAvailabilityCallback = callback
        } catch (exception: Exception) {
            trackedCameraIds = emptySet()
            unavailableCameraIds.clear()
            activeCameraOwnerPackages.clear()
            clearPendingCameraOwner()
            Log.e(TAG, "registerCameraAvailabilityCallback: failed", exception)
        }
    }

    private fun unregisterCameraAvailabilityCallback() {
        val callback = cameraAvailabilityCallback ?: run {
            trackedCameraIds = emptySet()
            unavailableCameraIds.clear()
            activeCameraOwnerPackages.clear()
            clearPendingCameraOwner()
            return
        }
        try {
            cameraManager.unregisterAvailabilityCallback(callback)
        } catch (exception: Exception) {
            Log.e(TAG, "unregisterCameraAvailabilityCallback: failed", exception)
        } finally {
            cameraAvailabilityCallback = null
            trackedCameraIds = emptySet()
            unavailableCameraIds.clear()
            activeCameraOwnerPackages.clear()
            clearPendingCameraOwner()
        }
    }

    private fun updateCameraAvailability(
        cameraId: String,
        available: Boolean,
    ) {
        if (!trackedCameraIds.contains(cameraId)) return
        if (available) {
            unavailableCameraIds.remove(cameraId)
            activeCameraOwnerPackages.remove(cameraId)
        } else {
            unavailableCameraIds.add(cameraId)
            val ownerPackage = consumePendingCameraOwnerPackage() ?: currentForegroundCameraOwnerPackage()
            ownerPackage?.let { activeCameraOwnerPackages[cameraId] = it }
        }
    }

    private fun isAnyCameraActive(): Boolean = unavailableCameraIds.isNotEmpty()

    private fun rememberPendingCameraOwner(
        appModel: AppModel,
        keyCode: Int,
    ) {
        if (keyCode != CAMERA_KEY_CODE) return
        pendingCameraOwnerPackage = resolveTargetPackage(appModel)
        pendingCameraOwnerExpiryUptimeMs = SystemClock.uptimeMillis() + CAMERA_OWNER_PENDING_WINDOW_MS
    }

    private fun consumePendingCameraOwnerPackage(): String? {
        val pendingPackage = pendingCameraOwnerPackage
        val expiryUptimeMs = pendingCameraOwnerExpiryUptimeMs
        clearPendingCameraOwner()
        if (pendingPackage.isNullOrBlank()) return null
        return pendingPackage.takeIf { SystemClock.uptimeMillis() <= expiryUptimeMs }
    }

    private fun clearPendingCameraOwner() {
        pendingCameraOwnerPackage = null
        pendingCameraOwnerExpiryUptimeMs = 0L
    }

    private fun currentForegroundCameraOwnerPackage(): String? =
        if (isLumaForeground()) {
            null
        } else {
            currentForegroundPackage
        }

    private fun shouldPassThroughCameraKey(
        pressAction: Action,
        longPressAction: Action,
    ): Boolean =
        shouldPassThroughToActiveCameraOwner() ||
            isKeyTargetForeground(pressAction, { prefs.getCameraKeyPressApp() }) ||
            isKeyTargetForeground(longPressAction, { prefs.getCameraKeyLongPressApp() })

    private fun shouldPassThroughToActiveCameraOwner(): Boolean {
        if (!isAnyCameraActive()) return false
        val foregroundPackage = currentForegroundCameraOwnerPackage() ?: return false
        if (activeCameraOwnerPackages.isEmpty()) return false
        return activeCameraOwnerPackages.values.any { it == foregroundPackage }
    }

    private fun isLightOsCameraTool(appModel: AppModel): Boolean {
        val tool =
            Tool.fromPackageName(appModel.appPackage) ?: when {
                appModel.appPackage == LIGHT_OS_PACKAGE || appModel.entryType == AppEntryType.Tool -> Tool.fromId(appModel.appActivityName)
                else -> null
            }
        return tool == Tool.Camera
    }

    private fun isLightOsForeground(): Boolean = !isLumaForeground() && currentForegroundPackage == LIGHT_OS_PACKAGE

    private fun registerTorchCallback() {
        val callback =
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(
                    cameraId: String,
                    enabled: Boolean,
                ) {
                    if (cameraId == resolveTorchCameraId()) {
                        torchEnabled = enabled
                    }
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    if (cameraId == resolveTorchCameraId()) {
                        torchEnabled = false
                    }
                }
            }
        try {
            cameraManager.registerTorchCallback(callback, mainHandler)
            torchCallback = callback
        } catch (exception: Exception) {
            Log.e(TAG, "registerTorchCallback: failed", exception)
        }
    }

    private fun unregisterTorchCallback() {
        val callback = torchCallback ?: return
        try {
            cameraManager.unregisterTorchCallback(callback)
        } catch (exception: Exception) {
            Log.e(TAG, "unregisterTorchCallback: failed", exception)
        } finally {
            torchCallback = null
        }
    }

    private fun resolveTorchCameraId(): String? {
        torchCameraId?.let { return it }
        val resolved =
            runCatching {
                cameraManager.cameraIdList.firstOrNull { cameraId ->
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }.getOrNull()
        torchCameraId = resolved
        return resolved
    }

    fun toggleFlashlight(): Boolean {
        val cameraId = resolveTorchCameraId() ?: return false
        return try {
            val enabled = !torchEnabled
            cameraManager.setTorchMode(cameraId, enabled)
            torchEnabled = enabled
            true
        } catch (exception: CameraAccessException) {
            Log.e(TAG, "toggleFlashlight: camera access failed", exception)
            showToast(this, getString(R.string.toast_unable_to_toggle_flashlight))
            false
        } catch (exception: SecurityException) {
            Log.e(TAG, "toggleFlashlight: security failure", exception)
            showToast(this, getString(R.string.toast_unable_to_toggle_flashlight))
            false
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "toggleFlashlight: invalid camera id", exception)
            showToast(this, getString(R.string.toast_unable_to_toggle_flashlight))
            false
        }
    }

    private fun resolveMediaAppPackage(mediaPkg: String): String =
        when (mediaPkg) {
            "com.spotify.music" -> {
                if (isPackageInstalled("com.vandam.echo")) "com.vandam.echo" else mediaPkg
            }

            else -> {
                mediaPkg
            }
        }

    private fun isPackageInstalled(pkg: String): Boolean =
        try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }

    private fun openLastUsedApp(): Boolean {
        val pkg = lastForegroundPackage ?: return false
        if (pkg == packageName) return false
        return openAppByPackage(pkg)
    }

    private fun openAppByPackage(pkg: String): Boolean {
        if (pkg == packageName) return false
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isInCall(): Boolean =
        try {
            getSystemService(TelecomManager::class.java)?.isInCall == true
        } catch (_: SecurityException) {
            false
        }

    private fun isPhoneRinging(): Boolean =
        try {
            @Suppress("DEPRECATION")
            getSystemService(TelephonyManager::class.java)?.callState == TelephonyManager.CALL_STATE_RINGING
        } catch (_: SecurityException) {
            false
        }

    private fun isKeyTargetForeground(
        action: Action,
        getApp: () -> com.vandam.luma.data.AppModel,
    ): Boolean = action == Action.OpenApp && isTargetAppForeground(getApp())

    private fun resolveTargetPackage(appModel: AppModel): String =
        when {
            appModel.entryType == AppEntryType.Tool || Tool.fromPackageName(appModel.appPackage) != null -> LIGHT_OS_PACKAGE
            else -> appModel.appPackage
        }

    private fun isTargetAppForeground(appModel: com.vandam.luma.data.AppModel): Boolean {
        if (isLightOsCameraTool(appModel)) {
            return isLightOsCameraForeground()
        }
        val targetPackage = resolveTargetPackage(appModel)
        return !isLumaForeground() && currentForegroundPackage == targetPackage
    }

    private fun isLumaForeground(): Boolean = MainActivity.isLumaForeground()

    private fun isLightOsCameraForeground(): Boolean {
        if (!isLightOsForeground()) return false
        if (!isAnyCameraActive()) return false
        if (activeCameraOwnerPackages.isEmpty()) return false
        return activeCameraOwnerPackages.values.any { it == LIGHT_OS_PACKAGE }
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
        unlockGateVolumeIndicatorVisible = false
        unlockGateVolumeIndicatorHideRunnable?.let { mainHandler.removeCallbacks(it) }
        unlockGateVolumeIndicatorHideRunnable = null
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
                        Intent.ACTION_SCREEN_OFF -> {
                            runOnMainThread {
                                dispatchUnlockGateEventOnMain(
                                    UnlockGateEvent.ScreenOff(SystemClock.uptimeMillis()),
                                )
                            }
                        }

                        Intent.ACTION_SCREEN_ON -> {
                            runOnMainThread {
                                dispatchUnlockGateEventOnMain(
                                    UnlockGateEvent.ScreenOn(
                                        nowUptimeMs = SystemClock.uptimeMillis(),
                                        gateEnabled = true,
                                        deviceLocked = keyguardManager.isDeviceLocked,
                                        ringingCall = isPhoneRinging(),
                                    ),
                                )
                            }
                        }

                        Intent.ACTION_USER_PRESENT -> {
                            runOnMainThread {
                                dispatchUnlockGateEventOnMain(
                                    UnlockGateEvent.UserPresent(
                                        nowUptimeMs = SystemClock.uptimeMillis(),
                                        gateEnabled = true,
                                        deviceLocked = keyguardManager.isDeviceLocked,
                                        ringingCall = isPhoneRinging(),
                                    ),
                                )
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

    private fun dispatchUnlockGateEventOnMain(event: UnlockGateEvent) {
        val effects = unlockGateStateMachine.dispatch(event)
        val rendered = renderUnlockGateStateOnMain()
        publishUnlockGateState()
        if (!rendered) return
        effects.forEach(::handleUnlockGateEffectOnMain)
    }

    private fun handleUnlockGateEffectOnMain(effect: UnlockGateEffect) {
        when (effect) {
            UnlockGateEffect.BringLumaToFront -> {
                bringLumaToFrontUnderUnlockGate()
            }

            UnlockGateEffect.CancelDismiss -> {
                cancelUnlockGateDismissCallback()
            }

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
            MediaSessionHelper.setTrackingEnabled(false)
            cancelUnlockGateCallbacks()
            stopUnlockGateStatusBarMonitors()
            removeUnlockGateViewOnMain()
            return true
        }

        hideVolumeOnlyOverlay()

        val isDark = prefs.isDarkTheme()
        val shouldTrackMediaSessions =
            state.phase == UnlockGatePhase.SecureMask ||
                state.phase == UnlockGatePhase.UnlockGateVisible ||
                state.phase == UnlockGatePhase.Dismissing
        MediaSessionHelper.setTrackingEnabled(shouldTrackMediaSessions)
        val view =
            unlockGateView ?: overlayInflater.inflate(R.layout.unlock_gate_overlay, null).also {
                unlockGateView = it
            }

        PhoneSignalHelper.getCachedUnreadPhoneSignal(this)
        resetUnlockGateViewState(view)
        if (shouldTrackMediaSessions) {
            MediaSessionHelper.refresh()
        }
        updateUnlockGateTextAppearance(view, isDark)
        updateSecureLockMaskStatusBar(view)
        applyUnlockGateVolumeIndicator(view)
        updateUnlockGateText(view)
        if (state.phase == UnlockGatePhase.WakeCover) {
            cancelUnlockGateClockTick()
        } else {
            scheduleNextUnlockGateClockTick(view)
        }

        when (state.phase) {
            UnlockGatePhase.WakeCover -> updateUnlockGateWakeCoverMode(view)
            UnlockGatePhase.SecureMask -> updateUnlockGateSecureMode(view, isDark, blank = false)
            UnlockGatePhase.AwaitingCredential -> updateUnlockGateAwaitingCredentialMode(view, isDark)
            UnlockGatePhase.UnlockGateVisible, UnlockGatePhase.Dismissing -> updateUnlockGateVisibleMode(view, isDark)
            UnlockGatePhase.Idle -> Unit
        }

        val layoutParams =
            when (state.phase) {
                UnlockGatePhase.WakeCover -> {
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = false,
                    )
                }

                UnlockGatePhase.SecureMask -> {
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = true,
                    )
                }

                UnlockGatePhase.AwaitingCredential -> {
                    createSecureLockMaskLayoutParams(
                        title = SECURE_LOCK_MASK_WINDOW_TITLE,
                        touchable = false,
                    )
                }

                UnlockGatePhase.UnlockGateVisible,
                UnlockGatePhase.Dismissing,
                -> {
                    createUnlockGateLayoutParams(
                        title = UNLOCK_GATE_WINDOW_TITLE,
                        topInsetPx = currentUnlockGateTopInsetPx(),
                    )
                }

                UnlockGatePhase.Idle -> {
                    return true
                }
            }

        val shouldAddView = !view.isAttachedToWindow && view.windowToken == null && view.parent == null

        val result =
            if (shouldAddView) {
                try {
                    windowManager.addView(view, layoutParams)
                    true
                } catch (exception: Exception) {
                    if (exception is IllegalStateException && exception.message?.contains("already been added") == true) {
                        if (tryUpdateUnlockGateViewLayoutOnMain(view, layoutParams, logPrefix = "renderUnlockGateStateOnMain recover")) {
                            return true
                        }
                    }
                    Log.e(TAG, "renderUnlockGateStateOnMain: addView failed", exception)
                    forceRemoveUnlockGateViewInstanceOnMain(view)
                    unlockGateStateMachine.forceIdle(clearRepeatedHomeGateEligibility = false)
                    cancelUnlockGateCallbacks()
                    stopUnlockGateStatusBarMonitors()
                    publishUnlockGateState()
                    false
                }
            } else {
                if (tryUpdateUnlockGateViewLayoutOnMain(view, layoutParams, logPrefix = "renderUnlockGateStateOnMain")) {
                    true
                } else {
                    forceRemoveUnlockGateViewInstanceOnMain(view)
                    unlockGateStateMachine.forceIdle(clearRepeatedHomeGateEligibility = false)
                    cancelUnlockGateCallbacks()
                    stopUnlockGateStatusBarMonitors()
                    publishUnlockGateState()
                    false
                }
            }
        return result
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
        view.findViewById<TextView>(R.id.unlockGateClock).apply {
            if (prefs.getLockscreenClockTapAction() == Action.Disabled) {
                setOnClickListener(null)
            } else {
                setOnClickListener {
                    if (dispatchLockscreenClockTap()) {
                        dispatchUnlockGateEventOnMain(
                            UnlockGateEvent.DismissRequested(
                                nowUptimeMs = SystemClock.uptimeMillis(),
                                minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
                            ),
                        )
                    }
                }
            }
        }
        view.findViewById<TextView>(R.id.unlockGateDate).setOnClickListener {
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
                if (dispatchLockscreenShortcut()) {
                    dispatchUnlockGateEventOnMain(
                        UnlockGateEvent.DismissRequested(
                            nowUptimeMs = SystemClock.uptimeMillis(),
                            minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
                        ),
                    )
                }
            }
        }
        setupMediaButtonHandlers(view)
        bindUnlockGateGestureListeners(view)
    }

    private fun updateUnlockGateWakeCoverMode(view: View) {
        view.setBackgroundColor(Color.BLACK)
        hideUnlockGatePrimaryContent(view)
        setUnlockGatePatternGridVisible(view, visible = false)
        view.isClickable = false
        view.isFocusable = false
        view.setOnClickListener(null)
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
        view.findViewById<TextView>(R.id.unlockGateClock).apply {
            setOnClickListener(null)
            isClickable = false
            isFocusable = false
        }
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
        if (!blank) {
            setupMediaButtonHandlers(view)
        }
    }

    private fun setupMediaButtonHandlers(view: View) {
        view.findViewById<ImageView>(R.id.unlockGateMediaPlayPause).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performAppTapHapticFeedback(this@ActionService)
                MediaSessionHelper.togglePlayPause()
            }
        }
        view.findViewById<ImageView>(R.id.unlockGateMediaStop).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performAppTapHapticFeedback(this@ActionService)
                MediaSessionHelper.stopAndDismiss()
            }
        }
        view.findViewById<ImageView>(R.id.unlockGateMediaPrev).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performAppTapHapticFeedback(this@ActionService)
                val info = MediaSessionHelper.mediaInfo.value
                if (info?.isPodcast == true) {
                    MediaSessionHelper.rewind()
                } else {
                    MediaSessionHelper.skipToPrevious()
                }
            }
        }
        view.findViewById<ImageView>(R.id.unlockGateMediaNext).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performAppTapHapticFeedback(this@ActionService)
                val info = MediaSessionHelper.mediaInfo.value
                if (info?.isPodcast == true) {
                    MediaSessionHelper.fastForward()
                } else {
                    MediaSessionHelper.skipToNext()
                }
            }
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

    private fun hideUnlockGatePrimaryContent(view: View) {
        view.findViewById<View>(R.id.unlockGateStatusBar).visibility = View.GONE
        view.findViewById<TextView>(R.id.unlockGateClock).visibility = View.GONE
        view.findViewById<TextView>(R.id.unlockGateDate).visibility = View.GONE
        view.findViewById<View>(R.id.unlockGateHomeButton).visibility = View.GONE
        view.findViewById<View>(R.id.unlockGateMediaControls).visibility = View.GONE
    }

    private fun restoreUnlockGatePrimaryContent(view: View) {
        view.findViewById<TextView>(R.id.unlockGateClock).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.unlockGateDate).visibility =
            if (prefs.lockscreenDateFormat != Prefs.LockscreenDateFormat.None) {
                View.VISIBLE
            } else {
                View.GONE
            }
        view.findViewById<View>(R.id.unlockGateHomeButton).visibility = View.VISIBLE
        if (MediaSessionHelper.mediaInfo.value != null) {
            view.findViewById<View>(R.id.unlockGateMediaControls).visibility = View.VISIBLE
        }
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
                row.getChildAt(columnIndex).background =
                    dotBackground.constantState?.newDrawable()?.mutate() ?: createUnlockGatePatternDotBackground(isDark)
            }
        }
    }

    private fun removeUnlockGateViewOnMain() {
        val view = unlockGateView ?: return
        unlockGateVolumeIndicatorVisible = false
        unlockGateVolumeIndicatorHideRunnable?.let { mainHandler.removeCallbacks(it) }
        unlockGateVolumeIndicatorHideRunnable = null
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

    private fun tryUpdateUnlockGateViewLayoutOnMain(
        view: View,
        layoutParams: WindowManager.LayoutParams,
        logPrefix: String,
    ): Boolean =
        try {
            windowManager.updateViewLayout(view, layoutParams)
            true
        } catch (exception: Exception) {
            Log.e(TAG, "$logPrefix: updateViewLayout failed", exception)
            false
        }

    private fun forceRemoveUnlockGateViewInstanceOnMain(view: View) {
        view.animate().setListener(null)
        view.animate().cancel()
        resetUnlockGateViewState(view)
        try {
            windowManager.removeViewImmediate(view)
        } catch (exception: Exception) {
            Log.e(TAG, "forceRemoveUnlockGateViewInstanceOnMain: removeViewImmediate failed", exception)
        } finally {
            if (unlockGateView === view) {
                unlockGateView = null
            }
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
        WindowManager
            .LayoutParams(
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
        WindowManager
            .LayoutParams(
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
        WindowManager
            .LayoutParams(
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

    private fun createVolumeOnlyOverlayLayoutParams(compactPadding: Boolean): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = VOLUME_ONLY_OVERLAY_WINDOW_TITLE
            }
    }

    private fun handleVolumeIndicatorTap() {
        performAppTapHapticFeedback(this)
        val phase = unlockGateStateMachine.state.phase
        if (phase != UnlockGatePhase.Idle) {
            hideUnlockGateVolumeIndicator()
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.DismissRequested(
                    nowUptimeMs = SystemClock.uptimeMillis(),
                    minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
                ),
            )
        } else {
            hideVolumeOnlyOverlay()
        }
        launchLightOsRoute(this, NOTIFICATION_SETTINGS_LIGHT_ROUTE)
    }

    fun showVolumeOnlyOverlay(
        labelRes: Int,
        progress: Float,
        compactPadding: Boolean = false,
    ) {
        runOnMainThread {
            val isDark = prefs.isDarkTheme()
            val backgroundColor = if (isDark) Color.BLACK else Color.WHITE
            val textColor = if (isDark) Color.WHITE else Color.BLACK
            val view =
                volumeOnlyOverlayView ?: overlayInflater.inflate(R.layout.volume_only_overlay, null).also {
                    volumeOnlyOverlayView = it
                }

            val indicator = view as LinearLayout
            indicator.visibility = View.VISIBLE
            indicator.setBackgroundColor(backgroundColor)
            val density = indicator.context.resources.displayMetrics.density
            val paddingBottomPx = if (compactPadding) (2.3f * density).toInt() else (9.3f * density).toInt()
            indicator.setPadding(
                indicator.paddingLeft,
                indicator.paddingTop,
                indicator.paddingRight,
                paddingBottomPx,
            )
            val label = indicator.findViewById<TextView>(R.id.volumeIndicatorLabel)
            label.setText(labelRes)
            label.setTextColor(textColor)
            label.setOnClickListener { handleVolumeIndicatorTap() }
            indicator.findViewById<View>(R.id.volumeIndicatorTrackLine).setBackgroundColor(textColor)
            indicator.findViewById<View>(R.id.volumeIndicatorFill).apply {
                setBackgroundColor(textColor)
                pivotX = 0f
                scaleX = progress.coerceIn(0f, 1f)
            }

            val layoutParams = createVolumeOnlyOverlayLayoutParams(compactPadding)
            if (!view.isAttachedToWindow) {
                try {
                    windowManager.addView(view, layoutParams)
                } catch (_: Exception) {
                }
            } else {
                try {
                    windowManager.updateViewLayout(view, layoutParams)
                } catch (_: Exception) {
                }
            }

            volumeOnlyOverlayHideRunnable?.let { mainHandler.removeCallbacks(it) }
            val hideRunnable = Runnable { hideVolumeOnlyOverlay() }
            volumeOnlyOverlayHideRunnable = hideRunnable
            mainHandler.postDelayed(hideRunnable, VOLUME_INDICATOR_HIDE_DELAY_MS)
        }
    }

    fun hideVolumeOnlyOverlay() {
        runOnMainThread {
            volumeOnlyOverlayHideRunnable?.let { mainHandler.removeCallbacks(it) }
            volumeOnlyOverlayHideRunnable = null
            val view = volumeOnlyOverlayView ?: return@runOnMainThread
            volumeOnlyOverlayView = null
            if (!view.isAttachedToWindow) return@runOnMainThread
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
    }

    fun isVolumeOnlyOverlayVisible(): Boolean = volumeOnlyOverlayView?.isAttachedToWindow == true

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
        launchLumaHome(suppressLauncherIntentHandling = true)
    }

    private fun launchLumaHomeFromShortcut() {
        launchLumaHome(suppressLauncherIntentHandling = false)
    }

    private fun launchLumaHome(suppressLauncherIntentHandling: Boolean) {
        try {
            startActivity(MainActivity.createLumaHomeIntent(this, suppressLauncherIntentHandling))
        } catch (exception: Exception) {
            Log.e(TAG, "launchLumaHome: startActivity failed", exception)
        }
    }

    private fun dispatchLockscreenShortcut(): Boolean =
        try {
            startActivity(MainActivity.createLockscreenShortcutIntent(this))
            true
        } catch (exception: Exception) {
            Log.e(TAG, "dispatchLockscreenShortcut: startActivity failed", exception)
            false
        }

    private fun dispatchLockscreenClockTap(): Boolean {
        if (prefs.getLockscreenClockTapAction() == Action.Disabled) return false
        return try {
            startActivity(MainActivity.createLockscreenClockTapIntent(this))
            true
        } catch (exception: Exception) {
            Log.e(TAG, "dispatchLockscreenClockTap: startActivity failed", exception)
            false
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

    private fun canHandleLockscreenGesture(gestureType: GestureType): Boolean {
        val action = prefs.getGestureAction(gestureType, GestureScope.Lockscreen)
        if (action == Action.Disabled) return false
        if (action == Action.OpenApp && prefs.getGestureApp(gestureType, GestureScope.Lockscreen).appPackage.isEmpty()) return false
        return true
    }

    private fun dispatchLockscreenGesture(gestureType: GestureType): Boolean {
        if (!canHandleLockscreenGesture(gestureType)) return false
        return try {
            startActivity(MainActivity.createLockscreenGestureIntent(this, gestureType))
            true
        } catch (exception: Exception) {
            Log.e(TAG, "dispatchLockscreenGesture: startActivity failed", exception)
            false
        }
    }

    private fun handleUnlockGateGesture(gestureType: GestureType) {
        val phase = unlockGateStateMachine.state.phase
        if (phase != UnlockGatePhase.UnlockGateVisible && phase != UnlockGatePhase.Dismissing) return
        if (!dispatchLockscreenGesture(gestureType)) return
        performGestureActionHapticFeedback(this)
        dispatchUnlockGateEventOnMain(
            UnlockGateEvent.DismissRequested(
                nowUptimeMs = SystemClock.uptimeMillis(),
                minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
            ),
        )
    }

    private fun dismissUnlockGateIfNeeded() {
        val phase = unlockGateStateMachine.state.phase
        if (phase != UnlockGatePhase.UnlockGateVisible && phase != UnlockGatePhase.Dismissing) return
        dispatchUnlockGateEventOnMain(
            UnlockGateEvent.DismissRequested(
                nowUptimeMs = SystemClock.uptimeMillis(),
                minDelayMs = UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS,
            ),
        )
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

    private fun bindUnlockGateGestureListeners(view: View) {
        view.setOnTouchListener(createUnlockGateGestureTouchListener())
        bindUnlockGateGestureTarget(
            view.findViewById(R.id.unlockGateClock),
            preserveSingleTap = true,
            onPress = { if (prefs.getLockscreenClockTapAction() != Action.Disabled) performAppTapHapticFeedback(this) },
        )
        bindUnlockGateGestureTarget(
            view.findViewById(R.id.unlockGateDate),
            preserveSingleTap = true,
            onPress = { if (prefs.getLockscreenDateTapAction() != Action.Disabled) performAppTapHapticFeedback(this) },
        )
        bindUnlockGateGestureTarget(
            view.findViewById(R.id.unlockGateHomeButton),
            preserveSingleTap = true,
            onPress = { performAppTapHapticFeedback(this) },
        )
        bindUnlockGateGestureTarget(view.findViewById(R.id.unlockGateStatusBar))
        bindUnlockGateGestureTarget(
            view.findViewById(R.id.statusConnectivityLayout),
            preserveSingleTap = true,
            onPress = { if (canHandleStatusBarSectionTap(StatusBarSectionType.CELLULAR)) performStatusBarPressHapticFeedback(this) },
        )
        bindUnlockGateGestureTarget(
            view.findViewById(R.id.statusBatteryLayout),
            preserveSingleTap = true,
            onPress = { if (canHandleStatusBarSectionTap(StatusBarSectionType.BATTERY)) performStatusBarPressHapticFeedback(this) },
        )
        bindUnlockGateGestureTarget(view.findViewById(R.id.volumeIndicator))
        bindUnlockGateGestureTarget(
            view.findViewById(R.id.volumeIndicatorLabel),
            preserveSingleTap = true,
            onPress = { performAppTapHapticFeedback(this) },
        )
    }

    private fun bindUnlockGateGestureTarget(
        target: View,
        preserveSingleTap: Boolean = false,
        onPress: (() -> Unit)? = null,
    ) {
        target.setOnTouchListener(createUnlockGateGestureTouchListener(target, preserveSingleTap, onPress))
    }

    private fun createUnlockGateGestureTouchListener(
        target: View? = null,
        preserveSingleTap: Boolean = false,
        onPress: (() -> Unit)? = null,
    ): View.OnTouchListener =
        object : SwipeTouchListener(
            context = this,
            view = target,
            confirmSingleTap = preserveSingleTap,
            consumeTouchEvents = true,
        ) {
            override fun onTouch(
                v: View,
                motionEvent: MotionEvent,
            ): Boolean {
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    onPress?.invoke()
                }
                return super.onTouch(v, motionEvent)
            }

            override fun onSwipeRight() = handleUnlockGateGesture(GestureType.SWIPE_RIGHT)

            override fun onSwipeLeft() = handleUnlockGateGesture(GestureType.SWIPE_LEFT)

            override fun onSwipeUp() = handleUnlockGateGesture(GestureType.SWIPE_UP)

            override fun onSwipeDown() = handleUnlockGateGesture(GestureType.SWIPE_DOWN)

            override fun onDoubleClick() = handleUnlockGateGesture(GestureType.DOUBLE_TAP)

            override fun onClick(view: View) {
                if (preserveSingleTap) {
                    view.performClick()
                }
            }
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
        val iconTint = if (isDark) Color.WHITE else Color.BLACK
        for (id in listOf(R.id.unlockGateMediaPrev, R.id.unlockGateMediaPlayPause, R.id.unlockGateMediaStop, R.id.unlockGateMediaNext)) {
            val iv = view.findViewById<ImageView>(id)
            @Suppress("DEPRECATION")
            iv.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun updateUnlockGateText(view: View) {
        val phase = unlockGateStateMachine.state.phase
        val isInteractive =
            phase == UnlockGatePhase.UnlockGateVisible || phase == UnlockGatePhase.Dismissing
        val clockView = view.findViewById<TextView>(R.id.unlockGateClock)
        val dateView = view.findViewById<TextView>(R.id.unlockGateDate)
        val mediaControlsView = view.findViewById<LinearLayout>(R.id.unlockGateMediaControls)

        clockView.text =
            formatClockText(
                prefs = prefs,
                appendNotificationIndicator = prefs.lockscreenClockNotificationIndicator,
                hasNotificationIndicator = hasClockNotificationIndicator(this),
            )
        if (phase == UnlockGatePhase.AwaitingCredential) {
            clockView.visibility = View.GONE
            dateView.visibility = View.GONE
            mediaControlsView.visibility = View.GONE
            return
        }

        if (clockView.visibility == View.GONE) {
            clockView.visibility = View.VISIBLE
        }
        clockView.apply {
            val enabled = isInteractive && prefs.getLockscreenClockTapAction() != Action.Disabled
            isClickable = enabled
            isFocusable = enabled
        }

        dateView.apply {
            if (prefs.lockscreenDateFormat != Prefs.LockscreenDateFormat.None) {
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
        val mediaInfo = MediaSessionHelper.mediaInfo.value
        if (mediaInfo != null) {
            val playPauseView = view.findViewById<ImageView>(R.id.unlockGateMediaPlayPause)
            playPauseView.setImageResource(
                if (mediaInfo.showsPauseButton) R.drawable.ic_media_pause else R.drawable.ic_media_play,
            )
            view.findViewById<ImageView>(R.id.unlockGateMediaStop).visibility =
                if (mediaInfo.showsStopButton) View.VISIBLE else View.GONE

            val prevView = view.findViewById<ImageView>(R.id.unlockGateMediaPrev)
            val nextView = view.findViewById<ImageView>(R.id.unlockGateMediaNext)
            if (mediaInfo.isPodcast) {
                prevView.setImageResource(R.drawable.ic_media_replay_10)
                nextView.setImageResource(R.drawable.ic_media_forward_10)
            } else {
                prevView.setImageResource(R.drawable.ic_media_skip_previous)
                nextView.setImageResource(R.drawable.ic_media_skip_next)
            }

            mediaControlsView.visibility = View.VISIBLE
        } else {
            mediaControlsView.visibility = View.GONE
        }
    }

    private fun resetUnlockGateViewState(view: View) {
        view.translationY = 0f
        view.alpha = 1f
        clearUnlockGateGestureTouchListeners(view)
    }

    private fun clearUnlockGateGestureTouchListeners(view: View) {
        view.setOnTouchListener(null)
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateClock))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateDate))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateHomeButton))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateStatusBar))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateMediaControls))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateMediaPrev))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateMediaPlayPause))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateMediaStop))
        clearUnlockGateGestureTarget(view.findViewById(R.id.unlockGateMediaNext))
        clearUnlockGateGestureTarget(view.findViewById(R.id.statusConnectivityLayout))
        clearUnlockGateGestureTarget(view.findViewById(R.id.statusBatteryLayout))
        clearUnlockGateGestureTarget(view.findViewById(R.id.volumeIndicator))
        clearUnlockGateGestureTarget(view.findViewById(R.id.volumeIndicatorLabel))
    }

    private fun clearUnlockGateGestureTarget(target: View) {
        target.setOnTouchListener(null)
        target.isPressed = false
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

        return GestureDescription
            .Builder()
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

        updateSecureLockMaskBatteryStatus(view, textColor)
        getSystemService(TelephonyManager::class.java)?.let { updateUnlockGateCellularSnapshot(it) }
        if (unlockGateStateMachine.state.phase != UnlockGatePhase.Dismissing) {
            updateSecureLockMaskConnectivityStatus(view, textColor)
        }
        syncUnlockGateStatusBarMonitors()
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
        val battery = LumaStatusBarUi.batteryIconRes(sticky)

        batteryLayout.visibility = View.VISIBLE
        batteryText.visibility = if (prefs.batteryPercentage) View.VISIBLE else View.GONE
        batteryText.text = "$pct%"
        batteryIcon.visibility = if (prefs.batteryIcon) View.VISIBLE else View.GONE
        LumaStatusBarUi.setBatteryIcon(batteryIcon, battery.iconRes, textColor, battery.isCharging)
    }

    private fun updateSecureLockMaskConnectivityStatus(
        view: View,
        textColor: Int,
    ) {
        val connectivityLayout = view.findViewById<LinearLayout>(R.id.statusConnectivityLayout)
        val airplaneIcon = view.findViewById<ImageView>(R.id.statusAirplane)
        val networkType = view.findViewById<TextView>(R.id.statusNetworkType)
        val signalIcon = view.findViewById<ImageView>(R.id.statusSignal)
        val wifiIcon = view.findViewById<ImageView>(R.id.statusWifi)
        val bluetoothIcon = view.findViewById<ImageView>(R.id.statusBluetooth)

        val airplaneMode = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)

        if (airplaneMode != 0 && prefs.cellularEnabled) {
            LumaStatusBarUi.showTinted(airplaneIcon, R.drawable.airplane, textColor)
            signalIcon.visibility = View.GONE
            networkType.visibility = View.GONE
            wifiIcon.visibility = View.GONE
        } else {
            airplaneIcon.visibility = View.GONE

            if (prefs.cellularEnabled) {
                val state = unlockGateCellularSnapshot.serviceState
                if (state == ServiceState.STATE_EMERGENCY_ONLY) {
                    val level = unlockGateCellularSnapshot.signalLevel
                    if (level != null) {
                        LumaStatusBarUi.showTinted(signalIcon, LumaStatusBarUi.signalDrawableForLevel(level), textColor)
                    } else {
                        signalIcon.visibility = View.GONE
                    }
                    networkType.visibility = View.VISIBLE
                    networkType.text = "SOS"
                } else if (state == ServiceState.STATE_IN_SERVICE) {
                    val level = unlockGateCellularSnapshot.signalLevel
                    if (level != null) {
                        LumaStatusBarUi.showTinted(signalIcon, LumaStatusBarUi.signalDrawableForLevel(level), textColor)
                    } else {
                        signalIcon.visibility = View.GONE
                    }
                    val label = LumaStatusBarUi.networkLabelForType(unlockGateCellularSnapshot.networkType)
                    networkType.visibility = if (label.isNotEmpty()) View.VISIBLE else View.GONE
                    networkType.text = label
                } else {
                    LumaStatusBarUi.showTinted(signalIcon, R.drawable.signal_nodata, textColor)
                    networkType.visibility = View.GONE
                }
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
        }

        if (prefs.bluetoothEnabled) {
            when (val state = BluetoothStatusHelper.indicatorState(this)) {
                BluetoothStatusHelper.IndicatorState.On,
                BluetoothStatusHelper.IndicatorState.Connected -> LumaStatusBarUi.showTinted(
                    bluetoothIcon,
                    LumaStatusBarUi.bluetoothDrawableForState(state),
                    textColor,
                )

                BluetoothStatusHelper.IndicatorState.Off, null -> bluetoothIcon.visibility = View.GONE
            }
        } else {
            bluetoothIcon.visibility = View.GONE
        }

        val anyVisible =
            airplaneIcon.visibility == View.VISIBLE ||
                networkType.visibility == View.VISIBLE ||
                signalIcon.visibility == View.VISIBLE ||
                wifiIcon.visibility == View.VISIBLE ||
                bluetoothIcon.visibility == View.VISIBLE
        connectivityLayout.visibility = if (anyVisible) View.VISIBLE else View.INVISIBLE
    }

    private fun shouldShowUnlockGateStatusBar(): Boolean =
        unlockGateStateMachine.snapshot.visible &&
            unlockGateStateMachine.state.phase != UnlockGatePhase.AwaitingCredential &&
            !shouldShowUnlockGateVolumeIndicator() &&
            !unlockGateStateMachine.state.prefersHomeStatusBar &&
            prefs.isStatusBarVisibleOnLockscreen()

    private fun shouldShowUnlockGateVolumeIndicator(): Boolean =
        unlockGateVolumeIndicatorVisible &&
            unlockGateStateMachine.state.phase != UnlockGatePhase.Idle &&
            unlockGateStateMachine.state.phase != UnlockGatePhase.WakeCover

    private fun applyUnlockGateVolumeIndicator(view: View) {
        val indicator = view.findViewById<LinearLayout>(R.id.volumeIndicator)
        val shouldShow = shouldShowUnlockGateVolumeIndicator()
        indicator.visibility = if (shouldShow) View.VISIBLE else View.GONE
        val label = indicator.findViewById<TextView>(R.id.volumeIndicatorLabel)
        label.isClickable = false
        label.isFocusable = false
        label.setOnClickListener(null)
        if (!shouldShow) {
            return
        }

        val phase = unlockGateStateMachine.state.phase
        val isInteractive = phase == UnlockGatePhase.UnlockGateVisible || phase == UnlockGatePhase.Dismissing
        val textColor = unlockGateTextColor(view)
        label.apply {
            setText(unlockGateVolumeIndicatorLabelRes)
            setTextColor(textColor)
            isClickable = isInteractive
            isFocusable = isInteractive
            setOnClickListener(
                if (isInteractive) {
                    View.OnClickListener { handleVolumeIndicatorTap() }
                } else {
                    null
                },
            )
        }
        indicator.findViewById<View>(R.id.volumeIndicatorTrackLine).setBackgroundColor(textColor)
        indicator.findViewById<View>(R.id.volumeIndicatorFill).apply {
            setBackgroundColor(textColor)
            pivotX = 0f
            scaleX = unlockGateVolumeIndicatorProgress
        }
    }

    private fun unlockGateTextColor(view: View): Int = view.findViewById<TextView>(R.id.unlockGateClock).currentTextColor

    private fun refreshUnlockGateBatteryStatus(batteryIntent: Intent? = null) {
        val view = unlockGateView ?: return
        if (!shouldShowUnlockGateStatusBar()) return
        updateSecureLockMaskBatteryStatus(view, unlockGateTextColor(view), batteryIntent)
    }

    private fun refreshUnlockGateConnectivityStatus() {
        val view = unlockGateView ?: return
        if (!shouldShowUnlockGateStatusBar()) return
        if (unlockGateStateMachine.state.phase == UnlockGatePhase.Dismissing) return
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
                TelephonyCallback.DataConnectionStateListener,
                TelephonyCallback.ServiceStateListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val level = signalStrength.level.coerceIn(0, 4)
                    runOnMainThread {
                        unlockGateCellularSnapshot = unlockGateCellularSnapshot.copy(signalLevel = level)
                        refreshUnlockGateConnectivityStatus()
                    }
                }

                override fun onDataConnectionStateChanged(
                    state: Int,
                    networkType: Int,
                ) {
                    runOnMainThread {
                        if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                            unlockGateCellularSnapshot = unlockGateCellularSnapshot.copy(networkType = networkType)
                        }
                        refreshUnlockGateConnectivityStatus()
                    }
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    runOnMainThread {
                        unlockGateCellularSnapshot = unlockGateCellularSnapshot.copy(serviceState = serviceState.state)
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
        var updatedSnapshot = unlockGateCellularSnapshot
        try {
            val state = telephonyManager.serviceState?.state ?: ServiceState.STATE_OUT_OF_SERVICE
            updatedSnapshot = updatedSnapshot.copy(serviceState = state)
        } catch (_: SecurityException) {
        }
        try {
            telephonyManager.signalStrength?.let {
                updatedSnapshot = updatedSnapshot.copy(signalLevel = it.level.coerceIn(0, 4))
            }
        } catch (_: SecurityException) {
        }
        runCatching {
            telephonyManager.dataNetworkType.takeIf { it != TelephonyManager.NETWORK_TYPE_UNKNOWN }
        }.getOrNull()?.let { networkType ->
            updatedSnapshot = updatedSnapshot.copy(networkType = networkType)
        }
        unlockGateCellularSnapshot = updatedSnapshot
    }

    private fun startIncomingCallMonitor() {
        if (incomingCallCallback != null) return
        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return
        val callback =
            IncomingCallCallback(
                onRinging = {
                    handleIncomingRinging()
                },
            )
        try {
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            incomingCallCallback = callback
        } catch (exception: SecurityException) {
            Log.w(TAG, "startIncomingCallMonitor: registerTelephonyCallback failed", exception)
        }
    }

    private fun stopIncomingCallMonitor() {
        val callback = incomingCallCallback ?: return
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        try {
            telephonyManager?.unregisterTelephonyCallback(callback)
        } catch (exception: Exception) {
            Log.e(TAG, "stopIncomingCallMonitor: unregisterTelephonyCallback failed", exception)
        } finally {
            incomingCallCallback = null
        }
    }

    private fun handleIncomingRinging() {
        val phase = unlockGateStateMachine.state.phase
        if (phase == UnlockGatePhase.UnlockGateVisible || phase == UnlockGatePhase.Dismissing) {
            dispatchUnlockGateEventOnMain(
                UnlockGateEvent.DismissRequested(
                    nowUptimeMs = SystemClock.uptimeMillis(),
                    minDelayMs = 150L,
                ),
            )
        }
        launchLightOsRoute(this, "call")
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
        if (!BluetoothStatusHelper.hasBluetoothConnectPermission(this)) {
            refreshUnlockGateConnectivityStatus()
            return
        }

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
            registerReceiver(receiver, BluetoothStatusHelper.bluetoothIntentFilter())
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
                val delayMs = 60_000L - (now % 60_000L)
                mainHandler.postDelayed(runnable, delayMs)
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
            val hasBiometrics =
                BiometricManager
                    .from(this)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            imageView.setImageResource(
                if (hasBiometrics) R.drawable.ic_shortcut_fingerprint else R.drawable.ic_unlock_gate_lock,
            )
            @Suppress("DEPRECATION")
            imageView.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            imageView.scaleX = ICON_SCALE
            imageView.scaleY = ICON_SCALE
        } else {
            val iconRes = shortcutIconDrawable(prefs.lockscreenShortcutIcon)
            imageView.background = null
            imageView.setImageResource(iconRes)
            @Suppress("DEPRECATION")
            imageView.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            imageView.imageTintList = null
            imageView.scaleX = ICON_SCALE
            imageView.scaleY = ICON_SCALE
        }
    }

    private fun shortcutIconDrawable(icon: Prefs.LockscreenShortcutIcon): Int =
        when (icon) {
            Prefs.LockscreenShortcutIcon.Ring -> R.drawable.ic_shortcut_ring
            Prefs.LockscreenShortcutIcon.Star -> R.drawable.ic_shortcut_star
            Prefs.LockscreenShortcutIcon.Camera -> R.drawable.ic_shortcut_camera
            Prefs.LockscreenShortcutIcon.Phone -> R.drawable.ic_shortcut_phone
            Prefs.LockscreenShortcutIcon.Heart -> R.drawable.ic_shortcut_heart
            Prefs.LockscreenShortcutIcon.Flashlight -> R.drawable.ic_shortcut_flashlight
            Prefs.LockscreenShortcutIcon.Music -> R.drawable.ic_shortcut_music
            Prefs.LockscreenShortcutIcon.Message -> R.drawable.ic_shortcut_message
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
        private const val ZERO_PACKAGE = "com.vandam.zero"
        private const val CAMERA_OWNER_PENDING_WINDOW_MS = 2000L
        private const val MIN_MASK_VISIBILITY_MS = 200L
        private const val HARD_TIMEOUT_MS = 900L
        private const val TOOL_LAUNCH_MASK_WINDOW_TITLE = "Luma Tool Launch Mask"
        private const val NOTIFICATION_SETTINGS_LIGHT_ROUTE = "notificationsettings"
        private const val UNLOCK_GATE_MIN_VISIBILITY_MS = 150L
        private const val UNLOCK_GATE_HIDE_DELAY_MS = 100L
        private const val UNLOCK_GATE_SHORTCUT_HIDE_DELAY_MS = 150L
        private const val VOLUME_INDICATOR_HIDE_DELAY_MS = 1500L
        private const val ICON_SCALE = 1.0f
        private const val UNLOCK_GATE_WINDOW_TITLE = "Luma Unlock Gate"
        private const val SECURE_LOCK_MASK_WINDOW_TITLE = "Luma Secure Lock Mask"
        private const val VOLUME_ONLY_OVERLAY_WINDOW_TITLE = "Luma Volume Overlay"
        private const val SECURE_LOCK_MASK_GESTURE_DISPATCH_DELAY_MS = 96L
        private const val SECURE_LOCK_MASK_GESTURE_RETRY_DELAY_MS = 160L
        private const val SECURE_LOCK_MASK_GESTURE_DURATION_MS = 320L
        private const val SECURE_LOCK_MASK_GESTURE_START_Y_RATIO = 0.90f
        private const val SECURE_LOCK_MASK_GESTURE_END_Y_RATIO = 0.14f
        private const val SECURE_LOCK_MASK_GESTURE_MAX_RETRIES = 1
        private const val SCROLLWHEEL_BRIGHTNESS_UP_KEY_CODE = 317
        private const val SCROLLWHEEL_BRIGHTNESS_DOWN_KEY_CODE = 318
        private const val SCROLLWHEEL_BUTTON_KEY_CODE = 319
        private const val CAMERA_KEY_CODE = 27
        private const val KEYMAP_LONG_PRESS_MS = 450L
        private val HOME_LONG_PRESS_TOKEN = Object()
        private const val MIN_BRIGHTNESS = 1
        private const val MAX_BRIGHTNESS = 255
        private const val DEFAULT_BRIGHTNESS = 128
        private const val BRIGHTNESS_STEP = 20
        private const val WRITE_SETTINGS_PROMPT_COOLDOWN_MS = 1500L

        private var instance: WeakReference<ActionService> = WeakReference(null)
        private val _unlockGateState = MutableStateFlow(UnlockGateUiSnapshot())
        val unlockGateState: StateFlow<UnlockGateUiSnapshot> = _unlockGateState.asStateFlow()
        private val _unlockGateVisible = MutableStateFlow(false)
        val unlockGateVisible: StateFlow<Boolean> = _unlockGateVisible.asStateFlow()
        private val _unlockGateShowingHomeStatusBar = MutableStateFlow(false)
        val unlockGateShowingHomeStatusBar: StateFlow<Boolean> = _unlockGateShowingHomeStatusBar.asStateFlow()

        fun instance(): ActionService? = instance.get()
    }

    private class IncomingCallCallback(
        private val onRinging: () -> Unit,
    ) : TelephonyCallback(),
        TelephonyCallback.CallStateListener {
        private var wasRinging = false

        override fun onCallStateChanged(state: Int) {
            if (state == TelephonyManager.CALL_STATE_RINGING && !wasRinging) {
                wasRinging = true
                onRinging()
            } else if (state != TelephonyManager.CALL_STATE_RINGING) {
                wasRinging = false
            }
        }
    }
}
