package app.luma.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.luma.MainViewModel
import app.luma.R
import app.luma.data.Prefs
import app.luma.helper.performAppTapHapticFeedback
import app.luma.style.SettingsTheme
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.noRippleClickable

class FolderNameFragment : Fragment() {
    private val folderId: String? by lazy { arguments?.getString("folderId") }
    private val folderName: String by lazy { arguments?.getString("folderName") ?: "" }
    private val appPackage: String? by lazy { arguments?.getString("appPackage") }
    private val appActivityName: String? by lazy { arguments?.getString("appActivityName") }
    private val isFolderHidden: Boolean by lazy { arguments?.getBoolean("isHidden", false) ?: false }
    private val userSerial: Long by lazy { arguments?.getLong("userSerial", -1L) ?: -1L }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { FolderNameContent() }

    @Composable
    private fun FolderNameContent() {
        val initialName = folderName
        val textState =
            remember {
                mutableStateOf(
                    TextFieldValue(
                        text = initialName,
                        selection = TextRange(initialName.length),
                    ),
                )
            }
        val focusRequester = remember { FocusRequester() }
        val underlineColor = SettingsTheme.typography.item.color
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        fun saveAndReturn(newName: String) {
            val prefs = Prefs.getInstance(requireContext())
            val trimmedName = newName.trim()
            if (trimmedName.isEmpty()) return

            if (folderId != null) {
                prefs.renameFolder(folderId!!, trimmedName)
            } else {
                val newFolder = prefs.createFolder(trimmedName)
                if (appPackage != null && appActivityName != null) {
                    val entry = app.luma.data.PinnedAppEntry(appPackage!!, appActivityName!!, if (userSerial < 0) prefs.mySerial else userSerial)
                    prefs.addAppToFolder(newFolder.id, entry)
                }
            }

            ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList()
            findNavController().popBackStack(R.id.mainFragment, false)
        }

        Column {
            SettingsHeader(
                title = stringResource(if (folderId != null) R.string.folder_rename else R.string.folder_create),
                onBack = ::goBack,
                onAction = { saveAndReturn(textState.value.text) },
            )

            ContentContainer {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 37.dp)
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                val y = size.height
                                drawLine(
                                    color = underlineColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = strokeWidth,
                                )
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = textState.value,
                        onValueChange = { newValue -> textState.value = newValue },
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .padding(start = 6.dp, end = 4.dp, bottom = 6.dp),
                        textStyle =
                            TextStyle(
                                fontSize = 24.sp,
                                color = SettingsTheme.typography.item.color,
                            ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    saveAndReturn(textState.value.text)
                                },
                            ),
                    )
                    if (textState.value.text.isNotEmpty()) {
                        Icon(
                            painter = painterResource(id = R.drawable.close_24px),
                            contentDescription = stringResource(R.string.content_desc_clear),
                            tint = SettingsTheme.typography.item.color,
                            modifier =
                                Modifier
                                    .padding(bottom = 6.dp, end = 6.dp)
                                    .size(20.dp)
                                    .noRippleClickable {
                                        performAppTapHapticFeedback(context)
                                        textState.value = TextFieldValue("")
                                    },
                        )
                    }
                }

                if (folderId != null) {
                    val prefs = Prefs.getInstance(requireContext())
                    val folders = prefs.folders
                    val index = folders.indexOfFirst { it.id == folderId }

                    Spacer(modifier = Modifier.height(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        if (index > 0) {
                            app.luma.ui.compose.SettingsComposable.SimpleTextButton(stringResource(R.string.app_actions_move_up)) {
                                prefs.moveFolderUp(folderId!!)
                                ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList()
                                findNavController().popBackStack(R.id.mainFragment, false)
                            }
                        }
                        if (index >= 0 && index < folders.size - 1) {
                            app.luma.ui.compose.SettingsComposable.SimpleTextButton(stringResource(R.string.app_actions_move_down)) {
                                prefs.moveFolderDown(folderId!!)
                                ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList()
                                findNavController().popBackStack(R.id.mainFragment, false)
                            }
                        }
                        app.luma.ui.compose.SettingsComposable.SimpleTextButton(stringResource(if (isFolderHidden) R.string.app_actions_show else R.string.app_actions_hide)) {
                            if (isFolderHidden) {
                                prefs.unhideFolder(folderId!!)
                            } else {
                                prefs.hideFolder(folderId!!)
                            }
                            ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList()
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }
                        app.luma.ui.compose.SettingsComposable.SimpleTextButton(stringResource(R.string.folder_delete)) {
                            findNavController().navigate(
                                R.id.confirmFragment,
                                bundleOf(
                                    "title" to getString(R.string.folder_delete),
                                    "message" to getString(R.string.folder_delete_confirm),
                                    "confirmText" to getString(R.string.folder_delete),
                                    "action" to "deleteFolder",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeConfirmationResult()
    }

    private fun observeConfirmationResult() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        savedStateHandle.getLiveData<Boolean>("confirmed").observe(viewLifecycleOwner) { confirmed ->
            if (confirmed != true) return@observe
            savedStateHandle.remove<Boolean>("confirmed")
            val action = savedStateHandle.remove<String>("action")

            if (action == "deleteFolder") {
                folderId?.let { Prefs.getInstance(requireContext()).deleteFolder(it) }
                ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList()
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        }
    }
}
