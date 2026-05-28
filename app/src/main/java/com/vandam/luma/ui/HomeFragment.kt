package com.vandam.luma.ui

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import com.vandam.luma.data.AppEntryType
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants.Action
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.Tool
import com.vandam.luma.databinding.FragmentHomeBinding
import com.vandam.luma.helper.*
import com.vandam.luma.helper.LumaNotificationListener
import com.vandam.luma.listener.SwipeTouchListener
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private const val TAG = "HomeFragment"
private const val PAGE_INDICATOR_TOUCH_TARGET_DP = 48

private data class PageIndicatorConfig(
    val totalPages: Int,
    val position: Prefs.PageIndicatorPosition,
)

private data class PageIndicatorTouchTarget(
    val view: View,
    val localBounds: Rect,
    val hitBounds: Rect,
    val slopBounds: Rect,
    val centerX: Int,
    val centerY: Int,
)

private class PageIndicatorTouchHandler(
    private val targets: List<PageIndicatorTouchTarget>,
) {
    private var activeTarget: PageIndicatorTouchTarget? = null
    private val missLocation = -10000f

    fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTarget =
                    targets
                        .filter { it.hitBounds.contains(x, y) }
                        .minByOrNull { target ->
                            val dx = x - target.centerX
                            val dy = y - target.centerY
                            (dx * dx) + (dy * dy)
                        }
                val target = activeTarget ?: return false
                dispatchToTarget(target, event, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val target = activeTarget ?: return false
                dispatchToTarget(target, event, target.slopBounds.contains(x, y))
                return true
            }

            MotionEvent.ACTION_UP -> {
                val target = activeTarget ?: return false
                val sendHit = target.slopBounds.contains(x, y)
                activeTarget = null
                dispatchToTarget(target, event, sendHit)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val target = activeTarget ?: return false
                activeTarget = null
                dispatchToTarget(target, event, false)
                return true
            }

            else -> return false
        }
    }

    private fun dispatchToTarget(
        target: PageIndicatorTouchTarget,
        event: MotionEvent,
        sendHit: Boolean,
    ) {
        val targetEvent = MotionEvent.obtain(event)
        if (sendHit) {
            targetEvent.setLocation(target.view.width / 2f, target.view.height / 2f)
        } else {
            targetEvent.setLocation(missLocation, missLocation)
        }
        target.view.dispatchTouchEvent(targetEvent)
        targetEvent.recycle()
    }
}

