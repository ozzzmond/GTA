package com.joel.gta

import androidx.compose.ui.graphics.Color
import com.joel.gta.ui.theme.AppThemeMode
import com.joel.gta.ui.theme.CustomStageColors
import org.junit.Assert.*
import org.junit.Test

class CustomThemeTest {

    @Test
    fun testAppThemeModeEnumValues() {
        val modes = AppThemeMode.values()
        assertTrue(modes.contains(AppThemeMode.AMOLED_DARK))
        assertTrue(modes.contains(AppThemeMode.PAPER_LIGHT))
        assertTrue(modes.contains(AppThemeMode.AMOLED_CYAN))
        assertTrue(modes.contains(AppThemeMode.CUSTOM_STAGE))
    }

    @Test
    fun testParseColorOrNull_Valid6HexWithHash() {
        val color = CustomStageColors.parseColorOrNull("#FFC107")
        assertNotNull(color)
        assertEquals(1.0f, color!!.alpha, 0.01f)
        assertEquals(1.0f, color.red, 0.01f)
        assertEquals(0.757f, color.green, 0.01f)
        assertEquals(0.027f, color.blue, 0.01f)
    }

    @Test
    fun testParseColorOrNull_Valid6HexWithoutHash() {
        val color = CustomStageColors.parseColorOrNull("00E676")
        assertNotNull(color)
        assertEquals(1.0f, color!!.alpha, 0.01f)
        assertEquals(0.0f, color.red, 0.01f)
        assertEquals(0.902f, color.green, 0.01f)
        assertEquals(0.463f, color.blue, 0.01f)
    }

    @Test
    fun testParseColorOrNull_Valid8HexWithAlpha() {
        val color = CustomStageColors.parseColorOrNull("#80FFC107")
        assertNotNull(color)
        assertEquals(0.502f, color!!.alpha, 0.01f)
    }

    @Test
    fun testParseColorOrNull_InvalidHexReturnsNull() {
        assertNull(CustomStageColors.parseColorOrNull("xyz123"))
        assertNull(CustomStageColors.parseColorOrNull("#12345"))
        assertNull(CustomStageColors.parseColorOrNull(""))
        assertNull(CustomStageColors.parseColorOrNull("not-a-color"))
    }

    @Test
    fun testIsColorDark() {
        val black = CustomStageColors.parseColorOrNull("#000000")!!
        val darkSlate = CustomStageColors.parseColorOrNull("#121212")!!
        val darkNavy = CustomStageColors.parseColorOrNull("#0B132B")!!
        assertTrue(CustomStageColors.isColorDark(black))
        assertTrue(CustomStageColors.isColorDark(darkSlate))
        assertTrue(CustomStageColors.isColorDark(darkNavy))

        val white = CustomStageColors.parseColorOrNull("#FFFFFF")!!
        val sepia = CustomStageColors.parseColorOrNull("#FBF0D9")!!
        val cream = CustomStageColors.parseColorOrNull("#FFFBEB")!!
        assertFalse(CustomStageColors.isColorDark(white))
        assertFalse(CustomStageColors.isColorDark(sepia))
        assertFalse(CustomStageColors.isColorDark(cream))
    }

    @Test
    fun testToGtaCustomColors_DarkBackground() {
        val custom = CustomStageColors(
            canvasBackgroundHex = "#000000",
            chordAccentHex = "#00E676",
            textPrimaryHex = "#FFFFFF",
            sectionHeaderHex = "#818CF8"
        )
        val palette = custom.toGtaCustomColors()

        assertEquals(Color(0xFF000000), palette.canvasBackground)
        assertEquals(CustomStageColors.parseColorOrNull("#00E676"), palette.chordAccent)
        assertEquals(Color(0xFFFFFFFF), palette.textPrimary)
        assertEquals(CustomStageColors.parseColorOrNull("#818CF8"), palette.sectionHeader)
        // Dark background should get slate secondary text
        assertEquals(Color(0xFF94A3B8), palette.textSecondary)
    }

    @Test
    fun testToGtaCustomColors_LightSepiaBackground() {
        val custom = CustomStageColors(
            canvasBackgroundHex = "#FBF0D9",
            chordAccentHex = "#D97706",
            textPrimaryHex = "#1E293B",
            sectionHeaderHex = "#4F46E5"
        )
        val palette = custom.toGtaCustomColors()

        assertEquals(CustomStageColors.parseColorOrNull("#FBF0D9"), palette.canvasBackground)
        // Light background should get dark secondary text
        assertEquals(Color(0xFF64748B), palette.textSecondary)
        assertEquals(Color(0xFFDFD7C7), palette.divider)
    }
}
