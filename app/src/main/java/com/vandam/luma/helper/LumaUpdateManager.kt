package com.vandam.luma.helper

import android.content.Context
import com.vandam.luma.BuildConfig
import com.vandam.luma.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object LumaUpdateManager {
    private const val RELEASE_URL = "https://api.github.com/repos/vandamd/luma/releases/latest"
    private val apkPattern = Regex(""".*\.apk$""", RegexOption.IGNORE_CASE)
    private val json = Json { ignoreUnknownKeys = true }

    data class AvailableUpdate(
        val fileName: String,
        val versionName: String,
        val downloadUrl: String,
    )

    suspend fun fetchAvailableUpdate(): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val release = fetchLatestRelease() ?: return@withContext null
            val latestVersion = normalizeVersion(release.tagName)
            val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)

            if (latestVersion.isEmpty() || compareVersions(latestVersion, currentVersion) <= 0) {
                return@withContext null
            }

            val apkAsset =
                release.assets.firstOrNull { asset ->
                    apkPattern.matches(asset.name) && asset.browserDownloadUrl.isNotBlank()
                } ?: return@withContext null

            AvailableUpdate(
                fileName = apkAsset.name,
                versionName = latestVersion,
                downloadUrl = apkAsset.browserDownloadUrl,
            )
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

        val apkFile =
            withContext(Dispatchers.IO) {
                downloadReleaseApk(context, update)
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
            ApkInstaller.openInstallPrompt(context, apkFile)
        }

        return true
    }

    private fun fetchLatestRelease(): GitHubLatestReleaseResponse? {
        val connection =
            (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Luma/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15_000
                readTimeout = 20_000
            }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<GitHubLatestReleaseResponse>(response)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeVersion(versionName: String): String =
        versionName.trim().removePrefix("v").trim()

    private fun compareVersions(
        left: String,
        right: String,
    ): Int {
        val leftParts = left.split('.', '-', '_')
        val rightParts = right.split('.', '-', '_')
        val maxSize = maxOf(leftParts.size, rightParts.size)

        for (index in 0 until maxSize) {
            val leftPart = leftParts.getOrNull(index).orEmpty()
            val rightPart = rightParts.getOrNull(index).orEmpty()
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()

            val comparison =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    else -> leftPart.compareTo(rightPart)
                }

            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    private fun downloadReleaseApk(
        context: Context,
        update: AvailableUpdate,
    ): File? {
        val cacheDir = File(context.cacheDir, "luma-updates").apply { mkdirs() }
        val destinationFile = File(cacheDir, "luma-${update.versionName}.apk")
        if (destinationFile.exists() && destinationFile.length() > 0) {
            return destinationFile
        }

        val tempFile = File(cacheDir, update.fileName.ifBlank { "luma.download" })
        val connection =
            (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
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
        val assets: List<GitHubReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAsset(
        val name: String,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
    )
}
