package com.vandam.luma.helper

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

class LumaNotificationListener : NotificationListenerService() {
    companion object {
        private const val LIGHT_OS_PACKAGE = "com.lightos"
        private data class NotificationSnapshot(
            val notifications: List<StatusBarNotification> = emptyList(),
            val packages: Set<String> = emptySet(),
            val mediaKeys: Set<String> = emptySet(),
        )

        private val PHONE_NOTIFICATION_PACKAGES =
            setOf(
                "com.android.server.telecom",
                LIGHT_OS_PACKAGE,
            )

        private var instance: WeakReference<LumaNotificationListener> = WeakReference(null)
        @Volatile
        private var notificationSnapshot = NotificationSnapshot()
        @Volatile
        private var listenerConnected = false

        private val _changeVersion = MutableStateFlow(0L)
        val changeVersion: StateFlow<Long> = _changeVersion.asStateFlow()
        private val _badgeChangeVersion = MutableStateFlow(0L)
        val badgeChangeVersion: StateFlow<Long> = _badgeChangeVersion.asStateFlow()
        private val _mediaChangeVersion = MutableStateFlow(0L)
        val mediaChangeVersion: StateFlow<Long> = _mediaChangeVersion.asStateFlow()

        @Suppress("DEPRECATION")
        private fun StatusBarNotification.shouldFilter(svc: LumaNotificationListener): Boolean {
            if (notification.category == Notification.CATEGORY_TRANSPORT) return true
            return isLightOsKeepAlive(svc)
        }

        private fun StatusBarNotification.isMediaNotification(): Boolean {
            val template = notification.extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
            return notification.category == Notification.CATEGORY_TRANSPORT ||
                template.contains("MediaStyle")
        }

        private fun StatusBarNotification.isLightOsKeepAlive(svc: LumaNotificationListener): Boolean {
            if (packageName == LIGHT_OS_PACKAGE) {
                val title = notification.extras.getString(Notification.EXTRA_TITLE)
                val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (title.isNullOrBlank() && text.isNullOrBlank() && notification.contentIntent == null && notification.deleteIntent == null) {
                    return true
                }
            }

            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (!text.isNullOrBlank()) return false
            val title = notification.extras.getString(Notification.EXTRA_TITLE)
            if (title == "LightOS") return true
            if (title == null) {
                val pm = svc.packageManager
                val appLabel =
                    try {
                        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                    } catch (_: Exception) {
                        null
                    }
                return appLabel == "LightOS"
            }
            return false
        }

        fun getActiveNotificationPackages(): Set<String> {
            rebuildNotificationSnapshot()
            return notificationSnapshot.packages
        }

        fun getActiveNotifications(): List<StatusBarNotification> {
            rebuildNotificationSnapshot()
            return notificationSnapshot.notifications
        }

        fun dismissNotification(key: String) {
            instance.get()?.cancelNotification(key)
        }

        fun dismissMediaNotifications(packageName: String) {
            val svc = instance.get() ?: return
            svc.activeNotifications
                .filter { it.packageName == packageName && it.isMediaNotification() }
                .forEach { svc.cancelNotification(it.key) }
        }

        fun hasActiveMediaNotification(packageName: String): Boolean {
            val svc = instance.get() ?: return false
            return svc.activeNotifications.any {
                it.packageName == packageName && it.isMediaNotification()
            }
        }

        private fun rebuildNotificationSnapshot() {
            val svc = instance.get()
            if (svc == null || !listenerConnected) return
            val previousSnapshot = notificationSnapshot
            val activeNotifications =
                runCatching {
                    svc.activeNotifications.toList()
                }.getOrNull() ?: return
            val filteredNotifications =
                activeNotifications.filterNot { it.shouldFilter(svc) }
            val nextSnapshot =
                NotificationSnapshot(
                    notifications = filteredNotifications,
                    packages =
                        filteredNotifications
                            .asSequence()
                            .filterNot { it.isOngoing }
                            .map { it.packageName }
                            .filterNot(PHONE_NOTIFICATION_PACKAGES::contains)
                            .toSet(),
                    mediaKeys =
                        activeNotifications
                            .asSequence()
                            .filter { it.isMediaNotification() }
                            .map { it.key }
                            .toSet(),
                )
            notificationSnapshot = nextSnapshot
            if (previousSnapshot.notifications.notificationKeys() != nextSnapshot.notifications.notificationKeys()) {
                _changeVersion.update { it + 1 }
            }
            if (previousSnapshot.packages != nextSnapshot.packages) {
                _badgeChangeVersion.update { it + 1 }
            }
            if (previousSnapshot.mediaKeys != nextSnapshot.mediaKeys) {
                _mediaChangeVersion.update { it + 1 }
            }
        }

        private fun List<StatusBarNotification>.notificationKeys(): List<String> = map { it.key }
    }

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
    }

    override fun onListenerConnected() {
        listenerConnected = true
        rebuildNotificationSnapshot()
    }

    override fun onListenerDisconnected() {
        listenerConnected = false
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerConnected = false
        instance = WeakReference(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        rebuildNotificationSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        rebuildNotificationSnapshot()
    }
}
