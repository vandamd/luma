package com.vandam.luma.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vandam.luma.MainViewModel
import com.vandam.luma.R
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Prefs
import com.vandam.luma.databinding.FragmentAppsBinding
import com.vandam.luma.helper.ActionService
import com.vandam.luma.helper.LumaNotificationListener
import com.vandam.luma.helper.performGestureActionHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.style.isDarkTheme
import com.vandam.luma.ui.compose.SettingsHeader
import kotlinx.coroutines.launch

class AppsFragment : Fragment() {
    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appAdapter: AppPickerAdapter
    private var restoreUnlockGateOnBack = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        restoreUnlockGateOnBack = arguments?.getBoolean(RESTORE_UNLOCK_GATE_ON_BACK, false) == true

        binding.headerCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        renderHeader()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
        viewModel.getAppList()

        appAdapter =
            AppPickerAdapter(
                AppPickerConfig(
                    gravity = Gravity.CENTER,
                    clickListener = appClickListener(viewModel),
                    showToolIcon = prefs.showAppPickerToolIcons,
                ),
            )

        initViewModel(viewModel)

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
                    navigateBack()
                }
            },
        )
    }

    private fun initViewModel(viewModel: MainViewModel) {
        viewModel.appList.observe(viewLifecycleOwner) {
            it?.let { appList ->
                binding.listEmptyHint.visibility = if (appList.isEmpty()) View.VISIBLE else View.GONE
                populateAppList(appList)
            }
        }
    }

    private fun renderHeader() {
        val context = requireContext()
        binding.headerCompose.setContent {
            SettingsTheme(isDarkTheme(Prefs.getInstance(context))) {
                SettingsHeader(
                    title = context.getString(R.string.app_picker_title),
                    onBack = ::navigateBack,
                )
            }
        }
    }

    private fun populateAppList(apps: List<AppModel>) {
        appAdapter.setAppList(apps.toMutableList())
        appAdapter.updateNotifications(LumaNotificationListener.getActiveNotificationPackages())
    }

    private fun appClickListener(
        viewModel: MainViewModel,
    ): (appModel: AppModel) -> Unit =
        { appModel ->
            val handled = viewModel.selectedApp(appModel, launchContext = requireActivity())
            if (handled) {
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        }

    private fun navigateBack() {
        findNavController().popBackStack(R.id.mainFragment, false)
        if (restoreUnlockGateOnBack) {
            ActionService.instance()?.restoreUnlockGate()
        }
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
                            navigateBack()
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
