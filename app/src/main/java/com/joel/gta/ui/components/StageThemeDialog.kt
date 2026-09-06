package com.joel.gta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joel.gta.ui.theme.AppThemeMode
import com.joel.gta.ui.theme.CustomStageColors
import com.joel.gta.ui.theme.LocalGtaColors

private data class ColorPreset(val name: String, val hex: String)

private val BackgroundPresets = listOf(
    ColorPreset("Solarized Dark", "#002B36"),
    ColorPreset("AG Dark Slate", "#131418"),
    ColorPreset("AG Deep Charcoal", "#1A1B22"),
    ColorPreset("Muted Slate", "#22242D"),
    ColorPreset("Dark Teal", "#0E2226"),
    ColorPreset("Pure AMOLED", "#000000"),
    ColorPreset("Sepia", "#FBF0D9"),
    ColorPreset("Dark Navy", "#0B132B")
)

private val ChordPresets = listOf(
    ColorPreset("Solarized Yellow", "#B58900"),
    ColorPreset("Solarized Cyan", "#2AA198"),
    ColorPreset("Soft Amber Gold", "#E5B866"),
    ColorPreset("AG Electric Blue", "#6C8EEF"),
    ColorPreset("Stage Amber", "#FFC107"),
    ColorPreset("Sky Blue", "#38BDF8"),
    ColorPreset("Hot Coral", "#F43F5E")
)

private val LyricsPresets = listOf(
    ColorPreset("Solarized Text", "#EEE8D5"),
    ColorPreset("Pure White", "#FFFFFF"),
    ColorPreset("Soft Ivory", "#F1F5F9"),
    ColorPreset("Light Gray", "#D1D5DB"),
    ColorPreset("Slate Ink", "#1E293B")
)

private val SectionHeaderPresets = listOf(
    ColorPreset("Solarized Violet", "#8B5CF6"),
    ColorPreset("Soft Indigo", "#818CF8"),
    ColorPreset("Cyan Flame", "#06B6D4"),
    ColorPreset("Emerald", "#10B981"),
    ColorPreset("Amber Flare", "#F59E0B"),
    ColorPreset("Ruby Red", "#F43F5E")
)

private data class ThemePreset(
    val name: String,
    val mode: AppThemeMode,
    val bgHex: String,
    val chordHex: String,
    val textHex: String,
    val headerHex: String,
    val colorDot: Color
)

private val AppThemePresets = listOf(
    ThemePreset("Solarized Dark", AppThemeMode.AMOLED_DARK, "#002B36", "#B58900", "#EEE8D5", "#8B5CF6", Color(0xFFB58900)),
    ThemePreset("Paper Light", AppThemeMode.PAPER_LIGHT, "#FBF8F2", "#D97706", "#1E293B", "#6366F1", Color(0xFFD97706)),
    ThemePreset("Solarized Cyan", AppThemeMode.AMOLED_CYAN, "#002B36", "#2AA198", "#EEE8D5", "#8B5CF6", Color(0xFF2AA198)),
    ThemePreset("AG Dark Slate", AppThemeMode.CUSTOM_STAGE, "#131418", "#E5B866", "#F1F5F9", "#6C8EEF", Color(0xFFE5B866)),
    ThemePreset("Pure AMOLED", AppThemeMode.CUSTOM_STAGE, "#000000", "#FFC107", "#FFFFFF", "#818CF8", Color(0xFFFFC107)),
    ThemePreset("Dark Teal", AppThemeMode.CUSTOM_STAGE, "#0E2226", "#2AA198", "#F1F5F9", "#818CF8", Color(0xFF0E2226)),
    ThemePreset("Sepia Warm", AppThemeMode.CUSTOM_STAGE, "#FBF0D9", "#D97706", "#1E293B", "#4F46E5", Color(0xFFD97706))
)

