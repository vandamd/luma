package com.vandam.luma.data

import android.content.Context
import android.util.Log
import com.vandam.luma.LumaApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

sealed interface ToolSyncResult {
    data class Success(
        val enabledToolIds: List<String>,
        val enabledAppIds: List<String>,
        val enabledAndroidApps: List<AndroidLauncherApp>,
        val requestedAppUpdateVersions: Map<String, String>,
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
        val enabledAndroidApps: List<AndroidLauncherApp> = emptyList(),
        val enabledAppIds: List<String> = emptyList(),
        val enabledToolIds: List<String> = emptyList(),
        val requestedAppUpdateVersions: Map<String, String> = emptyMap(),
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
                                val appIds = ManagedAppCatalog.normalizeIds(payload.enabledAppIds)
                                val androidApps = normalizeAndroidApps(payload.enabledAndroidApps)
                                applyHomeLayout(context, toolIds, appIds, androidApps)
                                Log.d(
                                    LOG_TAG,
                                    "Applied tool sync tools=${toolIds.joinToString()} apps=${appIds.joinToString()} android=${androidApps.joinToString { it.key }}",
                                )
                                ToolSyncResult.Success(
                                    enabledToolIds = toolIds,
                                    enabledAppIds = appIds,
                                    enabledAndroidApps = androidApps,
                                    requestedAppUpdateVersions = payload.requestedAppUpdateVersions,
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

    private fun normalizeAndroidApps(enabledAndroidApps: List<AndroidLauncherApp>): List<AndroidLauncherApp> {
        val androidAppsByKey = linkedMapOf<String, AndroidLauncherApp>()

        enabledAndroidApps.forEach { app ->
            val normalizedApp =
                AndroidLauncherApp(
                    label = app.label.trim().ifEmpty { app.packageName },
                    packageName = app.packageName.trim(),
                    activityName = app.activityName.trim(),
                )

            if (normalizedApp.packageName.isBlank() || normalizedApp.activityName.isBlank()) {
                return@forEach
            }

            androidAppsByKey.putIfAbsent(normalizedApp.key, normalizedApp)
        }

        return androidAppsByKey.values.toList()
    }

    private fun applyHomeLayout(
        context: Context,
        enabledToolIds: List<String>,
        enabledAppIds: List<String>,
        enabledAndroidApps: List<AndroidLauncherApp>,
    ) {
        val prefs = Prefs.getInstance(context)
        val tools = enabledToolIds.mapNotNull(Tool::fromId)
        val apps = enabledAppIds.mapNotNull(ManagedAppCatalog::fromId)

        Tool.entries.forEach { tool ->
            prefs.setToolEnabled(tool, tools.contains(tool))
        }

        prefs.enabledManagedAppIds = apps.map(ManagedApp::id).toSet()
        prefs.enabledAndroidApps = enabledAndroidApps
        HomeItemsManager.applyCurrentHomeLayout(context, prefs)
    }
}
