package com.joel.gta.ui.theme

import androidx.compose.ui.graphics.Color

// =======================================================================
// GTA Solarized Dark Default Palette (v1.0.35)
// =======================================================================
val SolarizedBg = Color(0xFF002B36)             // --bg (#002B36)
val SolarizedSurface = Color(0xFF073642)        // --surface (#073642)
val SolarizedSurfaceVariant = Color(0xFF094352) // subtle surface variant (#094352)
val SolarizedText = Color(0xFFEEE8D5)           // --text (#EEE8D5)
val SolarizedTextMuted = Color(0xFF93A1A1)      // --text-muted (#93A1A1)
val SolarizedBorder = Color(0xFF1A4A55)         // --border / divider (#1A4A55)
val SolarizedPrimary = Color(0xFF2AA198)        // --primary (#2AA198)
val SolarizedPrimaryHover = Color(0xFF35B8AD)   // --primary-hover (#35B8AD)
val SolarizedAccent = Color(0xFF8B5CF6)         // --accent (#8B5CF6)
val SolarizedSuccess = Color(0xFF859900)        // --success (#859900)
val SolarizedWarning = Color(0xFFB58900)        // --warning (#B58900)
val SolarizedDanger = Color(0xFFDC6E67)         // --danger (#DC6E67)

// Backward-compatible aliases mapped to the default theme
val SlateCanvas = SolarizedBg
val SlateSurface = SolarizedSurface
val SlateSurfaceVariant = SolarizedSurfaceVariant
val SlateBorder = SolarizedBorder
val SlateDivider = SolarizedBorder
val SlateTextPrimary = SolarizedText
val SlateTextSecondary = SolarizedTextMuted

// Stage Accents
val SoftAmberGold = SolarizedWarning           // Amber / Gold chord highlight (#B58900)
val AgElectricBlue = SolarizedPrimary          // Cyan / Primary chord highlight (#2AA198)
val SectionHeaderColor = SolarizedAccent       // Accent violet (#8B5CF6)
val TabLineColor = SolarizedPrimaryHover       // Primary hover cyan (#35B8AD)
val DirectiveBadgeColor = SolarizedSuccess     // Success green (#859900)

// Paper Light Mode Palette (Muted warm cream, non-glare for reading chords in daylight)
val PaperCanvas = Color(0xFFFBF8F2)
val PaperSurface = Color(0xFFF3ECE1)
val PaperSurfaceVariant = Color(0xFFE8DFCE)
val PaperTextPrimary = Color(0xFF1E293B)
val PaperTextSecondary = Color(0xFF64748B)
val PaperDivider = Color(0xFFDFD7C7)
