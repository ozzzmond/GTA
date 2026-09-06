package com.joel.gta.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.joel.gta.data.audio.MetronomeEngine
import com.joel.gta.data.audio.MetronomeSoundProfile
import com.joel.gta.data.audio.PitchDetector
import com.joel.gta.data.sync.BandSyncRole
import com.joel.gta.data.sync.BandSyncState
import com.joel.gta.ui.theme.LocalGtaColors
import kotlin.math.abs
import kotlin.math.ceil

enum class StageToolTab {
    METRONOME,
    GUITAR_TUNER,
    BAND_SYNC
}

@Composable
fun StageToolsDialog(
    onDismissRequest: () -> Unit,
    initialTab: StageToolTab = StageToolTab.METRONOME,
    bandSyncState: BandSyncState = BandSyncState(),
    onStartBandHost: () -> Unit = {},
    onStartBandClient: () -> Unit = {},
    onConnectBandHost: (String) -> Unit = {},
    onStopBandSync: () -> Unit = {}
) {
    val customColors = LocalGtaColors.current
    var selectedTab by remember { mutableStateOf(initialTab) }
    val scope = rememberCoroutineScope()


    Dialog(
        onDismissRequest = {
            PitchDetector.stopListening()
            onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            color = customColors.surfaceBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = customColors.chordAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STAGE TOOLS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = customColors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            PitchDetector.stopListening()
                            onDismissRequest()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = customColors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs (Metronome vs Tuner)
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = customColors.canvasBackground,
                    contentColor = customColors.chordAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, customColors.divider, RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == StageToolTab.METRONOME,
                        onClick = {
                            selectedTab = StageToolTab.METRONOME
                            PitchDetector.stopListening()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Metronome", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == StageToolTab.GUITAR_TUNER,
                        onClick = {
                            selectedTab = StageToolTab.GUITAR_TUNER
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guitar Tuner", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == StageToolTab.BAND_SYNC,
                        onClick = {
                            selectedTab = StageToolTab.BAND_SYNC
                            PitchDetector.stopListening()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Band Sync", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Panel
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        StageToolTab.METRONOME -> MetronomePanel()
                        StageToolTab.GUITAR_TUNER -> GuitarTunerPanel()
                        StageToolTab.BAND_SYNC -> BandSyncPanel(
                            bandSyncState = bandSyncState,
                            onStartHost = onStartBandHost,
                            onStartClient = onStartBandClient,
                            onConnectHost = onConnectBandHost,
                            onStopSync = onStopBandSync
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun MetronomePanel() {
    val customColors = LocalGtaColors.current
    val metronomeState by MetronomeEngine.state.collectAsState()
    var showBpmInputDialog by remember { mutableStateOf(false) }
    var showTimeSignatureDialog by remember { mutableStateOf(false) }

    // Pulse animation on each beat
    val pulseScale by animateFloatAsState(
        targetValue = if (metronomeState.isRunning) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "beatPulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Visual Beat Indicator Ring & Pulse (Clickable to edit exact BPM)
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    if (metronomeState.isRunning && metronomeState.currentBeat == 1)
                        customColors.chordAccent.copy(alpha = 0.25f)
                    else if (metronomeState.isRunning)
                        Color.Gray.copy(alpha = 0.15f)
                    else
                        customColors.canvasBackground
                )
                .border(
                    width = if (metronomeState.isRunning && metronomeState.currentBeat == 1) 3.dp else 1.5.dp,
                    color = if (metronomeState.isRunning && metronomeState.currentBeat == 1)
                        customColors.chordAccent
                    else if (metronomeState.isRunning)
                        customColors.textSecondary
                    else
                        customColors.divider,
                    shape = CircleShape
                )
                .clickable { showBpmInputDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${metronomeState.bpm}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = customColors.textPrimary
                )
                Text(
                    text = "BPM (${metronomeState.beatsPerBar}/${metronomeState.beatUnit})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = customColors.chordAccent
                )
                Text(
                    text = "Tap to edit",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = customColors.textSecondary
                )
            }
        }

        // Sound Profile Selector (Woodblock, Digital Beep, Rimshot)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetronomeSoundProfile.values().forEach { profile ->
                val isSelected = metronomeState.soundProfile == profile
                FilterChip(
                    selected = isSelected,
                    onClick = { MetronomeEngine.setSoundProfile(profile) },
                    label = { Text(profile.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = customColors.chordAccent,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Dynamic LED Beat Dots (supports 1 to 16 beats, wraps cleanly into rows of max 8 dots)
        val beatsPerBar = metronomeState.beatsPerBar
        val beatsPerRow = if (beatsPerBar <= 8) beatsPerBar else ceil(beatsPerBar / 2.0).toInt().coerceAtLeast(1)
        val beatChunks = (1..beatsPerBar).chunked(beatsPerRow)
        val dotBaseSize = when {
            beatsPerBar <= 6 -> 20.dp
            beatsPerBar <= 8 -> 18.dp
            beatsPerBar <= 12 -> 15.dp
            else -> 13.dp
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            beatChunks.forEach { chunk ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chunk.forEach { beat ->
                        val isCurrent = metronomeState.isRunning && metronomeState.currentBeat == beat
                        val isFirst = beat == 1
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                isCurrent && isFirst -> customColors.chordAccent
                                isCurrent -> Color.White
                                isFirst -> customColors.chordAccent.copy(alpha = 0.35f)
                                else -> customColors.divider
                            },
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) dotBaseSize + 4.dp else dotBaseSize)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isFirst) customColors.chordAccent else customColors.divider,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dotBaseSize >= 14.dp) {
                                Text(
                                    text = "$beat",
                                    fontSize = if (dotBaseSize >= 18.dp) 10.sp else 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isCurrent -> Color.Black
                                        isFirst -> customColors.chordAccent
                                        else -> customColors.textSecondary.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // BPM Slider + Fine Adjustment Controls (Range: 30 - 300 BPM)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = metronomeState.bpm.toFloat(),
                onValueChange = { MetronomeEngine.setBpm(it.toInt()) },
                valueRange = 30f..300f,
                colors = SliderDefaults.colors(
                    thumbColor = customColors.chordAccent,
                    activeTrackColor = customColors.chordAccent,
                    inactiveTrackColor = customColors.divider
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Steppers: [-5] [-1] [+1] [+5]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { MetronomeEngine.adjustBpm(-5) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("-5", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = { MetronomeEngine.adjustBpm(-1) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("-1", fontWeight = FontWeight.Bold) }

                // Tap Tempo button
                Button(
                    onClick = { MetronomeEngine.recordTap() },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.canvasBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("TAP TEMPO", color = customColors.chordAccent, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { MetronomeEngine.adjustBpm(1) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("+1", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = { MetronomeEngine.adjustBpm(5) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("+5", fontWeight = FontWeight.Bold) }
            }
        }

        // Time Signature Selector Row (Scrollable Presets + Custom Button) & Mute Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val standardPresets = listOf(
                2 to 4,
                3 to 4,
                4 to 4,
                5 to 4,
                6 to 8,
                7 to 8,
                9 to 8,
                12 to 8
            )
            val currentPair = metronomeState.beatsPerBar to metronomeState.beatUnit
            val isCustom = currentPair !in standardPresets

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                standardPresets.forEach { (num, den) ->
                    val isSelected = currentPair == (num to den)
                    FilterChip(
                        selected = isSelected,
                        onClick = { MetronomeEngine.setTimeSignature(num, den) },
                        label = {
                            Text(
                                text = "$num/$den",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.chordAccent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                // Custom Chip opens Time Signature Dialog
                FilterChip(
                    selected = isCustom,
                    onClick = { showTimeSignatureDialog = true },
                    label = {
                        Text(
                            text = if (isCustom) "${metronomeState.beatsPerBar}/${metronomeState.beatUnit} ⚙️" else "+ Custom",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = customColors.chordAccent,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = { MetronomeEngine.toggleMute() }) {
                Icon(
                    imageVector = if (metronomeState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mute",
                    tint = if (metronomeState.isMuted) Color(0xFFEF4444) else customColors.chordAccent
                )
            }
        }

        // Big Main Start / Stop Button
        Button(
            onClick = { MetronomeEngine.toggle() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (metronomeState.isRunning) Color(0xFFEF4444) else customColors.chordAccent
            )
        ) {
            Icon(
                imageVector = if (metronomeState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (metronomeState.isRunning) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (metronomeState.isRunning) "STOP METRONOME" else "START METRONOME",
                fontWeight = FontWeight.Black,
                color = if (metronomeState.isRunning) Color.White else Color.Black
            )
        }
    }

    // Direct Exact BPM Input Dialog
    if (showBpmInputDialog) {
        var tempBpmText by remember { mutableStateOf("${metronomeState.bpm}") }
        AlertDialog(
            onDismissRequest = { showBpmInputDialog = false },
            title = {
                Text(
                    text = "Enter Exact Tempo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set metronome tempo (30 - 300 BPM):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = customColors.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempBpmText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                tempBpmText = input
                            }
                        },
                        label = { Text("BPM") },
                        placeholder = { Text("e.g. 128") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = tempBpmText.toIntOrNull()
                        if (parsed != null) {
                            MetronomeEngine.setBpm(parsed)
                        }
                        showBpmInputDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBpmInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Custom Time Signature Dialog
    if (showTimeSignatureDialog) {
        var tempNumerator by remember { mutableIntStateOf(metronomeState.beatsPerBar) }
        var tempDenominator by remember { mutableIntStateOf(metronomeState.beatUnit) }

        AlertDialog(
            onDismissRequest = { showTimeSignatureDialog = false },
            title = {
                Text(
                    text = "Time Signature",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Big Preview Badge with LED Dots preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(customColors.canvasBackground)
                            .border(1.dp, customColors.divider, RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$tempNumerator / $tempDenominator",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = customColors.chordAccent
                            )
                            Text(
                                text = "$tempNumerator beats per bar (Accent on Beat 1)",
                                style = MaterialTheme.typography.labelSmall,
                                color = customColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Preview of dots
                            val previewRows = if (tempNumerator <= 8) listOf(1..tempNumerator) else (1..tempNumerator).chunked(ceil(tempNumerator / 2.0).toInt())
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                previewRows.forEach { rowBeats ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        rowBeats.forEach { b ->
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (b == 1) customColors.chordAccent else customColors.divider)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Numerator (Beats per Bar: 1 - 16)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "BEATS PER MEASURE: $tempNumerator",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = customColors.chordAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { if (tempNumerator > 1) tempNumerator-- },
                                enabled = tempNumerator > 1,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("-1", fontWeight = FontWeight.Bold) }

                            Slider(
                                value = tempNumerator.toFloat(),
                                onValueChange = { tempNumerator = it.toInt() },
                                valueRange = 1f..16f,
                                steps = 14,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = customColors.chordAccent,
                                    activeTrackColor = customColors.chordAccent,
                                    inactiveTrackColor = customColors.divider
                                )
                            )

                            OutlinedButton(
                                onClick = { if (tempNumerator < 16) tempNumerator++ },
                                enabled = tempNumerator < 16,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("+1", fontWeight = FontWeight.Bold) }
                        }
                    }

                    // Denominator (Beat Value: /2, /4, /8, /16)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "BEAT VALUE (DENOMINATOR)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = customColors.chordAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                2 to "/2",
                                4 to "/4",
                                8 to "/8",
                                16 to "/16"
                            ).forEach { (den, label) ->
                                val isSelected = tempDenominator == den
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { tempDenominator = den },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = customColors.chordAccent,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    // Popular Prog / Odd Meter Shortcuts
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "POPULAR PROG & ODD METERS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = customColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                5 to 4, 7 to 4,
                                5 to 8, 7 to 8, 9 to 8, 11 to 8, 13 to 8, 15 to 16
                            ).forEach { (num, den) ->
                                AssistChip(
                                    onClick = {
                                        tempNumerator = num
                                        tempDenominator = den
                                    },
                                    label = { Text("$num/$den", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        MetronomeEngine.setTimeSignature(tempNumerator, tempDenominator)
                        showTimeSignatureDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeSignatureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GuitarTunerPanel() {
    val customColors = LocalGtaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tunerState by PitchDetector.tunerState.collectAsState()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            PitchDetector.startListening(scope)
        }
    }

    DisposableEffect(hasMicPermission) {
        if (hasMicPermission) {
            PitchDetector.startListening(scope)
        }
        onDispose {
            PitchDetector.stopListening()
        }
    }

    if (!hasMicPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = customColors.chordAccent,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Microphone Access Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = customColors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To detect guitar pitch in real-time, GTAR needs microphone permission. Audio is processed 100% offline on your device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = customColors.textSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
            ) {
                Text("Allow Microphone", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    // Active Tuner Display
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Badge (In Tune / Flat / Sharp / Listening)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                tunerState.isInTune -> Color(0xFF10B981).copy(alpha = 0.2f)
                tunerState.frequencyHz > 0f -> Color(0xFFEF4444).copy(alpha = 0.15f)
                else -> customColors.canvasBackground
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                    tunerState.isInTune -> Color(0xFF10B981)
                    tunerState.frequencyHz > 0f -> Color(0xFFEF4444)
                    else -> customColors.divider
                }
            )
        ) {
            Text(
                text = when {
                    tunerState.isInTune -> "IN TUNE 🎯"
                    tunerState.centsOffset < -3f -> "FLAT ♭ (${tunerState.centsOffset.toInt()} cents)"
                    tunerState.centsOffset > 3f -> "SHARP ♯ (+${tunerState.centsOffset.toInt()} cents)"
                    else -> "Pluck a string..."
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    tunerState.isInTune -> Color(0xFF10B981)
                    tunerState.frequencyHz > 0f -> Color(0xFFEF4444)
                    else -> customColors.textSecondary
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Big Note Letter & Octave
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = tunerState.noteName,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (tunerState.isInTune) Color(0xFF10B981) else customColors.chordAccent
                )
                if (tunerState.octave > 0) {
                    Text(
                        text = "${tunerState.octave}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                }
            }

            if (tunerState.frequencyHz > 0f) {
                Text(
                    text = String.format("%.1f Hz (Target: %.1f Hz)", tunerState.frequencyHz, tunerState.targetFrequencyHz),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = customColors.textSecondary
                )
            }
        }

        // Cents Gauge Meter (-50 .. 0 .. +50)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ruler Marks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-50¢", style = MaterialTheme.typography.labelSmall, color = customColors.textSecondary)
                Text("♭ FLAT", style = MaterialTheme.typography.labelSmall, color = customColors.textSecondary)
                Text("0¢", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                Text("SHARP ♯", style = MaterialTheme.typography.labelSmall, color = customColors.textSecondary)
                Text("+50¢", style = MaterialTheme.typography.labelSmall, color = customColors.textSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gauge Bar with centered needle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(customColors.canvasBackground)
                    .border(1.dp, customColors.divider, RoundedCornerShape(8.dp))
            ) {
                // Center In-Tune Target Line
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF10B981))
                )

                // Animated Needle Indicator
                val needleFraction = ((tunerState.centsOffset + 50f) / 100f).coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = if (tunerState.frequencyHz > 0f) needleFraction else 0.5f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "tunerNeedle"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = 6.dp, height = 26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (tunerState.isInTune) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                    )
                }
            }
        }

        // Standard 6-String Guitar Reference Chips
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "STANDARD GUITAR STRINGS",
                style = MaterialTheme.typography.labelSmall,
                color = customColors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PitchDetector.GUITAR_STRINGS.forEach { (stringName, targetFreq) ->
                    val isNearest = tunerState.nearestGuitarString == stringName
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isNearest) customColors.chordAccent.copy(alpha = 0.25f) else customColors.canvasBackground,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isNearest) customColors.chordAccent else customColors.divider
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringName,
                                fontWeight = if (isNearest) FontWeight.Black else FontWeight.Bold,
                                color = if (isNearest) customColors.chordAccent else customColors.textPrimary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${targetFreq.toInt()}Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = customColors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSyncPanel(
    bandSyncState: BandSyncState,
    onStartHost: () -> Unit,
    onStartClient: () -> Unit,
    onConnectHost: (String) -> Unit,
    onStopSync: () -> Unit
) {
    val customColors = LocalGtaColors.current
    var manualIpInput by remember { mutableStateOf("192.168.43.1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onStopSync,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (bandSyncState.role == BandSyncRole.OFF) customColors.chordAccent.copy(alpha = 0.2f) else customColors.canvasBackground,
                    contentColor = if (bandSyncState.role == BandSyncRole.OFF) customColors.chordAccent else customColors.textSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (bandSyncState.role == BandSyncRole.OFF) customColors.chordAccent else customColors.divider)
            ) {
                Text("Off", fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = onStartHost,
                modifier = Modifier.weight(1.3f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (bandSyncState.role == BandSyncRole.HOST) customColors.chordAccent else customColors.canvasBackground,
                    contentColor = if (bandSyncState.role == BandSyncRole.HOST) Color.Black else customColors.textSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (bandSyncState.role == BandSyncRole.HOST) customColors.chordAccent else customColors.divider)
            ) {
                Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Band Leader", fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = onStartClient,
                modifier = Modifier.weight(1.3f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (bandSyncState.role == BandSyncRole.CLIENT) customColors.chordAccent else customColors.canvasBackground,
                    contentColor = if (bandSyncState.role == BandSyncRole.CLIENT) Color.Black else customColors.textSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (bandSyncState.role == BandSyncRole.CLIENT) customColors.chordAccent else customColors.divider)
            ) {
                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Band Member", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (bandSyncState.role) {
            BandSyncRole.OFF -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.canvasBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = customColors.chordAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Play Together (Local Network Sync)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = customColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Zero-latency song and scroll syncing for your whole band over Wi-Fi or Tablet Hotspot without internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onStartHost,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black)
                            ) {
                                Text("Host as Leader", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onStartClient,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, customColors.chordAccent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = customColors.chordAccent)
                            ) {
                                Text("Join Member", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            BandSyncRole.HOST -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.canvasBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BAND LEADER MODE ACTIVE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF22C55E)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Broadcasting on Port: ${bandSyncState.hostPort}${if (bandSyncState.hostIp != null) " • IP: ${bandSyncState.hostIp}" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = customColors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Connected Members: ${bandSyncState.connectedClientsCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.chordAccent,
                            fontWeight = FontWeight.Bold
                        )

                        if (bandSyncState.connectedClientNames.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(bandSyncState.connectedClientNames.size) { idx ->
                                    val name = bandSyncState.connectedClientNames[idx]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = customColors.chordAccent.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent)
                                    ) {
                                        Text(
                                            text = "🎸 $name",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = customColors.chordAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Whenever you select a song or scroll your stage viewer, all connected band members will synchronously follow your screen with zero latency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onStopSync,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Band Host", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            BandSyncRole.CLIENT -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.canvasBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (bandSyncState.isConnectedToHost) Color(0xFF22C55E) else customColors.chordAccent)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (bandSyncState.isConnectedToHost) Color(0xFF22C55E) else Color(0xFFEAB308))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (bandSyncState.isConnectedToHost) "CONNECTED TO LEADER" else "SEARCHING FOR LEADER",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = if (bandSyncState.isConnectedToHost) Color(0xFF22C55E) else Color(0xFFEAB308)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = bandSyncState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (bandSyncState.isConnectedToHost) {
                            Text(
                                text = "Synced with Band Leader (${bandSyncState.currentHostName}). Your screen will automatically follow song changes and scrolling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = onStopSync,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Disconnect", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Discovered Leaders via NSD
                            if (bandSyncState.discoveredHosts.isNotEmpty()) {
                                Text(
                                    text = "DISCOVERED LEADERS ON LOCAL WI-FI:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                bandSyncState.discoveredHosts.forEach { host ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = customColors.surfaceBackground,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = host.name, fontWeight = FontWeight.Bold, color = customColors.textPrimary)
                                                Text(text = "${host.hostAddress}:${host.port}", style = MaterialTheme.typography.labelSmall, color = customColors.textSecondary)
                                            }
                                            Button(
                                                onClick = { onConnectHost(host.hostAddress) },
                                                colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Connect", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Manual IP Input (for Hotspot or Direct IP)
                            Text(
                                text = "CONNECT VIA IP (TABLET HOTSPOT):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualIpInput,
                                    onValueChange = { manualIpInput = it },
                                    label = { Text("Leader IP Address") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = customColors.chordAccent,
                                        unfocusedBorderColor = customColors.divider,
                                        focusedTextColor = customColors.textPrimary,
                                        unfocusedTextColor = customColors.textPrimary
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (manualIpInput.isNotBlank()) {
                                            onConnectHost(manualIpInput.trim())
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black),
                                    modifier = Modifier.height(54.dp)
                                ) {
                                    Text("Connect", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

