package app.luma.ui

import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import app.luma.R
import app.luma.data.Constants
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.helper.performGestureActionHapticFeedback
import app.luma.style.SettingsTheme
import app.luma.style.isDarkTheme
import kotlin.math.abs

fun Fragment.composeView(
    onSwipeBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
): View {
    val prefs = Prefs.getInstance(requireContext())
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme(isDarkTheme(prefs)) {
                if (onSwipeBack != null) {
                    SwipeBackContainer(onSwipeBack) { content() }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SwipeBackContainer(
    onSwipeBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentOnSwipeBack = rememberUpdatedState(onSwipeBack)
    val edgeThresholdPx = with(LocalDensity.current) { 30.dp.toPx() }
    val dragThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > edgeThresholdPx) return@awaitEachGesture

                        var totalX = 0f
                        var totalY = 0f
                        var committed = false
                        var triggered = false

                        while (!triggered) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            totalX = change.position.x - down.position.x
                            totalY = change.position.y - down.position.y

                            if (!committed && abs(totalY) > abs(totalX) * 1.5f) break

                            if (!committed && abs(totalX) > abs(totalY)) {
                                committed = true
                            }

                            if (committed) change.consume()

                            if (committed && totalX > dragThresholdPx) {
                                triggered = true
                                performGestureActionHapticFeedback(context)
                                currentOnSwipeBack.value()
                            }
                        }
                    }
                },
    ) {
        content()
    }
}

fun Fragment.goBack() {
    requireActivity().onBackPressedDispatcher.onBackPressed()
}

fun Fragment.hasNotificationListenerPermission(): Boolean =
    NotificationManagerCompat
        .getEnabledListenerPackages(requireContext())
        .contains(requireContext().packageName)

fun Fragment.openNotificationListenerSettings() {
    try {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (_: Exception) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@Composable
fun actionDisplayValue(
    action: Constants.Action,
    openAppLabel: String,
): String =
    when (action) {
        Constants.Action.OpenApp -> {
            if (openAppLabel.isNotEmpty()) {
                stringResource(R.string.action_open_app_name, openAppLabel)
            } else {
                stringResource(R.string.action_open_app)
            }
        }

        Constants.Action.Disabled -> {
            stringResource(R.string.action_disabled)
        }

        else -> {
            action.displayName()
        }
    }

@Composable
fun actionDisplayValue(
    action: Constants.Action,
    prefs: Prefs,
    section: StatusBarSectionType,
): String = actionDisplayValue(action, prefs.getSectionApp(section).displayName)

@Composable
fun lockscreenActionDisplayValue(
    action: Constants.Action,
    prefs: Prefs,
): String = actionDisplayValue(action, prefs.getLockscreenShortcutApp().displayName)

@Composable
fun lockscreenDateTapActionDisplayValue(
    action: Constants.Action,
    prefs: Prefs,
): String = actionDisplayValue(action, prefs.getLockscreenDateTapApp().displayName)

fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)
