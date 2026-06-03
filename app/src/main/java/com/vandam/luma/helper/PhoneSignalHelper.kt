package com.vandam.luma.helper

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.vandam.luma.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneNotificationSummary(
    val kind: Kind,
    val count: Int,
    val detail: String?,
    val timestampMillis: Long,
) {
    enum class Kind {
        UnreadMessages,
        MissedCalls,
    }

    val dismissedNotificationKey: String
        get() = "$syntheticNotificationKey|$timestampMillis|$count"

    val syntheticNotificationKey: String
        get() =
            when (kind) {
                Kind.UnreadMessages -> "__phone_unread_messages__"
                Kind.MissedCalls -> "__phone_missed_calls__"
            }
}

object PhoneSignalHelper {
    private const val CACHE_TTL_MS = 5_000L

    private val phoneToolPermissions =
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
        )
    private val smsConversationsUri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cachedUnreadPhoneSignal = false
    @Volatile private var cachedUnreadPhoneSignalAtMs = 0L
    @Volatile private var refreshInFlight = false
    private val _changeVersion = MutableStateFlow(0L)
    val changeVersion: StateFlow<Long> = _changeVersion.asStateFlow()

    fun phoneToolPermissions(): Array<String> = phoneToolPermissions.copyOf()

    fun hasPhoneToolPermissions(context: Context): Boolean = phoneToolPermissions.all { hasPermission(context, it) }

    fun hasUnreadPhoneSignal(context: Context): Boolean {
        val value = queryUnreadPhoneSignal(context.applicationContext)
        updateCachedUnreadPhoneSignal(value)
        return value
    }

    fun getCachedUnreadPhoneSignal(context: Context): Boolean {
        if (SystemClock.elapsedRealtime() - cachedUnreadPhoneSignalAtMs >= CACHE_TTL_MS) {
            refreshUnreadPhoneSignalAsync(context)
        }
        return cachedUnreadPhoneSignal
    }

    fun refreshUnreadPhoneSignal(context: Context): Boolean {
        val value = queryUnreadPhoneSignal(context.applicationContext)
        updateCachedUnreadPhoneSignal(value)
        return value
    }

    fun refreshUnreadPhoneSignalAsync(context: Context) {
        if (refreshInFlight) return
        refreshInFlight = true
        val appContext = context.applicationContext
        refreshScope.launch {
            try {
                updateCachedUnreadPhoneSignal(queryUnreadPhoneSignal(appContext))
            } finally {
                refreshInFlight = false
            }
        }
    }

    fun getNotificationSummaries(context: Context): List<PhoneNotificationSummary> =
        listOfNotNull(
            getMissedCallsSummary(context),
            getUnreadMessagesSummary(context),
        ).sortedByDescending { it.timestampMillis }

    fun hasVisibleNotificationSignal(context: Context): Boolean {
        val dismissedKeys = Prefs.getInstance(context).dismissedPhoneNotificationKeys
        return getNotificationSummaries(context).any { it.dismissedNotificationKey !in dismissedKeys }
    }

    fun hasUnreadMessages(context: Context): Boolean = queryHasUnreadMessages(context.applicationContext)

    fun hasMissedCalls(context: Context): Boolean = queryHasMissedCalls(context.applicationContext)

    private fun getUnreadMessagesSummary(context: Context): PhoneNotificationSummary? {
        if (!hasPermission(context, Manifest.permission.READ_SMS)) return null
        return runCatching {
            context.contentResolver.query(
                smsConversationsUri,
                arrayOf("_id", "snippet", "date"),
                "read = 0",
                null,
                "date DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val snippet = normalizeMessageSnippet(cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
                val timestampMillis = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                PhoneNotificationSummary(
                    kind = PhoneNotificationSummary.Kind.UnreadMessages,
                    count = cursor.count,
                    detail = snippet,
                    timestampMillis = timestampMillis,
                )
            }
        }.getOrNull()
    }

    private fun queryUnreadPhoneSignal(context: Context): Boolean = queryHasUnreadMessages(context) || queryHasMissedCalls(context)

    private fun queryHasUnreadMessages(context: Context): Boolean {
        if (!hasPermission(context, Manifest.permission.READ_SMS)) return false
        return runCatching {
            context.contentResolver.query(
                smsConversationsUri,
                arrayOf("_id"),
                "read = 0",
                null,
                "date DESC",
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun getMissedCallsSummary(context: Context): PhoneNotificationSummary? {
        if (!hasPermission(context, Manifest.permission.READ_CALL_LOG)) return null
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                ),
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.IS_READ} IS NULL OR ${CallLog.Calls.IS_READ} = ?)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val caller =
                    cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                        ?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                            ?.takeIf { it.isNotBlank() }
                val timestampMillis = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                PhoneNotificationSummary(
                    kind = PhoneNotificationSummary.Kind.MissedCalls,
                    count = cursor.count,
                    detail = caller,
                    timestampMillis = timestampMillis,
                )
            }
        }.getOrNull()
    }

    private fun queryHasMissedCalls(context: Context): Boolean {
        if (!hasPermission(context, Manifest.permission.READ_CALL_LOG)) return false
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.IS_READ} IS NULL OR ${CallLog.Calls.IS_READ} = ?)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun normalizeMessageSnippet(snippet: String?): String? =
        snippet
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    it != "NULL" &&
                    it != "__THREAD_PLACEHOLDER__"
            }

    private fun hasPermission(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun updateCachedUnreadPhoneSignal(value: Boolean) {
        val didChange = cachedUnreadPhoneSignal != value
        cachedUnreadPhoneSignal = value
        cachedUnreadPhoneSignalAtMs = SystemClock.elapsedRealtime()
        if (didChange) {
            _changeVersion.update { it + 1 }
        }
    }
}
