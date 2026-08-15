package com.vandam.luma

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.vandam.luma.data.AndroidLauncherApp
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants.Action
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.data.Tool
import com.vandam.luma.data.ToolSyncManager
import com.vandam.luma.data.ToolSyncResult
import com.vandam.luma.databinding.ActivityMainBinding
import com.vandam.luma.helper.ActionExecutionCallbacks
import com.vandam.luma.helper.ActionService
import com.vandam.luma.helper.ApkInstaller
import com.vandam.luma.helper.BluetoothStatusHelper
import com.vandam.luma.helper.ManagedAppManager
import com.vandam.luma.helper.PhoneSignalHelper
import com.vandam.luma.helper.UnlockGatePhase
import com.vandam.luma.helper.VolumeController
import com.vandam.luma.helper.executeSecondaryAction
import com.vandam.luma.helper.hideStatusBar
import com.vandam.luma.helper.isAccessibilityEnabled
import com.vandam.luma.helper.showStatusBar
import com.vandam.luma.style.DisplayDefaults.withDisplayDefaults
import com.vandam.luma.ui.HomeFragment
import com.vandam.luma.ui.RESTORE_UNLOCK_GATE_ON_BACK
import dev.convex.android.WebSocketState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeController: VolumeController
    private var consumeHandledVolumeKeyUp = false
    private var shouldFinishOnStop = false
    private var toolSyncJob: Job? = null
    private var toolSyncWebSocketJob: Job? = null
    private var toolSyncReconnectWatchdogJob: Job? = null
    private var subscribedAccountNumber: String? = null
    private var lastSyncedToolIds: List<String>? = null
    private var lastSyncedManagedAppIds: List<String>? = null
    private var lastSyncedAndroidApps: List<AndroidLauncherApp>? = null
    private var lastRequestedAppUpdateVersions: Map<String, String>? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withDisplayDefaults())
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(overrideConfiguration.withDisplayDefaults(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs.getInstance(this)
        val themeMode =
            when (prefs.themeMode) {
                Prefs.ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                Prefs.ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(themeMode)
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        if (savedInstanceState == null || navController.currentDestination == null) {
            setupNavGraph()
        }
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        volumeController = VolumeController(this).also { it.init() }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateSystemStatusBarVisibility(destination.id)
            syncRepeatedHomeGateEligibility(destination.id)
        }
        updateSystemStatusBarVisibility(navController.currentDestination?.id)
        syncRepeatedHomeGateEligibility(navController.currentDestination?.id)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (navController.currentDestination?.id == R.id.onboardingWelcomeFragment) {
                        return
                    }
                    if (navController.currentDestination?.id != R.id.mainFragment) {
                        if (!navController.popBackStack()) {
                            handleOnboardingBackFallback(navController.currentDestination?.id)
                        }
                    }
                }
            },
        )

        initObservers()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        notifyUnlockGateLauncherIntent(intent)
        handleLockscreenClockTapIntent(intent)
        handleLockscreenShortcutIntent(intent)
        handleLockscreenDateTapIntent(intent)
        handleLockscreenGestureIntent(intent)
        handleStatusBarSectionIntent(intent)
        sanitizeActivityIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        startToolSyncSubscription()
    }

    override fun onDestroy() {
        stopToolSyncSubscription()
        setLumaForeground(false)
        ActionService.instance()?.setRepeatedHomeGateEligible(false)
        volumeController.destroy()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setLumaForeground(true)
        updateSystemStatusBarVisibility(navController.currentDestination?.id)
        syncRepeatedHomeGateEligibility(navController.currentDestination?.id)
        ManagedAppManager.onResume(this)
    }

    override fun onPause() {
        setLumaForeground(false)
        ActionService.instance()?.setRepeatedHomeGateEligible(false)
        super.onPause()
    }

    override fun onStop() {
        val finishAfterStop = shouldFinishOnStop && !isChangingConfigurations
        shouldFinishOnStop = false
        if (finishAfterStop) {
            finish()
        }
        stopToolSyncSubscription()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        shouldFinishOnStop = true
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val launcherIntent = isLauncherIntent(intent)

        backToHomeScreen()
        if (launcherIntent) {
            resetHomePageImmediately()
        }
        notifyUnlockGateLauncherIntent(intent)
        handleLockscreenClockTapIntent(intent)
        handleLockscreenShortcutIntent(intent)
        handleLockscreenDateTapIntent(intent)
        handleLockscreenGestureIntent(intent)
        handleStatusBarSectionIntent(intent)
        sanitizeActivityIntent(intent)
        syncRepeatedHomeGateEligibility(navController.currentDestination?.id)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_UP && consumeHandledVolumeKeyUp) {
            consumeHandledVolumeKeyUp = false
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        val phase = ActionService.unlockGateState.value.phase
        if (phase == UnlockGatePhase.SecureMask) {
            return super.dispatchKeyEvent(event)
        }

        val handled = volumeController.handleVolumeKey(event.keyCode)
        if (handled) {
            consumeHandledVolumeKeyUp = true
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun initObservers() {
        lifecycleScope.launch {
            ActionService.unlockGateState.collect {
                updateSystemStatusBarVisibility(navController.currentDestination?.id)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun setupNavGraph() {
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            when {
                prefs.accountNumber.isNotBlank() && hasCompletedRequiredOnboardingPermissions() -> R.id.mainFragment
                prefs.accountNumber.isNotBlank() -> R.id.onboardingPermissionsFragment
                prefs.onboardingLoginStarted && hasCompletedRequiredOnboardingPermissions() -> R.id.loginFragment
                prefs.onboardingStarted -> R.id.onboardingPermissionsFragment
                else -> R.id.onboardingWelcomeFragment
            },
        )
        navController.setGraph(graph, null)
    }

    private fun hasCompletedRequiredOnboardingPermissions(): Boolean {
        val hasNotificationPermission =
            NotificationManagerCompat
                .getEnabledListenerPackages(this)
                .contains(packageName)
        val hasWriteSettingsPermission = Settings.System.canWrite(this)
        val hasInstallAppsPermission = ApkInstaller.canRequestPackageInstalls(this)

        return isAccessibilityEnabled(this) &&
            BluetoothStatusHelper.hasBluetoothConnectPermission(this) &&
            PhoneSignalHelper.hasPhoneToolPermissions(this) &&
            hasNotificationPermission &&
            hasWriteSettingsPermission &&
            hasInstallAppsPermission
    }

    private fun handleOnboardingBackFallback(destinationId: Int?) {
        if (destinationId == R.id.onboardingPermissionsFragment && prefs.accountNumber.isNotBlank()) {
            return
        }

        val fallbackDestination =
            when (destinationId) {
                R.id.loginFragment -> {
                    R.id.onboardingPermissionsFragment
                }

                R.id.onboardingPermissionsFragment -> {
                    if (prefs.accountNumber.isNotBlank()) {
                        R.id.mainFragment
                    } else {
                        R.id.onboardingWelcomeFragment
                    }
                }

                else -> {
                    return
                }
            }

        val currentDestinationId = navController.currentDestination?.id ?: return
        navController.navigate(
            fallbackDestination,
            null,
            NavOptions
                .Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(currentDestinationId, true)
                .build(),
        )
    }

    fun onToolSyncApplied() {
        viewModel.resetHomePageNow()
        getVisibleHomeFragment()?.reloadHomeLayoutFromPrefs(resetToFirstPage = true)
    }

    fun restartToolSyncSubscription(
        initialToolIds: List<String>? = null,
        initialManagedAppIds: List<String>? = null,
        initialAndroidApps: List<AndroidLauncherApp>? = null,
        initialRequestedAppUpdateVersions: Map<String, String>? = null,
    ) {
        lastSyncedToolIds = initialToolIds
        lastSyncedManagedAppIds = initialManagedAppIds
        lastSyncedAndroidApps = initialAndroidApps
        lastRequestedAppUpdateVersions = initialRequestedAppUpdateVersions
        stopToolSyncSubscription()
        startToolSyncSubscription()
        ActionService.instance()?.refreshLockscreenMessageSubscription()
    }

    fun logoutToLogin() {
        prefs.accountNumber = ""
        prefs.onboardingStarted = true
        prefs.onboardingLoginStarted = true

        lastSyncedToolIds = null
        lastSyncedManagedAppIds = null
        lastSyncedAndroidApps = null
        lastRequestedAppUpdateVersions = null
        stopToolSyncSubscription()
        ActionService.instance()?.refreshLockscreenMessageSubscription()

        ManagedAppManager.clearSessionWork()

        setupNavGraph()
        updateSystemStatusBarVisibility(navController.currentDestination?.id)
        syncRepeatedHomeGateEligibility(navController.currentDestination?.id)
    }

    private fun startToolSyncSubscription() {
        val accountNumber = prefs.accountNumber
        if (accountNumber.isBlank()) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "Skipping tool sync subscription because account is blank")
            }
            stopToolSyncSubscription()
            return
        }

        if (
            subscribedAccountNumber == accountNumber &&
            toolSyncJob?.isActive == true &&
            toolSyncWebSocketJob?.isActive == true
        ) {
            return
        }

        stopToolSyncSubscription()
        subscribedAccountNumber = accountNumber
        if (!ManagedAppManager.syncPendingInstalledAppsToDashboardForStoredAccount(this)) {
            ManagedAppManager.syncInstalledAppsToDashboard(this, accountNumber)
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Starting tool sync subscription")
        }
        toolSyncJob =
            lifecycleScope.launch {
                try {
                    ToolSyncManager
                        .observeSyncResults(this@MainActivity, accountNumber)
                        .collectLatest { result ->
                            handleToolSyncResult(result, "subscription")
                        }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(LOG_TAG, "Tool sync subscription failed", exception)
                }
            }

        toolSyncWebSocketJob =
            lifecycleScope.launch {
                val application = applicationContext as? LumaApplication ?: return@launch
                var lastState: WebSocketState? = null

                application
                    .getOrCreateConvexClient()
                    ?.webSocketStateFlow
                    ?.collectLatest { state ->
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "Convex websocket state=$state")
                        }

                        when (state) {
                            WebSocketState.CONNECTED -> {
                                toolSyncReconnectWatchdogJob?.cancel()
                                toolSyncReconnectWatchdogJob = null
                            }

                            WebSocketState.CONNECTING -> {
                                if (lastState == WebSocketState.CONNECTED) {
                                    Log.w(
                                        LOG_TAG,
                                        "Convex websocket dropped back to CONNECTING",
                                    )
                                }

                                if (toolSyncReconnectWatchdogJob == null) {
                                    toolSyncReconnectWatchdogJob =
                                        lifecycleScope.launch {
                                            delay(WEBSOCKET_RECONNECT_GRACE_MS)
                                            handleConvexReconnectTimeout(accountNumber, application)
                                        }
                                }
                            }
                        }

                        lastState = state
                    }
            }
    }

    private fun stopToolSyncSubscription() {
        toolSyncJob?.cancel()
        toolSyncWebSocketJob?.cancel()
        toolSyncReconnectWatchdogJob?.cancel()
        toolSyncJob = null
        toolSyncWebSocketJob = null
        toolSyncReconnectWatchdogJob = null
        subscribedAccountNumber = null
    }

    private fun handleConvexReconnectTimeout(
        accountNumber: String,
        application: LumaApplication,
    ) {
        if (subscribedAccountNumber != accountNumber) {
            return
        }

        val currentState = application.getOrCreateConvexClient()?.webSocketStateFlow?.value
        if (currentState != WebSocketState.CONNECTING) {
            return
        }

        Log.w(LOG_TAG, "Convex websocket stuck in CONNECTING, recreating client")
        toolSyncJob?.cancel()
        toolSyncWebSocketJob?.cancel()
        toolSyncJob = null
        toolSyncWebSocketJob = null
        toolSyncReconnectWatchdogJob = null
        subscribedAccountNumber = null
        application.recreateConvexClient()
        startToolSyncSubscription()
        ActionService.instance()?.refreshLockscreenMessageSubscription()
    }

    private fun prefsEnabledToolIds(): Set<String> =
        Tool.entries
            .filter(prefs::isToolEnabled)
            .mapTo(mutableSetOf(), Tool::id)

    private fun handleToolSyncResult(
        result: ToolSyncResult,
        source: String,
    ) {
        when (result) {
            is ToolSyncResult.Success -> {
                val hasSyncBaseline =
                    lastSyncedToolIds != null &&
                        lastSyncedManagedAppIds != null &&
                        lastSyncedAndroidApps != null
                val toolsChanged =
                    if (hasSyncBaseline) {
                        lastSyncedToolIds != result.enabledToolIds
                    } else {
                        prefsEnabledToolIds() != result.enabledToolIds.toSet()
                    }
                val appsChanged =
                    if (hasSyncBaseline) {
                        lastSyncedManagedAppIds != result.enabledAppIds
                    } else {
                        prefs.enabledManagedAppIds != result.enabledAppIds.toSet()
                    }
                val androidAppsChanged =
                    if (hasSyncBaseline) {
                        lastSyncedAndroidApps != result.enabledAndroidApps
                    } else {
                        prefs.enabledAndroidApps.toSet() != result.enabledAndroidApps.toSet()
                    }
                val requestedAppUpdatesChanged =
                    lastRequestedAppUpdateVersions != result.requestedAppUpdateVersions
                val homeLayoutChanged = toolsChanged || appsChanged || androidAppsChanged

                Log.d(
                    LOG_TAG,
                    "Tool sync result from $source changedTools=$toolsChanged changedApps=$appsChanged changedAndroidApps=$androidAppsChanged changedRequestedUpdates=$requestedAppUpdatesChanged",
                )

                val previousManagedAppIds = lastSyncedManagedAppIds ?: prefs.enabledManagedAppIds.toList()
                if (homeLayoutChanged) {
                    when (ToolSyncManager.applySyncResult(this, result)) {
                        is ToolSyncResult.Success -> Unit
                        ToolSyncResult.InvalidAccount -> return
                        is ToolSyncResult.Failure -> return
                    }
                }

                if (homeLayoutChanged || requestedAppUpdatesChanged || !hasSyncBaseline) {
                    lastSyncedToolIds = result.enabledToolIds
                    lastSyncedManagedAppIds = result.enabledAppIds
                    lastSyncedAndroidApps = result.enabledAndroidApps
                    lastRequestedAppUpdateVersions = result.requestedAppUpdateVersions
                    ManagedAppManager.reconcileEnabledApps(
                        this,
                        previousManagedAppIds,
                        result.enabledAppIds,
                        result.requestedAppUpdateVersions,
                    )
                    if (homeLayoutChanged) {
                        onToolSyncApplied()
                    }
                }
            }

            ToolSyncResult.InvalidAccount -> {
                Log.w(LOG_TAG, "Tool sync returned invalid account from $source")
            }

            is ToolSyncResult.Failure -> {
                Log.w(LOG_TAG, "Tool sync failed from $source")
            }
        }
    }

    private fun backToHomeScreen() {
        if (navController.currentDestination?.id != R.id.mainFragment) {
            navController.popBackStack(R.id.mainFragment, false)
        }
    }

    private fun resetHomePageImmediately() {
        viewModel.resetHomePageNow()
        getVisibleHomeFragment()?.resetToFirstPage()
    }

    private fun isLauncherIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN &&
            (
                intent.hasCategory(Intent.CATEGORY_HOME) ||
                    intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            )

    private fun notifyUnlockGateLauncherIntent(intent: Intent?) {
        if (intent == null || !isLauncherIntent(intent)) return
        if (consumePendingUnlockGateHomeLaunch()) return
        if (intent.getBooleanExtra(EXTRA_UNLOCK_GATE_HOME_LAUNCH, false)) return
        ActionService.instance()?.handleLauncherIntent()
    }

    private fun sanitizeActivityIntent(intent: Intent?) {
        if (intent == null) return
        if (
            !intent.getBooleanExtra(EXTRA_UNLOCK_GATE_HOME_LAUNCH, false) &&
            !intent.getBooleanExtra(EXTRA_RUN_LOCKSCREEN_CLOCK_TAP, false) &&
            !intent.getBooleanExtra(EXTRA_RUN_LOCKSCREEN_SHORTCUT, false) &&
            !intent.getBooleanExtra(EXTRA_RUN_LOCKSCREEN_DATE_TAP, false) &&
            intent.getStringExtra(EXTRA_RUN_LOCKSCREEN_GESTURE) == null &&
            intent.getStringExtra(EXTRA_RUN_STATUS_BAR_SECTION) == null
        ) {
            return
        }

        setIntent(
            Intent(intent).apply {
                removeExtra(EXTRA_UNLOCK_GATE_HOME_LAUNCH)
                removeExtra(EXTRA_RUN_LOCKSCREEN_CLOCK_TAP)
                removeExtra(EXTRA_RUN_LOCKSCREEN_SHORTCUT)
                removeExtra(EXTRA_RUN_LOCKSCREEN_DATE_TAP)
                removeExtra(EXTRA_RUN_LOCKSCREEN_GESTURE)
                removeExtra(EXTRA_RUN_STATUS_BAR_SECTION)
            },
        )
    }

    fun syncRepeatedHomeGateEligibility(destinationId: Int? = navController.currentDestination?.id) {
        val isEligible =
            destinationId == R.id.mainFragment &&
                (getVisibleHomeFragment()?.isOnFirstPage() ?: (viewModel.getCurrentHomePage() == 0))
        ActionService.instance()?.setRepeatedHomeGateEligible(isEligible)
    }

    private fun updateSystemStatusBarVisibility(destinationId: Int?) {
        val unlockGateVisible = ActionService.unlockGateState.value.visible
        val shouldShowSystemStatusBar =
            destinationId == R.id.mainFragment &&
                if (unlockGateVisible) {
                    prefs.showsAndroidStatusBarOnLockscreen()
                } else {
                    prefs.showsAndroidStatusBarOnHomescreen()
                }

        if (shouldShowSystemStatusBar) {
            showStatusBar(this)
        } else {
            hideStatusBar(this)
        }
    }

    private fun getVisibleHomeFragment(): HomeFragment? {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return null
        return navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment
    }

    private fun handleLockscreenShortcutIntent(intent: Intent?) {
        handleLockscreenActionIntent(
            intent = intent,
            extraName = EXTRA_RUN_LOCKSCREEN_SHORTCUT,
            action = prefs.getLockscreenShortcutAction(),
            appModel = prefs.getLockscreenShortcutApp(),
        )
    }

    private fun handleLockscreenClockTapIntent(intent: Intent?) {
        handleLockscreenActionIntent(
            intent = intent,
            extraName = EXTRA_RUN_LOCKSCREEN_CLOCK_TAP,
            action = prefs.getLockscreenClockTapAction(),
            appModel = prefs.getLockscreenClockTapApp(),
        )
    }

    private fun handleLockscreenDateTapIntent(intent: Intent?) {
        handleLockscreenActionIntent(
            intent = intent,
            extraName = EXTRA_RUN_LOCKSCREEN_DATE_TAP,
            action = prefs.getLockscreenDateTapAction(),
            appModel = prefs.getLockscreenDateTapApp(),
        )
    }

    private fun handleLockscreenGestureIntent(intent: Intent?) {
        val gestureName = intent?.getStringExtra(EXTRA_RUN_LOCKSCREEN_GESTURE) ?: return
        intent.removeExtra(EXTRA_RUN_LOCKSCREEN_GESTURE)

        val gestureType = runCatching { GestureType.valueOf(gestureName) }.getOrNull() ?: return
        val action = prefs.getGestureAction(gestureType, GestureScope.Lockscreen)
        if (action == Action.Disabled) return

        if (action == Action.OpenApp) {
            val appModel = prefs.getGestureApp(gestureType, GestureScope.Lockscreen)
            if (appModel.appPackage.isEmpty()) return
            viewModel.selectedApp(
                appModel,
                launchContext = this,
            )
            return
        }

        executeSecondaryAction(
            context = this,
            action = action,
            callbacks = lockscreenNavigationCallbacks(),
        )
    }

    private fun handleStatusBarSectionIntent(intent: Intent?) {
        val sectionName = intent?.getStringExtra(EXTRA_RUN_STATUS_BAR_SECTION) ?: return
        intent.removeExtra(EXTRA_RUN_STATUS_BAR_SECTION)

        val section = runCatching { StatusBarSectionType.valueOf(sectionName) }.getOrNull() ?: return
        val action = prefs.getSectionAction(section)
        if (action == Action.Disabled) return

        if (action == Action.OpenApp) {
            val appModel = prefs.getSectionApp(section)
            if (appModel.appPackage.isEmpty()) return
            viewModel.selectedApp(
                appModel,
                launchContext = this,
            )
            return
        }

        executeSecondaryAction(
            context = this,
            action = action,
            callbacks = lockscreenNavigationCallbacks(),
        )
    }

    private fun handleLockscreenActionIntent(
        intent: Intent?,
        extraName: String,
        action: Action,
        appModel: AppModel,
    ) {
        if (intent?.getBooleanExtra(extraName, false) != true) return
        intent.removeExtra(extraName)

        if (action == Action.OpenApp) {
            viewModel.selectedApp(
                appModel,
                launchContext = this,
            )
            return
        }

        executeSecondaryAction(
            context = this,
            action = action,
            callbacks = lockscreenNavigationCallbacks(),
        )
    }

    private fun lockscreenNavigationCallbacks(): ActionExecutionCallbacks =
        ActionExecutionCallbacks(
            showNotificationList = { showNotificationList(restoreUnlockGateOnBack = true) },
        )

    private fun showNotificationList(restoreUnlockGateOnBack: Boolean = false) {
        try {
            navController.navigate(
                R.id.action_mainFragment_to_notificationListFragment,
                bundleOf(RESTORE_UNLOCK_GATE_ON_BACK to restoreUnlockGateOnBack),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val LOG_TAG = "ToolSync"
        private const val WEBSOCKET_RECONNECT_GRACE_MS = 60_000L
        const val EXTRA_UNLOCK_GATE_HOME_LAUNCH = "com.vandam.luma.extra.UNLOCK_GATE_HOME_LAUNCH"
        const val EXTRA_RUN_LOCKSCREEN_CLOCK_TAP = "com.vandam.luma.extra.RUN_LOCKSCREEN_CLOCK_TAP"
        const val EXTRA_RUN_LOCKSCREEN_SHORTCUT = "com.vandam.luma.extra.RUN_LOCKSCREEN_SHORTCUT"
        const val EXTRA_RUN_LOCKSCREEN_DATE_TAP = "com.vandam.luma.extra.RUN_LOCKSCREEN_DATE_TAP"
        const val EXTRA_RUN_LOCKSCREEN_GESTURE = "com.vandam.luma.extra.RUN_LOCKSCREEN_GESTURE"
        const val EXTRA_RUN_STATUS_BAR_SECTION = "com.vandam.luma.extra.RUN_STATUS_BAR_SECTION"
        private const val PENDING_UNLOCK_GATE_HOME_LAUNCH_TIMEOUT_MS = 3000L

        @Volatile
        private var lumaForeground = false

        @Volatile
        private var pendingUnlockGateHomeLaunchUntilUptimeMs = 0L

        fun isLumaForeground(): Boolean = lumaForeground

        private fun setLumaForeground(isForeground: Boolean) {
            lumaForeground = isForeground
        }

        private fun markPendingUnlockGateHomeLaunch() {
            pendingUnlockGateHomeLaunchUntilUptimeMs =
                SystemClock.uptimeMillis() + PENDING_UNLOCK_GATE_HOME_LAUNCH_TIMEOUT_MS
        }

        private fun consumePendingUnlockGateHomeLaunch(): Boolean {
            val now = SystemClock.uptimeMillis()
            val deadline = pendingUnlockGateHomeLaunchUntilUptimeMs
            if (deadline == 0L) return false
            pendingUnlockGateHomeLaunchUntilUptimeMs = 0L
            return now <= deadline
        }

        fun createLumaHomeIntent(
            context: Context,
            suppressLauncherIntentHandling: Boolean = false,
        ): Intent =
            Intent(Intent.ACTION_MAIN).apply {
                if (suppressLauncherIntentHandling) {
                    markPendingUnlockGateHomeLaunch()
                }
                setClass(context, MainActivity::class.java)
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (suppressLauncherIntentHandling) {
                    putExtra(EXTRA_UNLOCK_GATE_HOME_LAUNCH, true)
                }
            }

        fun createUnlockGateHomeIntent(context: Context): Intent = createLumaHomeIntent(context, suppressLauncherIntentHandling = true)

        fun createLockscreenClockTapIntent(context: Context): Intent =
            createLumaHomeIntent(context, suppressLauncherIntentHandling = true).apply {
                putExtra(EXTRA_RUN_LOCKSCREEN_CLOCK_TAP, true)
            }

        fun createLockscreenShortcutIntent(context: Context): Intent =
            createLumaHomeIntent(context, suppressLauncherIntentHandling = true).apply {
                putExtra(EXTRA_RUN_LOCKSCREEN_SHORTCUT, true)
            }

        fun createLockscreenDateTapIntent(context: Context): Intent =
            createLumaHomeIntent(context, suppressLauncherIntentHandling = true).apply {
                putExtra(EXTRA_RUN_LOCKSCREEN_DATE_TAP, true)
            }

        fun createLockscreenGestureIntent(
            context: Context,
            gestureType: GestureType,
        ): Intent =
            createLumaHomeIntent(context, suppressLauncherIntentHandling = true).apply {
                putExtra(EXTRA_RUN_LOCKSCREEN_GESTURE, gestureType.name)
            }

        fun createStatusBarSectionIntent(
            context: Context,
            section: StatusBarSectionType,
        ): Intent =
            createLumaHomeIntent(context, suppressLauncherIntentHandling = true).apply {
                putExtra(EXTRA_RUN_STATUS_BAR_SECTION, section.name)
            }
    }
}
