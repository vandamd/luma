package com.vandam.luma.helper

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat

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
}

object PhoneSignalHelper {
    private val phoneToolPermissions =
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
        )
    private val smsConversationsUri = Uri.parse("content://mms-sms/conversations?simple=true")

    fun phoneToolPermissions(): Array<String> = phoneToolPermissions.copyOf()

    fun hasPhoneToolPermissions(context: Context): Boolean = phoneToolPermissions.all { hasPermission(context, it) }

    fun hasUnreadPhoneSignal(context: Context): Boolean = hasUnreadMessages(context) || hasMissedCalls(context)

    fun getNotificationSummaries(context: Context): List<PhoneNotificationSummary> =
        listOfNotNull(
            getMissedCallsSummary(context),
            getUnreadMessagesSummary(context),
        ).sortedByDescending { it.timestampMillis }

    fun hasUnreadMessages(context: Context): Boolean = getUnreadMessagesSummary(context) != null

    fun hasMissedCalls(context: Context): Boolean = getMissedCallsSummary(context) != null

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
}
