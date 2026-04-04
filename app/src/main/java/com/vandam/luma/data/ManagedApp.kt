package com.vandam.luma.data

import android.content.Context
import android.os.Build
import java.text.Collator

data class ManagedApp(
    val id: String,
    val label: String,
    val packageName: String,
    val repoOwner: String,
    val repoName: String,
    val assetNamePattern: Regex = DEFAULT_ASSET_PATTERN,
) {
    fun toAppModel(
        collator: Collator,
    ): AppModel {
        return AppModel(
            appLabel = label,
            key = collator.getCollationKey(label),
            appPackage = packageName,
            appActivityName = id,
            user = android.os.Process.myUserHandle(),
            hasNotification = false,
            entryType = AppEntryType.ManagedApp,
        )
    }

    companion object {
        private val DEFAULT_ASSET_PATTERN = Regex(""".*\.apk$""", RegexOption.IGNORE_CASE)
    }
}

data class InstalledManagedApp(
    val appId: String,
    val versionName: String,
)

object ManagedAppCatalog {
    private const val DEFAULT_REPO_OWNER = "vandamd"

    val entries: List<ManagedApp> =
        listOf(
            ManagedApp(
                id = "buses",
                label = "Buses",
                packageName = "com.vandam.buses",
                repoOwner = DEFAULT_REPO_OWNER,
                repoName = "buses",
            ),
            ManagedApp(
                id = "passes",
                label = "Passes",
                packageName = "com.vandamd.passes",
                repoOwner = DEFAULT_REPO_OWNER,
                repoName = "passes",
            ),
            ManagedApp(
                id = "weather",
                label = "Weather",
                packageName = "com.vandam.weather",
                repoOwner = DEFAULT_REPO_OWNER,
                repoName = "weather",
            ),
            ManagedApp(
                id = "zero",
                label = "Zero",
                packageName = "com.vandam.zero",
                repoOwner = DEFAULT_REPO_OWNER,
                repoName = "zero",
            ),
        )

    fun fromId(id: String?): ManagedApp? = entries.firstOrNull { it.id == id }

    fun fromPackageName(packageName: String?): ManagedApp? = entries.firstOrNull { it.packageName == packageName }

    fun normalizeIds(enabledAppIds: List<String>): List<String> {
        val normalized = mutableListOf<String>()
        val seenIds = linkedSetOf<String>()
        enabledAppIds.forEach { appId ->
            val managedApp = fromId(appId) ?: return@forEach
            if (seenIds.add(managedApp.id)) {
                normalized.add(managedApp.id)
            }
        }
        return normalized
    }

    fun installedIds(context: Context): List<String> =
        entries
            .filter { managedApp ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(
                            managedApp.packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(managedApp.packageName, 0)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }.map(ManagedApp::id)
}
