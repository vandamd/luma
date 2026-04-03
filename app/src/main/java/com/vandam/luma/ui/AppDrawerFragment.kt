package com.vandam.luma.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.UserManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vandam.luma.MainViewModel
import com.vandam.luma.R
import com.vandam.luma.data.AppEntryType
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants.AppDrawerFlag
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.Tool
import com.vandam.luma.databinding.FragmentAppDrawerBinding
import com.vandam.luma.helper.ActionService
import com.vandam.luma.helper.LumaNotificationListener
import com.vandam.luma.helper.performGestureActionHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.style.isDarkTheme
import com.vandam.luma.ui.compose.SettingsComposable.HeaderIconButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import kotlinx.coroutines.launch

class AppDrawerFragment : Fragment() {
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    private lateinit var flag: AppDrawerFlag
    private lateinit var appAdapter: AppDrawerAdapter
    private var allApps: List<AppModel> = emptyList()
    private var n: Int = 0
    private var showHiddenApps: Boolean = false
    private var restoreUnlockGateOnBack = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)

        val flagString = arguments?.getString("flag", AppDrawerFlag.LaunchApp.toString()) ?: AppDrawerFlag.LaunchApp.toString()
        flag = AppDrawerFlag.valueOf(flagString)
        n = arguments?.getInt("n", 0) ?: 0
        showHiddenApps = Prefs.getInstance(requireContext()).showHiddenAppsInHomePicker
        restoreUnlockGateOnBack = arguments?.getBoolean(RESTORE_UNLOCK_GATE_ON_BACK, false) == true

        binding.headerCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        renderHeader()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        allApps = emptyList()
        _binding = null
    }

    @SuppressLint("RtlHardcoded")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        registerRestoreUnlockGateOnBackCallback()

        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val prefs = Prefs.getInstance(requireContext())

        if (shouldAllowHiddenAppsToggle()) {
            viewModel.getAppList(includeHidden = true)
        }

        appAdapter =
            AppDrawerAdapter(
                requireContext(),
                AppDrawerConfig(
                    gravity = Gravity.CENTER,
                    clickListener = appClickListener(viewModel, flag, n),
                    appLongPressListener =
                        if (flag == AppDrawerFlag.LaunchApp ||
                            flag == AppDrawerFlag.HiddenApps
                        ) {
                            appLongPressListener()
                        } else {
                            null
                        },
                    showToolIcon = prefs.showAppDrawerToolIcons,
                    showPinIcon = prefs.showAppDrawerPinIcons,
                ),
            )

        initViewModel(flag, viewModel)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appAdapter
        binding.recyclerView.addOnItemTouchListener(swipeBackTouchListener())

        viewLifecycleOwner.lifecycleScope.launch {
            LumaNotificationListener.changeVersion.collect {
                val packages = LumaNotificationListener.getActiveNotificationPackages()
                appAdapter.updateNotifications(packages)
            }
        }
    }

    private fun registerRestoreUnlockGateOnBackCallback() {
        if (!restoreUnlockGateOnBack) return
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().popBackStack()
                    ActionService.instance()?.restoreUnlockGate()
                }
            },
        )
    }

    private fun initViewModel(
        flag: AppDrawerFlag,
        viewModel: MainViewModel,
    ) {
        viewModel.hiddenApps.observe(viewLifecycleOwner) {
            if (flag != AppDrawerFlag.HiddenApps) return@observe
            it?.let { appList ->
                binding.listEmptyHint.visibility = if (appList.isEmpty()) View.VISIBLE else View.GONE
                populateAppList(appList)
            }
        }

        viewModel.appList.observe(viewLifecycleOwner) {
            if (flag == AppDrawerFlag.HiddenApps) return@observe
            it?.let { appList ->
                allApps = appList
                updateDisplayedApps()
            }
        }
    }

    private fun renderHeader() {
        val context = requireContext()
        val headerTitle =
            when (flag) {
                AppDrawerFlag.SetHomeApp -> context.getString(R.string.app_drawer_select_rename)
                AppDrawerFlag.HiddenApps -> context.getString(R.string.app_drawer_hidden_apps)
                else -> context.getString(R.string.app_drawer_title)
            }

        binding.headerCompose.setContent {
            SettingsTheme(isDarkTheme(Prefs.getInstance(context))) {
                SettingsHeader(
                    title = headerTitle,
                    onBack = { findNavController().popBackStack() },
                    trailingContent =
                        if (shouldAllowHiddenAppsToggle()) {
                            {
                                HeaderIconButton(
                                    iconRes = if (showHiddenApps) R.drawable.visibility else R.drawable.visibility_off,
                                    contentDescription =
                                        context.getString(
                                            if (showHiddenApps) {
                                                R.string.content_desc_hide_hidden_apps
                                            } else {
                                                R.string.content_desc_show_hidden_apps
                                            },
                                        ),
                                    onClick = {
                                        showHiddenApps = !showHiddenApps
                                        Prefs.getInstance(context).showHiddenAppsInHomePicker = showHiddenApps
                                        renderHeader()
                                        updateDisplayedApps()
                                    },
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }

    private fun updateDisplayedApps() {
        if (flag == AppDrawerFlag.HiddenApps) return
        val displayedApps =
            if (shouldAllowHiddenAppsToggle() && showHiddenApps) {
                allApps
            } else {
                filterHiddenApps(allApps)
            }

        binding.listEmptyHint.visibility = if (displayedApps.isEmpty()) View.VISIBLE else View.GONE
        populateAppList(displayedApps)
    }

    private fun filterHiddenApps(apps: List<AppModel>): List<AppModel> {
        val prefs = Prefs.getInstance(requireContext())
        val userManager = requireContext().getSystemService(android.content.Context.USER_SERVICE) as UserManager
        val hiddenShortcutIds = prefs.hiddenShortcutIds

        return apps.filterNot { app ->
            when (app.entryType) {
                AppEntryType.PinnedShortcut -> hiddenShortcutIds.contains(app.appActivityName)
                AppEntryType.Tool -> Tool.fromPackageName(app.appPackage)?.let(prefs::isToolHidden) ?: false
                AppEntryType.LauncherApp -> prefs.isAppHidden(app.appPackage, userManager.getSerialNumberForUser(app.user))
            }
        }
    }

    private fun populateAppList(apps: List<AppModel>) {
        appAdapter.setAppList(apps.toMutableList())
        appAdapter.updateNotifications(LumaNotificationListener.getActiveNotificationPackages())
    }

    private fun shouldAllowHiddenAppsToggle(): Boolean =
        when (flag) {
            AppDrawerFlag.SetHomeApp,
            AppDrawerFlag.SetCameraKeyPress,
            AppDrawerFlag.SetCameraKeyLongPress,
            AppDrawerFlag.SetScrollwheelButtonPress,
            AppDrawerFlag.SetScrollwheelButtonLongPress,
            AppDrawerFlag.SetSwipeLeft,
            AppDrawerFlag.SetSwipeRight,
            AppDrawerFlag.SetSwipeUp,
            AppDrawerFlag.SetSwipeDown,
            AppDrawerFlag.SetDoubleTap,
            AppDrawerFlag.SetLockscreenSwipeLeft,
            AppDrawerFlag.SetLockscreenSwipeRight,
            AppDrawerFlag.SetLockscreenSwipeUp,
            AppDrawerFlag.SetLockscreenSwipeDown,
            AppDrawerFlag.SetLockscreenDoubleTap,
            AppDrawerFlag.SetStatusBarCellular,
            AppDrawerFlag.SetStatusBarTime,
            AppDrawerFlag.SetStatusBarBattery,
            AppDrawerFlag.SetLockscreenShortcut,
            AppDrawerFlag.SetLockscreenDateTap,
            -> true

            AppDrawerFlag.LaunchApp,
            AppDrawerFlag.HiddenApps,
            -> false
        }

    private fun appClickListener(
        viewModel: MainViewModel,
        flag: AppDrawerFlag,
        n: Int = 0,
    ): (appModel: AppModel) -> Unit =
        { appModel ->
            val handled = viewModel.selectedApp(appModel, flag, n, requireActivity())
            if (handled) {
                if (flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.SetHomeApp) {
                    findNavController().popBackStack(R.id.mainFragment, false)
                } else {
                    findNavController().popBackStack()
                }
            }
        }

    private fun appLongPressListener(): (AppModel) -> Unit =
        { appModel ->
            val userManager = requireContext().getSystemService(android.content.Context.USER_SERVICE) as UserManager
            findNavController().navigate(
                R.id.appActionsFragment,
                bundleOf(
                    "appPackage" to appModel.appPackage,
                    "appLabel" to appModel.appLabel,
                    "appAlias" to appModel.appAlias,
                    "appActivityName" to appModel.appActivityName,
                    "isHidden" to (flag == AppDrawerFlag.HiddenApps),
                    "userSerial" to userManager.getSerialNumberForUser(appModel.user),
                ),
            )
        }

    private fun swipeBackTouchListener(): RecyclerView.OnItemTouchListener {
        val density = resources.displayMetrics.density
        val edgeThreshold = 30 * density
        val dragThreshold = 80 * density

        var startX = 0f
        var startY = 0f
        var tracking = false
        var committed = false

        return object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(
                rv: RecyclerView,
                e: MotionEvent,
            ): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        tracking = e.x <= edgeThreshold
                        committed = false
                        startX = e.x
                        startY = e.y
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!tracking) return false
                        val dx = e.x - startX
                        val dy = e.y - startY
                        if (!committed && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                            tracking = false
                            return false
                        }
                        if (!committed && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            committed = true
                        }
                        if (committed && dx > dragThreshold) {
                            tracking = false
                            performGestureActionHapticFeedback(requireContext())
                            if (flag == AppDrawerFlag.LaunchApp) {
                                findNavController().popBackStack(R.id.mainFragment, false)
                            } else {
                                findNavController().popBackStack()
                            }
                            return true
                        }
                    }

                    else -> {
                        tracking = false
                    }
                }
                return false
            }

            override fun onTouchEvent(
                rv: RecyclerView,
                e: MotionEvent,
            ) {}

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        }
    }
}