class HomeFragment :
    Fragment(),
    View.OnClickListener {
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var currentPage = 0
    private var totalPages = 1
    private var pageIndicatorLayout: LinearLayout? = null
    private var pageIndicatorConfig: PageIndicatorConfig? = null
    private var pageIndicatorTouchHandler: PageIndicatorTouchHandler? = null
    private var visiblePageApps: List<AppModel> = emptyList()

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

        return view
    }

    override fun onDestroyView() {
        pageIndicatorTouchHandler = null
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        currentPage = viewModel.getCurrentHomePage()

        initObservers()
        initPageNavigation()
        initSwipeTouchListener()
        binding.touchArea.post { syncUnlockGateHomeContentTop() }
    }

    override fun onResume() {
        super.onResume()
        HomeCleanupHelper.setOnHomeCleanupCallback {
            refreshHomeStructure()
            refreshHomeBadges()
        }
        totalPages = prefs.homePages
        currentPage = viewModel.getCurrentHomePage()
        if (currentPage >= totalPages) currentPage = totalPages - 1
        viewModel.setCurrentHomePage(currentPage)
        pageIndicatorLayout = null
        pageIndicatorConfig = null
        refreshHomeStructure()
        refreshHomeBadges()
        syncRepeatedHomeGateEligibility()
        syncUnlockGateHomeContentTop()
    }

    override fun onPause() {
        super.onPause()
        HomeCleanupHelper.setOnHomeCleanupCallback(null)
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

    private fun initSwipeTouchListener() {
        val gestureListener =
            createGestureListener(
                onLongClick = {
                    try {
                        performLongPressHaptic()
                        findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    } catch (_: Exception) {
                    }
                },
            )
        binding.touchArea.setOnTouchListener(
            View.OnTouchListener { view, motionEvent ->
                if (pageIndicatorTouchHandler?.onTouchEvent(motionEvent) == true) {
                    true
                } else {
                    gestureListener.onTouch(view, motionEvent)
                }
            },
        )
    }

    private fun initPageNavigation() {
        totalPages = prefs.homePages
        if (currentPage >= totalPages) currentPage = totalPages - 1
        refreshHomeStructure()
        refreshHomeBadges()
    }

    private fun updatePageIndicator() {
        if (totalPages < 2) {
            currentPage = 0
            removePageIndicator()
            pageIndicatorLayout = null
            return
        }

        val position = prefs.pageIndicatorPosition
        if (position == Prefs.PageIndicatorPosition.Hidden) {
            removePageIndicator()
            return
        }

        val config = PageIndicatorConfig(totalPages = totalPages, position = position)
        if (pageIndicatorLayout != null && pageIndicatorConfig == config) {
            syncPageIndicatorSelection()
            return
        }

        removePageIndicator()

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
                    when (position) {
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
        pageIndicatorConfig = config
        installPageIndicatorTouchHandler(newLayout)
    }

    private fun removePageIndicator() {
        pageIndicatorTouchHandler = null
        binding.mainLayout.findViewWithTag<View>("pageIndicator")?.let {
            binding.mainLayout.removeView(it)
        }
        pageIndicatorLayout = null
        pageIndicatorConfig = null
    }

    private fun syncPageIndicatorSelection() {
        val indicatorLayout = pageIndicatorLayout ?: return
        for (index in 0 until indicatorLayout.childCount) {
            val circle = indicatorLayout.getChildAt(index)
            circle.setBackgroundResource(
                if (index == currentPage) {
                    R.drawable.filled_circle
                } else {
                    R.drawable.hollow_circle
                },
            )
        }
    }

    private fun installPageIndicatorTouchHandler(indicatorLayout: LinearLayout) {
        val targetSizePx = (PAGE_INDICATOR_TOUCH_TARGET_DP * resources.displayMetrics.density).toInt()
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        binding.mainLayout.post {
            if (_binding == null || pageIndicatorLayout !== indicatorLayout) return@post

            val targets = mutableListOf<PageIndicatorTouchTarget>()
            for (index in 0 until indicatorLayout.childCount) {
                val circle = indicatorLayout.getChildAt(index)
                val localBounds = Rect().apply {
                    circle.getDrawingRect(this)
                    binding.mainLayout.offsetDescendantRectToMyCoords(circle, this)
                }
                val hitBounds =
                    Rect().apply {
                        val location = IntArray(2)
                        circle.getLocationOnScreen(location)
                        set(location[0], location[1], location[0] + circle.width, location[1] + circle.height)
                    }
                val horizontalInset = ((targetSizePx - hitBounds.width()).coerceAtLeast(0)) / 2
                val verticalInset = ((targetSizePx - hitBounds.height()).coerceAtLeast(0)) / 2
                localBounds.inset(-horizontalInset, -verticalInset)
                hitBounds.inset(-horizontalInset, -verticalInset)
                targets +=
                    PageIndicatorTouchTarget(
                        view = circle,
                        localBounds = Rect(localBounds),
                        hitBounds = Rect(hitBounds),
                        slopBounds =
                            Rect(hitBounds).apply {
                                inset(-touchSlop, -touchSlop)
                            },
                        centerX = hitBounds.centerX(),
                        centerY = hitBounds.centerY(),
                    )
            }

            pageIndicatorTouchHandler = targets.takeIf { it.isNotEmpty() }?.let(::PageIndicatorTouchHandler)
        }
    }

    private fun switchToPage(page: Int) {
        if (page >= 0 && page < totalPages) {
            currentPage = page
            viewModel.setCurrentHomePage(currentPage)
            refreshHomeStructure()
            refreshHomeBadges()
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
        pageIndicatorConfig = null
        refreshHomeStructure()
        refreshHomeBadges()
        syncRepeatedHomeGateEligibility()
    }

    private fun syncRepeatedHomeGateEligibility() {
        (activity as? MainActivity)?.syncRepeatedHomeGateEligibility()
    }

    private fun lockscreenStatusBarInsetPx(): Int = resources.getDimensionPixelSize(R.dimen.lockscreen_gate_home_content_top)

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
                merge(
                    LumaNotificationListener.badgeChangeVersion.map { Unit },
                    PhoneSignalHelper.changeVersion.map { Unit },
                ).collect {
                    refreshHomeBadges()
                }
            }
        }
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

    private fun resolveTool(appModel: AppModel): Tool? =
        Tool.fromPackageName(appModel.appPackage) ?: if (appModel.entryType == AppEntryType.Tool) Tool.fromId(appModel.appActivityName) else null

    private fun getAppDisplayName(
        appModel: AppModel,
        packagesWithNotifications: Set<String>,
        hasPhoneSignal: Boolean,
    ): String {
        val appName = appModel.appLabel
        if (!prefs.showNotificationIndicator) return appName

        val hasNotification =
            when (resolveTool(appModel)) {
                Tool.Phone -> hasPhoneSignal
                else -> packagesWithNotifications.contains(appModel.appPackage)
            }
        return if (hasNotification) "$appName*" else appName
    }

    private fun refreshHomeStructure() {
        totalPages = prefs.homePages
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1
            viewModel.setCurrentHomePage(currentPage)
        }
        val appsPerPage = prefs.getAppsPerPage(currentPage + 1)
        val startIndex = currentPage * HomeLayout.APPS_PER_PAGE
        visiblePageApps =
            List(appsPerPage) { index ->
                prefs.getHomeAppModel(startIndex + index)
            }

        updatePageIndicator()
        updateAppCountForPage(appsPerPage)
        val availableLabelWidth =
            (
                binding.homeAppsLayout.width
                    .takeIf { it > 0 }
                    ?: resources.displayMetrics.widthPixels
            ) - binding.homeAppsLayout.paddingLeft - binding.homeAppsLayout.paddingRight

        for (i in 0 until appsPerPage) {
            val appIndex = startIndex + i
            val view = binding.homeAppsLayout.getChildAt(i)
            if (view is TextView) {
                val appModel = visiblePageApps[i]
                view.maxWidth = availableLabelWidth
                view.text = appModel.displayName
                view.id = appIndex
            }
        }
    }

    private fun refreshHomeBadges() {
        if (_binding == null) return
        val pageApps = visiblePageApps
        if (pageApps.isEmpty()) return

        val packagesWithNotifications =
            if (prefs.showNotificationIndicator) {
                LumaNotificationListener.getActiveNotificationPackages()
            } else {
                emptySet()
            }
        val hasPhoneSignal =
            prefs.showNotificationIndicator &&
                pageApps.any { resolveTool(it) == Tool.Phone } &&
                PhoneSignalHelper.getCachedUnreadPhoneSignal(requireContext())

        for (i in pageApps.indices) {
            val view = binding.homeAppsLayout.getChildAt(i)
            if (view is TextView) {
                view.text = getAppDisplayName(pageApps[i], packagesWithNotifications, hasPhoneSignal)
            }
        }
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
