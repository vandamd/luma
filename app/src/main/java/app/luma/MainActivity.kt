package app.luma

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import app.luma.data.AppModel
import app.luma.data.Constants
import app.luma.data.Constants.Action
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.GestureScope
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.databinding.ActivityMainBinding
import app.luma.helper.ActionExecutionCallbacks
import app.luma.helper.ActionService
import app.luma.helper.HomeCleanupHelper
import app.luma.helper.executeSecondaryAction
import app.luma.helper.hideStatusBar
import app.luma.helper.showStatusBar
import app.luma.helper.showToast
import app.luma.style.DisplayDefaults.withDisplayDefaults
import app.luma.ui.HomeFragment
import app.luma.ui.RESTORE_UNLOCK_GATE_ON_BACK
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var consumeHandledVolumeKeyUp = false
    private var shouldFinishOnStop = false

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
                Prefs.ThemeMode.Automatic -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        AppCompatDelegate.setDefaultNightMode(themeMode)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
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
                    if (navController.currentDestination?.id != R.id.mainFragment) {
                        navController.popBackStack()
                    }
                }
            },
        )

        initObservers()
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        HomeCleanupHelper.setOnAppListCleanupCallback { viewModel.getAppList() }

        handlePinShortcutRequest(intent)
        notifyUnlockGateLauncherIntent(intent)
        handleLockscreenShortcutIntent(intent)
        handleLockscreenDateTapIntent(intent)
        handleLockscreenGestureIntent(intent)
        handleStatusBarSectionIntent(intent)
        sanitizeActivityIntent(intent)
    }

    override fun onDestroy() {
        setLumaForeground(false)
        ActionService.instance()?.setRepeatedHomeGateEligible(false)
        HomeCleanupHelper.setOnAppListCleanupCallback(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setLumaForeground(true)
        updateSystemStatusBarVisibility(navController.currentDestination?.id)
        syncRepeatedHomeGateEligibility(navController.currentDestination?.id)
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
        super.onStop()
    }

    override fun onUserLeaveHint() {
        shouldFinishOnStop = true
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePinShortcutRequest(intent)
        val launcherIntent = isLauncherIntent(intent)

        backToHomeScreen()
        if (launcherIntent) {
            resetHomePageImmediately()
        }
        notifyUnlockGateLauncherIntent(intent)
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

        if (event.action != KeyEvent.ACTION_DOWN || navController.currentDestination?.id != R.id.mainFragment) {
            return super.dispatchKeyEvent(event)
        }

        val homeFragment = getVisibleHomeFragment() ?: return super.dispatchKeyEvent(event)
        val handled = homeFragment.handleHardwareVolumeKey(event.keyCode)
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

    private fun handlePinShortcutRequest(intent: Intent?) {
        if (intent == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (intent.action != Constants.REQUEST_CONFIRM_PIN_SHORTCUT) return

        val launcherApps = getSystemService(LauncherApps::class.java) ?: return
        val request =
            try {
                launcherApps.getPinItemRequest(intent)
            } catch (_: Exception) {
                return
            } ?: return

        if (!request.isValid) return
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return

        val shortcutInfo = request.shortcutInfo ?: return
        val shortcutPackage = shortcutInfo.`package` ?: return
        val shortcutId = shortcutInfo.id ?: return

        val label =
            shortcutInfo.shortLabel?.toString()
                ?: shortcutInfo.longLabel?.toString()
                ?: "Shortcut"

        val accepted =
            try {
                request.accept()
            } catch (_: Exception) {
                false
            }

        if (!accepted) {
            showToast(this, getString(R.string.toast_unable_to_add_shortcut))
            return
        }

        prefs.addPinnedShortcut(shortcutPackage, shortcutId, label)

        showToast(this, getString(R.string.toast_added_to_app_drawer))
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
                AppDrawerFlag.LaunchApp,
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
                AppDrawerFlag.LaunchApp,
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
                AppDrawerFlag.LaunchApp,
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
            showAppList = { showAppList(restoreUnlockGateOnBack = true) },
            showNotificationList = { showNotificationList(restoreUnlockGateOnBack = true) },
        )

    private fun showAppList(restoreUnlockGateOnBack: Boolean = false) {
        viewModel.getAppList()
        try {
            navController.navigate(
                R.id.appListFragment,
                bundleOf(
                    "flag" to AppDrawerFlag.LaunchApp.toString(),
                    RESTORE_UNLOCK_GATE_ON_BACK to restoreUnlockGateOnBack,
                ),
            )
        } catch (_: Exception) {
        }
    }

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
        const val EXTRA_UNLOCK_GATE_HOME_LAUNCH = "app.luma.extra.UNLOCK_GATE_HOME_LAUNCH"
        const val EXTRA_RUN_LOCKSCREEN_SHORTCUT = "app.luma.extra.RUN_LOCKSCREEN_SHORTCUT"
        const val EXTRA_RUN_LOCKSCREEN_DATE_TAP = "app.luma.extra.RUN_LOCKSCREEN_DATE_TAP"
        const val EXTRA_RUN_LOCKSCREEN_GESTURE = "app.luma.extra.RUN_LOCKSCREEN_GESTURE"
        const val EXTRA_RUN_STATUS_BAR_SECTION = "app.luma.extra.RUN_STATUS_BAR_SECTION"
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
