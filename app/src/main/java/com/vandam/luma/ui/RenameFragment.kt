package com.vandam.luma.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.Tool
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.noRippleClickable

class RenameFragment : Fragment() {
    private val appPackage: String by lazy { arguments?.getString("appPackage") ?: "" }
    private val appLabel: String by lazy { arguments?.getString("appLabel") ?: "" }
    private val appAlias: String by lazy { arguments?.getString("appAlias") ?: "" }
    private val homePosition: Int by lazy { arguments?.getInt("homePosition", -1) ?: -1 }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { RenameContent() }

    @Composable
    private fun RenameContent() {
        val initialName = appAlias.ifEmpty { appLabel }
        val textState =
            remember {
                mutableStateOf(
                    TextFieldValue(
                        text = initialName,
                        selection = TextRange(0, initialName.length),
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

            val newAlias = if (trimmedName.isEmpty() || trimmedName == appLabel) "" else trimmedName

            if (homePosition >= 0) {
                val currentApp = prefs.getHomeAppModel(homePosition)
                if (Tool.fromPackageName(currentApp.appPackage) != null) {
                    prefs.setAppAlias(currentApp.appPackage, newAlias)
                }
                val updatedAppModel = currentApp.copy(appAlias = newAlias)
                prefs.setHomeAppModel(homePosition, updatedAppModel)
            } else {
                prefs.setAppAlias(appPackage, newAlias)
            }

            findNavController().popBackStack(R.id.mainFragment, false)
        }

        Column {
            SettingsHeader(
                title = stringResource(R.string.app_drawer_rename),
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
            }
        }
    }
}
