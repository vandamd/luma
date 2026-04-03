package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.compose.SettingsComposable.MessageText
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.noRippleClickable
import java.util.Locale

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
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = title,
                onBack = ::goBack,
            )
            MessageText(
                text = message,
                modifier = Modifier.padding(start = 37.dp),
            )
            val context = LocalContext.current
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(bottom = 14.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = confirmText.uppercase(Locale.getDefault()),
                    style = SettingsTheme.typography.pageButton,
                    fontSize = 40.sp,
                    modifier =
                        Modifier.noRippleClickable {
                            performAppTapHapticFeedback(context)
                            findNavController().previousBackStackEntry?.savedStateHandle?.apply {
                                set("confirmed", true)
                                set("action", action)
                            }
                            findNavController().popBackStack()
                        },
                )
            }
        }
    }
}
