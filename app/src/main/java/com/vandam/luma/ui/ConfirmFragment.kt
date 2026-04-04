package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.ui.compose.BottomActionText
import com.vandam.luma.ui.compose.MessageText
import com.vandam.luma.ui.compose.SettingsInsetContentPadding
import com.vandam.luma.ui.compose.SettingsScreen

class ConfirmFragment : Fragment() {
    private val title: String by lazy { arguments?.getString("title") ?: "" }
    private val message: String by lazy { arguments?.getString("message") ?: "" }
    private val confirmText: String by lazy { arguments?.getString("confirmText") ?: "" }
    private val action: String by lazy { arguments?.getString("action") ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { ConfirmScreen() }

    @Composable
    private fun ConfirmScreen() {
        SettingsScreen(
            title = title,
            onBack = ::goBack,
            scrollable = false,
            contentPadding = SettingsInsetContentPadding,
            footer = {
                BottomActionText(title = confirmText) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.apply {
                        set("confirmed", true)
                        set("action", action)
                    }
                    findNavController().popBackStack()
                }
            },
        ) {
            MessageText(text = message)
        }
    }
}
