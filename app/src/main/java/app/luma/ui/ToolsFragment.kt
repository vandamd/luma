package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import app.luma.R
import app.luma.data.Prefs
import app.luma.data.Tool
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader

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
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_tools),
                onBack = ::goBack,
            )

            ContentContainer {
                    toolToggleOptions.forEach { option ->
                        PrefsToggleTextButton(
                            title = stringResource(option.titleRes),
                            initialValue = prefs.isToolEnabled(option.tool),
                            onValueChange = { prefs.setToolEnabled(option.tool, it) },
                        )
                    }
            }
        }
    }
}
