package com.joel.gta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    AMOLED_DARK,    // Solarized Dark (#002B36) default stage theme
    PAPER_LIGHT,    // #FBF8F2 cream paper with warm amber/brown chords
    AMOLED_CYAN,    // Solarized Dark with Primary Cyan chords
    CUSTOM_STAGE    // Musician-tailored hex palette for custom stage lighting
}

data class CustomStageColors(
    val canvasBackgroundHex: String = "#002B36",
    val chordAccentHex: String = "#B58900",
    val textPrimaryHex: String = "#EEE8D5",
    val sectionHeaderHex: String = "#8B5CF6"
) {
    fun toGtaCustomColors(): GtaCustomColors {
        val bg = parseColorOrNull(canvasBackgroundHex) ?: SolarizedBg
        val chord = parseColorOrNull(chordAccentHex) ?: SolarizedWarning
        val text = parseColorOrNull(textPrimaryHex) ?: SolarizedText
        val header = parseColorOrNull(sectionHeaderHex) ?: SolarizedAccent

        val isDark = isColorDark(bg)
        val surface = if (isDark) {
            if (bg == SolarizedBg) SolarizedSurface
            else if (bg == Color(0xFF131418)) Color(0xFF22242D)
            else darkenOrLighten(bg, 0.07f)
        } else {
            darkenOrLighten(bg, -0.05f)
        }
        val textSecondary = if (isDark) {
            if (bg == SolarizedBg) SolarizedTextMuted else Color(0xFF94A3B8)
        } else {
            Color(0xFF64748B)
        }
        val divider = if (isDark) {
            if (bg == SolarizedBg) SolarizedBorder else Color(0xFF1A4A55)
        } else {
            Color(0xFFDFD7C7)
        }

        return GtaCustomColors(
            canvasBackground = bg,
            surfaceBackground = surface,
            textPrimary = text,
            textSecondary = textSecondary,
            chordAccent = chord,
            sectionHeader = header,
            tabLineColor = if (isDark) SolarizedPrimaryHover else Color(0xFF0284C7),
            divider = divider,
            primary = SolarizedPrimary,
            primaryHover = SolarizedPrimaryHover,
            accent = SolarizedAccent,
            success = SolarizedSuccess,
            warning = SolarizedWarning,
            danger = SolarizedDanger
        )
    }

    companion object {
        fun parseColorOrNull(hex: String): Color? {
            return try {
                var clean = hex.trim().removePrefix("#")
                if (clean.length == 6) {
                    clean = "FF$clean"
                }
                if (clean.length == 8) {
                    val colorLong = clean.toLong(16)
                    Color(
                        alpha = ((colorLong shr 24) and 0xFF) / 255f,
                        red = ((colorLong shr 16) and 0xFF) / 255f,
                        green = ((colorLong shr 8) and 0xFF) / 255f,
                        blue = (colorLong and 0xFF) / 255f
                    )
                } else null
            } catch (_: Exception) {
                null
            }
        }

        fun isColorDark(color: Color): Boolean {
            val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
            return luminance < 0.5
        }

        private fun darkenOrLighten(color: Color, factor: Float): Color {
            return Color(
                red = (color.red + factor).coerceIn(0f, 1f),
                green = (color.green + factor).coerceIn(0f, 1f),
                blue = (color.blue + factor).coerceIn(0f, 1f),
                alpha = color.alpha
            )
        }
    }
}

data class GtaCustomColors(
    val canvasBackground: Color,
    val surfaceBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val chordAccent: Color,
    val sectionHeader: Color,
    val tabLineColor: Color,
    val divider: Color,
    val primary: Color = SolarizedPrimary,
    val primaryHover: Color = SolarizedPrimaryHover,
    val accent: Color = SolarizedAccent,
    val success: Color = SolarizedSuccess,
    val warning: Color = SolarizedWarning,
    val danger: Color = SolarizedDanger
)

val LocalGtaColors = compositionLocalOf {
    GtaCustomColors(
        canvasBackground = SolarizedBg,
        surfaceBackground = SolarizedSurface,
        textPrimary = SolarizedText,
        textSecondary = SolarizedTextMuted,
        chordAccent = SolarizedWarning,
        sectionHeader = SolarizedAccent,
        tabLineColor = SolarizedPrimaryHover,
        divider = SolarizedBorder,
        primary = SolarizedPrimary,
        primaryHover = SolarizedPrimaryHover,
        accent = SolarizedAccent,
        success = SolarizedSuccess,
        warning = SolarizedWarning,
        danger = SolarizedDanger
    )
}

