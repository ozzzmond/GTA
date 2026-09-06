package com.joel.gta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

/**
 * High-readability monospaced text style used specifically for
 * song chord sheets and guitar tablatures.
 */
val ChordMonospaceStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.8.sp
)

val LyricMonospaceStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.8.sp
)

/**
 * Supported typography styles for song viewer chord sheets and lyrics.
 */
enum class SongFontStyle(
    val displayName: String,
    val subtitle: String,
    val fontFamily: FontFamily,
    val chordFontWeight: FontWeight = FontWeight.Bold,
    val lyricFontWeight: FontWeight = FontWeight.Normal
) {
    MONOSPACE(
        displayName = "Monospace",
        subtitle = "Recommended for stage: chords align directly above lyrics",
        fontFamily = FontFamily.Monospace,
        chordFontWeight = FontWeight.Bold,
        lyricFontWeight = FontWeight.Normal
    ),
    SANS_SERIF(
        displayName = "Sans-Serif",
        subtitle = "Clean / Modern system look",
        fontFamily = FontFamily.SansSerif,
        chordFontWeight = FontWeight.Bold,
        lyricFontWeight = FontWeight.Normal
    ),
    SERIF(
        displayName = "Serif / Bold",
        subtitle = "High contrast editorial serif style for stage use",
        fontFamily = FontFamily.Serif,
        chordFontWeight = FontWeight.ExtraBold,
        lyricFontWeight = FontWeight.Medium
    )
}
