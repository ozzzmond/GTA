package com.joel.gta

import com.joel.gta.data.local.entity.SearchHistoryEntity
import com.joel.gta.ui.screens.HomeTab
import com.joel.gta.ui.screens.formatRelativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun testSearchHistoryEntityDefaults() {
        val entry = SearchHistoryEntity(
            query = "Hotel California"
        )
        assertEquals("Hotel California", entry.query)
        assertFalse(entry.isImported)
        assertNull(entry.importedSongId)
        assertTrue(entry.timestamp > 0)
    }

    @Test
    fun testSearchHistoryEntityMarkImported() {
        val entry = SearchHistoryEntity(
            id = 10,
            query = "Creep Radiohead",
            isImported = false,
            importedSongId = null
        )

        val imported = entry.copy(
            isImported = true,
            importedSongId = 42L
        )

        assertTrue(imported.isImported)
        assertEquals(42L, imported.importedSongId)
        assertEquals("Creep Radiohead", imported.query)
    }

    @Test
    fun testHomeTabIncludesHistory() {
        val tabs = HomeTab.values()
        assertTrue(tabs.contains(HomeTab.HISTORY))
        assertEquals(HomeTab.HISTORY, HomeTab.valueOf("HISTORY"))
    }

    @Test
    fun testFormatRelativeTime() {
        val now = 1700000000000L

        // Under 1 minute
        assertEquals("Just now", formatRelativeTime(timestamp = now - 30_000L, now = now))

        // 1 minute
        assertEquals("1 min ago", formatRelativeTime(timestamp = now - 65_000L, now = now))

        // 5 minutes
        assertEquals("5 mins ago", formatRelativeTime(timestamp = now - 5 * 60 * 1000L, now = now))

        // 1 hour
        assertEquals("1 hour ago", formatRelativeTime(timestamp = now - 65 * 60 * 1000L, now = now))

        // 3 hours
        assertEquals("3 hours ago", formatRelativeTime(timestamp = now - 3 * 3600 * 1000L, now = now))

        // Yesterday (between 24h and 48h)
        assertEquals("Yesterday", formatRelativeTime(timestamp = now - 28 * 3600 * 1000L, now = now))

        // 3 days ago
        assertEquals("3 days ago", formatRelativeTime(timestamp = now - 3 * 24 * 3600 * 1000L, now = now))
    }
}
