package com.vandam.luma.data

import android.content.Context
import android.util.Log
import com.vandam.luma.LumaApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.Serializable

@Serializable
data class LockscreenMessage(
    val messageId: String,
    val text: String,
    val expiresAt: Double,
)

object LockscreenMessageManager {
    private const val LOG_TAG = "LockscreenMessage"
    private const val QUERY_PATH = "lockscreenMessages:getCurrentForDevice"

    fun observe(
        context: Context,
        accountNumber: String,
    ): Flow<LockscreenMessage?> {
        if (!accountNumber.matches(Regex("^\\d{16}$"))) {
            return flowOf(null)
        }

        val client =
            (context.applicationContext as? LumaApplication)?.convexClient
                ?: return emptyFlow()

        return runCatching {
            client
                .subscribe<LockscreenMessage?>(
                    name = QUERY_PATH,
                    args = mapOf("accountNumber" to accountNumber),
                ).transform { result ->
                    result.fold(
                        onSuccess = { emit(it) },
                        onFailure = { error ->
                            Log.w(LOG_TAG, "Lock-screen message subscription failed", error)
                        },
                    )
                }
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Unable to observe lock-screen messages", error)
            emptyFlow()
        }
    }
}
