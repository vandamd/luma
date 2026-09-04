package app.luma.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.UserManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.luma.MainViewModel
import app.luma.R
import app.luma.data.AppDrawerItem
import app.luma.data.AppModel
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.Prefs
import app.luma.databinding.FragmentAppDrawerBinding
import app.luma.helper.LumaNotificationListener
import app.luma.helper.performGestureActionHapticFeedback
import app.luma.style.SettingsTheme
import app.luma.style.isDarkTheme
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import kotlinx.coroutines.launch

class FolderFragment : Fragment() {
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    private val folderId: String by lazy { arguments?.getString("folderId") ?: "" }
    private val folderName: String by lazy { arguments?.getString("folderName") ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)

        val header: ComposeView = binding.headerCompose
        header.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        header.setContent {
            SettingsTheme(isDarkTheme(Prefs.getInstance(requireContext()))) {
                SettingsHeader(title = folderName, onBack = { findNavController().popBackStack() })
            }
        }
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

        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val appAdapter =
            AppDrawerAdapter(
                requireContext(),
                AppDrawerConfig(
                    gravity = Gravity.CENTER,
                    clickListener = { item ->
                        if (item is AppDrawerItem.App) {
                            viewModel.selectedApp(item.appModel, AppDrawerFlag.LaunchApp)
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }
                    },
                    appLongPressListener = { item ->
                        if (item is AppDrawerItem.App) {
                            val appModel = item.appModel
                            val userManager = requireContext().getSystemService(android.content.Context.USER_SERVICE) as UserManager
                            findNavController().navigate(
                                R.id.appActionsFragment,
                                bundleOf(
                                    "appPackage" to appModel.appPackage,
                                    "appLabel" to appModel.appLabel,
                                    "appAlias" to appModel.appAlias,
                                    "appActivityName" to appModel.appActivityName,
                                    "isHidden" to false,
                                    "userSerial" to userManager.getSerialNumberForUser(appModel.user),
                                ),
                            )
                        }
                    },
                    showPinnedIcon = true,
                ),
            )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appAdapter
        binding.recyclerView.addOnItemTouchListener(swipeBackTouchListener())

        viewModel.appList.observe(viewLifecycleOwner) { _ ->
            val folder = Prefs.getInstance(requireContext()).folders.find { it.id == folderId }
            if (folder == null) {
                findNavController().popBackStack()
            }
        }

        // For now, let's re-fetch the full list and filter in the fragment (not ideal but works for now)
        viewLifecycleOwner.lifecycleScope.launch {
            val allApps = app.luma.helper.getAppsList(requireContext(), grouped = false)
            // Filter allApps to find those that belong to this folder
            val prefs = Prefs.getInstance(requireContext())
            val folder = prefs.folders.find { it.id == folderId }
            if (folder != null) {
                val userManager = requireContext().getSystemService(android.content.Context.USER_SERVICE) as UserManager
                val folderApps =
                    allApps
                        .filterIsInstance<AppDrawerItem.App>()
                        .filter { item ->
                            val serial = userManager.getSerialNumberForUser(item.appModel.user)
                            val entry = app.luma.data.PinnedAppEntry(item.appModel.appPackage, item.appModel.appActivityName, serial)
                            folder.apps.contains(entry)
                        }.sortedBy { item ->
                            val serial = userManager.getSerialNumberForUser(item.appModel.user)
                            val entry = app.luma.data.PinnedAppEntry(item.appModel.appPackage, item.appModel.appActivityName, serial)
                            folder.apps.indexOf(entry)
                        }
                appAdapter.setItemList(folderApps.toMutableList())
                appAdapter.updateNotifications(LumaNotificationListener.getActiveNotificationPackages())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            LumaNotificationListener.changeVersion.collect {
                val packages = LumaNotificationListener.getActiveNotificationPackages()
                appAdapter.updateNotifications(packages)
            }
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
                            findNavController().popBackStack()
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
