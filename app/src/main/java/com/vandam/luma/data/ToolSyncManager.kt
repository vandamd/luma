package com.vandam.luma.data

import android.content.Context
import android.util.Log
import com.vandam.luma.LumaApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.text.Collator
import kotlin.math.max

sealed interface ToolSyncResult {
    data class Success(
        val enabledToolIds: List<String>,
    ) : ToolSyncResult

    data object InvalidAccount : ToolSyncResult

    data class Failure(
        val message: String,
    ) : ToolSyncResult
}

object ToolSyncManager {
    private const val LOG_TAG = "ToolSync"
    private const val QUERY_PATH = "accounts:getTools"

    @Serializable
    private data class DashboardTools(
        val enabledAppIds: List<String> = emptyList(),
        val enabledToolIds: List<String> = emptyList(),
    )

    suspend fun syncAndApply(
        context: Context,
        accountNumber: String,
    ): ToolSyncResult = observeSyncResults(context, accountNumber).first()

    fun observeSyncResults(
        context: Context,
        accountNumber: String,
    ): Flow<ToolSyncResult> {
        if (!accountNumber.matches(Regex("^\\d{16}$"))) {
            return flowOf(ToolSyncResult.InvalidAccount)
        }

        val client =
            (context.applicationContext as? LumaApplication)?.convexClient
                ?: return flowOf(ToolSyncResult.Failure("Convex URL not configured"))

        return client
            .subscribe<DashboardTools?>(
                name = QUERY_PATH,
                args = mapOf("accountNumber" to accountNumber),
            ).map { result ->
                result.fold(
                    onSuccess = { payload ->
                        if (payload == null) {
                            Log.w(LOG_TAG, "Convex returned no account for $accountNumber")
                            ToolSyncResult.InvalidAccount
                        } else {
                            runCatching {
                                val toolIds = normalizeToolIds(payload.enabledToolIds)
                                applyToolLayout(context, toolIds)
                                Log.d(
                                    LOG_TAG,
                                    "Applied tool sync tools=${toolIds.joinToString()} apps=${payload.enabledAppIds.joinToString()}",
                                )
                                ToolSyncResult.Success(
                                    enabledToolIds = toolIds,
                                )
                            }.getOrElse { error ->
                                Log.w(LOG_TAG, "Failed to apply tool layout", error)
                                ToolSyncResult.Failure(error.message ?: "Unable to apply tools")
                            }
                        }
                    },
                    onFailure = { error ->
                        Log.w(LOG_TAG, "Tool sync failed", error)
                        ToolSyncResult.Failure(error.message ?: "Unable to sync tools")
                    },
                )
            }
    }

    private fun normalizeToolIds(enabledToolIds: List<String>): List<String> {
        val orderedTools = mutableListOf<String>()
        val seenToolIds = linkedSetOf<String>()

        enabledToolIds.forEach { toolId ->
            val tool = Tool.fromId(toolId) ?: return@forEach
            if (seenToolIds.add(tool.id)) {
                orderedTools.add(tool.id)
            }
        }

        if (Tool.Phone.id !in seenToolIds) {
            orderedTools.add(0, Tool.Phone.id)
        }
        if (Tool.Settings.id !in seenToolIds) {
            orderedTools.add(Tool.Settings.id)
        }

        return orderedTools
    }

    private fun applyToolLayout(
        context: Context,
        enabledToolIds: List<String>,
    ) {
        val prefs = Prefs.getInstance(context)
        val collator = Collator.getInstance()
        val tools = enabledToolIds.mapNotNull(Tool::fromId)

        Tool.entries.forEach { tool ->
            prefs.setToolEnabled(tool, tools.contains(tool))
        }

        tools.forEachIndexed { index, tool ->
            val alias = prefs.getAppAlias(tool.packageName)
            prefs.setHomeAppModel(index, tool.toAppModel(context, collator, alias))
        }

        for (index in tools.size until HomeLayout.TOTAL_SLOTS) {
            prefs.setHomeAppModel(index, emptyAppModel())
        }

        val pageCount = max(1, (tools.size + HomeLayout.APPS_PER_PAGE - 1) / HomeLayout.APPS_PER_PAGE)
        prefs.homePages = pageCount

        for (page in 1..HomeLayout.MAX_PAGES) {
            val startIndex = (page - 1) * HomeLayout.APPS_PER_PAGE
            val remaining = (tools.size - startIndex).coerceAtLeast(0)
            val appCount =
                when {
                    page > pageCount -> 0
                    remaining >= HomeLayout.APPS_PER_PAGE -> HomeLayout.APPS_PER_PAGE
                    else -> remaining
                }
            prefs.setAppsPerPage(page, appCount)
        }
    }

    private fun emptyAppModel(): AppModel =
        AppModel(
            appLabel = "",
            key = null,
            appPackage = "",
            appActivityName = "",
            user = android.os.Process.myUserHandle(),
            appAlias = "",
            hasNotification = false,
            entryType = AppEntryType.Tool,
        )
}
