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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
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
        "https://api.github.com/repos/ozzzmond/GTAR/releases/latest"
    private const val GITHUB_RELEASES_LIST_URL =
        "https://api.github.com/repos/ozzzmond/GTAR/releases?per_page=10"
    private const val GITHUB_TAGS_URL =
        "https://api.github.com/repos/ozzzmond/GTAR/tags"

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
                var highestDevTag: String? = null

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val isPrerelease = item.optBoolean("prerelease", false)
                    val isDraft = item.optBoolean("draft", false)
                    val tagName = item.optString("tag_name", "")

                    if (!isDraft && (isPrerelease || tagName.contains("dev", ignoreCase = true))) {
                        if (latestDevRelease == null || highestDevTag == null || isVersionNewer(tagName, highestDevTag)) {
                            latestDevRelease = item
                            highestDevTag = tagName
                        }
                    }
                }

                if (latestDevRelease != null) {
                    val tagName = latestDevRelease.optString("tag_name", "")
                    val cleanLatestVersion = cleanVersionString(tagName)
                    val releaseTitle = latestDevRelease.optString("name", "GTAR Dev Preview $tagName")
                    val releaseNotes = latestDevRelease.optString("body", "Developer preview update.")
                    val htmlUrl = latestDevRelease.optString("html_url", "https://github.com/ozzzmond/GTAR/releases")

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
                val cleanLatestVersion = cleanVersionString(tagName)
                val releaseTitle = jsonObject.optString("name", "GTAR $tagName")
                val releaseNotes = jsonObject.optString("body", "Bug fixes and stage enhancements.")
                val htmlUrl = jsonObject.optString("html_url", "https://github.com/ozzzmond/GTAR/releases")

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
                    val cleanTag = cleanVersionString(latestTag)
                    val isNewer = isVersionNewer(cleanTag, currentVersion)
                    if (isNewer) {
                        val info = ReleaseInfo(
                            tagName = latestTag,
                            versionName = cleanTag,
                            releaseTitle = "GTAR $latestTag",
                            releaseNotes = "New release tag $latestTag available on GitHub. Tap to view release details or download build artifacts.",
                            apkDownloadUrl = null,
                            htmlUrl = "https://github.com/ozzzmond/GTAR/releases",
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

    /** Removes Android tag/display prefixes while preserving version and dev iteration. */
    fun cleanVersionString(raw: String): String {
        val compact = raw.filterNot { it.isWhitespace() }
        return compact.replaceFirst(Regex("^app-?", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("^v", RegexOption.IGNORE_CASE), "")
    }

    /**
     * Compares semantic version parts and developer preview iterations.
     * Supports standard versions ("1.0.30" vs "1.0.29") and dev pre-releases
     * ("v1.0.50-dev.2" vs "v1.0.50-dev.1").
     */
    fun isVersionNewer(latest: String, current: String): Boolean {
        val cleanLatest = cleanVersionString(latest)
        val cleanCurrent = cleanVersionString(current)

        if (cleanLatest.isEmpty() || cleanCurrent.isEmpty()) return false
        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) return false

        // Extract base version before any hyphen or dev suffix (e.g. "1.0.50" from "1.0.50-dev.11")
        val baseLatestStr = cleanLatest.substringBefore("-").substringBefore(".dev").substringBefore("_dev")
        val baseCurrentStr = cleanCurrent.substringBefore("-").substringBefore(".dev").substringBefore("_dev")

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

        // Base semantic versions are equal. Compare pre-release iteration numerically.
        val latestDevIter = parseDevIteration(cleanLatest)
        val currentDevIter = parseDevIteration(cleanCurrent)

        // Production release (null) is newer than pre-release of the same version
        if (latestDevIter == null && currentDevIter != null) return true
        // Pre-release is older than production release of the same version
        if (latestDevIter != null && currentDevIter == null) return false

        // Both are dev iterations (e.g. dev.11 vs dev.10, dev.10 vs dev.9)
        if (latestDevIter != null && currentDevIter != null) {
            return latestDevIter > currentDevIter
        }

        return false
    }

    fun parseDevIteration(version: String): Int? {
        val lower = version.lowercase(Locale.US)
        if (!lower.contains("dev")) return null
        val regex = Regex("""dev[.\-_]?(\d+)""")
        val match = regex.find(lower)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        val afterDev = lower.substringAfter("dev").trimStart('.', '-', '_')
        return afterDev.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }

    @Volatile
    private var isDownloading = false

    /**
     * Downloads .apk directly with buffered streams, validates file integrity and size,
     * and triggers package installation only when the stream is completely closed and verified.
     */
    fun downloadAndInstallApk(context: Context, apkUrl: String, versionName: String) {
        if (isDownloading) {
            Toast.makeText(context, "Download already in progress...", Toast.LENGTH_SHORT).show()
            return
        }

        isDownloading = true
        Toast.makeText(context, "Downloading GTAR v$versionName...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val fileName = "GTAR-v$versionName.apk"
            val targetFile = File(downloadsDir, fileName)
            val tempFile = File(downloadsDir, "$fileName.tmp")

            try {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                AppLogManager.i("UpdateManager", "Starting direct buffered APK download from: $apkUrl to ${tempFile.absolutePath}")
                val connection = openDownloadConnection(apkUrl)
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP $responseCode from server: ${connection.responseMessage}")
                }

                val expectedLength = connection.contentLengthLong

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8 * 1024)
                        output.flush()
                    }
                }
                connection.disconnect()

                val actualLength = tempFile.length()
                AppLogManager.i("UpdateManager", "Download stream finished. Bytes received: $actualLength (expected: $expectedLength)")

                // Integrity Verification:
                // 1. File must not be empty
                if (actualLength <= 0L) {
                    throw IOException("Downloaded file is empty (0 bytes).")
                }

                // 2. Length must match Content-Length if provided
                if (expectedLength > 0L && actualLength != expectedLength) {
                    throw IOException("APK size mismatch! Expected $expectedLength bytes, but got $actualLength bytes.")
                }

                // 3. File must be large enough to be a valid APK (> 100KB)
                if (actualLength < 100_000L) {
                    throw IOException("Downloaded file is too small ($actualLength bytes) to be a valid Android APK.")
                }

                // 4. Must be a valid ZIP / APK archive header (PK\x03\x04)
                if (!isValidZipArchive(tempFile)) {
                    throw IOException("Downloaded file does not contain a valid APK/ZIP header.")
                }

                // Atomic rename to final target file
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                AppLogManager.i("UpdateManager", "APK download & integrity check successful: ${targetFile.absolutePath} (${targetFile.length()} bytes)")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download complete. Starting installation...", Toast.LENGTH_SHORT).show()
                    installApkFile(context, targetFile)
                }
            } catch (e: Exception) {
                AppLogManager.e("UpdateManager", "Download failed or corrupted: ${e.message}", e)
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}. Opening browser...", Toast.LENGTH_LONG).show()
                    openBrowserUrl(context, apkUrl)
                }
            } finally {
                isDownloading = false
            }
        }
    }

    /**
     * Follows HTTP 301/302/303/307/308 redirects up to maxRedirects to resolve GitHub asset S3 URLs cleanly.
     */
    private fun openDownloadConnection(initialUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var url = initialUrl
        var redirects = 0
        while (redirects < maxRedirects) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "GTAR-Android-App")
                setRequestProperty("Accept", "application/octet-stream,application/vnd.android.package-archive,*/*")
            }
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 ||
                responseCode == 308
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (!location.isNullOrBlank()) {
                    url = location
                    redirects++
                    AppLogManager.i("UpdateManager", "Redirect #$redirects -> $url")
                    continue
                }
            }
            return connection
        }
        throw IOException("Too many redirects ($maxRedirects) for $initialUrl")
    }

    /**
     * Validates that a file starts with standard ZIP / APK archive magic bytes (PK\x03\x04 or PK\x05\x06).
     */
    fun isValidZipArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val bytesRead = stream.read(header)
                if (bytesRead < 4) return false
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                ((header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                 (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()))
            }
        } catch (_: Exception) {
            false
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
