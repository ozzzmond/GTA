package com.joel.gta.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val isNewer: Boolean
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: ReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateManager {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/ozzzmond/GTA/releases/latest"

    /**
     * Checks the GitHub Releases API for the latest published GTA release.
     * Compares version semantics against [currentVersion] (e.g. "1.0.28").
     */
    suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GTA-Android-App")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("Unable to check updates (HTTP $responseCode)")
            }

            val jsonString = connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            val jsonObject = JSONObject(jsonString)

            val tagName = jsonObject.optString("tag_name", "")
            val cleanLatestVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseTitle = jsonObject.optString("name", "GTA $tagName")
            val releaseNotes = jsonObject.optString("body", "Bug fixes and stage enhancements.")
            val htmlUrl = jsonObject.optString("html_url", "https://github.com/ozzzmond/GTA/releases")

            // Look for .apk asset
            var apkDownloadUrl: String? = null
            val assetsArray = jsonObject.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(cleanLatestVersion, currentVersion)
            val info = ReleaseInfo(
                tagName = tagName,
                versionName = cleanLatestVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkDownloadUrl,
                htmlUrl = htmlUrl,
                isNewer = isNewer
            )

            if (isNewer) {
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Unable to check updates. Check your connection.")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Compares numeric semantic version parts (e.g. "1.0.28" vs "1.0.27").
     */
    fun isVersionNewer(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()

        if (cleanLatest.isEmpty() || cleanCurrent.isEmpty()) return false
        if (cleanLatest == cleanCurrent) return false

        val latestParts = cleanLatest.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val currentParts = cleanCurrent.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Enqueues .apk download using system DownloadManager and triggers installation on completion.
     */
    fun downloadAndInstallApk(context: Context, apkUrl: String, versionName: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                openBrowserUrl(context, apkUrl)
                return
            }

            val fileName = "GTA-v$versionName.apk"
            val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Downloading GTA v$versionName")
                setDescription("Downloading latest GTA release APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }

            Toast.makeText(context, "Downloading GTA v$versionName... Check notification bar", Toast.LENGTH_LONG).show()
            val downloadId = downloadManager.enqueue(request)

            val onCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (id == downloadId) {
                        try {
                            recvContext?.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        if (targetFile.exists() && recvContext != null) {
                            installApkFile(recvContext, targetFile)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    onCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    onCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}. Opening browser...", Toast.LENGTH_SHORT).show()
            openBrowserUrl(context, apkUrl)
        }
    }

    /**
     * Fires ACTION_VIEW with FileProvider content URI to start the Android package installer.
     */
    fun installApkFile(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Browser fallback if DownloadManager or installer permissions are restricted.
     */
    fun openBrowserUrl(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
