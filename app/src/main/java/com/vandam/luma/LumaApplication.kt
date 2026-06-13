package com.vandam.luma

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import com.vandam.luma.helper.HomeCleanupHelper
import com.vandam.luma.helper.ManagedAppManager
import dev.convex.android.ConvexClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LumaApplication : Application() {
    @Volatile
    private var convexClientInstance: ConvexClient? = null
    private val convexCloseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val convexClient: ConvexClient?
        get() = getOrCreateConvexClient()

    @Synchronized
    fun getOrCreateConvexClient(): ConvexClient? {
        convexClientInstance?.let { return it }

        val deploymentUrl = BuildConfig.CONVEX_URL.trim().removeSuffix("/")
        if (deploymentUrl.isBlank()) {
            return null
        }
        if (!BuildConfig.DEBUG && !deploymentUrl.startsWith("https://")) {
            return null
        }

        return ConvexClient(deploymentUrl).also {
            convexClientInstance = it
        }
    }

    @Synchronized
    fun recreateConvexClient(): ConvexClient? {
        closeConvexClient()
        return getOrCreateConvexClient()
    }

    @Synchronized
    fun closeConvexClient() {
        val client = convexClientInstance ?: return
        convexClientInstance = null
        convexCloseScope.launch {
            closeConvexClient(client)
        }
    }

    private fun closeConvexClient(client: ConvexClient) {
        runCatching {
            val ffiClient =
                client
                    .javaClass
                    .getDeclaredMethod("getFfiClient")
                    .apply { isAccessible = true }
                    .invoke(client)
            (ffiClient as? AutoCloseable)?.close()
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed to close Convex client", error)
        }
    }

    private val launcherAppsCallback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) {
                HomeCleanupHelper.cleanupRemovedPackage(this@LumaApplication, packageName, user)
                scheduleInstalledAppsDashboardSync()
            }

            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) {
                scheduleInstalledAppsDashboardSync()
            }

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) {
                scheduleInstalledAppsDashboardSync()
            }

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                scheduleInstalledAppsDashboardSync()
            }

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                scheduleInstalledAppsDashboardSync()
            }
        }

    private fun scheduleInstalledAppsDashboardSync() {
        ManagedAppManager.scheduleInstalledAppsToDashboardForStoredAccountIfForeground(this)
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(LauncherApps::class.java).registerCallback(launcherAppsCallback)
    }

    companion object {
        private const val LOG_TAG = "LumaApplication"
    }
}
