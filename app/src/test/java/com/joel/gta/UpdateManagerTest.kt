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
}
