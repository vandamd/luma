package com.vandam.luma.helper

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.vandam.luma.BuildConfig
import com.vandam.luma.LumaApplication
import com.vandam.luma.MainActivity
import com.vandam.luma.R
import com.vandam.luma.data.AndroidLauncherApp
import com.vandam.luma.data.InstalledManagedApp
import com.vandam.luma.data.ManagedApp
import com.vandam.luma.data.ManagedAppCatalog
import com.vandam.luma.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private const val LIGHT_OS_PACKAGE_PREFIX = "com.lightos"
    private const val SYNC_INSTALLED_APPS_MUTATION = "accounts:syncInstalledApps"
    private const val INSTALLED_APPS_SYNC_DEBOUNCE_MS = 1500L
    @Volatile
    private var installedAppsDashboardSyncPending = false
    private val androidAppLabelOverrides =
        mapOf(
            "com.android.settings|com.android.settings.Settings" to "System",
        )
    private val hiddenAndroidAppKeys =
        setOf(
            "com.android.camera2|com.android.camera.CameraLauncher",
            "com.android.calendar|com.android.calendar.AllInOneActivity",
            "com.android.deskclock|com.android.deskclock.DeskClock",
            "com.android.contacts|com.android.contacts.activities.PeopleActivity",
            "at.bitfire.davdroid|at.bitfire.davdroid.ui.AccountsActivity",
            "com.android.documentsui|com.android.documentsui.LauncherActivity",
            "com.android.mms|com.android.mms.ui.ConversationList",
            "com.android.music|com.android.music.MusicBrowserActivity",
            "com.android.music|com.android.music.VideoBrowserActivity",
            "com.android.dialer|com.android.dialer.main.impl.MainActivity",
            "com.android.quicksearchbox|com.android.quicksearchbox.SearchActivity",
            "org.codeaurora.snapcam|com.android.camera.CameraLauncher",
            "com.android.soundrecorder|com.android.soundrecorder.SoundRecorder",
            "com.android.gallery3d|com.android.gallery3d.app.GalleryActivity",
            "com.android.gallery3d|com.android.gallery3d.app.MovieActivity",
            "org.chromium.webview_shell|org.chromium.webview_shell.WebViewBrowserActivity",
        )
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
    private var dashboardSyncDebounceJob: Job? = null

    @Volatile
    private var lastReportedDashboardAccountNumber: String? = null

    @Volatile
    private var lastReportedInstalledManagedApps: List<InstalledManagedApp>? = null

    @Volatile
    private var lastReportedInstalledAndroidApps: List<AndroidLauncherApp>? = null

    fun reconcileEnabledApps(
        context: Context,
        previousEnabledAppIds: List<String>?,
        enabledAppIds: List<String>,
        requestedAppUpdateVersions: Map<String, String> = emptyMap(),
    ) {
        val appContext = context.applicationContext
        val previousIds = previousEnabledAppIds?.toSet().orEmpty()
        val enabledIds = enabledAppIds.toSet()

        if (previousEnabledAppIds != null) {
            previousIds
                .subtract(enabledIds)
                .sorted()
                .forEach { appId ->
                    enqueueAction(PendingAction.Uninstall(appId = appId))
                }
        }

        enabledIds
            .sorted()
            .forEach { appId ->
                maybeEnqueueManagedAppInstallOrUpdate(
                    context = appContext,
                    appId = appId,
                    requestedVersionName = requestedAppUpdateVersions[appId],
                )
            }

        processNext(appContext)
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
                val installedManagedApps = getInstalledManagedApps(appContext)
                val installedAndroidApps = getInstalledAndroidApps(appContext)

                if (
                    lastReportedDashboardAccountNumber == accountNumber &&
                    lastReportedInstalledManagedApps == installedManagedApps &&
                    lastReportedInstalledAndroidApps == installedAndroidApps
                ) {
                    return@launch
                }

                runCatching {
                    withContext(Dispatchers.IO) {
                        val prefs = Prefs.getInstance(appContext)
                        client.mutation<Unit?>(
                            name = SYNC_INSTALLED_APPS_MUTATION,
                            args =
                                mapOf(
                                    "accountNumber" to accountNumber,
                                    "installationId" to prefs.installationId,
                                    "installedManagedApps" to
                                        installedManagedApps.map { app ->
                                            mapOf(
                                                "appId" to app.appId,
                                                "versionName" to app.versionName,
                                            )
                                        },
                                    "installedAndroidApps" to
                                        installedAndroidApps.map { app ->
                                            mapOf(
                                                "label" to app.label,
                                                "packageName" to app.packageName,
                                                "activityName" to app.activityName,
                                            )
                                        },
                                ),
                        )
                    }
                }.onSuccess {
                    lastReportedDashboardAccountNumber = accountNumber
                    lastReportedInstalledManagedApps = installedManagedApps
                    lastReportedInstalledAndroidApps = installedAndroidApps
                }.onFailure { error ->
                    Log.w(LOG_TAG, "Failed to sync installed apps", error)
                }
            }
    }

    fun scheduleInstalledAppsToDashboard(
        context: Context,
        accountNumber: String,
        immediate: Boolean = false,
    ) {
        if (!accountNumber.matches(Regex("^\\d{16}$"))) {
            return
        }

        dashboardSyncDebounceJob?.cancel()
        dashboardSyncDebounceJob =
            scope.launch {
                if (!immediate) {
                    delay(INSTALLED_APPS_SYNC_DEBOUNCE_MS)
                }
                syncInstalledAppsToDashboard(context, accountNumber)
            }
    }

    fun syncInstalledAppsToDashboardForStoredAccount(context: Context) {
        val accountNumber = Prefs.getInstance(context).accountNumber
        if (accountNumber.isBlank()) {
            return
        }

        syncInstalledAppsToDashboard(context, accountNumber)
    }

    fun scheduleInstalledAppsToDashboardForStoredAccount(
        context: Context,
        immediate: Boolean = false,
    ) {
        val accountNumber = Prefs.getInstance(context).accountNumber
        if (accountNumber.isBlank()) {
            return
        }

        scheduleInstalledAppsToDashboard(context, accountNumber, immediate)
    }

    fun scheduleInstalledAppsToDashboardForStoredAccountIfForeground(
        context: Context,
        immediate: Boolean = false,
    ) {
        val accountNumber = Prefs.getInstance(context).accountNumber
        if (accountNumber.isBlank()) {
            return
        }

        if (MainActivity.isLumaForeground()) {
            scheduleInstalledAppsToDashboard(context, accountNumber, immediate)
        } else {
            installedAppsDashboardSyncPending = true
        }
    }

    fun syncPendingInstalledAppsToDashboardForStoredAccount(context: Context): Boolean {
        if (!installedAppsDashboardSyncPending) {
            return false
        }

        val accountNumber = Prefs.getInstance(context).accountNumber
        if (accountNumber.isBlank()) {
            installedAppsDashboardSyncPending = false
            return false
        }

        installedAppsDashboardSyncPending = false
        syncInstalledAppsToDashboard(context, accountNumber)
        return true
    }

    fun clearSessionWork() {
        installedAppsDashboardSyncPending = false
        dashboardSyncDebounceJob?.cancel()
        dashboardSyncDebounceJob = null
        dashboardSyncJob?.cancel()
        dashboardSyncJob = null
        processingJob?.cancel()
        processingJob = null

        synchronized(this) {
            pendingActions.clear()
            activeAction = null
            awaitingExternalResult = false
        }

        lastReportedDashboardAccountNumber = null
        lastReportedInstalledManagedApps = null
        lastReportedInstalledAndroidApps = null
    }

    fun onResume(context: Context) {
        val appContext = context.applicationContext
        val currentAction = activeAction
        when {
            currentAction is PendingAction.InstallOrUpdate && awaitingExternalResult -> {
                if (currentAction.installerLaunched) {
                    clearActiveAction()
                } else if (ApkInstaller.canRequestPackageInstalls(appContext)) {
                    awaitingExternalResult = false
                } else {
                    return
                }
            }

            currentAction is PendingAction.Uninstall && awaitingExternalResult -> {
                clearActiveAction()
            }
        }

        processNext(appContext)
    }

    fun onPackageInstallResult(
        context: Context,
        packageName: String,
        success: Boolean,
    ) {
        val appContext = context.applicationContext
        val currentAction = activeAction as? PendingAction.InstallOrUpdate ?: return
        val managedApp = ManagedAppCatalog.fromId(currentAction.appId) ?: return
        if (managedApp.packageName != packageName) return

        clearActiveAction()
        if (success) {
            scheduleInstalledAppsToDashboardForStoredAccount(appContext, immediate = true)
        }
        processNext(appContext)
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
            ApkInstaller.openUnknownSourcesSettings(context)
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
        val appContext = context.applicationContext
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
                        runCatching {
                            processInstallOrUpdate(appContext, nextAction)
                        }.onFailure { error ->
                            Log.w(LOG_TAG, "Managed app install/update failed", error)
                            withContext(Dispatchers.Main.immediate) {
                                val managedApp = ManagedAppCatalog.fromId(nextAction.appId)
                                if (managedApp != null) {
                                    showToast(
                                        appContext,
                                        appContext.getString(R.string.toast_unable_to_fetch_release, managedApp.label),
                                    )
                                }
                            }
                            clearActiveAction()
                            processingJob = null
                            processNext(appContext)
                        }
                    }
            }

            is PendingAction.Uninstall -> {
                val managedApp = ManagedAppCatalog.fromId(nextAction.appId)
                if (managedApp == null || !isPackageInstalled(context, managedApp.packageName)) {
                    clearActiveAction()
                    processNext(appContext)
                    return
                }

                awaitingExternalResult = true
                scope.launch(Dispatchers.Main.immediate) {
                    uninstallApp(appContext, managedApp.packageName)
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

        val installedVersionName = normalizeVersion(getInstalledVersionName(context, managedApp.packageName))
        val requestedVersionName = normalizeVersion(action.expectedVersion)

        if (installedVersionName != null && (requestedVersionName == null || installedVersionName == requestedVersionName)) {
            clearActiveAction()
            processingJob = null
            processNext(context)
            return
        }

        if (!ApkInstaller.canRequestPackageInstalls(context)) {
            updateActiveAction(action.copy(installerLaunched = false, expectedVersion = null))
            awaitingExternalResult = true
            withContext(Dispatchers.Main.immediate) {
                ApkInstaller.openUnknownSourcesSettings(context)
            }
            processingJob = null
            return
        }

        val latestRelease =
            withContext(Dispatchers.IO) {
                fetchRelease(managedApp, requestedVersionName)
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
            ApkInstaller.openInstallPrompt(
                context = context,
                apkFile = apkFile,
                packageName = managedApp.packageName,
                appLabel = managedApp.label,
            )
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

    private fun maybeEnqueueManagedAppInstallOrUpdate(
        context: Context,
        appId: String,
        requestedVersionName: String?,
    ) {
        val managedApp = ManagedAppCatalog.fromId(appId) ?: return
        val installedVersionName = normalizeVersion(getInstalledVersionName(context, managedApp.packageName))
        val normalizedRequestedVersionName = normalizeVersion(requestedVersionName)

        when {
            installedVersionName == null -> {
                enqueueAction(
                    PendingAction.InstallOrUpdate(
                        appId = appId,
                        expectedVersion = normalizedRequestedVersionName,
                    ),
                )
            }

            normalizedRequestedVersionName != null &&
                installedVersionName != normalizedRequestedVersionName -> {
                enqueueAction(
                    PendingAction.InstallOrUpdate(
                        appId = appId,
                        expectedVersion = normalizedRequestedVersionName,
                    ),
                )
            }
        }
    }

    private fun getInstalledAndroidApps(context: Context): List<AndroidLauncherApp> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val appsByKey = linkedMapOf<String, AndroidLauncherApp>()

        launcherApps.getActivityList(null, android.os.Process.myUserHandle()).forEach { activity ->
            val packageName = activity.applicationInfo.packageName

            if (packageName == BuildConfig.APPLICATION_ID) {
                return@forEach
            }

            if (packageName.startsWith(LIGHT_OS_PACKAGE_PREFIX)) {
                return@forEach
            }

            if (ManagedAppCatalog.fromPackageName(packageName) != null) {
                return@forEach
            }

            val label = activity.label?.toString()?.trim().orEmpty()
            val activityName = activity.componentName.className

            if (activityName.isBlank()) {
                return@forEach
            }

            val app =
                AndroidLauncherApp(
                    label = if (label.isBlank()) packageName else label,
                    packageName = packageName,
                    activityName = activityName,
                )

            if (app.key in hiddenAndroidAppKeys) {
                return@forEach
            }

            appsByKey.putIfAbsent(
                app.key,
                app.copy(label = androidAppLabelOverrides[app.key] ?: app.label),
            )
        }

        return appsByKey.values.sortedWith(
            compareBy<AndroidLauncherApp>({ it.label.lowercase() }, { it.key }),
        )
    }

    private fun getInstalledManagedApps(context: Context): List<InstalledManagedApp> =
        ManagedAppCatalog.entries.mapNotNull { managedApp ->
            val installedVersionName = normalizeVersion(getInstalledVersionName(context, managedApp.packageName))
            installedVersionName?.let {
                InstalledManagedApp(
                    appId = managedApp.id,
                    versionName = it,
                )
            }
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

    private fun fetchRelease(
        managedApp: ManagedApp,
        requestedVersionName: String? = null,
    ): ReleaseAsset? {
        val normalizedRequestedVersionName = normalizeVersion(requestedVersionName)
        val candidateUrls =
            if (normalizedRequestedVersionName == null) {
                listOf(
                    "https://api.github.com/repos/${managedApp.repoOwner}/${managedApp.repoName}/releases/latest",
                )
            } else {
                listOf(
                    "https://api.github.com/repos/${managedApp.repoOwner}/${managedApp.repoName}/releases/tags/v$normalizedRequestedVersionName",
                    "https://api.github.com/repos/${managedApp.repoOwner}/${managedApp.repoName}/releases/tags/$normalizedRequestedVersionName",
                )
            }

        candidateUrls.forEachIndexed { index, url ->
            val release = fetchReleaseFromUrl(managedApp, url)

            if (release != null) {
                return release
            }

            if (normalizedRequestedVersionName == null || index == candidateUrls.lastIndex) {
                return null
            }
        }

        return null
    }

    private fun fetchReleaseFromUrl(
        managedApp: ManagedApp,
        url: String,
    ): ReleaseAsset? =
        runCatching {
            fetchReleaseFromUrlOrThrow(managedApp, url)
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed to fetch release for ${managedApp.id}", error)
        }.getOrNull()

    private fun fetchReleaseFromUrlOrThrow(
        managedApp: ManagedApp,
        url: String,
    ): ReleaseAsset? {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
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
        if (
            ApkInstaller.isValidCachedApk(
                context = context,
                apkFile = destinationFile,
                expectedPackageName = managedApp.packageName,
                expectedVersionName = normalizedVersion,
            )
        ) {
            return destinationFile
        } else if (destinationFile.exists()) {
            destinationFile.delete()
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
}
