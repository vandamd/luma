package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.navigation.NavOptions
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.vandam.luma.MainActivity
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.ToolSyncManager
import com.vandam.luma.data.ToolSyncResult
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.compose.SettingsComposable.MessageText
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsItemSpacing
import com.vandam.luma.ui.noRippleClickable
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private fun formatAccountNumber(digits: String): String =
        buildString {
            digits.forEachIndexed { index, character ->
                if (index > 0 && index % 4 == 0) {
                    append('-')
                }
                append(character)
            }
        }

    private fun accountTextFieldValue(digits: String): TextFieldValue {
        val formatted = formatAccountNumber(digits)
        return TextFieldValue(
            text = formatted,
            selection = TextRange(formatted.length),
        )
    }

    private fun goToPermissions() {
        Prefs.getInstance(requireContext()).onboardingLoginStarted = false
        val navController = findNavController()
        if (!navController.popBackStack()) {
            navController.navigate(
                R.id.onboardingPermissionsFragment,
                null,
                NavOptions
                    .Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.loginFragment, true)
                    .build(),
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goToPermissions) { LoginContent() }

    @Composable
    private fun LoginContent() {
        val textState =
            remember {
                mutableStateOf(
                    TextFieldValue(
                        text = "",
                        selection = TextRange(0, 0),
                    ),
                )
            }
        val errorState = remember { mutableStateOf<String?>(null) }
        val isSubmitting = remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val underlineColor = SettingsTheme.typography.item.color
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        fun submitLogin() {
            if (isSubmitting.value) return

            val accountNumber = textState.value.text.filter(Char::isDigit)
            if (accountNumber.length != 16) {
                errorState.value = getString(R.string.login_error_account_number)
                return
            }

            errorState.value = null
            isSubmitting.value = true

            viewLifecycleOwner.lifecycleScope.launch {
                val result = ToolSyncManager.syncAndApply(requireContext(), accountNumber)
                when (result) {
                    is ToolSyncResult.Success -> {
                        Prefs.getInstance(requireContext()).apply {
                            this.accountNumber = accountNumber
                            onboardingLoginStarted = false
                        }
                        (activity as? MainActivity)?.restartToolSyncSubscription(initialToolIds = result.enabledToolIds)
                        (activity as? MainActivity)?.onToolSyncApplied()
                        findNavController().navigate(
                            R.id.mainFragment,
                            null,
                            NavOptions
                                .Builder()
                                .setPopUpTo(findNavController().graph.startDestinationId, true)
                                .build(),
                        )
                    }

                    ToolSyncResult.InvalidAccount -> {
                        errorState.value = getString(R.string.login_error_invalid_account)
                    }

                    is ToolSyncResult.Failure -> {
                        errorState.value = result.message.ifBlank { getString(R.string.login_error_sync_failed) }
                    }
                }

                isSubmitting.value = false
            }
        }

        Column {
            SettingsHeader(
                title = stringResource(R.string.login_title),
                onBack = ::goToPermissions,
                onAction = ::submitLogin,
            )

            Box(modifier = Modifier.padding(horizontal = 37.dp)) {
                Column {
                MessageText(
                    text = stringResource(R.string.login_message),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(SettingsItemSpacing))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
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
                ) {
                    BasicTextField(
                        value = textState.value,
                        onValueChange = { newValue ->
                            val digits = newValue.text.filter(Char::isDigit).take(16)
                            textState.value = accountTextFieldValue(digits)
                            if (errorState.value != null) {
                                errorState.value = null
                            }
                        },
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
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    submitLogin()
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
                                        errorState.value = null
                                    },
                        )
                    }
                }

                errorState.value?.let { error ->
                    MessageText(
                        text = error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            }
        }
    }
}
