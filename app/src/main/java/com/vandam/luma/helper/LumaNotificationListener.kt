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
        private var instance: WeakReference<LumaNotificationListener> = WeakReference(null)

        private val _changeVersion = MutableStateFlow(0L)
        val changeVersion: StateFlow<Long> = _changeVersion.asStateFlow()

        @Suppress("DEPRECATION")
        private fun StatusBarNotification.shouldFilter(svc: LumaNotificationListener): Boolean {
            if (notification.category == Notification.CATEGORY_TRANSPORT) return true
            return isLightOsKeepAlive(svc)
        }

        private fun StatusBarNotification.isLightOsKeepAlive(svc: LumaNotificationListener): Boolean {
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
            val svc = instance.get() ?: return emptySet()
            return svc.activeNotifications
                .filterNot { it.shouldFilter(svc) || it.isOngoing }
                .map { it.packageName }
                .toSet()
        }

        fun getActiveNotifications(): List<StatusBarNotification> {
            val svc = instance.get() ?: return emptyList()
            return svc.activeNotifications
                .filterNot { it.shouldFilter(svc) }
                .toList()
        }

        fun dismissNotification(key: String) {
            instance.get()?.cancelNotification(key)
        }

        fun hasActiveMediaNotification(packageName: String): Boolean {
            val svc = instance.get() ?: return false
            return svc.activeNotifications.any {
                val template = it.notification.extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
                it.packageName == packageName &&
                    (
                        it.notification.category == Notification.CATEGORY_TRANSPORT ||
                            template.contains("MediaStyle")
                    )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
    }

    override fun onListenerConnected() {
        _changeVersion.update { it + 1 }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = WeakReference(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        _changeVersion.update { it + 1 }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        _changeVersion.update { it + 1 }
    }
}
