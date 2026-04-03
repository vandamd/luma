package com.vandam.luma.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.vandam.luma.BuildConfig
import com.vandam.luma.LumaApplication
import com.vandam.luma.R
import com.vandam.luma.data.ManagedApp
import com.vandam.luma.data.ManagedAppCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ManagedAppManager {
    private const val LOG_TAG = "ManagedAppManager"
    private const val SYNC_INSTALLED_APPS_MUTATION = "accounts:syncInstalledApps"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingActions = ArrayDeque<PendingAction>()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activeAction: PendingAction? = null

    @Volatile
    private var awaitingExternalResult = false

    @Volatile
    private var processingJob: Job? = null

    @Volatile
    private var dashboardSyncJob: Job? = null

    @Volatile
    private var lastReportedDashboardAccountNumber: String? = null

    @Volatile
    private var lastReportedInstalledAppIds: List<String>? = null

    fun reconcileEnabledApps(
        context: Context,
        previousEnabledAppIds: List<String>?,
        enabledAppIds: List<String>,
    ) {
        val previousIds = previousEnabledAppIds?.toSet().orEmpty()
        val enabledIds = enabledAppIds.toSet()

        if (previousEnabledAppIds == null) {
            enabledAppIds.forEach { appId ->
                val managedApp = ManagedAppCatalog.fromId(appId) ?: return@forEach
                if (!isPackageInstalled(context, managedApp.packageName)) {
                    enqueueAction(PendingAction.InstallOrUpdate(appId = appId))
                }
            }
        } else {
            previousIds
                .subtract(enabledIds)
                .sorted()
                .forEach { appId ->
                    enqueueAction(PendingAction.Uninstall(appId = appId))
                }

            enabledIds
                .subtract(previousIds)
                .sorted()
                .forEach { appId ->
                    val managedApp = ManagedAppCatalog.fromId(appId) ?: return@forEach
                    if (!isPackageInstalled(context, managedApp.packageName)) {
                        enqueueAction(PendingAction.InstallOrUpdate(appId = appId))
                    }
                }
        }

        processNext(context)
    }

    fun syncInstalledAppsToDashboard(
        context: Context,
        accountNumber: String,
    ) {
        if (!accountNumber.matches(Regex("^\\d{16}$"))) {
            return
        }

        val appContext = context.applicationContext
        val client = (appContext as? LumaApplication)?.convexClient ?: return
        dashboardSyncJob?.cancel()
        dashboardSyncJob =
            scope.launch {
                val installedAppIds = ManagedAppCatalog.installedIds(appContext)

                if (
                    lastReportedDashboardAccountNumber == accountNumber &&
                    lastReportedInstalledAppIds == installedAppIds
                ) {
                    return@launch
                }

                runCatching {
                    withContext(Dispatchers.IO) {
                        client.mutation<UpdatedAtResponse>(
                            name = SYNC_INSTALLED_APPS_MUTATION,
                            args =
                                mapOf(
                                    "accountNumber" to accountNumber,
                                    "installedAppIds" to installedAppIds,
                                ),
                        )
                    }
                }.onSuccess {
                    lastReportedDashboardAccountNumber = accountNumber
                    lastReportedInstalledAppIds = installedAppIds
                }.onFailure { error ->
                    Log.w(LOG_TAG, "Failed to sync installed managed apps", error)
                }
            }
    }

    fun onResume(context: Context) {
        val currentAction = activeAction
        when {
            currentAction is PendingAction.InstallOrUpdate && awaitingExternalResult -> {
                if (currentAction.installerLaunched) {
                    clearActiveAction()
                } else if (canRequestPackageInstalls(context)) {
                    awaitingExternalResult = false
                } else {
                    return
                }
            }

            currentAction is PendingAction.Uninstall && awaitingExternalResult -> {
                clearActiveAction()
            }
        }

        processNext(context)
    }

    fun handleManagedAppLaunch(
        context: Context,
        managedApp: ManagedApp,
    ): Boolean {
        if (isPackageInstalled(context, managedApp.packageName)) {
            return false
        }

        val currentAction = activeAction
        if (
            currentAction is PendingAction.InstallOrUpdate &&
            currentAction.appId == managedApp.id &&
            awaitingExternalResult &&
            !currentAction.installerLaunched
        ) {
            openUnknownSourcesSettings(context)
            return true
        }

        enqueueAction(PendingAction.InstallOrUpdate(managedApp.id))
        processNext(context)
        return true
    }

    private fun processNext(
        context: Context,
        retryCurrentAction: Boolean = false,
    ) {
        if (awaitingExternalResult || processingJob?.isActive == true) {
            return
        }

        val nextAction =
            synchronized(this) {
                if (retryCurrentAction) {
                    activeAction
                } else {
                    activeAction ?: pendingActions.removeFirstOrNull()?.also { activeAction = it }
                }
            } ?: return

        when (nextAction) {
            is PendingAction.InstallOrUpdate -> {
                processingJob =
                    scope.launch {
                        processInstallOrUpdate(context, nextAction)
                    }
            }

            is PendingAction.Uninstall -> {
                val managedApp = ManagedAppCatalog.fromId(nextAction.appId)
                if (managedApp == null || !isPackageInstalled(context, managedApp.packageName)) {
                    clearActiveAction()
                    processNext(context)
                    return
                }

                awaitingExternalResult = true
                scope.launch(Dispatchers.Main.immediate) {
                    uninstallApp(context, managedApp.packageName)
                }
            }
        }
    }

    private suspend fun processInstallOrUpdate(
        context: Context,
        action: PendingAction.InstallOrUpdate,
    ) {
        val managedApp = ManagedAppCatalog.fromId(action.appId)
        if (managedApp == null) {
            clearActiveAction()
            processNext(context)
            return
        }

        if (isPackageInstalled(context, managedApp.packageName)) {
            clearActiveAction()
            processingJob = null
            processNext(context)
            return
        }

        if (!canRequestPackageInstalls(context)) {
            updateActiveAction(action.copy(installerLaunched = false, expectedVersion = null))
            awaitingExternalResult = true
            withContext(Dispatchers.Main.immediate) {
                openUnknownSourcesSettings(context)
            }
            processingJob = null
            return
        }

        val latestRelease =
            withContext(Dispatchers.IO) {
                fetchLatestRelease(managedApp)
            }

        if (latestRelease == null) {
            withContext(Dispatchers.Main.immediate) {
                showToast(
                    context,
                    context.getString(R.string.toast_unable_to_fetch_release, managedApp.label),
                )
            }
            clearActiveAction()
            processingJob = null
            processNext(context)
            return
        }

        val expectedVersion = normalizeVersion(latestRelease.versionName)

        val apkFile =
            withContext(Dispatchers.IO) {
                downloadReleaseApk(context, managedApp, latestRelease)
            }

        if (apkFile == null) {
            withContext(Dispatchers.Main.immediate) {
                showToast(
                    context,
                    context.getString(R.string.toast_unable_to_download_release, managedApp.label),
                )
            }
            clearActiveAction()
            processingJob = null
            processNext(context)
            return
        }

        updateActiveAction(
            action.copy(
                expectedVersion = expectedVersion,
                installerLaunched = true,
            ),
        )
        awaitingExternalResult = true
        withContext(Dispatchers.Main.immediate) {
            openInstallPrompt(context, apkFile)
        }
        processingJob = null
    }

    private fun enqueueAction(action: PendingAction) {
        synchronized(this) {
            val hasSameActiveAction =
                when (val current = activeAction) {
                    is PendingAction.InstallOrUpdate ->
                        action is PendingAction.InstallOrUpdate && current.appId == action.appId
                    is PendingAction.Uninstall ->
                        action is PendingAction.Uninstall && current.appId == action.appId
                    null -> false
                }
            if (hasSameActiveAction) {
                return
            }

            val alreadyQueued =
                pendingActions.any { queued ->
                    when {
                        queued is PendingAction.InstallOrUpdate && action is PendingAction.InstallOrUpdate ->
                            queued.appId == action.appId
                        queued is PendingAction.Uninstall && action is PendingAction.Uninstall ->
                            queued.appId == action.appId
                        else -> false
                    }
                }
            if (!alreadyQueued) {
                pendingActions.addLast(action)
            }
        }
    }

    private fun updateActiveAction(action: PendingAction) {
        synchronized(this) {
            activeAction = action
        }
    }

    private fun clearActiveAction() {
        synchronized(this) {
            activeAction = null
            awaitingExternalResult = false
        }
    }

    private fun canRequestPackageInstalls(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun openUnknownSourcesSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        context.startActivity(intent)
    }

    private fun openInstallPrompt(
        context: Context,
        apkFile: File,
    ) {
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        context.startActivity(intent)
    }

    private fun isPackageInstalled(
        context: Context,
        packageName: String,
    ): Boolean = getInstalledVersionName(context, packageName) != null

    private fun getInstalledVersionName(
        context: Context,
        packageName: String,
    ): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager
                    .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                    .versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        }

    private fun normalizeVersion(versionName: String?): String? =
        versionName
            ?.trim()
            ?.removePrefix("v")
            ?.takeIf { it.isNotBlank() }

    private fun fetchLatestRelease(managedApp: ManagedApp): ReleaseAsset? {
        val connection =
            (URL("https://api.github.com/repos/${managedApp.repoOwner}/${managedApp.repoName}/releases/latest").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Luma/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15_000
                readTimeout = 20_000
            }

        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubLatestReleaseResponse>(response)
                val asset =
                    release.assets.firstOrNull { asset ->
                        managedApp.assetNamePattern.matches(asset.name)
                    } ?: return null

                ReleaseAsset(
                    versionName = release.tagName.ifBlank { asset.name.substringBeforeLast(".apk") },
                    downloadUrl = asset.browserDownloadUrl,
                    fileName = asset.name,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadReleaseApk(
        context: Context,
        managedApp: ManagedApp,
        releaseAsset: ReleaseAsset,
    ): File? {
        val cacheDir = File(context.cacheDir, "managed-apps").apply { mkdirs() }
        val normalizedVersion = normalizeVersion(releaseAsset.versionName) ?: "latest"
        val destinationFile = File(cacheDir, "${managedApp.id}-$normalizedVersion.apk")
        if (destinationFile.exists() && destinationFile.length() > 0) {
            return destinationFile
        }

        val tempFile = File(cacheDir, "${managedApp.id}.download")
        val connection =
            (URL(releaseAsset.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Luma/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15_000
                readTimeout = 60_000
            }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }
                destinationFile.takeIf { it.exists() && it.length() > 0 }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
            if (tempFile.exists() && tempFile != destinationFile) {
                tempFile.delete()
            }
        }
    }

    private sealed interface PendingAction {
        val appId: String

        data class InstallOrUpdate(
            override val appId: String,
            val expectedVersion: String? = null,
            val installerLaunched: Boolean = false,
        ) : PendingAction

        data class Uninstall(
            override val appId: String,
        ) : PendingAction
    }

    @Serializable
    private data class GitHubLatestReleaseResponse(
        @SerialName("tag_name")
        val tagName: String = "",
        val assets: List<GitHubReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAsset(
        val name: String,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
    )

    private data class ReleaseAsset(
        val versionName: String,
        val downloadUrl: String,
        val fileName: String,
    )

    @Serializable
    private data class UpdatedAtResponse(
        val updatedAt: Long,
    )
}
