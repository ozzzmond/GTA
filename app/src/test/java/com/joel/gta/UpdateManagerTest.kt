package com.joel.gta

import com.joel.gta.data.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateManagerTest {

    @Test
    fun cleansOfficialTagsDisplayNamesAndLegacyPrefixes() {
        for (prefix in listOf("app-v", "app-", "app v", "APP-V", "v", "V", "")) {
            assertEquals("1.1.71", UpdateManager.cleanVersionString("  ${prefix}1.1.71  "))
            assertEquals("1.0.62-dev.10", UpdateManager.cleanVersionString("${prefix}1.0.62-dev.10"))
        }
        assertEquals("", UpdateManager.cleanVersionString("   "))
    }

    @Test
    fun comparesOfficialTagsAgainstInstalledDisplayVersions() {
        assertTrue(UpdateManager.isVersionNewer("app-v1.1.71", "app v1.1.70"))
        assertTrue(UpdateManager.isVersionNewer("app-v1.0.62-dev.10", "app v1.0.62-dev.9"))
        assertFalse(UpdateManager.isVersionNewer("app-v1.1.71", "app v1.1.71"))
        assertFalse(UpdateManager.isVersionNewer("app-v1.1.70", "app v1.1.71"))
        assertTrue(UpdateManager.isVersionNewer("app-v1.1.71", "app v1.1.71-dev.1"))
        assertFalse(UpdateManager.isVersionNewer("app-v1.1.71-dev.1", "app v1.1.71"))
    }


    @Test
    fun testVersionComparison_newerVersions() {
        assertTrue(UpdateManager.isVersionNewer("1.0.28", "1.0.27"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.28", "1.0.27"))
        assertTrue(UpdateManager.isVersionNewer("1.1.0", "1.0.28"))
        assertTrue(UpdateManager.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateManager.isVersionNewer("1.0.28.1", "1.0.28"))
    }

    @Test
    fun testVersionComparison_sameOrOlderVersions() {
        assertFalse(UpdateManager.isVersionNewer("1.0.27", "1.0.27"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.27", "1.0.27"))
        assertFalse(UpdateManager.isVersionNewer("1.0.27", "1.0.28"))
        assertFalse(UpdateManager.isVersionNewer("1.0.26", "1.0.27"))
        assertFalse(UpdateManager.isVersionNewer("", "1.0.27"))
    }

    @Test
    fun testVersionComparison_devPreReleases() {
        // Dev iterations
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.2", "v1.0.50-dev.1"))
        assertTrue(UpdateManager.isVersionNewer("1.0.50-dev.2", "1.0.50-dev.1"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.3", "v1.0.50-dev.2"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.4", "v1.0.50-dev.3"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.5", "v1.0.50-dev.4"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.6", "v1.0.50-dev.5"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.7", "v1.0.50-dev.6"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.8", "v1.0.50-dev.7"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.9", "v1.0.50-dev.8"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.10", "v1.0.50-dev.9"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.11", "v1.0.50-dev.10"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.11", "v1.0.50-dev.9"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.12", "v1.0.50-dev.11"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.12", "v1.0.50-dev.10"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.10", "v1.0.50-dev.2"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.50-dev.2", "v1.0.50-dev"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.1", "v1.0.50-dev.2"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.11", "v1.0.50-dev.12"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.10", "v1.0.50-dev.12"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.10", "v1.0.50-dev.11"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.9", "v1.0.50-dev.11"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.9", "v1.0.50-dev.10"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.8", "v1.0.50-dev.9"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.7", "v1.0.50-dev.8"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.6", "v1.0.50-dev.7"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.5", "v1.0.50-dev.6"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.4", "v1.0.50-dev.5"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.2", "v1.0.50-dev.2"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.10", "v1.0.50-dev.10"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.11", "v1.0.50-dev.11"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.12", "v1.0.50-dev.12"))

        // Dev to higher base version
        assertTrue(UpdateManager.isVersionNewer("v1.0.51-dev.1", "v1.0.50-dev.10"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.49-dev.5", "v1.0.50-dev.10"))

        // Production release vs dev pre-release with same base version
        assertTrue(UpdateManager.isVersionNewer("v1.0.50", "v1.0.50-dev.10"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.10", "v1.0.50"))

        // Production v1.1.62 release vs previous prod and dev cycles
        assertTrue(UpdateManager.isVersionNewer("v1.1.62", "v1.0.50"))
        assertTrue(UpdateManager.isVersionNewer("v1.1.62", "v1.0.50-dev.12"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50", "v1.1.62"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.12", "v1.1.62"))

        // New dev cycle baseline: v1.0.62-dev.1
        assertTrue(UpdateManager.isVersionNewer("v1.0.62-dev.1", "v1.0.50-dev.12"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.62-dev.1", "v1.0.50"))
        assertTrue(UpdateManager.isVersionNewer("v1.0.62-dev.2", "v1.0.62-dev.1"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.62-dev.1", "v1.0.62-dev.2"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.50-dev.12", "v1.0.62-dev.1"))
    }

    @Test
    fun testReleaseApkEligibility_filtersOutDebugAndNonApkAssets() {
        // Eligible release APKs
        assertTrue(UpdateManager.isEligibleReleaseApk("GTA_v1.0.34.apk"))
        assertTrue(UpdateManager.isEligibleReleaseApk("app-release.apk"))
        assertTrue(UpdateManager.isEligibleReleaseApk("GTA_v1.0.34.APK"))
        assertTrue(UpdateManager.isEligibleReleaseApk("my-app-v2.apk"))

        // Debug APKs must NEVER be accepted (case-insensitive)
        assertFalse(UpdateManager.isEligibleReleaseApk("GTA_v1.0.34-debug.apk"))
        assertFalse(UpdateManager.isEligibleReleaseApk("app-debug.apk"))
        assertFalse(UpdateManager.isEligibleReleaseApk("debug.apk"))
        assertFalse(UpdateManager.isEligibleReleaseApk("DEBUG.apk"))
        assertFalse(UpdateManager.isEligibleReleaseApk("GTA_Debug_Release.apk"))
        assertFalse(UpdateManager.isEligibleReleaseApk("GTA_v1.0.34-DEBUG.APK"))

        // Non-APK assets must be rejected
        assertFalse(UpdateManager.isEligibleReleaseApk("output-metadata.json"))
        assertFalse(UpdateManager.isEligibleReleaseApk("mapping.txt"))
        assertFalse(UpdateManager.isEligibleReleaseApk("release.zip"))
        assertFalse(UpdateManager.isEligibleReleaseApk(""))
    }

    @Test
    fun testIsValidZipArchive_detectsValidZipAndRejectsCorrupt() {
        val tempDir = java.nio.file.Files.createTempDirectory("gta_update_test").toFile()
        try {
            // 1. Valid ZIP/APK header (PK\x03\x04 + dummy content)
            val validApk = File(tempDir, "valid.apk")
            validApk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00))
            assertTrue(UpdateManager.isValidZipArchive(validApk))

            // 2. HTML Error response page (e.g. GitHub 404/500 error body)
            val htmlError = File(tempDir, "error.apk")
            htmlError.writeText("<!DOCTYPE html><html><head><title>404 Not Found</title></head></html>")
            assertFalse(UpdateManager.isValidZipArchive(htmlError))

            // 3. JSON Error payload
            val jsonError = File(tempDir, "error.json")
            jsonError.writeText("{\"message\":\"Not Found\",\"documentation_url\":\"https://docs.github.com\"}")
            assertFalse(UpdateManager.isValidZipArchive(jsonError))

            // 4. Empty file
            val emptyFile = File(tempDir, "empty.apk")
            emptyFile.createNewFile()
            assertFalse(UpdateManager.isValidZipArchive(emptyFile))

            // 5. Truncated stream (less than 4 bytes)
            val truncatedFile = File(tempDir, "truncated.apk")
            truncatedFile.writeBytes(byteArrayOf(0x50, 0x4B))
            assertFalse(UpdateManager.isValidZipArchive(truncatedFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
