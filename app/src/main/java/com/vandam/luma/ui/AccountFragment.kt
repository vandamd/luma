package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.MainActivity
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SelectorButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton
import com.vandam.luma.ui.compose.SettingsItemSpacing

class AccountFragment : Fragment() {
    private val prefs: Prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeConfirmationResult()
    }

    private fun observeConfirmationResult() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        savedStateHandle.getLiveData<Boolean>("confirmed").observe(viewLifecycleOwner) { confirmed ->
            if (confirmed != true) return@observe

            savedStateHandle.remove<Boolean>("confirmed")
            when (savedStateHandle.remove<String>("action")) {
                "logout" -> (activity as? MainActivity)?.logoutToLogin()
            }
        }
    }

    private fun formatAccountNumber(accountNumber: String): String =
        accountNumber
            .filter(Char::isDigit)
            .take(16)
            .chunked(4)
            .joinToString("-")

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.account_title),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SelectorButton(
                        label = stringResource(R.string.account_number_label),
                        value = formatAccountNumber(prefs.accountNumber),
                        enabled = false,
                        onClick = {},
                    )
                    SimpleTextButton(stringResource(R.string.account_log_out)) {
                        findNavController().navigate(
                            R.id.confirmFragment,
                            bundleOf(
                                "title" to getString(R.string.account_log_out_confirm_title),
                                "message" to getString(R.string.account_log_out_confirm_message),
                                "confirmText" to getString(R.string.account_log_out),
                                "action" to "logout",
                            ),
                        )
                    }
                }
            }
        }
    }
}