@Composable
fun StageThemeDialog(
    currentThemeMode: AppThemeMode,
    currentCustomColors: CustomStageColors,
    onDismissRequest: () -> Unit,
    onSaveTheme: (AppThemeMode, CustomStageColors) -> Unit,
    onResetDefaults: () -> Unit
) {
    val localColors = LocalGtaColors.current
    val focusManager = LocalFocusManager.current

    var selectedMode by remember(currentThemeMode) { mutableStateOf(currentThemeMode) }

    val initialColors = remember(currentThemeMode, currentCustomColors) {
        if (currentThemeMode == AppThemeMode.CUSTOM_STAGE) {
            currentCustomColors
        } else {
            CustomStageColors.defaultForMode(currentThemeMode)
        }
    }

    var bgHex by remember(initialColors) { mutableStateOf(initialColors.canvasBackgroundHex) }
    var chordHex by remember(initialColors) { mutableStateOf(initialColors.chordAccentHex) }
    var textHex by remember(initialColors) { mutableStateOf(initialColors.textPrimaryHex) }
    var headerHex by remember(initialColors) { mutableStateOf(initialColors.sectionHeaderHex) }

    val previewColors = remember(bgHex, chordHex, textHex, headerHex) {
        CustomStageColors(
            canvasBackgroundHex = bgHex,
            chordAccentHex = chordHex,
            textPrimaryHex = textHex,
            sectionHeaderHex = headerHex
        ).toGtaCustomColors()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = localColors.surfaceBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, localColors.divider),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(localColors.chordAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Stage Theme & Color Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = localColors.textPrimary
                            )
                            Text(
                                text = "Stage lighting & contrast customizer",
                                style = MaterialTheme.typography.bodySmall,
                                color = localColors.textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = localColors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Quick Preset Theme Switcher
                    Text(
                        text = "THEME PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = localColors.chordAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemePresets.forEach { preset ->
                            val isSelected = selectedMode == preset.mode &&
                                    bgHex.equals(preset.bgHex, ignoreCase = true) &&
                                    chordHex.equals(preset.chordHex, ignoreCase = true) &&
                                    textHex.equals(preset.textHex, ignoreCase = true) &&
                                    headerHex.equals(preset.headerHex, ignoreCase = true)

                            ThemePresetChip(
                                label = preset.name,
                                isSelected = isSelected,
                                colorDot = preset.colorDot,
                                onClick = {
                                    selectedMode = preset.mode
                                    bgHex = preset.bgHex
                                    chordHex = preset.chordHex
                                    textHex = preset.textHex
                                    headerHex = preset.headerHex
                                }
                            )
                        }

                        val isCustomPresetSelected = selectedMode == AppThemeMode.CUSTOM_STAGE &&
                                AppThemePresets.none { preset ->
                                    bgHex.equals(preset.bgHex, ignoreCase = true) &&
                                            chordHex.equals(preset.chordHex, ignoreCase = true) &&
                                            textHex.equals(preset.textHex, ignoreCase = true) &&
                                            headerHex.equals(preset.headerHex, ignoreCase = true)
                                }

                        ThemePresetChip(
                            label = "Custom Stage ✨",
                            isSelected = isCustomPresetSelected,
                            colorDot = previewColors.chordAccent,
                            onClick = {
                                selectedMode = AppThemeMode.CUSTOM_STAGE
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Live Stage Preview Card
                    Text(
                        text = "LIVE STAGE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = localColors.chordAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = previewColors.canvasBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, previewColors.divider)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "[Chorus]",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = previewColors.sectionHeader
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "G                 D/F#             Em7            Cadd9",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = previewColors.chordAccent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "When the night has come and the land is dark",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = previewColors.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Color Section: Background
                    HexColorInputRow(
                        title = "Background Color",
                        description = "Stage floor canvas background (#HEX)",
                        currentHex = bgHex,
                        presets = BackgroundPresets,
                        onHexChange = {
                            bgHex = it
                            selectedMode = AppThemeMode.CUSTOM_STAGE
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Color Section: Chords
                    HexColorInputRow(
                        title = "Chords Color",
                        description = "Primary chord accents & badges (#HEX)",
                        currentHex = chordHex,
                        presets = ChordPresets,
                        onHexChange = {
                            chordHex = it
                            selectedMode = AppThemeMode.CUSTOM_STAGE
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Color Section: Lyrics Text
                    HexColorInputRow(
                        title = "Lyrics Text Color",
                        description = "Main song lyrics text color (#HEX)",
                        currentHex = textHex,
                        presets = LyricsPresets,
                        onHexChange = {
                            textHex = it
                            selectedMode = AppThemeMode.CUSTOM_STAGE
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Color Section: Section Headers
                    HexColorInputRow(
                        title = "Section Headers Color",
                        description = "Labels for [Verse], [Chorus], [Bridge] (#HEX)",
                        currentHex = headerHex,
                        presets = SectionHeaderPresets,
                        onHexChange = {
                            headerHex = it
                            selectedMode = AppThemeMode.CUSTOM_STAGE
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            bgHex = "#002B36"
                            chordHex = "#B58900"
                            textHex = "#EEE8D5"
                            headerHex = "#8B5CF6"
                            selectedMode = AppThemeMode.AMOLED_DARK
                            onResetDefaults()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = localColors.textSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, localColors.divider),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Reset Defaults", fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = "Cancel", color = localColors.textSecondary)
                        }

                        Button(
                            onClick = {
                                val newCustomColors = CustomStageColors(
                                    canvasBackgroundHex = bgHex,
                                    chordAccentHex = chordHex,
                                    textPrimaryHex = textHex,
                                    sectionHeaderHex = headerHex
                                )
                                onSaveTheme(selectedMode, newCustomColors)
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = localColors.chordAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save & Apply",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePresetChip(
    label: String,
    isSelected: Boolean,
    colorDot: Color,
    onClick: () -> Unit
) {
    val localColors = LocalGtaColors.current
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) localColors.chordAccent.copy(alpha = 0.2f) else localColors.canvasBackground,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) localColors.chordAccent else localColors.divider
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colorDot)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) localColors.chordAccent else localColors.textPrimary
            )
        }
    }
}

@Composable
private fun HexColorInputRow(
    title: String,
    description: String,
    currentHex: String,
    presets: List<ColorPreset>,
    onHexChange: (String) -> Unit
) {
    val localColors = LocalGtaColors.current
    val parsedColor = remember(currentHex) {
        CustomStageColors.parseColorOrNull(currentHex) ?: Color.DarkGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(localColors.canvasBackground.copy(alpha = 0.6f))
            .border(1.dp, localColors.divider, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = localColors.textPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = localColors.textSecondary
                )
            }

            // Swatch + Hex Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Color Swatch Circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parsedColor)
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )

                // Direct #HEX Input
                OutlinedTextField(
                    value = currentHex,
                    onValueChange = { input ->
                        val cleaned = input.trim().uppercase()
                        onHexChange(cleaned)
                    },
                    modifier = Modifier.width(115.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = localColors.textPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = localColors.chordAccent,
                        unfocusedBorderColor = localColors.divider,
                        focusedContainerColor = localColors.surfaceBackground,
                        unfocusedContainerColor = localColors.surfaceBackground
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val presetColor = CustomStageColors.parseColorOrNull(preset.hex) ?: Color.Gray
                val isSelected = currentHex.equals(preset.hex, ignoreCase = true)

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onHexChange(preset.hex) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) localColors.chordAccent.copy(alpha = 0.25f) else localColors.surfaceBackground,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) localColors.chordAccent else localColors.divider
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = preset.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) localColors.chordAccent else localColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StageThemeDialog(
    currentThemeMode: AppThemeMode,
    currentCustomColors: CustomStageColors,
    onDismissRequest: () -> Unit,
    onSelectPresetMode: (AppThemeMode) -> Unit,
    onApplyCustomColors: (CustomStageColors) -> Unit,
    onResetDefaults: () -> Unit
) = StageThemeDialog(
    currentThemeMode = currentThemeMode,
    currentCustomColors = currentCustomColors,
    onDismissRequest = onDismissRequest,
    onSaveTheme = { mode, colors ->
        if (mode == AppThemeMode.CUSTOM_STAGE) {
            onApplyCustomColors(colors)
        } else {
            onSelectPresetMode(mode)
        }
    },
    onResetDefaults = onResetDefaults
)

