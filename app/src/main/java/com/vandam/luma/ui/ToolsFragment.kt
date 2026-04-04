package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.Tool
import com.vandam.luma.ui.compose.MessageText
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.ToggleTextButton

private data class ToolToggleOption(
    val tool: Tool,
    @param:StringRes val titleRes: Int,
)

private val toolToggleOptions =
    listOf(
        ToolToggleOption(Tool.Phone, R.string.tools_phone),
        ToolToggleOption(Tool.Settings, R.string.tools_settings),
        ToolToggleOption(Tool.Album, R.string.tools_album),
        ToolToggleOption(Tool.Alarm, R.string.tools_alarm),
        ToolToggleOption(Tool.Calculator, R.string.tools_calculator),
        ToolToggleOption(Tool.Directions, R.string.tools_directions),
        ToolToggleOption(Tool.Directory, R.string.tools_directory),
        ToolToggleOption(Tool.Camera, R.string.tools_camera),
        ToolToggleOption(Tool.Notes, R.string.tools_notes),
        ToolToggleOption(Tool.Calendar, R.string.tools_calendar),
        ToolToggleOption(Tool.Timer, R.string.tools_timer),
        ToolToggleOption(Tool.Music, R.string.tools_music),
        ToolToggleOption(Tool.Podcasts, R.string.tools_podcasts),
        ToolToggleOption(Tool.Hotspot, R.string.tools_hotspot),
    )

class ToolsFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }
    private val toolEnabledStates = toolToggleOptions.associate { it.tool to mutableStateOf(false) }

    override fun onResume() {
        super.onResume()
        toolToggleOptions.forEach { option ->
            toolEnabledStates.getValue(option.tool).value = prefs.isToolEnabled(option.tool)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        SettingsScreen(
            title = stringResource(R.string.settings_tools),
            onBack = ::goBack,
        ) {
            MessageText(
                stringResource(R.string.tools_message),
                modifier = Modifier.padding(end = 30.dp),
            )
            toolToggleOptions.forEach { option ->
                val enabledState = toolEnabledStates.getValue(option.tool)
                ToggleTextButton(
                    title = stringResource(option.titleRes),
                    checked = enabledState.value,
                    onValueChange = {
                        enabledState.value = it
                        prefs.setToolEnabled(option.tool, it)
                    },
                )
            }
        }
    }
}
