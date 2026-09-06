package com.joel.gta

import com.joel.gta.data.update.UpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

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
}
