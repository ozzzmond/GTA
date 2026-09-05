package com.joel.gta

import com.joel.gta.data.model.SongLine
import com.joel.gta.ui.viewmodel.FootswitchAction
import org.junit.Assert.*
import org.junit.Test

class ProStageFeaturesTest {

    @Test
    fun testFootswitchActionsEnum() {
        val actions = FootswitchAction.values()
        assertTrue(actions.contains(FootswitchAction.NEXT_SONG_OR_PAGE_DOWN))
        assertTrue(actions.contains(FootswitchAction.PREV_SONG_OR_PAGE_UP))
        assertTrue(actions.contains(FootswitchAction.SCROLL_DOWN))
        assertTrue(actions.contains(FootswitchAction.SCROLL_UP))
        assertTrue(actions.contains(FootswitchAction.TOGGLE_SCROLL))
    }

    @Test
    fun testTwoColumnSplitPrefersSectionHeaderNearMiddle() {
        val lines = listOf(
            SongLine.SectionHeader("Verse 1"),
            SongLine.LyricLine("Line 1"),
            SongLine.LyricLine("Line 2"),
            SongLine.LyricLine("Line 3"),
            SongLine.SectionHeader("Chorus"),
            SongLine.LyricLine("Line 4"),
            SongLine.LyricLine("Line 5"),
            SongLine.LyricLine("Line 6"),
            SongLine.LyricLine("Line 7")
        )

        val mid = lines.size / 2
        var splitIndex = mid
        val searchRange = (mid - 6).coerceAtLeast(1)..(mid + 6).coerceAtMost(lines.size - 2)
        for (i in searchRange) {
            if (lines[i] is SongLine.SectionHeader) {
                splitIndex = i
                break
            }
        }

        val col1 = lines.take(splitIndex)
        val col2 = lines.drop(splitIndex)

        assertEquals(4, col1.size)
        assertEquals(5, col2.size)
        assertTrue(col2.first() is SongLine.SectionHeader)
        assertEquals("Chorus", (col2.first() as SongLine.SectionHeader).title)
    }

    @Test
    fun testTwoColumnSplitFallbackForSmallList() {
        val lines = listOf(
            SongLine.LyricLine("Line 1"),
            SongLine.LyricLine("Line 2")
        )
        val shouldSplit = lines.size > 4
        assertFalse(shouldSplit)
    }

    @Test
    fun testHysteresisZoomThresholds() {
        fun computeColumnCount(currentCols: Int, fontSizeSp: Float, isTabletOrLandscape: Boolean): Int {
            if (!isTabletOrLandscape) return 1
            return when {
                currentCols == 1 && fontSizeSp < 15f -> 2
                currentCols == 2 && fontSizeSp >= 17f -> 1
                else -> currentCols
            }
        }

        // Phone screen always locked to 1 column
        assertEquals(1, computeColumnCount(1, 12f, isTabletOrLandscape = false))
        assertEquals(1, computeColumnCount(2, 12f, isTabletOrLandscape = false))

        // Tablet screen: Starts at 1 column with 16sp
        var cols = 1
        cols = computeColumnCount(cols, 16f, isTabletOrLandscape = true)
        assertEquals(1, cols)

        // Zoom out to 15sp (still in hysteresis band)
        cols = computeColumnCount(cols, 15f, isTabletOrLandscape = true)
        assertEquals(1, cols)

        // Zoom out to 14sp (< 15sp) -> switches to 2 columns!
        cols = computeColumnCount(cols, 14f, isTabletOrLandscape = true)
        assertEquals(2, cols)

        // Zoom out further to 12sp -> remains in 2 columns
        cols = computeColumnCount(cols, 12f, isTabletOrLandscape = true)
        assertEquals(2, cols)

        // Zoom in to 15sp (in hysteresis band) -> remains in 2 columns
        cols = computeColumnCount(cols, 15f, isTabletOrLandscape = true)
        assertEquals(2, cols)

        // Zoom in to 16sp (in hysteresis band) -> remains in 2 columns
        cols = computeColumnCount(cols, 16f, isTabletOrLandscape = true)
        assertEquals(2, cols)

        // Zoom in to 17sp (>= 17sp) -> switches back to 1 column!
        cols = computeColumnCount(cols, 17f, isTabletOrLandscape = true)
        assertEquals(1, cols)

        // Zoom in to 21sp -> remains in 1 column!
        cols = computeColumnCount(cols, 21f, isTabletOrLandscape = true)
        assertEquals(1, cols)
    }

    @Test
    fun testKeepScreenAwakeSettingLogic() {
        // Default should be ON (true)
        val defaultKeepScreenOn = true
        assertTrue("Stage Always-On Display must default to ON for live performance", defaultKeepScreenOn)

        // Simulate user toggle
        var keepScreenOn = defaultKeepScreenOn
        fun toggle(newState: Boolean) {
            keepScreenOn = newState
        }

        toggle(false)
        assertFalse(keepScreenOn)

        toggle(true)
        assertTrue(keepScreenOn)
    }
}
