package app.luma.helper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.Toast
import app.luma.R
import app.luma.data.Constants.Action

private const val NETWORK_SHORTCUT_LIGHT_ROUTE = "networksettings"

data class ActionExecutionCallbacks(
    val showAppList: () -> Unit = {},
    val showNotificationList: () -> Unit = {},
)

@SuppressLint("NewApi")
fun executeSecondaryAction(
    context: Context,
    action: Action,
    callbacks: ActionExecutionCallbacks = ActionExecutionCallbacks(),
): Boolean =
    when (action) {
        Action.NetworkShortcutLight -> {
            if (!isAccessibilityEnabled(context)) {
                openAccessibilitySettings(context)
            }
            launchLightOsRoute(context, NETWORK_SHORTCUT_LIGHT_ROUTE)
        }

        Action.ShowNotification -> {
            expandNotificationDrawer(context)
            true
        }

        Action.LockScreen -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActionService.instance()?.lockScreen()
                    ?: openAccessibilitySettings(context)
            } else {
                showToast(context, context.getString(R.string.toast_lock_requires_android_9), Toast.LENGTH_LONG)
            }
            true
        }

        Action.ShowAppList -> {
            callbacks.showAppList()
            true
        }

        Action.OpenQuickSettings -> {
            expandQuickSettings(context)
            true
        }

        Action.ShowRecents -> {
            initActionService(context)?.showRecents()
            true
        }

        Action.ShowNotificationList -> {
            callbacks.showNotificationList()
            true
        }

        Action.ToggleFlashlight,
        Action.OpenApp,
        Action.Disabled,
        -> {
            false
        }
    }
