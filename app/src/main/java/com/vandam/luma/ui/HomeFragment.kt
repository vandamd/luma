package com.vandam.luma.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import android.provider.Settings
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.vandam.luma.MainActivity
import com.vandam.luma.MainViewModel
import com.vandam.luma.R
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants.Action
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.databinding.FragmentHomeBinding
import com.vandam.luma.helper.*
import com.vandam.luma.helper.LumaNotificationListener
import com.vandam.luma.listener.SwipeTouchListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "HomeFragment"
private const val NETWORK_SHORTCUT_LIGHT_ROUTE = "networksettings"
private const val NOTIFICATION_SETTINGS_LIGHT_ROUTE = "notificationsettings"
private const val VOLUME_INDICATOR_HIDE_DELAY_MS = 1500L
private const val STATUS_BAR_EXTERNAL_ACTION_HIDE_DELAY_MS = 120L

private val FragmentHomeBinding.statusBattery: ImageView
    get() = statusBar.findViewById(R.id.statusBattery)

private val FragmentHomeBinding.statusBatteryLayout: LinearLayout
    get() = statusBar.findViewById(R.id.statusBatteryLayout)

private val FragmentHomeBinding.statusBatteryText: TextView
    get() = statusBar.findViewById(R.id.statusBatteryText)

private val FragmentHomeBinding.statusBluetooth: ImageView
    get() = statusBar.findViewById(R.id.statusBluetooth)

private val FragmentHomeBinding.statusConnectivityLayout: LinearLayout
    get() = statusBar.findViewById(R.id.statusConnectivityLayout)

private val FragmentHomeBinding.statusNetworkType: TextView
    get() = statusBar.findViewById(R.id.statusNetworkType)

private val FragmentHomeBinding.statusSignal: ImageView
    get() = statusBar.findViewById(R.id.statusSignal)

private val FragmentHomeBinding.statusWifi: ImageView
    get() = statusBar.findViewById(R.id.statusWifi)

private data class VolumeIndicatorState(
    val labelRes: Int,
    val progress: Float,
)

private enum class VolumeStateType {
    Media,
    Ringer,
    Vibrate,
    Silent,
}

private data class VolumeState(
    val type: VolumeStateType,
    val volume: Int = 0,
    val maxVolume: Int = 0,
) {
    fun toIndicatorState(): VolumeIndicatorState =
        when (type) {
            VolumeStateType.Media -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_media,
                    progress = if (maxVolume <= 0) 0f else volume.toFloat() / maxVolume.toFloat(),
                )
            }

            VolumeStateType.Ringer -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_ringer,
                    progress = if (maxVolume <= 0) 0f else volume.toFloat() / maxVolume.toFloat(),
                )
            }

            VolumeStateType.Vibrate -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_vibrate,
                    progress = 0f,
                )
            }

            VolumeStateType.Silent -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_silent,
                    progress = 0f,
                )
            }
        }
}

private data class VolumePrediction(
    val state: VolumeState,
    val promptForSilentAccess: Boolean = false,
)

