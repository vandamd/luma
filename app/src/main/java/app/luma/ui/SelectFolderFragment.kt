package app.luma.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.luma.MainViewModel
import app.luma.R
import app.luma.data.PinnedAppEntry
import app.luma.data.Prefs
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class SelectFolderFragment : Fragment() {
    private val appPackage: String by lazy { arguments?.getString("appPackage") ?: "" }
    private val appActivityName: String by lazy { arguments?.getString("appActivityName") ?: "" }
    private val userSerial: Long by lazy { arguments?.getLong("userSerial", -1L) ?: -1L }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { SelectFolderContent() }

    @Composable
    private fun SelectFolderContent() {
        val prefs = Prefs.getInstance(requireContext())
        val folders = prefs.folders
        val entry = PinnedAppEntry(appPackage, appActivityName, if (userSerial < 0) prefs.mySerial else userSerial)

        Column {
            SettingsHeader(
                title = stringResource(R.string.folder_select_title),
                onBack = ::goBack,
            )

            ContentContainer {
                CustomScrollView(verticalArrangement = Arrangement.spacedBy(33.5.dp)) {
                    SimpleTextButton(stringResource(R.string.folder_new)) {
                        findNavController().navigate(
                            R.id.folderNameFragment,
                            bundleOf(
                                "appPackage" to appPackage,
                                "appActivityName" to appActivityName,
                                "userSerial" to userSerial,
                            ),
                        )
                    }

                    folders.forEach { folder ->
                        SimpleTextButton(folder.name) {
                            prefs.addAppToFolder(folder.id, entry)
                            ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList()
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }
                    }
                }
            }
        }
    }
}
