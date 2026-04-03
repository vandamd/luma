package com.vandam.luma

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserHandle
import dev.convex.android.ConvexClient
import com.vandam.luma.helper.HomeCleanupHelper
import com.vandam.luma.helper.ManagedAppManager

class LumaApplication : Application() {
    @Volatile
    private var convexClientInstance: ConvexClient? = null

    val convexClient: ConvexClient?
        get() = getOrCreateConvexClient()

    @Synchronized
    fun getOrCreateConvexClient(): ConvexClient? {
        convexClientInstance?.let { return it }

        val deploymentUrl = BuildConfig.CONVEX_URL.trim().removeSuffix("/")
        if (deploymentUrl.isBlank()) {
            return null
        }

        return ConvexClient(deploymentUrl).also {
            convexClientInstance = it
        }
    }

    @Synchronized
    fun recreateConvexClient(): ConvexClient? {
        convexClientInstance = null
        return getOrCreateConvexClient()
    }

    private val launcherAppsCallback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) {
                HomeCleanupHelper.cleanupRemovedPackage(this@LumaApplication, packageName, user)
                ManagedAppManager.syncInstalledAppsToDashboardForStoredAccount(this@LumaApplication)
            }

            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) {
                ManagedAppManager.syncInstalledAppsToDashboardForStoredAccount(this@LumaApplication)
            }

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) {
                ManagedAppManager.syncInstalledAppsToDashboardForStoredAccount(this@LumaApplication)
            }

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                ManagedAppManager.syncInstalledAppsToDashboardForStoredAccount(this@LumaApplication)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                ManagedAppManager.syncInstalledAppsToDashboardForStoredAccount(this@LumaApplication)
            }
        }

    override fun onCreate() {
        super.onCreate()
        getSystemService(LauncherApps::class.java).registerCallback(launcherAppsCallback)
    }
}