class HomeFragment :
    Fragment(),
    View.OnClickListener {
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var currentPage = 0
    private var totalPages = 1
    private var pageIndicatorLayout: LinearLayout? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var volumeIndicatorHideJob: Job? = null
    private var audioManager: AudioManager? = null
    private var notificationManager: NotificationManager? = null
    private var volumeWorkerThread: HandlerThread? = null
    private var volumeWorkerHandler: Handler? = null
    private var lastKnownVolumeState: VolumeState? = null
    private var pendingVolumeIndicatorState: VolumeIndicatorState? = null
    private var volumeApplyGeneration = 0L
    private var isUnlockGateVisible = false
    private val applyVolumeIndicatorRunnable =
        Runnable {
            val currentBinding = _binding ?: return@Runnable
            val state = pendingVolumeIndicatorState ?: return@Runnable
            pendingVolumeIndicatorState = null
            currentBinding.volumeIndicatorLabel.setText(state.labelRes)
            updateVolumeIndicatorFill(state.progress)
        }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs.getInstance(requireContext())

        if (prefs.firstSettingsOpen()) {
            binding.firstRunTips.visibility = View.VISIBLE
        }

        primeStatusBar()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        volumeIndicatorHideJob?.cancel()
        volumeIndicatorHideJob = null
        volumeWorkerHandler?.removeCallbacksAndMessages(null)
        volumeWorkerThread?.quitSafely()
        volumeWorkerHandler = null
        volumeWorkerThread = null
        audioManager = null
        notificationManager = null
        lastKnownVolumeState = null
        pendingVolumeIndicatorState = null
        volumeApplyGeneration = 0L
        _binding?.root?.removeCallbacks(applyVolumeIndicatorRunnable)
        isUnlockGateVisible = false
        _binding = null
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        volumeWorkerThread =
            HandlerThread("HomeVolumeWorker").also {
                it.start()
                volumeWorkerHandler = Handler(it.looper)
            }

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        currentPage = viewModel.getCurrentHomePage()

        initObservers()
        initPageNavigation()
        initSwipeTouchListener()
        initStatusBarClickListeners()
        initVolumeIndicator()
        observeUnlockGateClockVisibility()
        binding.touchArea.post { syncUnlockGateHomeContentTop() }
    }

    override fun onResume() {
        super.onResume()
        HomeCleanupHelper.setOnHomeCleanupCallback { refreshAppNames() }
        totalPages = prefs.homePages
        currentPage = viewModel.getCurrentHomePage()
        if (currentPage >= totalPages) currentPage = totalPages - 1
        viewModel.setCurrentHomePage(currentPage)
        pageIndicatorLayout = null
        updatePageIndicator()
        refreshAppNames()
        applyStatusBarVisibility()
        startBatteryMonitor()
        startConnectivityMonitors()
        syncRepeatedHomeGateEligibility()
        syncUnlockGateHomeContentTop()
    }

    override fun onPause() {
        super.onPause()
        HomeCleanupHelper.setOnHomeCleanupCallback(null)
        hideVolumeIndicator()
        stopBatteryMonitor()
        stopConnectivityMonitors()
        (activity as? MainActivity)?.syncRepeatedHomeGateEligibility(null)
        ActionService.instance()?.setUnlockGateHomeContentTop(0)
    }

    override fun onClick(view: View) {
        try {
            val appLocation = view.id
            performAppTapHaptic()
            homeAppClicked(appLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling app click", e)
        }
    }

    fun handleHardwareVolumeKey(keyCode: Int): Boolean {
        if (_binding == null) return false
        val audioManager = audioManager ?: return false
        val notificationManager = notificationManager ?: return false

        val isVolumeUp =
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return false
            }

        val currentState =
            if (binding.volumeIndicator.visibility == View.VISIBLE) {
                lastKnownVolumeState ?: readCurrentVolumeState(audioManager)
            } else {
                readCurrentVolumeState(audioManager)
            }
        val prediction = predictNextVolumeState(currentState, isVolumeUp, notificationManager.isNotificationPolicyAccessGranted)
        if (prediction.promptForSilentAccess) {
            showToast(requireContext(), getString(R.string.toast_volume_silent_requires_permission), Toast.LENGTH_LONG)
            openNotificationPolicyAccessSettings(requireContext())
        }

        lastKnownVolumeState = prediction.state
        showVolumeIndicator(prediction.state.toIndicatorState())
        val generation = ++volumeApplyGeneration
        if (!prediction.promptForSilentAccess) {
            enqueueVolumeApply(prediction.state, generation)
        }
        return true
    }

    private fun initSwipeTouchListener() {
        binding.touchArea.setOnTouchListener(
            createGestureListener(
                onLongClick = {
                    try {
                        performLongPressHaptic()
                        findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    } catch (_: Exception) {
                    }
                },
            ),
        )
    }

    private fun initStatusBarClickListeners() {
        binding.statusConnectivityLayout.setOnClickListener { handleSectionPress(StatusBarSectionType.CELLULAR) }
        binding.statusBatteryLayout.setOnClickListener { handleSectionPress(StatusBarSectionType.BATTERY) }
    }

    private fun initVolumeIndicator() {
        val ta = requireContext().obtainStyledAttributes(intArrayOf(R.attr.primaryColor))
        val primaryColor = ta.getColor(0, 0)
        ta.recycle()
        binding.volumeIndicatorTrackLine.setBackgroundColor(primaryColor)
        binding.volumeIndicatorFill.setBackgroundColor(primaryColor)
        binding.volumeIndicatorFill.pivotX = 0f
        binding.volumeIndicatorFill.scaleX = 0f
        binding.volumeIndicatorLabel.setOnClickListener {
            performAppTapHaptic()
            launchLightOsRoute(requireActivity(), NOTIFICATION_SETTINGS_LIGHT_ROUTE)
        }
    }

    private fun handleSectionPress(section: StatusBarSectionType) {
        val action = prefs.getSectionAction(section)
        if (action == Action.Disabled) return
        performStatusBarPressHaptic()
        if (action == Action.OpenApp) {
            val app = prefs.getSectionApp(section)
            if (app.appPackage.isNotEmpty()) {
                launchApp(app)
                ActionService.instance()?.dismissUnlockGateForStatusBarAction(STATUS_BAR_EXTERNAL_ACTION_HIDE_DELAY_MS)
            }
        } else {
            handleOtherAction(action)
            ActionService.instance()?.dismissUnlockGateForStatusBarAction()
        }
    }

    private fun initPageNavigation() {
        totalPages = prefs.homePages
        if (currentPage >= totalPages) currentPage = totalPages - 1
        updatePageIndicator()
        refreshAppNames()
    }

    private fun updatePageIndicator() {
        binding.mainLayout.findViewWithTag<View>("pageIndicator")?.let {
            binding.mainLayout.removeView(it)
            if (it === pageIndicatorLayout) pageIndicatorLayout = null
        }

        if (totalPages < 2) {
            currentPage = 0
            pageIndicatorLayout = null
            return
        }

        if (prefs.pageIndicatorPosition == Prefs.PageIndicatorPosition.Hidden) {
            pageIndicatorLayout = null
            return
        }

        val newLayout =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                tag = "pageIndicator"
            }

        val density = resources.displayMetrics.density
        val circleSize = (11.6 * density).toInt()
        val circleMargin = (0.8 * density).toInt()
        val circleVerticalMargin = (7.8 * density).toInt()

        for (i in 0 until totalPages) {
            val circle =
                View(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(circleSize, circleSize).apply {
                            setMargins(circleMargin, circleVerticalMargin, circleMargin, circleVerticalMargin)
                        }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { switchToPage(i) }
                    setBackgroundResource(if (i == currentPage) R.drawable.filled_circle else R.drawable.hollow_circle)
                }
            newLayout.addView(circle)
        }

        val layoutParams =
            FrameLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    when (prefs.pageIndicatorPosition) {
                        Prefs.PageIndicatorPosition.Left -> {
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            marginStart = (15.5 * density).toInt()
                            topMargin = (-7.0 * density).toInt()
                        }

                        Prefs.PageIndicatorPosition.Right -> {
                            gravity = Gravity.END or Gravity.CENTER_VERTICAL
                            marginEnd = (15.5 * density).toInt()
                            topMargin = (-7.0 * density).toInt()
                        }

                        Prefs.PageIndicatorPosition.Hidden -> { }
                    }
                }

        binding.mainLayout.addView(newLayout, layoutParams)
        pageIndicatorLayout = newLayout
    }

    private fun switchToPage(page: Int) {
        if (page >= 0 && page < totalPages) {
            currentPage = page
            viewModel.setCurrentHomePage(currentPage)
            refreshAppNames()
            updatePageIndicator()
            syncRepeatedHomeGateEligibility()
        }
    }

    fun resetToFirstPage() {
        if (currentPage == 0) return
        switchToPage(0)
    }

    fun isOnFirstPage(): Boolean = currentPage == 0

    fun reloadHomeLayoutFromPrefs(resetToFirstPage: Boolean = false) {
        totalPages = prefs.homePages
        currentPage =
            when {
                resetToFirstPage -> 0
                currentPage >= totalPages -> totalPages - 1
                else -> currentPage
            }
        viewModel.setCurrentHomePage(currentPage)
        pageIndicatorLayout = null
        updatePageIndicator()
        refreshAppNames()
        syncRepeatedHomeGateEligibility()
    }

    private fun syncRepeatedHomeGateEligibility() {
        (activity as? MainActivity)?.syncRepeatedHomeGateEligibility()
    }

    private fun shouldShowLumaStatusBarNow(): Boolean = false

    private fun lockscreenStatusBarInsetPx(): Int = resources.getDimensionPixelSize(R.dimen.lockscreen_gate_home_content_top)

    private fun applyStatusBarVisibility() {
        binding.statusBar.visibility = if (shouldShowLumaStatusBarNow()) View.VISIBLE else View.GONE
    }

    private fun syncUnlockGateHomeContentTop() {
        if (_binding == null) return
        binding.touchArea.post {
            if (_binding == null) return@post
            val contentTop =
                if (prefs.isStatusBarVisibleOnLockscreen()) {
                    lockscreenStatusBarInsetPx()
                } else {
                    0
                }
            ActionService.instance()?.setUnlockGateHomeContentTop(contentTop)
        }
    }

    private fun initObservers() {
        binding.homeAppsLayout.gravity = android.view.Gravity.CENTER
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LumaNotificationListener.changeVersion.collect {
                    refreshAppNames()
                }
            }
        }
    }

    private fun readCurrentVolumeState(audioManager: AudioManager): VolumeState {
        if (audioManager.isMusicActive) {
            return VolumeState(
                type = VolumeStateType.Media,
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            )
        }

        val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> {
                return VolumeState(
                    type = VolumeStateType.Vibrate,
                    maxVolume = maxRingVolume,
                )
            }

            AudioManager.RINGER_MODE_SILENT -> {
                return VolumeState(
                    type = VolumeStateType.Silent,
                    maxVolume = maxRingVolume,
                )
            }

            else -> {
                return VolumeState(
                    type = VolumeStateType.Ringer,
                    volume = audioManager.getStreamVolume(AudioManager.STREAM_RING),
                    maxVolume = maxRingVolume,
                )
            }
        }
    }

    private fun predictNextVolumeState(
        currentState: VolumeState,
        isVolumeUp: Boolean,
        canEnterSilent: Boolean,
    ): VolumePrediction =
        when (currentState.type) {
            VolumeStateType.Media -> {
                val targetVolume =
                    if (isVolumeUp) {
                        (currentState.volume + 1).coerceAtMost(currentState.maxVolume)
                    } else {
                        (currentState.volume - 1).coerceAtLeast(0)
                    }
                VolumePrediction(currentState.copy(volume = targetVolume))
            }

            VolumeStateType.Ringer -> {
                if (isVolumeUp) {
                    val targetVolume =
                        if (currentState.volume == 0) {
                            1
                        } else {
                            (currentState.volume + 1).coerceAtMost(currentState.maxVolume)
                        }
                    VolumePrediction(currentState.copy(volume = targetVolume))
                } else {
                    if (currentState.volume > 1) {
                        VolumePrediction(currentState.copy(volume = currentState.volume - 1))
                    } else {
                        VolumePrediction(
                            VolumeState(
                                type = VolumeStateType.Vibrate,
                                maxVolume = currentState.maxVolume,
                            ),
                        )
                    }
                }
            }

            VolumeStateType.Vibrate -> {
                if (isVolumeUp) {
                    VolumePrediction(
                        VolumeState(
                            type = VolumeStateType.Ringer,
                            volume = 1,
                            maxVolume = currentState.maxVolume,
                        ),
                    )
                } else {
                    if (canEnterSilent) {
                        VolumePrediction(
                            VolumeState(
                                type = VolumeStateType.Silent,
                                maxVolume = currentState.maxVolume,
                            ),
                        )
                    } else {
                        VolumePrediction(currentState, promptForSilentAccess = true)
                    }
                }
            }

            VolumeStateType.Silent -> {
                if (isVolumeUp) {
                    VolumePrediction(
                        VolumeState(
                            type = VolumeStateType.Vibrate,
                            maxVolume = currentState.maxVolume,
                        ),
                    )
                } else {
                    VolumePrediction(currentState)
                }
            }
        }

    private fun enqueueVolumeApply(
        state: VolumeState,
        generation: Long,
    ) {
        val audioManager = audioManager ?: return
        val notificationManager = notificationManager ?: return
        val handler = volumeWorkerHandler ?: return

        handler.removeCallbacksAndMessages(null)
        handler.post {
            applyVolumeState(audioManager, notificationManager, state)
            val actualState = readCurrentVolumeState(audioManager)
            _binding?.root?.post {
                if (_binding == null) return@post
                if (generation != volumeApplyGeneration) return@post
                lastKnownVolumeState = actualState
                if (binding.volumeIndicator.visibility == View.VISIBLE) {
                    scheduleVolumeIndicatorUiUpdate(actualState.toIndicatorState())
                }
            }
        }
    }

    private fun applyVolumeState(
        audioManager: AudioManager,
        notificationManager: NotificationManager,
        state: VolumeState,
    ) {
        when (state.type) {
            VolumeStateType.Media -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, state.volume, 0)
            }

            VolumeStateType.Ringer -> {
                trySetRingerMode(audioManager, notificationManager, AudioManager.RINGER_MODE_NORMAL)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, state.volume, 0)
            }

            VolumeStateType.Vibrate -> {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                trySetRingerMode(audioManager, notificationManager, AudioManager.RINGER_MODE_VIBRATE)
            }

            VolumeStateType.Silent -> {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                trySetRingerMode(audioManager, notificationManager, AudioManager.RINGER_MODE_SILENT)
            }
        }
    }

    private fun trySetRingerMode(
        audioManager: AudioManager,
        notificationManager: NotificationManager,
        mode: Int,
        promptForPolicyAccess: Boolean = false,
    ): Boolean {
        if (mode == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            if (promptForPolicyAccess) {
                showToast(requireContext(), getString(R.string.toast_volume_silent_requires_permission), Toast.LENGTH_LONG)
                openNotificationPolicyAccessSettings(requireContext())
            }
            return false
        }

        return try {
            audioManager.ringerMode = mode
            true
        } catch (_: SecurityException) {
            if (promptForPolicyAccess) {
                showToast(requireContext(), getString(R.string.toast_volume_silent_requires_permission), Toast.LENGTH_LONG)
                openNotificationPolicyAccessSettings(requireContext())
            }
            false
        }
    }

    private fun showVolumeIndicator(state: VolumeIndicatorState) {
        binding.volumeIndicator.visibility = View.VISIBLE
        scheduleVolumeIndicatorUiUpdate(state)
        restartVolumeIndicatorHideTimer()
    }

    private fun scheduleVolumeIndicatorUiUpdate(state: VolumeIndicatorState) {
        pendingVolumeIndicatorState = state
        ActionService.instance()?.showUnlockGateVolumeIndicator(state.labelRes, state.progress)
        binding.root.removeCallbacks(applyVolumeIndicatorRunnable)
        binding.root.postOnAnimation(applyVolumeIndicatorRunnable)
    }

    private fun updateVolumeIndicatorFill(progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        binding.volumeIndicatorFill.scaleX = clampedProgress
    }

    private fun restartVolumeIndicatorHideTimer() {
        volumeIndicatorHideJob?.cancel()
        volumeIndicatorHideJob =
            viewLifecycleOwner.lifecycleScope.launch {
                delay(VOLUME_INDICATOR_HIDE_DELAY_MS)
                hideVolumeIndicator()
            }
    }

    private fun hideVolumeIndicator() {
        volumeIndicatorHideJob?.cancel()
        volumeIndicatorHideJob = null
        ActionService.instance()?.hideUnlockGateVolumeIndicator()

        if (_binding == null) return

        binding.root.removeCallbacks(applyVolumeIndicatorRunnable)
        pendingVolumeIndicatorState = null
        binding.volumeIndicator.visibility = View.GONE
    }

    private fun observeUnlockGateClockVisibility() {
        viewLifecycleOwner.lifecycleScope.launch {
            ActionService.unlockGateState.collect { snapshot ->
                if (_binding == null) return@collect
                isUnlockGateVisible = snapshot.visible
                applyStatusBarVisibility()
            }
        }
    }

    private fun ImageView.showTinted(icon: Int) {
        val color = binding.statusBatteryText.currentTextColor
        LumaStatusBarUi.showTinted(this, icon, color)
    }

    private fun primeStatusBar() {
        applyStatusBarVisibility()
        primeBatteryState()
        primeConnectivityState()
    }

    private fun primeBatteryState() {
        if (!prefs.showsLumaStatusBarAnywhere() || (!prefs.batteryPercentage && !prefs.batteryIcon)) {
            binding.statusBatteryText.visibility = View.GONE
            binding.statusBattery.visibility = View.GONE
            binding.statusBatteryLayout.visibility = View.INVISIBLE
            return
        }
        binding.statusBatteryLayout.visibility = View.VISIBLE
        val sticky = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            updateBatteryIcon(sticky)
        }
    }

    private fun startBatteryMonitor() {
        if (!prefs.showsLumaStatusBarAnywhere() || (!prefs.batteryPercentage && !prefs.batteryIcon)) {
            binding.statusBatteryText.visibility = View.GONE
            binding.statusBattery.visibility = View.GONE
            binding.statusBatteryLayout.visibility = View.INVISIBLE
            return
        }
        binding.statusBatteryLayout.visibility = View.VISIBLE
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (_binding == null) return
                    updateBatteryIcon(intent)
                }
            }
        batteryReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = requireContext().registerReceiver(receiver, filter)
        if (sticky != null) updateBatteryIcon(sticky)
    }

    private fun stopBatteryMonitor() {
        batteryReceiver?.let {
            requireContext().unregisterReceiver(it)
            batteryReceiver = null
        }
    }

    private fun updateBatteryIcon(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val icon = LumaStatusBarUi.batteryIconRes(intent)
        binding.statusBatteryText.visibility = if (prefs.batteryPercentage) View.VISIBLE else View.GONE
        binding.statusBatteryText.text = "$pct%"
        binding.statusBattery.visibility = if (prefs.batteryIcon) View.VISIBLE else View.GONE
        binding.statusBattery.setImageResource(icon)
        binding.statusBattery.setColorFilter(binding.statusBatteryText.currentTextColor)
    }

    private fun startConnectivityMonitors() {
        primeConnectivityState()
        if (!prefs.showsLumaStatusBarAnywhere()) {
            return
        }
        if (prefs.cellularEnabled) startCellularMonitor() else hideCellular()
        if (prefs.wifiEnabled) startWifiMonitor() else hideWifi()
        if (prefs.bluetoothEnabled) startBluetoothMonitor() else hideBluetooth()
    }

    private fun stopConnectivityMonitors() {
        stopCellularMonitor()
        stopWifiMonitor()
        stopBluetoothMonitor()
    }

    private fun primeConnectivityState() {
        if (!prefs.showsLumaStatusBarAnywhere()) {
            binding.statusConnectivityLayout.visibility = View.INVISIBLE
            return
        }
        val anyEnabled = prefs.cellularEnabled || prefs.wifiEnabled || prefs.bluetoothEnabled
        if (!anyEnabled) {
            binding.statusNetworkType.text = "LTE"
        }
        binding.statusConnectivityLayout.visibility = if (anyEnabled) View.VISIBLE else View.INVISIBLE
        if (prefs.cellularEnabled) {
            primeCellularState()
        } else {
            hideCellular()
        }
        if (prefs.wifiEnabled) {
            updateWifiSnapshot()
        } else {
            hideWifi()
        }
        if (prefs.bluetoothEnabled) {
            updateBluetoothState()
        } else {
            hideBluetooth()
        }
    }

    private fun primeCellularState() {
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        if (tm != null) {
            updateCellularSnapshot(tm)
        }
        applyCachedCellularState(fillMissingOnly = true)
    }

    private fun startCellularMonitor() {
        val tm =
            requireContext().getSystemService(TelephonyManager::class.java) ?: run {
                hideCellular()
                return
            }
        updateCellularSnapshot(tm)
        val callback =
            object :
                TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DataConnectionStateListener,
                TelephonyCallback.ServiceStateListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    if (_binding == null) return
                    val level = signalStrength.level.coerceIn(0, 4)
                    prefs.lastCellularSignalLevel = level
                    updateSignalIcon(level)
                }

                override fun onDataConnectionStateChanged(
                    state: Int,
                    networkType: Int,
                ) {
                    if (_binding == null) return
                    if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                        prefs.lastCellularNetworkType = networkType
                        updateNetworkTypeFromInt(networkType)
                    } else {
                        prefs.lastCellularNetworkType?.let { updateNetworkTypeFromInt(it) }
                    }
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    if (_binding == null) return
                    prefs.cellularServiceState = serviceState.state
                    applyCachedCellularState()
                }
            }
        telephonyCallback = callback
        try {
            tm.registerTelephonyCallback(requireContext().mainExecutor, callback)
        } catch (_: SecurityException) {
            telephonyCallback = null
            hideCellular()
        }
    }

    private fun updateCellularSnapshot(tm: TelephonyManager) {
        try {
            val state = tm.serviceState?.state ?: ServiceState.STATE_OUT_OF_SERVICE
            prefs.cellularServiceState = state
        } catch (_: SecurityException) {
        }
        try {
            tm.signalStrength?.let {
                val level = it.level.coerceIn(0, 4)
                prefs.lastCellularSignalLevel = level
                updateSignalIcon(level)
            }
        } catch (_: SecurityException) {
        }
        prefs.lastCellularNetworkType?.let { updateNetworkTypeFromInt(it) }
    }

    private fun applyCachedCellularState(fillMissingOnly: Boolean = false) {
        val cachedSignalLevel = prefs.lastCellularSignalLevel
        val cachedNetworkType = prefs.lastCellularNetworkType
        if ((!fillMissingOnly || binding.statusSignal.visibility != View.VISIBLE) && cachedSignalLevel != null) {
            updateSignalIcon(cachedSignalLevel)
        } else if (!fillMissingOnly) {
            binding.statusSignal.visibility = View.GONE
        }
        if ((!fillMissingOnly || binding.statusNetworkType.visibility != View.VISIBLE) && cachedNetworkType != null) {
            updateNetworkTypeFromInt(cachedNetworkType)
        } else if (!fillMissingOnly) {
            binding.statusNetworkType.visibility = View.GONE
        }
        if (binding.statusSignal.visibility != View.VISIBLE && binding.statusNetworkType.visibility != View.VISIBLE) {
            hideCellular()
        }
    }

    private fun stopCellularMonitor() {
        telephonyCallback?.let {
            val tm = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.unregisterTelephonyCallback(it)
            telephonyCallback = null
        }
    }

    private fun updateSignalIcon(level: Int) {
        val state = prefs.cellularServiceState
        when (state) {
            ServiceState.STATE_EMERGENCY_ONLY -> binding.statusSignal.showTinted(LumaStatusBarUi.signalDrawableForLevel(level))
            ServiceState.STATE_IN_SERVICE -> binding.statusSignal.showTinted(LumaStatusBarUi.signalDrawableForLevel(level))
            else -> binding.statusSignal.showTinted(R.drawable.signal_nodata)
        }
    }

    private fun updateNetworkTypeFromInt(type: Int) {
        val state = prefs.cellularServiceState
        when (state) {
            ServiceState.STATE_EMERGENCY_ONLY -> {
                binding.statusNetworkType.visibility = View.VISIBLE
                binding.statusNetworkType.text = "SOS"
            }

            ServiceState.STATE_IN_SERVICE -> {
                val label = LumaStatusBarUi.networkLabelForType(type)
                binding.statusNetworkType.visibility = if (label.isNotEmpty()) View.VISIBLE else View.GONE
                binding.statusNetworkType.text = label
            }

            else -> {
                binding.statusNetworkType.visibility = View.GONE
            }
        }
    }

    private fun hideCellular() {
        binding.statusSignal.visibility = View.GONE
        binding.statusNetworkType.visibility = View.GONE
    }

    private fun updateWifiSnapshot() {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            updateWifiIcon(wm.calculateSignalLevel(activeCaps.signalStrength))
        } else {
            hideWifi()
        }
    }

    private fun startWifiMonitor() {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val request =
            NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    if (_binding == null) return
                    val level = wm.calculateSignalLevel(caps.signalStrength)
                    binding.statusWifi.post { if (_binding != null) updateWifiIcon(level) }
                }

                override fun onLost(network: Network) {
                    if (_binding == null) return
                    binding.statusWifi.post { if (_binding != null) hideWifi() }
                }
            }
        wifiNetworkCallback = callback
        cm.registerNetworkCallback(request, callback)
        updateWifiSnapshot()
    }

    private fun stopWifiMonitor() {
        wifiNetworkCallback?.let {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
            wifiNetworkCallback = null
        }
    }

    private fun updateWifiIcon(level: Int) {
        binding.statusWifi.showTinted(LumaStatusBarUi.wifiDrawableForLevel(level))
    }

    private fun hideWifi() {
        binding.statusWifi.visibility = View.GONE
    }

    private fun updateBluetoothState() {
        val btOn = Settings.Global.getInt(requireContext().contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0
        if (btOn) showBluetooth() else hideBluetooth()
    }

    private fun startBluetoothMonitor() {
        updateBluetoothState()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (_binding == null) return
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                    if (state == BluetoothAdapter.STATE_ON) showBluetooth() else hideBluetooth()
                }
            }
        bluetoothReceiver = receiver
        requireContext().registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    private fun stopBluetoothMonitor() {
        bluetoothReceiver?.let {
            requireContext().unregisterReceiver(it)
            bluetoothReceiver = null
        }
    }

    private fun showBluetooth() {
        binding.statusBluetooth.showTinted(R.drawable.bluetooth)
    }

    private fun hideBluetooth() {
        binding.statusBluetooth.visibility = View.GONE
    }

    private fun homeAppClicked(location: Int) {
        val appModel = prefs.getHomeAppModel(location)
        if (appModel.appLabel.isNotEmpty()) {
            launchApp(appModel)
        }
    }

    private fun launchApp(appModel: AppModel) {
        viewModel.selectedApp(appModel, launchContext = requireActivity())
    }

    private fun openGestureApp(gestureType: GestureType) {
        val app = prefs.getGestureApp(gestureType)
        if (app.appPackage.isNotEmpty()) {
            launchApp(app)
        }
    }

    private fun handleOtherAction(action: Action) {
        executeSecondaryAction(
            context = requireActivity(),
            action = action,
            callbacks =
                ActionExecutionCallbacks(
                    showNotificationList = {
                        try {
                            findNavController().navigate(R.id.action_mainFragment_to_notificationListFragment)
                        } catch (_: Exception) {
                        }
                    },
                ),
        )
    }

    private fun handleGesture(gestureType: GestureType) {
        val action = prefs.getGestureAction(gestureType)
        if (action == Action.Disabled) return
        performGestureActionHaptic()
        if (action == Action.OpenApp) {
            openGestureApp(gestureType)
        } else {
            handleOtherAction(action)
        }
    }

    private fun handleSwipeUp() {
        if (totalPages > 1 && currentPage < totalPages - 1) {
            switchToPage(currentPage + 1)
        } else {
            handleGesture(GestureType.SWIPE_UP)
        }
    }

    private fun handleSwipeDown() {
        if (totalPages > 1 && currentPage > 0) {
            switchToPage(currentPage - 1)
        } else {
            handleGesture(GestureType.SWIPE_DOWN)
        }
    }

    private fun createGestureListener(
        view: View? = null,
        onLongClick: () -> Unit = {},
        onViewLongClick: ((View) -> Unit)? = null,
        onClick: (View) -> Unit = {},
    ): View.OnTouchListener =
        object : SwipeTouchListener(requireContext(), view) {
            override fun onSwipeLeft() = handleGesture(GestureType.SWIPE_LEFT)

            override fun onSwipeRight() = handleGesture(GestureType.SWIPE_RIGHT)

            override fun onSwipeUp() = handleSwipeUp()

            override fun onSwipeDown() = handleSwipeDown()

            override fun onDoubleClick() = handleGesture(GestureType.DOUBLE_TAP)

            override fun onLongClick() = onLongClick()

            override fun onLongClick(view: View) {
                onViewLongClick?.invoke(view)
            }

            override fun onClick(view: View) = onClick(view)
        }

    private fun updateAppCountForPage(appsCount: Int) {
        val currentAppCount = binding.homeAppsLayout.childCount

        if (currentAppCount < appsCount) {
            for (i in currentAppCount until appsCount) {
                val view = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                view.apply {
                    textSize = 41f
                    setOnTouchListener(
                        createGestureListener(
                            view = this,
                            onClick = { v -> this@HomeFragment.onClick(v) },
                        ),
                    )
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    gravity = android.view.Gravity.CENTER
                }
                binding.homeAppsLayout.addView(view)
            }
        } else if (currentAppCount > appsCount) {
            binding.homeAppsLayout.removeViews(appsCount, currentAppCount - appsCount)
        }
    }

    private fun getAppDisplayName(appModel: AppModel): String {
        val appName = appModel.appLabel
        if (!prefs.showNotificationIndicator) return appName

        val packagesWithNotifications = LumaNotificationListener.getActiveNotificationPackages()
        val hasNotification = packagesWithNotifications.contains(appModel.appPackage)
        return if (hasNotification) "$appName*" else appName
    }

    private fun refreshAppNames() {
        val appsPerPage = prefs.getAppsPerPage(currentPage + 1)
        val startIndex = currentPage * HomeLayout.APPS_PER_PAGE

        updateAppCountForPage(appsPerPage)

        for (i in 0 until appsPerPage) {
            val appIndex = startIndex + i
            val view = binding.homeAppsLayout.getChildAt(i)
            if (view is TextView) {
                val appModel = prefs.getHomeAppModel(appIndex)
                view.text = getAppDisplayName(appModel)
                view.id = appIndex
            }
        }

        updatePageIndicator()
    }

    private fun performAppTapHaptic() {
        performAppTapHapticFeedback(requireContext())
    }

    private fun performLongPressHaptic() {
        performLongPressHapticFeedback(requireContext())
    }

    private fun performGestureActionHaptic() {
        performGestureActionHapticFeedback(requireContext())
    }

    private fun performStatusBarPressHaptic() {
        performStatusBarPressHapticFeedback(requireContext())
    }
}
