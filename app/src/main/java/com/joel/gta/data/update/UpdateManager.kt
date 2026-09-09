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
import com.joel.gta.BuildConfig
import com.joel.gta.data.logger.AppLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

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
    private const val GITHUB_RELEASES_LIST_URL =
        "https://api.github.com/repos/ozzzmond/GTA/releases?per_page=10"
    private const val GITHUB_TAGS_URL =
        "https://api.github.com/repos/ozzzmond/GTA/tags"

    /**
     * Checks the GitHub Releases API for updates.
     * If BuildConfig.DEBUG (or dev version name), queries the releases list endpoint
     * to find the latest pre-release (*-dev*) and matches the corresponding dev APK asset.
     * In Production builds, targets stable /releases/latest.
     */
    suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val isDev = BuildConfig.DEBUG || currentVersion.contains("-dev", ignoreCase = true)
        if (isDev) {
            checkDevUpdates(currentVersion)
        } else {
            checkProdUpdates(currentVersion)
        }
    }

    private fun checkDevUpdates(currentVersion: String): UpdateCheckResult {
        var connection: HttpURLConnection? = null
        try {
            AppLogManager.i("UpdateManager", "Checking for DEV updates. Endpoint: $GITHUB_RELEASES_LIST_URL (Local version: $currentVersion)")
            val url = URL(GITHUB_RELEASES_LIST_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GTAR-Android-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
                val jsonArray = JSONArray(jsonString)

                var latestDevRelease: JSONObject? = null
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val isPrerelease = item.optBoolean("prerelease", false)
                    val isDraft = item.optBoolean("draft", false)
                    val tagName = item.optString("tag_name", "")

                    if (!isDraft && (isPrerelease || tagName.contains("-dev", ignoreCase = true))) {
                        latestDevRelease = item
                        break
                    }
                }

                if (latestDevRelease != null) {
                    val tagName = latestDevRelease.optString("tag_name", "")
                    val cleanLatestVersion = tagName.removePrefix("v").removePrefix("V").trim()
                    val releaseTitle = latestDevRelease.optString("name", "GTAR Dev Preview $tagName")
                    val releaseNotes = latestDevRelease.optString("body", "Developer preview update.")
                    val htmlUrl = latestDevRelease.optString("html_url", "https://github.com/ozzzmond/GTA/releases")

                    AppLogManager.i("UpdateManager", "Detected local version: $currentVersion vs latest remote dev release tag: $tagName")

                    // Search for matching dev APK asset
                    var apkDownloadUrl: String? = null
                    var matchedAssetName: String? = null
                    val assetsArray = latestDevRelease.optJSONArray("assets")
                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                                matchedAssetName = assetName
                                if (assetName.contains("-dev", ignoreCase = true)) {
                                    break
                                }
                            }
                        }
                    }

                    if (apkDownloadUrl != null) {
                        AppLogManager.i("UpdateManager", "Asset download URL matched: $apkDownloadUrl (Asset name: $matchedAssetName)")
                    } else {
                        AppLogManager.w("UpdateManager", "No APK asset attached to remote dev release $tagName")
                    }

                    val isNewer = isVersionNewer(tagName, currentVersion)
                    val info = ReleaseInfo(
                        tagName = tagName,
                        versionName = cleanLatestVersion,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkDownloadUrl,
                        htmlUrl = htmlUrl,
                        isNewer = isNewer
                    )

                    return if (isNewer) {
                        AppLogManager.i("UpdateManager", "Newer DEV update available: $tagName > $currentVersion")
                        UpdateCheckResult.UpdateAvailable(info)
                    } else {
                        AppLogManager.i("UpdateManager", "DEV build is up to date: local $currentVersion >= remote $tagName")
                        UpdateCheckResult.UpToDate(currentVersion)
                    }
                } else {
                    AppLogManager.i("UpdateManager", "No pre-releases found in releases list, checking production releases as fallback...")
                    return checkProdUpdates(currentVersion)
                }
            } else {
                AppLogManager.w("UpdateManager", "DEV releases list returned HTTP $responseCode, falling back to production endpoint")
                return checkProdUpdates(currentVersion)
            }
        } catch (e: Exception) {
            AppLogManager.e("UpdateManager", "Error checking DEV updates: ${e.message}", e)
            return UpdateCheckResult.Error("Unable to check updates. Check your connection.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun checkProdUpdates(currentVersion: String): UpdateCheckResult {
        var connection: HttpURLConnection? = null
        try {
            AppLogManager.i("UpdateManager", "Checking for Production updates. Endpoint: $GITHUB_LATEST_RELEASE_URL (Local version: $currentVersion)")
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GTAR-Android-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
                val jsonObject = JSONObject(jsonString)

                val tagName = jsonObject.optString("tag_name", "")
                val cleanLatestVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val releaseTitle = jsonObject.optString("name", "GTAR $tagName")
                val releaseNotes = jsonObject.optString("body", "Bug fixes and stage enhancements.")
                val htmlUrl = jsonObject.optString("html_url", "https://github.com/ozzzmond/GTA/releases")

                AppLogManager.i("UpdateManager", "Detected local version: $currentVersion vs latest remote release tag: $tagName")

                // Look for production .apk asset
                var apkDownloadUrl: String? = null
                var matchedAssetName: String? = null
                val assetsArray = jsonObject.optJSONArray("assets")
                if (assetsArray != null) {
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (isEligibleReleaseApk(assetName)) {
                            apkDownloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                            matchedAssetName = assetName
                            break
                        }
                    }
                }

                if (apkDownloadUrl != null) {
                    AppLogManager.i("UpdateManager", "Asset download URL matched: $apkDownloadUrl (Asset name: $matchedAssetName)")
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

                return if (isNewer) {
                    AppLogManager.i("UpdateManager", "Newer production release available: $tagName > $currentVersion")
                    UpdateCheckResult.UpdateAvailable(info)
                } else {
                    AppLogManager.i("UpdateManager", "Production build is up to date: local $currentVersion >= remote $tagName")
                    UpdateCheckResult.UpToDate(currentVersion)
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return checkFallbackGitTags(currentVersion)
            } else {
                return UpdateCheckResult.Error("Unable to check updates (HTTP $responseCode)")
            }
        } catch (e: Exception) {
            AppLogManager.e("UpdateManager", "Error checking production updates: ${e.message}", e)
            return UpdateCheckResult.Error("Unable to check updates. Check your connection.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun checkFallbackGitTags(currentVersion: String): UpdateCheckResult {
        var tagsConnection: HttpURLConnection? = null
        try {
            val tagsUrl = URL(GITHUB_TAGS_URL)
            tagsConnection = (tagsUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GTAR-Android-App")
            }

            if (tagsConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val tagsJson = tagsConnection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
                val tagsArray = JSONArray(tagsJson)
                if (tagsArray.length() > 0) {
                    val latestTag = tagsArray.getJSONObject(0).optString("name", "")
                    val cleanTag = latestTag.removePrefix("v").removePrefix("V").trim()
                    val isNewer = isVersionNewer(cleanTag, currentVersion)
                    if (isNewer) {
                        val info = ReleaseInfo(
                            tagName = latestTag,
                            versionName = cleanTag,
                            releaseTitle = "GTAR $latestTag",
                            releaseNotes = "New release tag $latestTag available on GitHub. Tap to view release details or download build artifacts.",
                            apkDownloadUrl = null,
                            htmlUrl = "https://github.com/ozzzmond/GTA/releases",
                            isNewer = true
                        )
                        return UpdateCheckResult.UpdateAvailable(info)
                    } else {
                        return UpdateCheckResult.UpToDate(currentVersion)
                    }
                }
            }
            // Friendly message instead of raw 404
            return UpdateCheckResult.Error("No official releases found on GitHub yet.")
        } catch (e: Exception) {
            return UpdateCheckResult.Error("No official releases found on GitHub yet.")
        } finally {
            tagsConnection?.disconnect()
        }
    }

    /**
     * Verifies if an asset filename qualifies as a production release APK:
     * Must end with ".apk" and NOT contain "debug" or "-dev" (case-insensitive).
     */
    fun isEligibleReleaseApk(assetName: String): Boolean {
        return assetName.endsWith(".apk", ignoreCase = true) &&
                !assetName.contains("debug", ignoreCase = true) &&
                !assetName.contains("-dev", ignoreCase = true)
    }

    /**
     * Compares semantic version parts and developer preview iterations.
     * Supports standard versions ("1.0.30" vs "1.0.29") and dev pre-releases
     * ("v1.0.50-dev.2" vs "v1.0.50-dev.1").
     */
    fun isVersionNewer(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()

        if (cleanLatest.isEmpty() || cleanCurrent.isEmpty()) return false
        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) return false

        // Extract base version before any hyphen or dev suffix
        val baseLatestStr = cleanLatest.substringBefore("-")
        val baseCurrentStr = cleanCurrent.substringBefore("-")

        val latestParts = baseLatestStr.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val currentParts = baseCurrentStr.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        // Base semantic versions are equal. Compare pre-release iteration.
        val latestDevIter = parseDevIteration(cleanLatest)
        val currentDevIter = parseDevIteration(cleanCurrent)

        // Production release (null) is newer than pre-release of the same version
        if (latestDevIter == null && currentDevIter != null) return true
        // Pre-release is older than production release of the same version
        if (latestDevIter != null && currentDevIter == null) return false

        // Both are dev iterations (e.g. dev.2 vs dev.1)
        if (latestDevIter != null && currentDevIter != null) {
            return latestDevIter > currentDevIter
        }

        return false
    }

    private fun parseDevIteration(version: String): Int? {
        val lower = version.lowercase(Locale.US)
        if (!lower.contains("-dev")) return null
        val afterDev = lower.substringAfter("-dev").removePrefix(".")
        return afterDev.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
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

            val fileName = "GTAR-v$versionName.apk"
            val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Downloading GTAR v$versionName")
                setDescription("Downloading latest GTAR release APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }

            Toast.makeText(context, "Downloading GTAR v$versionName... Check notification bar", Toast.LENGTH_LONG).show()
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
        AppLogManager.i("UpdateManager", "installApkFile invoked for: ${apkFile.absolutePath} (exists=${apkFile.exists()}, size=${apkFile.length()} bytes)")
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
            AppLogManager.i("UpdateManager", "Launching ACTION_VIEW package installer intent with FLAG_ACTIVITY_NEW_TASK and FLAG_GRANT_READ_URI_PERMISSION (uri=$apkUri)")
            context.startActivity(installIntent)
        } catch (e: Exception) {
            AppLogManager.e("UpdateManager", "Cannot launch installer: ${e.message}", e)
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
