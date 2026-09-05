package com.joel.gta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    AMOLED_DARK,    // #000000 canvas with Electric Amber chords
    PAPER_LIGHT,    // #FBF8F2 cream paper with warm amber/brown chords
    AMOLED_CYAN,    // #000000 canvas with Neon Cyan chords
    CUSTOM_STAGE    // Musician-tailored hex palette for custom stage lighting
}

data class CustomStageColors(
    val canvasBackgroundHex: String = "#131418",
    val chordAccentHex: String = "#E5B866",
    val textPrimaryHex: String = "#F1F5F9",
    val sectionHeaderHex: String = "#818CF8"
) {
    fun toGtaCustomColors(): GtaCustomColors {
        val bg = parseColorOrNull(canvasBackgroundHex) ?: SlateCanvas
        val chord = parseColorOrNull(chordAccentHex) ?: SoftAmberGold
        val text = parseColorOrNull(textPrimaryHex) ?: SlateTextPrimary
        val header = parseColorOrNull(sectionHeaderHex) ?: SectionHeaderColor

        val isDark = isColorDark(bg)
        val surface = if (isDark) {
            if (bg == SlateCanvas) SlateSurface else darkenOrLighten(bg, 0.07f)
        } else {
            darkenOrLighten(bg, -0.05f)
        }
        val textSecondary = if (isDark) {
            SlateTextSecondary
        } else {
            Color(0xFF64748B)
        }
        val divider = if (isDark) {
            SlateDivider
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
            tabLineColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            divider = divider
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
    val divider: Color
)

val LocalGtaColors = compositionLocalOf {
    GtaCustomColors(
        canvasBackground = SlateCanvas,
        surfaceBackground = SlateSurface,
        textPrimary = SlateTextPrimary,
        textSecondary = SlateTextSecondary,
        chordAccent = SoftAmberGold,
        sectionHeader = SectionHeaderColor,
        tabLineColor = TabLineColor,
        divider = SlateDivider
    )
}

private val AmoledDarkColorScheme = darkColorScheme(
    primary = SoftAmberGold,
    onPrimary = Color.Black,
    secondary = AgElectricBlue,
    background = SlateCanvas,
    surface = SlateSurface,
    onBackground = SlateTextPrimary,
    onSurface = SlateTextPrimary
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
                canvasBackground = SlateCanvas,
                surfaceBackground = SlateSurface,
                textPrimary = SlateTextPrimary,
                textSecondary = SlateTextSecondary,
                chordAccent = SoftAmberGold,
                sectionHeader = SectionHeaderColor,
                tabLineColor = TabLineColor,
                divider = SlateDivider
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
                divider = PaperDivider
            )
        }
        AppThemeMode.AMOLED_CYAN -> {
            AmoledDarkColorScheme.copy(primary = AgElectricBlue) to GtaCustomColors(
                canvasBackground = SlateCanvas,
                surfaceBackground = SlateSurface,
                textPrimary = SlateTextPrimary,
                textSecondary = SlateTextSecondary,
                chordAccent = AgElectricBlue,
                sectionHeader = SectionHeaderColor,
                tabLineColor = TabLineColor,
                divider = SlateDivider
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

    CompositionLocalProvider(LocalGtaColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
