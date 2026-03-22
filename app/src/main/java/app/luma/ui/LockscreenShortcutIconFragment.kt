package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class LockscreenShortcutIconFragment : Fragment() {
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
    fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.lockscreen_shortcut_icon),
                onBack = ::goBack,
            )

            ContentContainer {
                for (icon in Prefs.LockscreenShortcutIcon.values()) {
                    SimpleTextButton(
                        title = iconDisplayName(icon),
                        underline = prefs.lockscreenShortcutIcon == icon,
                        iconRes = iconDrawableRes(icon),
                        onClick = { select(icon) },
                    )
                }
            }
        }
    }

    private fun select(icon: Prefs.LockscreenShortcutIcon) {
        prefs.lockscreenShortcutIcon = icon
        goBack()
    }

    companion object {
        fun iconDisplayName(icon: Prefs.LockscreenShortcutIcon): String = icon.name

        @DrawableRes
        fun iconDrawableRes(icon: Prefs.LockscreenShortcutIcon): Int =
            when (icon) {
                Prefs.LockscreenShortcutIcon.Ring -> R.drawable.ic_shortcut_ring
                Prefs.LockscreenShortcutIcon.Star -> R.drawable.ic_shortcut_star
                Prefs.LockscreenShortcutIcon.Camera -> R.drawable.ic_shortcut_camera
                Prefs.LockscreenShortcutIcon.Phone -> R.drawable.ic_shortcut_phone
                Prefs.LockscreenShortcutIcon.Heart -> R.drawable.ic_shortcut_heart
                Prefs.LockscreenShortcutIcon.Flashlight -> R.drawable.ic_shortcut_flashlight
                Prefs.LockscreenShortcutIcon.Music -> R.drawable.ic_shortcut_music
                Prefs.LockscreenShortcutIcon.Message -> R.drawable.ic_shortcut_message
            }
    }
}
