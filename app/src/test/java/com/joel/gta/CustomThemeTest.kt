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

    @Test
    fun testAntigravityDarkTealAndSlatePresets() {
        val darkTeal = CustomStageColors.parseColorOrNull("#0E2226")
        assertNotNull(darkTeal)
        assertTrue("Antigravity Dark Teal should be recognized as dark", CustomStageColors.isColorDark(darkTeal!!))

        val slate = CustomStageColors.parseColorOrNull("#1E1F22")
        assertNotNull(slate)
        assertTrue("Slate should be recognized as dark", CustomStageColors.isColorDark(slate!!))

        val darkTealTheme = CustomStageColors(
            canvasBackgroundHex = "#0E2226",
            chordAccentHex = "#FFC107",
            textPrimaryHex = "#F1F5F9",
            sectionHeaderHex = "#818CF8"
        ).toGtaCustomColors()

        assertEquals(darkTeal, darkTealTheme.canvasBackground)
        assertEquals(Color(0xFF94A3B8), darkTealTheme.textSecondary)
    }

    @Test
    fun testAgDarkSlateAndSoftAmberGoldPresets() {
        val darkSlate = CustomStageColors.parseColorOrNull("#131418")
        assertNotNull(darkSlate)
        assertTrue("AG Dark Slate should be recognized as dark", CustomStageColors.isColorDark(darkSlate!!))

        val deepCharcoal = CustomStageColors.parseColorOrNull("#1A1B22")
        assertNotNull(deepCharcoal)
        assertTrue("AG Deep Charcoal should be recognized as dark", CustomStageColors.isColorDark(deepCharcoal!!))

        val agTheme = CustomStageColors(
            canvasBackgroundHex = "#131418",
            chordAccentHex = "#E5B866",
            textPrimaryHex = "#F1F5F9",
            sectionHeaderHex = "#6C8EEF"
        ).toGtaCustomColors()

        assertEquals(darkSlate, agTheme.canvasBackground)
        assertEquals(Color(0xFF22242D), agTheme.surfaceBackground)
        assertEquals(CustomStageColors.parseColorOrNull("#E5B866"), agTheme.chordAccent)
    }

    @Test
    fun testSongFontStyleEnumAndProperties() {
        val styles = com.joel.gta.ui.theme.SongFontStyle.entries
        assertEquals(3, styles.size)

        val mono = com.joel.gta.ui.theme.SongFontStyle.MONOSPACE
        assertEquals("Monospace", mono.displayName)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, mono.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, mono.chordFontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, mono.lyricFontWeight)

        val sans = com.joel.gta.ui.theme.SongFontStyle.SANS_SERIF
        assertEquals("Sans-Serif", sans.displayName)
        assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, sans.fontFamily)

        val serif = com.joel.gta.ui.theme.SongFontStyle.SERIF
        assertEquals("Serif / Bold", serif.displayName)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Serif, serif.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontWeight.ExtraBold, serif.chordFontWeight)

        // ValueOf roundtrip
        assertEquals(mono, com.joel.gta.ui.theme.SongFontStyle.valueOf("MONOSPACE"))
        assertEquals(sans, com.joel.gta.ui.theme.SongFontStyle.valueOf("SANS_SERIF"))
        assertEquals(serif, com.joel.gta.ui.theme.SongFontStyle.valueOf("SERIF"))
    }

    @Test
    fun testSolarizedDarkDefaultPreset() {
        val bg = CustomStageColors.parseColorOrNull("#002B36")
        assertNotNull(bg)
        assertTrue("Solarized Dark BG should be recognized as dark", CustomStageColors.isColorDark(bg!!))

        val solarized = CustomStageColors(
            canvasBackgroundHex = "#002B36",
            chordAccentHex = "#B58900",
            textPrimaryHex = "#EEE8D5",
            sectionHeaderHex = "#8B5CF6"
        ).toGtaCustomColors()

        assertEquals(bg, solarized.canvasBackground)
        assertEquals(CustomStageColors.parseColorOrNull("#B58900"), solarized.chordAccent)
        assertEquals(CustomStageColors.parseColorOrNull("#EEE8D5"), solarized.textPrimary)
        assertEquals(CustomStageColors.parseColorOrNull("#8B5CF6"), solarized.sectionHeader)
        assertEquals(Color(0xFF93A1A1), solarized.textSecondary)
        assertEquals(Color(0xFF1A4A55), solarized.divider)
    }

    @Test
    fun testDefaultForModePresets() {
        val dark = CustomStageColors.defaultForMode(AppThemeMode.AMOLED_DARK)
        assertEquals("#002B36", dark.canvasBackgroundHex)
        assertEquals("#B58900", dark.chordAccentHex)
        assertEquals("#EEE8D5", dark.textPrimaryHex)
        assertEquals("#8B5CF6", dark.sectionHeaderHex)

        val light = CustomStageColors.defaultForMode(AppThemeMode.PAPER_LIGHT)
        assertEquals("#FBF8F2", light.canvasBackgroundHex)
        assertEquals("#D97706", light.chordAccentHex)
        assertEquals("#1E293B", light.textPrimaryHex)
        assertEquals("#6366F1", light.sectionHeaderHex)

        val cyan = CustomStageColors.defaultForMode(AppThemeMode.AMOLED_CYAN)
        assertEquals("#002B36", cyan.canvasBackgroundHex)
        assertEquals("#2AA198", cyan.chordAccentHex)
        assertEquals("#EEE8D5", cyan.textPrimaryHex)
        assertEquals("#8B5CF6", cyan.sectionHeaderHex)
    }
}
