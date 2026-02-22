package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.luma.MainViewModel
import app.luma.R
import app.luma.data.Constants
import app.luma.data.Constants.Action
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class GestureActionFragment : Fragment() {
    companion object {
        const val GESTURE_TYPE = "gesture_type"
        const val SECTION_TYPE = "section_type"

        private val gestureDisplayInfo =
            mapOf(
                GestureType.SWIPE_LEFT to ActionDisplayInfo(R.string.gesture_swipe_left, AppDrawerFlag.SetSwipeLeft),
                GestureType.SWIPE_RIGHT to ActionDisplayInfo(R.string.gesture_swipe_right, AppDrawerFlag.SetSwipeRight),
                GestureType.SWIPE_UP to ActionDisplayInfo(R.string.gesture_swipe_up, AppDrawerFlag.SetSwipeUp),
                GestureType.SWIPE_DOWN to ActionDisplayInfo(R.string.gesture_swipe_down, AppDrawerFlag.SetSwipeDown),
                GestureType.DOUBLE_TAP to ActionDisplayInfo(R.string.gesture_double_tap, AppDrawerFlag.SetDoubleTap),
            )

        private val sectionDisplayInfo =
            mapOf(
                StatusBarSectionType.CELLULAR to ActionDisplayInfo(R.string.status_bar_connectivity, AppDrawerFlag.SetStatusBarCellular),
                StatusBarSectionType.TIME to ActionDisplayInfo(R.string.status_bar_time, AppDrawerFlag.SetStatusBarTime),
                StatusBarSectionType.BATTERY to ActionDisplayInfo(R.string.status_bar_battery, AppDrawerFlag.SetStatusBarBattery),
            )
    }

    private data class ActionDisplayInfo(
        @StringRes val titleRes: Int,
        val appDrawerFlag: AppDrawerFlag,
    )

    private lateinit var prefs: Prefs
    private var gestureType: GestureType? = null
    private var sectionType: StatusBarSectionType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
        arguments?.getString(GESTURE_TYPE)?.takeIf { it.isNotEmpty() }?.let {
            gestureType = runCatching { GestureType.valueOf(it) }.getOrNull()
        }
        arguments?.getString(SECTION_TYPE)?.takeIf { it.isNotEmpty() }?.let {
            sectionType = runCatching { StatusBarSectionType.valueOf(it) }.getOrNull()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    private fun getDisplayInfo(): ActionDisplayInfo =
        gestureType?.let { gestureDisplayInfo[it] }
            ?: sectionType?.let { sectionDisplayInfo[it] }
            ?: error("No gesture or section type provided")

    @Composable
    fun Screen() {
        val displayInfo = getDisplayInfo()
        Column {
            SettingsHeader(
                title = stringResource(displayInfo.titleRes),
                onBack = ::goBack,
            )

            ContentContainer {
                CustomScrollView {
                    for (action in Constants.Action.values()) {
                        val isSelected = getCurrentAction() == action
                        val buttonText =
                            when {
                                action == Constants.Action.OpenApp && isSelected -> {
                                    val appLabel = getAppLabel()
                                    if (appLabel.isNotEmpty()) {
                                        stringResource(R.string.action_open_app_name, appLabel)
                                    } else {
                                        stringResource(R.string.action_open_app)
                                    }
                                }

                                action == Constants.Action.OpenApp -> {
                                    stringResource(R.string.action_open_app)
                                }

                                else -> {
                                    action.displayName()
                                }
                            }
                        SimpleTextButton(
                            title = buttonText,
                            underline = isSelected,
                            onClick = { handleActionSelection(action) },
                        )
                    }
                }
            }
        }
    }

    private fun getCurrentAction(): Action =
        gestureType?.let { prefs.getGestureAction(it) }
            ?: sectionType?.let { prefs.getSectionAction(it) }
            ?: Action.Disabled

    private fun setCurrentAction(action: Action) {
        gestureType?.let { prefs.setGestureAction(it, action) }
            ?: sectionType?.let { prefs.setSectionAction(it, action) }
    }

    private fun getAppLabel(): String =
        gestureType?.let { prefs.getGestureApp(it).appLabel }
            ?: sectionType?.let { prefs.getSectionApp(it).appLabel }
            ?: ""

    private fun handleActionSelection(action: Action) {
        if (action == Action.OpenApp) {
            val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
            viewModel.getAppList()
            val displayInfo = getDisplayInfo()
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf("flag" to displayInfo.appDrawerFlag.toString()),
            )
        } else {
            setCurrentAction(action)
            goBack()
        }
    }
}
