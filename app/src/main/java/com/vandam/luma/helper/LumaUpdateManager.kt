package com.vandam.luma.helper

import android.content.Context
import com.vandam.luma.BuildConfig
import com.vandam.luma.LumaApplication
import com.vandam.luma.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object LumaUpdateManager {
    private const val VERSION_QUERY_PATH = "managedApps:getReleaseVersions"
    private val apkPattern = Regex(""".*\.apk$""", RegexOption.IGNORE_CASE)
    private val versionSeparatorPattern = Regex("[._-]")
    private val json = Json { ignoreUnknownKeys = true }

    data class AvailableUpdate(
        val versionName: String,
    )

    private data class ParsedVersion(
        val core: List<Int>,
        val prerelease: List<String>,
    )

    suspend fun fetchAvailableUpdate(context: Context): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val latestVersion = fetchLatestVersion(context) ?: return@withContext null
            val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)

            if (latestVersion.isEmpty() || compareVersions(latestVersion, currentVersion) <= 0) {
                return@withContext null
            }

            AvailableUpdate(
                versionName = latestVersion,
            )
        }

    suspend fun fetchReleaseNotes(versionName: String): String =
        withContext(Dispatchers.IO) {
            fetchRelease(versionName)?.body.orEmpty().trim()
        }

    suspend fun installUpdate(
        context: Context,
        update: AvailableUpdate,
    ): Boolean {
        if (!ApkInstaller.canRequestPackageInstalls(context)) {
            withContext(Main) {
                ApkInstaller.openUnknownSourcesSettings(context)
            }
            return false
        }

        val release =
            withContext(Dispatchers.IO) {
                fetchRelease(update.versionName)
            }
        val normalizedReleaseVersion = normalizeVersion(release?.tagName.orEmpty())

        if (release == null || normalizedReleaseVersion != update.versionName) {
            withContext(Main) {
                showToast(
                    context.applicationContext,
                    context.getString(R.string.toast_unable_to_fetch_release, context.getString(R.string.app_name)),
                )
            }
            return false
        }

        val apkAsset =
            release.assets.firstOrNull { asset ->
                apkPattern.matches(asset.name) && asset.browserDownloadUrl.isNotBlank()
            } ?: run {
                withContext(Main) {
                    showToast(
                        context.applicationContext,
                        context.getString(R.string.toast_unable_to_fetch_release, context.getString(R.string.app_name)),
                    )
                }
                return false
            }

        val apkFile =
            withContext(Dispatchers.IO) {
                downloadReleaseApk(context, update.versionName, apkAsset)
            }

        if (apkFile == null) {
            withContext(Main) {
                showToast(
                    context.applicationContext,
                    context.getString(R.string.toast_unable_to_download_release, context.getString(R.string.app_name)),
                )
            }
            return false
        }

        withContext(Main) {
            ApkInstaller.openInstallPrompt(
                context = context,
                apkFile = apkFile,
                packageName = context.packageName,
                appLabel = context.getString(R.string.app_name),
            )
        }

        return true
    }

    private suspend fun fetchLatestVersion(context: Context): String? {
        val client =
            (context.applicationContext as? LumaApplication)?.convexClient
                ?: return null

        return client
            .subscribe<Map<String, String>>(
                name = VERSION_QUERY_PATH,
                args = emptyMap(),
            ).first()
            .getOrNull()
            ?.get("luma")
            ?.let(::normalizeVersion)
            ?.takeIf { it.isNotBlank() }
    }

    private fun fetchRelease(versionName: String): GitHubLatestReleaseResponse? {
        val normalizedVersion = normalizeVersion(versionName)
        val candidateUrls =
            listOf(
                "https://api.github.com/repos/vandamd/luma/releases/tags/v$normalizedVersion",
                "https://api.github.com/repos/vandamd/luma/releases/tags/$normalizedVersion",
            )

        candidateUrls.forEach { releaseUrl ->
            val connection =
                (URL(releaseUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Luma/${BuildConfig.VERSION_NAME}")
                    connectTimeout = 15_000
                    readTimeout = 20_000
                }

            try {
                if (connection.responseCode in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    return json.decodeFromString<GitHubLatestReleaseResponse>(response)
                }
            } finally {
                connection.disconnect()
            }
        }

        return null
    }

    private fun normalizeVersion(versionName: String): String =
        versionName.trim().removePrefix("v").trim()

    private fun compareVersions(
        left: String,
        right: String,
    ): Int {
        val leftVersion = parseVersion(left)
        val rightVersion = parseVersion(right)
        val coreSize = maxOf(leftVersion.core.size, rightVersion.core.size)

        for (index in 0 until coreSize) {
            val comparison =
                leftVersion.core.getOrElse(index) { 0 }.compareTo(
                    rightVersion.core.getOrElse(index) { 0 },
                )
            if (comparison != 0) {
                return comparison
            }
        }

        if (leftVersion.prerelease.isEmpty() && rightVersion.prerelease.isEmpty()) {
            return 0
        }

        if (leftVersion.prerelease.isEmpty()) {
            return 1
        }

        if (rightVersion.prerelease.isEmpty()) {
            return -1
        }

        val prereleaseSize = maxOf(leftVersion.prerelease.size, rightVersion.prerelease.size)
        for (index in 0 until prereleaseSize) {
            val leftPart = leftVersion.prerelease.getOrNull(index) ?: return -1
            val rightPart = rightVersion.prerelease.getOrNull(index) ?: return 1
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()

            val comparison =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> leftPart.compareTo(rightPart)
                }

            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    private fun parseVersion(versionName: String): ParsedVersion {
        val tokens =
            versionSeparatorPattern
                .split(normalizeVersion(versionName))
                .filter { it.isNotBlank() }

        val core = mutableListOf<Int>()
        var prereleaseStart = tokens.size

        tokens.forEachIndexed { index, token ->
            val number = token.toIntOrNull()
            if (prereleaseStart == tokens.size && number != null) {
                core += number
            } else if (prereleaseStart == tokens.size) {
                prereleaseStart = index
            }
        }

        return ParsedVersion(
            core = core,
            prerelease = tokens.drop(prereleaseStart),
        )
    }

    private fun downloadReleaseApk(
        context: Context,
        versionName: String,
        asset: GitHubReleaseAsset,
    ): File? {
        val cacheDir = File(context.cacheDir, "luma-updates").apply { mkdirs() }
        val destinationFile = File(cacheDir, "luma-$versionName.apk")
        if (destinationFile.exists() && destinationFile.length() > 0) {
            return destinationFile
        }

        val tempFile = File(cacheDir, asset.name.ifBlank { "luma.download" })
        val connection =
            (URL(asset.browserDownloadUrl).openConnection() as HttpURLConnection).apply {
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

    @Serializable
    private data class GitHubLatestReleaseResponse(
        @SerialName("tag_name")
        val tagName: String = "",
        val body: String = "",
        val assets: List<GitHubReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAsset(
        val name: String,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
    )
}