private val AmoledDarkColorScheme = darkColorScheme(
    primary = SolarizedPrimary,
    onPrimary = SolarizedBg,
    primaryContainer = SolarizedSurface,
    onPrimaryContainer = SolarizedText,
    secondary = SolarizedAccent,
    onSecondary = Color.White,
    secondaryContainer = SolarizedBorder,
    onSecondaryContainer = SolarizedText,
    tertiary = SolarizedWarning,
    onTertiary = SolarizedBg,
    background = SolarizedBg,
    surface = SolarizedSurface,
    surfaceVariant = SolarizedSurface,
    onBackground = SolarizedText,
    onSurface = SolarizedText,
    onSurfaceVariant = SolarizedTextMuted,
    outline = SolarizedBorder,
    outlineVariant = SolarizedBorder,
    error = SolarizedDanger,
    onError = Color.White
)

private val PaperLightColorScheme = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    secondary = DirectiveBadgeColor,
    background = PaperCanvas,
    surface = PaperSurface,
    onBackground = PaperTextPrimary,
    onSurface = PaperTextPrimary
)

@Composable
fun GTATheme(
    themeMode: AppThemeMode = AppThemeMode.AMOLED_DARK,
    customStageColors: CustomStageColors = CustomStageColors(),
    content: @Composable () -> Unit
) {
    val (colorScheme, customColors) = when (themeMode) {
        AppThemeMode.AMOLED_DARK -> {
            AmoledDarkColorScheme to GtaCustomColors(
                canvasBackground = SolarizedBg,
                surfaceBackground = SolarizedSurface,
                textPrimary = SolarizedText,
                textSecondary = SolarizedTextMuted,
                chordAccent = SolarizedWarning,
                sectionHeader = SolarizedAccent,
                tabLineColor = SolarizedPrimaryHover,
                divider = SolarizedBorder,
                primary = SolarizedPrimary,
                primaryHover = SolarizedPrimaryHover,
                accent = SolarizedAccent,
                success = SolarizedSuccess,
                warning = SolarizedWarning,
                danger = SolarizedDanger
            )
        }
        AppThemeMode.PAPER_LIGHT -> {
            PaperLightColorScheme to GtaCustomColors(
                canvasBackground = PaperCanvas,
                surfaceBackground = PaperSurface,
                textPrimary = PaperTextPrimary,
                textSecondary = PaperTextSecondary,
                chordAccent = Color(0xFFD97706),
                sectionHeader = Color(0xFF6366F1),
                tabLineColor = Color(0xFF0284C7),
                divider = PaperDivider,
                primary = Color(0xFFD97706),
                primaryHover = Color(0xFFB45309),
                accent = Color(0xFF6366F1),
                success = DirectiveBadgeColor,
                warning = Color(0xFFD97706),
                danger = Color(0xFFDC2626)
            )
        }
        AppThemeMode.AMOLED_CYAN -> {
            AmoledDarkColorScheme.copy(primary = SolarizedPrimary) to GtaCustomColors(
                canvasBackground = SolarizedBg,
                surfaceBackground = SolarizedSurface,
                textPrimary = SolarizedText,
                textSecondary = SolarizedTextMuted,
                chordAccent = SolarizedPrimary,
                sectionHeader = SolarizedAccent,
                tabLineColor = SolarizedPrimaryHover,
                divider = SolarizedBorder,
                primary = SolarizedPrimary,
                primaryHover = SolarizedPrimaryHover,
                accent = SolarizedAccent,
                success = SolarizedSuccess,
                warning = SolarizedWarning,
                danger = SolarizedDanger
            )
        }
        AppThemeMode.CUSTOM_STAGE -> {
            val custom = customStageColors.toGtaCustomColors()
            val isDark = CustomStageColors.isColorDark(custom.canvasBackground)
            val scheme = if (isDark) {
                AmoledDarkColorScheme.copy(
                    primary = custom.chordAccent,
                    background = custom.canvasBackground,
                    surface = custom.surfaceBackground,
                    onBackground = custom.textPrimary,
                    onSurface = custom.textPrimary
                )
            } else {
                PaperLightColorScheme.copy(
                    primary = custom.chordAccent,
                    background = custom.canvasBackground,
                    surface = custom.surfaceBackground,
                    onBackground = custom.textPrimary,
                    onSurface = custom.textPrimary
                )
            }
            scheme to custom
        }
    }

    val textSelectionColors = remember(customColors.chordAccent) {
        TextSelectionColors(
            handleColor = customColors.chordAccent,
            backgroundColor = customColors.chordAccent.copy(alpha = 0.35f)
        )
    }

    CompositionLocalProvider(
        LocalGtaColors provides customColors,
        LocalTextSelectionColors provides textSelectionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
