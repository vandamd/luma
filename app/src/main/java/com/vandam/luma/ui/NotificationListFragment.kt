package com.vandam.luma.ui

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.helper.ActionService
import com.vandam.luma.helper.LumaNotificationListener
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.MessageText
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.noRippleClickable

private data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String?,
    val contentIntent: PendingIntent?,
)

class NotificationListFragment : Fragment() {
    private val hasPermission = mutableStateOf(false)
    private var restoreUnlockGateOnBack = false

    private fun checkPermission() {
        val ctx = context ?: return
        hasPermission.value =
            NotificationManagerCompat
                .getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreUnlockGateOnBack = arguments?.getBoolean(RESTORE_UNLOCK_GATE_ON_BACK, false) == true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { NotificationListScreen() }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        registerRestoreUnlockGateOnBackCallback()
    }

    @Suppress("DEPRECATION")
    private fun loadNotifications(): List<NotificationItem> {
        val pm = requireContext().packageManager
        val nonOngoing =
            LumaNotificationListener
                .getActiveNotifications()
                .filter { !it.isOngoing }
        val groupKeysWithChildren =
            nonOngoing
                .filterNot { it.isGroupSummary() }
                .map { it.groupKey }
                .toSet()
        return nonOngoing
            .filterNot { it.isGroupSummary() && it.groupKey in groupKeysWithChildren }
            .map { sbn -> sbn.toNotificationItem(pm) }
            .sortedBy { it.title.lowercase() }
    }

    private fun StatusBarNotification.isGroupSummary(): Boolean = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    @Suppress("DEPRECATION")
    private fun StatusBarNotification.toNotificationItem(pm: android.content.pm.PackageManager): NotificationItem {
        val appLabel =
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) {
                packageName
            }
        return NotificationItem(
            key = key,
            packageName = packageName,
            title = notification.extras.getString(Notification.EXTRA_TITLE) ?: appLabel,
            text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            contentIntent = notification.contentIntent,
        )
    }

    @Composable
    private fun NotificationListScreen() {
        val context = LocalContext.current
        val version by LumaNotificationListener.changeVersion.collectAsState()
        val dismissedKeys = remember { mutableSetOf<String>() }
        val notifications = remember { mutableStateListOf<NotificationItem>() }
        LaunchedEffect(version) {
            val fresh = loadNotifications().filter { it.key !in dismissedKeys }
            notifications.clear()
            notifications.addAll(fresh)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = stringResource(R.string.notification_list_title),
                onBack = ::goBack,
            )
            if (!hasPermission.value) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .noRippleClickable { openNotificationListenerSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    MessageText(stringResource(R.string.notification_list_no_permission))
                }
            } else if (notifications.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MessageText(stringResource(R.string.notification_list_empty))
                }
            } else {
                ContentContainer(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        notifications.forEach { item ->
                            NotificationRow(
                                item = item,
                                onTap = {
                                    val opened =
                                        try {
                                            if (item.contentIntent != null) {
                                                val opts = ActivityOptions.makeBasic()
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                    opts.setPendingIntentBackgroundActivityStartMode(
                                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                                                    )
                                                }
                                                item.contentIntent.send(
                                                    context,
                                                    0,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    opts.toBundle(),
                                                )
                                                true
                                            } else {
                                                false
                                            }
                                        } catch (_: PendingIntent.CanceledException) {
                                            false
                                        }
                                    if (!opened) {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        launchIntent?.let { context.startActivity(it) }
                                    }
                                },
                                onDismiss = {
                                    if (item.key.isNotEmpty()) {
                                        LumaNotificationListener.dismissNotification(item.key)
                                    }
                                    dismissedKeys.add(item.key)
                                    notifications.remove(item)
                                },
                            )
                        }
                }
            }
        }
    }

    private fun registerRestoreUnlockGateOnBackCallback() {
        if (!restoreUnlockGateOnBack) return
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().popBackStack()
                    ActionService.instance()?.restoreUnlockGate()
                }
            },
        )
    }

    @Composable
    private fun NotificationRow(
        item: NotificationItem,
        onTap: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        val context = LocalContext.current

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.close_24px),
                contentDescription = stringResource(R.string.content_desc_dismiss),
                modifier =
                    Modifier
                        .size(24.dp)
                        .noRippleClickable {
                            performAppTapHapticFeedback(context)
                            onDismiss()
                        },
                colorFilter = ColorFilter.tint(SettingsTheme.typography.title.color),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .noRippleClickable {
                            performAppTapHapticFeedback(context)
                            onTap()
                        },
            ) {
                Text(
                    item.title,
                    style = SettingsTheme.typography.pageButton,
                    fontSize = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.text.isNullOrBlank()) {
                    Text(
                        item.text,
                        style = SettingsTheme.typography.item,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
