package com.joel.gta.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.KeyEvent as AndroidKeyEvent
import com.joel.gta.data.audio.MetronomeEngine
import com.joel.gta.data.engine.TransposeEngine
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.data.model.SongLine
import com.joel.gta.data.parser.ChordRegex
import com.joel.gta.data.parser.SongParser
import com.joel.gta.data.scraper.ScrapedSong
import com.joel.gta.ui.components.PreSaveSongReviewDialog
import com.joel.gta.ui.components.StageToolsDialog
import com.joel.gta.ui.theme.ChordMonospaceStyle
import com.joel.gta.ui.theme.LocalGtaColors
import com.joel.gta.ui.theme.LyricMonospaceStyle
import com.joel.gta.ui.theme.SongFontStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import com.joel.gta.data.chord.ChordDictionary
import com.joel.gta.data.model.ChordVoicing
import com.joel.gta.ui.components.FretboardDiagramDialog
import com.joel.gta.ui.viewmodel.FootswitchAction
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongViewerScreen(
    song: ParsedSong,
    originalKey: String?,
    fileName: String?,
    isFavorite: Boolean,
    transposeOffset: Int,
    isAutoScrolling: Boolean,
    scrollSpeed: Float,
    speedLevel: Int = 3,
    fontSizeSp: Float,
    currentThemeName: String,
    allSetlists: List<SetlistWithSongs>,
    isInSetlistMode: Boolean = false,
    setlistSongs: List<com.joel.gta.data.local.entity.SongEntity> = emptyList(),
    currentSetlistIndex: Int = -1,
    setlistProgressText: String? = null,
    hasPreviousSong: Boolean = false,
    hasNextSong: Boolean = false,
    onSelectSetlistIndex: ((Int) -> Unit)? = null,
    onNextSong: (() -> Unit)? = null,
    onPreviousSong: (() -> Unit)? = null,
    onToggleAutoScroll: () -> Unit,
    onStopAutoScroll: () -> Unit,
    onAdjustSpeedLevel: (Int) -> Unit = {},
    onAdjustScrollSpeed: (Float) -> Unit = {},
    onSetScrollSpeed: (Float) -> Unit = {},
    onAdjustFontSize: (Float) -> Unit,
    onCycleTheme: () -> Unit,
    onTranspose: (Int) -> Unit,
    onSelectTransposeOffset: (Int) -> Unit,
    onResetTranspose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToSetlist: (Long) -> Unit,
    onCreateSetlist: (String) -> Unit,
    onBack: () -> Unit,
    footswitchActionFlow: SharedFlow<FootswitchAction>? = null,
    bandSyncState: com.joel.gta.data.sync.BandSyncState = com.joel.gta.data.sync.BandSyncState(),
    bandScrollOffset: Float? = null,
    onScrollFractionChanged: (Float) -> Unit = {},
    onStartBandHost: () -> Unit = {},
    onStartBandClient: () -> Unit = {},
    onConnectBandHost: (String) -> Unit = {},
    onStopBandSync: () -> Unit = {},
    songEntityId: Long? = null,
    rawContent: String = "",
    tags: String = "",
    onUpdateSongDetails: (id: Long, title: String, artist: String?, tags: String, rawContent: String, key: String?, capo: String?) -> Unit = { _, _, _, _, _, _, _ -> },
    keepScreenOn: Boolean = true,
    songFontStyle: SongFontStyle = SongFontStyle.MONOSPACE,
    onSelectSongFontStyle: (SongFontStyle) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Pro Stage Keep-Screen-Awake Engine
    val context = LocalContext.current
    DisposableEffect(keepScreenOn) {
        val window = (context as? android.app.Activity)?.window
            ?: (context as? android.content.ContextWrapper)?.let {
                var ctx: android.content.Context = it
                while (ctx is android.content.ContextWrapper) {
                    if (ctx is android.app.Activity) return@let ctx.window
                    ctx = ctx.baseContext
                }
                null
            }

        if (keepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val configuration = LocalConfiguration.current
    val isTabletOrLandscape = configuration.screenWidthDp >= 600 ||
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var columnCount by remember(isTabletOrLandscape) { mutableIntStateOf(1) }

    // Bi-directional hysteresis toggle:
    // Zoom out to < 15sp -> switch to 2 Columns
    // Zoom in to >= 17sp -> switch back to 1 Column
    // 15sp..16.99sp -> 2sp hysteresis buffer to prevent layout flickering
    LaunchedEffect(fontSizeSp, isTabletOrLandscape) {
        if (!isTabletOrLandscape) {
            columnCount = 1
        } else {
            if (columnCount == 1 && fontSizeSp < 15f) {
                columnCount = 2
            } else if (columnCount == 2 && fontSizeSp >= 17f) {
                columnCount = 1
            }
        }
    }

    var currentCapo by remember(song.capo) { mutableStateOf(song.capo ?: "No Capo") }
    var pedalFeedbackText by remember { mutableStateOf<String?>(null) }
    var showKeyPickerDialog by remember { mutableStateOf(false) }

    // Auto-dismiss pedal HUD indicator after 1.2 seconds
    LaunchedEffect(pedalFeedbackText) {
        if (pedalFeedbackText != null) {
            kotlinx.coroutines.delay(1200)
            pedalFeedbackText = null
        }
    }
    var showSetlistDialog by remember { mutableStateOf(false) }
    var showStageToolsDialog by remember { mutableStateOf(false) }
    var showEditSongDialog by remember { mutableStateOf(false) }
    var showSpeedInputDialog by remember { mutableStateOf(false) }
    var newSetlistName by remember { mutableStateOf("") }
    var isCreatingSetlist by remember { mutableStateOf(false) }
    var isFocusMode by remember { mutableStateOf(false) }

    var selectedChordVoicing by remember { mutableStateOf<ChordVoicing?>(null) }
    val onChordClick: (String) -> Unit = remember(context) {
        { chordName ->
            val clean = chordName.trim()
            if (clean.isNotBlank()) {
                selectedChordVoicing = ChordDictionary.getVoicing(clean, context)
            }
        }
    }

    // Hardware/System Back Button handler - navigate cleanly back to previous view
    BackHandler {
        if (isFocusMode) {
            isFocusMode = false
        } else {
            onBack()
        }
    }

    // Request focus on screen to catch Bluetooth pedals / HID events
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Bluetooth Footswitch & Hardware Page Turner Event Listener
    LaunchedEffect(footswitchActionFlow) {
        footswitchActionFlow?.collect { action ->
            when (action) {
                FootswitchAction.NEXT_SONG_OR_PAGE_DOWN -> {
                    if (isInSetlistMode && hasNextSong) {
                        pedalFeedbackText = "PEDAL: NEXT SONG"
                        onNextSong?.invoke()
                    } else {
                        pedalFeedbackText = "PEDAL: PAGE DOWN"
                        val pageStep = 600
                        verticalScrollState.animateScrollTo(
                            (verticalScrollState.value + pageStep).coerceAtMost(verticalScrollState.maxValue)
                        )
                    }
                }
                FootswitchAction.PREV_SONG_OR_PAGE_UP -> {
                    if (isInSetlistMode && hasPreviousSong && verticalScrollState.value <= 80) {
                        pedalFeedbackText = "PEDAL: PREVIOUS SONG"
                        onPreviousSong?.invoke()
                    } else {
                        pedalFeedbackText = "PEDAL: PAGE UP"
                        val pageStep = 600
                        verticalScrollState.animateScrollTo(
                            (verticalScrollState.value - pageStep).coerceAtLeast(0)
                        )
                    }
                }
                FootswitchAction.SCROLL_DOWN -> {
                    pedalFeedbackText = "PEDAL: SCROLL DOWN"
                    val step = 450
                    verticalScrollState.animateScrollTo(
                        (verticalScrollState.value + step).coerceAtMost(verticalScrollState.maxValue)
                    )
                }
                FootswitchAction.SCROLL_UP -> {
                    pedalFeedbackText = "PEDAL: SCROLL UP"
                    val step = 450
                    verticalScrollState.animateScrollTo(
                        (verticalScrollState.value - step).coerceAtLeast(0)
                    )
                }
                FootswitchAction.TOGGLE_SCROLL -> {
                    pedalFeedbackText = if (isAutoScrolling) "PEDAL: PAUSE SCROLL" else "PEDAL: START SCROLL"
                    onToggleAutoScroll()
                }
            }
        }
    }

    // When switching songs in setlist, smoothly reset scroll to top
    LaunchedEffect(song.title, songEntityId, currentSetlistIndex) {
        verticalScrollState.scrollTo(0)
    }

    // Band Sync: Broadcast scroll fraction when host scrolls
    LaunchedEffect(verticalScrollState.value) {
        if (verticalScrollState.maxValue > 0 && bandSyncState.isHost) {
            val fraction = verticalScrollState.value.toFloat() / verticalScrollState.maxValue.toFloat()
            onScrollFractionChanged(fraction)
        }
    }

    // Band Sync: Member follows host scroll position
    LaunchedEffect(bandScrollOffset) {
        if (!bandSyncState.isHost && bandScrollOffset != null) {
            if (verticalScrollState.maxValue > 0) {
                val target = (bandScrollOffset * verticalScrollState.maxValue).toInt()
                verticalScrollState.animateScrollTo(target)
            } else if (bandScrollOffset == 0f) {
                verticalScrollState.scrollTo(0)
            }
        }
    }

    // Smooth Coroutine Auto-Scroll Engine with Mutex Lock and Clean Boundary Detection
    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (!isAutoScrolling) return@LaunchedEffect

        try {
            var lastFrameTimeNanos = 0L
            verticalScrollState.scroll(MutatePriority.Default) {
                while (isActive) {
                    // Boundary check before frame
                    if (verticalScrollState.value >= verticalScrollState.maxValue && verticalScrollState.maxValue > 0) {
                        break
                    }
                    if (!verticalScrollState.canScrollForward && verticalScrollState.maxValue > 0) {
                        break
                    }

                    var reachedBottom = false
                    withFrameNanos { frameTimeNanos ->
                        if (verticalScrollState.value >= verticalScrollState.maxValue && verticalScrollState.maxValue > 0) {
                            reachedBottom = true
                            return@withFrameNanos
                        }

                        if (lastFrameTimeNanos != 0L) {
                            val dtSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                            val speedInPx = with(density) { scrollSpeed.dp.toPx() }
                            val deltaPx = speedInPx * dtSeconds
                            val consumed = scrollBy(deltaPx)
                            if (deltaPx > 0f && consumed <= 0.001f && verticalScrollState.value > 0) {
                                reachedBottom = true
                            }
                        }
                        lastFrameTimeNanos = frameTimeNanos
                    }

                    if (reachedBottom) {
                        break
                    }
                }
            }
        } finally {
            // Clean stop when user interrupts with touch drag, reaches bottom, or toggles off
            onStopAutoScroll()
        }
    }

    // Clean Manual Drag Stop: When user touches and drags screen, stop auto-scroll immediately
    LaunchedEffect(verticalScrollState.interactionSource) {
        verticalScrollState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && isAutoScrolling) {
                onStopAutoScroll()
            }
        }
    }

    Scaffold(
        containerColor = customColors.canvasBackground,
        topBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isFocusMode,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it }
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textPrimary,
                                maxLines = 1
                            )
                            val subtitle = if (isInSetlistMode && setlistProgressText != null) {
                                setlistProgressText
                            } else {
                                song.artist ?: fileName ?: "GTAR Viewer"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isInSetlistMode) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isInSetlistMode) customColors.chordAccent else customColors.textSecondary,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = customColors.textPrimary
                            )
                        }
                    },
                    actions = {
                        // Gig Mode Prev/Next Quick Navigation in Top Bar
                        if (isInSetlistMode) {
                            IconButton(
                                onClick = { onPreviousSong?.invoke() },
                                enabled = hasPreviousSong
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Song in Setlist",
                                    tint = if (hasPreviousSong) customColors.chordAccent else customColors.textSecondary.copy(alpha = 0.35f)
                                )
                            }
                            IconButton(
                                onClick = { onNextSong?.invoke() },
                                enabled = hasNextSong
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Song in Setlist",
                                    tint = if (hasNextSong) customColors.chordAccent else customColors.textSecondary.copy(alpha = 0.35f)
                                )
                            }
                        }

                        // Songbook Editor: Pencil icon if saved song
                        if (songEntityId != null) {
                            IconButton(onClick = { showEditSongDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Song in Songbook",
                                    tint = customColors.textPrimary
                                )
                            }
                        }

                        // Favorite Star Toggle
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Toggle Favorite",
                                tint = if (isFavorite) customColors.chordAccent else customColors.textSecondary
                            )
                        }

                        // Add to Setlist
                        IconButton(onClick = { showSetlistDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to Setlist",
                                tint = customColors.textPrimary
                            )
                        }

                        // Font Size Zoom Stepper [ - ] 15 [ + ]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(customColors.canvasBackground)
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { onAdjustFontSize(-1f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextDecrease,
                                    contentDescription = "Decrease text size",
                                    tint = customColors.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${fontSizeSp.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { onAdjustFontSize(1f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextIncrease,
                                    contentDescription = "Increase text size",
                                    tint = customColors.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Quick Font Style Stepper/Cycle Button
                        IconButton(
                            onClick = {
                                val allStyles = SongFontStyle.entries
                                val nextStyle = allStyles[(allStyles.indexOf(songFontStyle) + 1) % allStyles.size]
                                onSelectSongFontStyle(nextStyle)
                                pedalFeedbackText = "FONT: ${nextStyle.displayName.uppercase()}"
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(customColors.canvasBackground)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FontDownload,
                                contentDescription = "Font Style: ${songFontStyle.displayName}",
                                tint = if (songFontStyle == SongFontStyle.MONOSPACE) customColors.chordAccent else customColors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Column Reflow Toggle (Tablet / Landscape Only)
                        if (isTabletOrLandscape) {
                            IconButton(
                                onClick = { columnCount = if (columnCount == 1) 2 else 1 }
                            ) {
                                Icon(
                                    imageVector = if (columnCount == 2) Icons.Default.ViewStream else Icons.Default.ViewColumn,
                                    contentDescription = if (columnCount == 2) "Switch to 1 Column" else "Switch to 2 Columns",
                                    tint = if (columnCount == 2) customColors.chordAccent else customColors.textPrimary
                                )
                            }
                        }

                        // Standard Transpose Stepper Control: [ - ] Key: G (+1) [ + ]
                        val offsetStr = when {
                            transposeOffset > 0 -> "+$transposeOffset"
                            transposeOffset < 0 -> "$transposeOffset"
                            else -> "0"
                        }
                        val currentEffectiveKey = song.key ?: originalKey ?: "Orig"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (transposeOffset != 0) customColors.chordAccent.copy(alpha = 0.15f) else customColors.canvasBackground)
                                .border(
                                    1.dp,
                                    if (transposeOffset != 0) customColors.chordAccent else customColors.divider,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { onTranspose(-1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Transpose Down (-1)",
                                    tint = customColors.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showKeyPickerDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (transposeOffset != 0) "Key: $currentEffectiveKey ($offsetStr)" else "Key: $currentEffectiveKey",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (transposeOffset != 0) customColors.chordAccent else customColors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Target Key",
                                    tint = if (transposeOffset != 0) customColors.chordAccent else customColors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { onTranspose(1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Transpose Up (+1)",
                                    tint = customColors.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Stage Tools (Metronome & Guitar Tuner)
                        val metronomeState by MetronomeEngine.state.collectAsState()
                        IconButton(onClick = { showStageToolsDialog = true }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Stage Tools (Metronome & Tuner)",
                                    tint = if (metronomeState.isRunning) Color(0xFF10B981) else customColors.chordAccent
                                )
                                if (metronomeState.isRunning) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                }
                            }
                        }

                        // Stage Focus Mode (Fullscreen)
                        IconButton(onClick = { isFocusMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Stage Focus Mode (Fullscreen)",
                                tint = customColors.textPrimary
                            )
                        }

                        // Theme cycle
                        IconButton(onClick = onCycleTheme) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Switch Theme ($currentThemeName)",
                                tint = customColors.chordAccent
                            )
                        }

                        // Stage Settings
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Stage Settings",
                                tint = customColors.textSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = customColors.surfaceBackground
                    )
                )
            }
        },
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isFocusMode,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Gig Performance Navigation Strip (Only shown in Setlist Mode)
                    if (isInSetlistMode) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = customColors.surfaceBackground,
                            shadowElevation = 4.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { onPreviousSong?.invoke() },
                                    enabled = hasPreviousSong,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = customColors.chordAccent,
                                        disabledContentColor = customColors.textSecondary.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous Song",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PREV",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = customColors.chordAccent.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = customColors.chordAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = setlistProgressText ?: "SETLIST",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = customColors.chordAccent
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { onNextSong?.invoke() },
                                    enabled = hasNextSong,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = customColors.chordAccent,
                                        disabledContentColor = customColors.textSecondary.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text(
                                        text = "NEXT",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Song",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Floating Glassmorphic Stage Controller (Auto-Scroll & Speed)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceBackground.copy(alpha = 0.95f),
                        shadowElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Stage Play/Pause Action Button
                            Button(
                                onClick = onToggleAutoScroll,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAutoScrolling) Color(0xFFEF4444) else customColors.chordAccent
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isAutoScrolling) "Stop Scroll" else "Start Auto-Scroll",
                                    tint = if (isAutoScrolling) Color.White else Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAutoScrolling) "PAUSE" else "SCROLL",
                                    color = if (isAutoScrolling) Color.White else Color.Black,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            // Pro dp/s Speed Controls: (-) [30 dp/s] (+)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(customColors.canvasBackground)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        onAdjustScrollSpeed(-2f)
                                        onAdjustSpeedLevel(-1)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Slower",
                                        tint = customColors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = customColors.surfaceBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showSpeedInputDialog = true }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${scrollSpeed.toInt()} dp/s",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = customColors.chordAccent
                                        )
                                        Text(
                                            text = "Tap to type",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = customColors.textSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        onAdjustScrollSpeed(2f)
                                        onAdjustSpeedLevel(1)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Faster",
                                        tint = customColors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Song Display Canvas with Pinch-To-Zoom, Double-Tap Focus Mode, and Horizontal Pager
            var zoomScale by remember { mutableFloatStateOf(1f) }
            var isPinching by remember { mutableStateOf(false) }

            val gestureModifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_PAGE_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                                if (isInSetlistMode && hasNextSong) {
                                    pedalFeedbackText = "PEDAL: NEXT SONG"
                                    onNextSong?.invoke()
                                } else {
                                    pedalFeedbackText = "PEDAL: PAGE DOWN"
                                    coroutineScope.launch {
                                        verticalScrollState.animateScrollTo((verticalScrollState.value + 600).coerceAtMost(verticalScrollState.maxValue))
                                    }
                                }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_PAGE_UP, AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_BUTTON_L1 -> {
                                if (isInSetlistMode && hasPreviousSong && verticalScrollState.value <= 80) {
                                    pedalFeedbackText = "PEDAL: PREVIOUS SONG"
                                    onPreviousSong?.invoke()
                                } else {
                                    pedalFeedbackText = "PEDAL: PAGE UP"
                                    coroutineScope.launch {
                                        verticalScrollState.animateScrollTo((verticalScrollState.value - 600).coerceAtLeast(0))
                                    }
                                }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                pedalFeedbackText = "PEDAL: SCROLL DOWN"
                                coroutineScope.launch {
                                    verticalScrollState.animateScrollTo((verticalScrollState.value + 450).coerceAtMost(verticalScrollState.maxValue))
                                }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                pedalFeedbackText = "PEDAL: SCROLL UP"
                                coroutineScope.launch {
                                    verticalScrollState.animateScrollTo((verticalScrollState.value - 450).coerceAtLeast(0))
                                }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_SPACE, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                pedalFeedbackText = if (isAutoScrolling) "PEDAL: PAUSE SCROLL" else "PEDAL: START SCROLL"
                                onToggleAutoScroll()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .pointerInput(isFocusMode) {
                    detectTapGestures(
                        onDoubleTap = {
                            isFocusMode = !isFocusMode
                        },
                        onTap = {
                            if (isFocusMode) {
                                isFocusMode = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (isAutoScrolling) {
                            onStopAutoScroll()
                        }
                        do {
                            val event = awaitPointerEvent()
                            val downPointers = event.changes.filter { it.pressed }
                            if (downPointers.size >= 2) {
                                isPinching = true
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    zoomScale *= zoom
                                    if (zoomScale > 1.08f) {
                                        onAdjustFontSize(1f)
                                        zoomScale = 1f
                                    } else if (zoomScale < 0.92f) {
                                        onAdjustFontSize(-1f)
                                        zoomScale = 1f
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } else {
                                isPinching = false
                            }
                        } while (event.changes.any { it.pressed })
                        isPinching = false
                    }
                }

            Box(modifier = gestureModifier) {
                if (isInSetlistMode && setlistSongs.size > 1) {
                    val pagerState = rememberPagerState(
                        initialPage = currentSetlistIndex.coerceIn(0, setlistSongs.size - 1),
                        pageCount = { setlistSongs.size }
                    )

                    // Sync pager swipe to ViewModel & connected Band Members
                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage != currentSetlistIndex && pagerState.currentPage in setlistSongs.indices) {
                            onSelectSetlistIndex?.invoke(pagerState.currentPage)
                        }
                    }

                    // Sync external index changes to pager (Host broadcast or next/prev navigation)
                    LaunchedEffect(currentSetlistIndex) {
                        if (pagerState.currentPage != currentSetlistIndex && currentSetlistIndex in setlistSongs.indices) {
                            pagerState.animateScrollToPage(currentSetlistIndex)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val pageSong = if (pageIndex == currentSetlistIndex) {
                            song
                        } else {
                            remember(setlistSongs[pageIndex].id, setlistSongs[pageIndex].transposeOffset) {
                                val entity = setlistSongs[pageIndex]
                                val parsed = SongParser.parse(entity.rawContent, entity.title)
                                if (entity.transposeOffset != 0) {
                                    TransposeEngine.transposeSong(parsed, entity.transposeOffset)
                                } else {
                                    parsed
                                }
                            }
                        }

                        SongLinesColumn(
                            song = pageSong,
                            fontSizeSp = fontSizeSp,
                            songFontStyle = songFontStyle,
                            scrollState = if (pageIndex == currentSetlistIndex) verticalScrollState else rememberScrollState(),
                            columnCount = columnCount,
                            activeCapo = currentCapo,
                            isFocusMode = isFocusMode,
                            onChordClick = onChordClick
                        )
                    }
                } else {
                    SongLinesColumn(
                        song = song,
                        fontSizeSp = fontSizeSp,
                        songFontStyle = songFontStyle,
                        scrollState = verticalScrollState,
                        columnCount = columnCount,
                        activeCapo = currentCapo,
                        isFocusMode = isFocusMode,
                        onChordClick = onChordClick
                    )
                }

                // Floating pinch-zoom HUD indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPinching,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (columnCount == 2) Icons.Default.ViewColumn else Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = customColors.chordAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (columnCount == 2) "2-Column View (${fontSizeSp.toInt()}sp)" else "Font Size: ${fontSizeSp.toInt()}sp",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Bluetooth Footswitch Stage HUD Feedback Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = pedalFeedbackText != null,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isFocusMode) 60.dp else 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.94f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = customColors.chordAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pedalFeedbackText ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = customColors.chordAccent
                            )
                        }
                    }
                }

                // Stage Focus Mode Fullscreen Exit Hint Pill (Upper Left)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFocusMode,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 16.dp, start = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceBackground.copy(alpha = 0.88f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.clickable { isFocusMode = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Focus Mode",
                                tint = customColors.chordAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FOCUS MODE (Tap to Exit)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = customColors.chordAccent
                            )
                        }
                    }
                }

                // Stage Focus Mode: Clickable Transpose Badge (Upper Right)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFocusMode,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    val offsetStr = when {
                        transposeOffset > 0 -> "+$transposeOffset"
                        transposeOffset < 0 -> "$transposeOffset"
                        else -> "0"
                    }
                    val currentEffectiveKey = song.key ?: originalKey ?: "Orig"
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceBackground.copy(alpha = 0.90f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (transposeOffset != 0) customColors.chordAccent else customColors.divider
                        ),
                        shadowElevation = 6.dp,
                        modifier = Modifier.clickable { showKeyPickerDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (transposeOffset != 0) customColors.chordAccent else customColors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (transposeOffset != 0) "KEY: $currentEffectiveKey ($offsetStr)" else "KEY: $currentEffectiveKey",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (transposeOffset != 0) customColors.chordAccent else customColors.textPrimary
                            )
                        }
                    }
                }

                // Stage Focus Mode: Minimalist Scroll FAB (Lower Right)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFocusMode,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 20.dp)
                ) {
                    FilledIconButton(
                        onClick = onToggleAutoScroll,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isAutoScrolling) Color(0xFFEF4444).copy(alpha = 0.85f) else customColors.chordAccent.copy(alpha = 0.85f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isAutoScrolling) "Pause Auto-scroll" else "Start Auto-scroll",
                            tint = if (isAutoScrolling) Color.White else Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }

    // Clean Transpose & Target Key Selector Dialog
    if (showKeyPickerDialog) {
        val baseKey = originalKey ?: song.key ?: "C"
        AlertDialog(
            onDismissRequest = { showKeyPickerDialog = false },
            containerColor = customColors.surfaceBackground,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transpose Key",
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPrimary
                    )
                    if (transposeOffset != 0) {
                        TextButton(
                            onClick = {
                                onResetTranspose()
                                showKeyPickerDialog = false
                            }
                        ) {
                            Text("Reset (Orig)", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Select semitone shift or target key:",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    for (k in -6..6) {
                        val targetKeyName = TransposeEngine.transposeChord(baseKey, k)
                        val isSelected = k == transposeOffset
                        val offsetLabel = when {
                            k > 0 -> "+$k"
                            k < 0 -> "$k"
                            else -> "0"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTransposeOffset(k)
                                    showKeyPickerDialog = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) customColors.chordAccent.copy(alpha = 0.2f) else customColors.canvasBackground
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) customColors.chordAccent else customColors.divider
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) customColors.chordAccent else customColors.surfaceBackground,
                                        modifier = Modifier.width(44.dp)
                                    ) {
                                        Text(
                                            text = offsetLabel,
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else customColors.textSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = targetKeyName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) customColors.chordAccent else customColors.textPrimary
                                    )

                                    if (k == 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Original)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.textSecondary
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected Key",
                                        tint = customColors.chordAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeyPickerDialog = false }) {
                    Text("Close", color = customColors.chordAccent)
                }
            }
        )
    }

    // Add to Setlist Dialog
    if (showSetlistDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetlistDialog = false
                isCreatingSetlist = false
            },
            title = {
                Text(
                    text = if (isCreatingSetlist) "New Setlist" else "Add to Setlist",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (isCreatingSetlist) {
                        OutlinedTextField(
                            value = newSetlistName,
                            onValueChange = { newSetlistName = it },
                            label = { Text("Setlist Name") },
                            placeholder = { Text("e.g. Gig - Saturday Night") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        if (allSetlists.isEmpty()) {
                            Text(
                                text = "No setlists created yet. Create one now to organize your songs!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = customColors.textSecondary
                            )
                        } else {
                            Text(
                                text = "Select a setlist to add '${song.title}':",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.textSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            for (setlist in allSetlists) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            onAddToSetlist(setlist.setlist.id)
                                            showSetlistDialog = false
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = customColors.chordAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = setlist.setlist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "${setlist.songs.size} songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isCreatingSetlist) {
                    Button(
                        onClick = {
                            if (newSetlistName.isNotBlank()) {
                                onCreateSetlist(newSetlistName)
                                newSetlistName = ""
                                isCreatingSetlist = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                    ) {
                        Text("Create", color = Color.Black)
                    }
                } else {
                    Button(
                        onClick = { isCreatingSetlist = true },
                        colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Setlist", color = Color.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isCreatingSetlist) {
                        isCreatingSetlist = false
                    } else {
                        showSetlistDialog = false
                    }
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pro dp/s Exact Numeric Input Dialog
    if (showSpeedInputDialog) {
        var tempSpeedText by remember { mutableStateOf("${scrollSpeed.toInt()}") }
        AlertDialog(
            onDismissRequest = { showSpeedInputDialog = false },
            title = {
                Text(
                    text = "Custom Scroll Speed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter speed in density-independent pixels per second (dp/s):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = customColors.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempSpeedText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                tempSpeedText = input
                            }
                        },
                        label = { Text("Speed (dp/s)") },
                        placeholder = { Text("e.g. 30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = tempSpeedText.toFloatOrNull()
                        if (parsed != null && parsed >= 5f) {
                            onSetScrollSpeed(parsed.coerceIn(5f, 200f))
                        }
                        showSpeedInputDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Stage Tools Modal Dialog (Metronome, Guitar Tuner & Band Sync)
    if (showStageToolsDialog) {
        StageToolsDialog(
            onDismissRequest = { showStageToolsDialog = false },
            bandSyncState = bandSyncState,
            onStartBandHost = onStartBandHost,
            onStartBandClient = onStartBandClient,
            onConnectBandHost = onConnectBandHost,
            onStopBandSync = onStopBandSync
        )
    }

    // Songbook Editor Dialog (PreSaveSongReviewDialog with prefilled content and tags)
    if (showEditSongDialog && songEntityId != null) {
        PreSaveSongReviewDialog(
            scrapedSong = ScrapedSong(
                title = song.title,
                artist = song.artist,
                key = originalKey ?: song.key,
                capo = currentCapo,
                rawContent = rawContent.ifBlank { song.title },
                sourceUrl = ""
            ),
            dialogTitle = "Edit Song: ${song.title}",
            saveButtonText = "Save Changes",
            initialTags = tags,
            onDismiss = { showEditSongDialog = false },
            onSave = { updatedTitle, updatedArtist, updatedContent, updatedKey, updatedCapo, updatedTags ->
                onUpdateSongDetails(
                    songEntityId,
                    updatedTitle,
                    updatedArtist,
                    updatedTags,
                    updatedContent,
                    updatedKey,
                    updatedCapo
                )
                showEditSongDialog = false
            }
        )
    }

    // 100% Offline Interactive Chord Voicing Popup Dialog
    selectedChordVoicing?.let { voicing ->
        FretboardDiagramDialog(
            voicing = voicing,
            onDismissRequest = { selectedChordVoicing = null }
        )
    }
}

@Composable
private fun MetaBadge(label: String) {
    val customColors = LocalGtaColors.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = customColors.surfaceBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = customColors.chordAccent
        )
    }
}

private fun splitSongLinesForColumns(lines: List<SongLine>): Pair<List<SongLine>, List<SongLine>> {
    if (lines.size <= 4) return lines to emptyList()
    val mid = lines.size / 2
    var splitIndex = mid
    val searchRange = (mid - 6).coerceAtLeast(1)..(mid + 6).coerceAtMost(lines.size - 2)
    for (i in searchRange) {
        if (lines[i] is SongLine.SectionHeader) {
            splitIndex = i
            break
        }
    }
    return lines.take(splitIndex) to lines.drop(splitIndex)
}

@Composable
private fun RenderSongLine(
    line: SongLine,
    fontSizeSp: Float,
    songFontStyle: SongFontStyle = SongFontStyle.MONOSPACE,
    onChordClick: (String) -> Unit = {}
) {
    val customColors = LocalGtaColors.current
    when (line) {
        is SongLine.SectionHeader -> {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "[${line.title}]",
                fontFamily = songFontStyle.fontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (fontSizeSp + 1).sp,
                lineHeight = ((fontSizeSp + 1) * 1.35f).sp,
                letterSpacing = if (songFontStyle == SongFontStyle.MONOSPACE) 0.8.sp else 0.5.sp,
                color = customColors.sectionHeader,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
        }

        is SongLine.ChordLine -> {
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = line.chords,
                style = ChordMonospaceStyle.copy(
                    fontFamily = songFontStyle.fontFamily,
                    fontWeight = songFontStyle.chordFontWeight,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.35f).sp,
                    letterSpacing = if (songFontStyle == SongFontStyle.MONOSPACE) 0.8.sp else 0.5.sp,
                    color = customColors.chordAccent
                ),
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 1.dp)
                    .pointerInput(line.chords) {
                        detectTapGestures { tapOffset ->
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(tapOffset)
                                val chord = extractChordAtOffset(line.chords, offset)
                                if (chord != null) {
                                    onChordClick(chord)
                                }
                            }
                        }
                    }
            )
        }

        is SongLine.LyricLine -> {
            Text(
                text = line.lyrics,
                style = LyricMonospaceStyle.copy(
                    fontFamily = songFontStyle.fontFamily,
                    fontWeight = songFontStyle.lyricFontWeight,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.35f).sp,
                    letterSpacing = if (songFontStyle == SongFontStyle.MONOSPACE) 0.8.sp else 0.5.sp,
                    color = customColors.textPrimary
                ),
                modifier = Modifier.padding(top = 1.dp, bottom = 5.dp)
            )
        }

        is SongLine.ChordProLine -> {
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotatedText = buildAnnotatedString {
                for (segment in line.segments) {
                    if (segment.chord != null) {
                        pushStringAnnotation(tag = "CHORD", annotation = segment.chord)
                        withStyle(
                            style = SpanStyle(
                                color = customColors.chordAccent,
                                fontWeight = songFontStyle.chordFontWeight
                            )
                        ) {
                            append("[${segment.chord}]")
                        }
                        pop()
                    }
                    withStyle(
                        style = SpanStyle(
                            color = customColors.textPrimary,
                            fontWeight = songFontStyle.lyricFontWeight
                        )
                    ) {
                        append(segment.text)
                    }
                }
            }
            Text(
                text = annotatedText,
                fontFamily = songFontStyle.fontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.4f).sp,
                letterSpacing = if (songFontStyle == SongFontStyle.MONOSPACE) 0.8.sp else 0.5.sp,
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .pointerInput(annotatedText) {
                        detectTapGestures { tapOffset ->
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(tapOffset)
                                val clickedChord = annotatedText
                                    .getStringAnnotations(tag = "CHORD", start = offset, end = offset)
                                    .firstOrNull()?.item
                                    ?: if (offset > 0) {
                                        annotatedText
                                            .getStringAnnotations(tag = "CHORD", start = offset - 1, end = offset - 1)
                                            .firstOrNull()?.item
                                    } else null
                                    ?: if (offset < annotatedText.length - 1) {
                                        annotatedText
                                            .getStringAnnotations(tag = "CHORD", start = offset + 1, end = offset + 1)
                                            .firstOrNull()?.item
                                    } else null

                                if (clickedChord != null) {
                                    onChordClick(clickedChord)
                                }
                            }
                        }
                    }
            )
        }

        is SongLine.TabLine -> {
            val tabFontSize = (fontSizeSp - 1f).coerceAtLeast(11f)
            Text(
                text = line.content,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = tabFontSize.sp,
                lineHeight = (tabFontSize * 1.25f).sp,
                letterSpacing = 0.8.sp,
                color = customColors.tabLineColor,
                modifier = Modifier.padding(vertical = 1.5.dp)
            )
        }

        is SongLine.EmptyLine -> {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun extractChordAtOffset(text: String, offset: Int): String? {
    if (text.isBlank() || offset !in text.indices) {
        return null
    }

    // Delimiters that separate chords (do not treat internal parentheses like (b9) as delimiters)
    val isDelim = { c: Char -> c.isWhitespace() || c in "-–—|,:;[]{}" }

    if (isDelim(text[offset])) {
        if (offset > 0 && !isDelim(text[offset - 1])) {
            return extractChordAtOffset(text, offset - 1)
        }
        if (offset + 1 in text.indices && !isDelim(text[offset + 1])) {
            return extractChordAtOffset(text, offset + 1)
        }
        return null
    }

    var start = offset
    while (start > 0 && !isDelim(text[start - 1])) {
        start--
    }

    var end = offset
    while (end < text.length && !isDelim(text[end])) {
        end++
    }

    val word = text.substring(start, end).trim(' ', '\t', '[', ']', '{', '}', ',', ';', ':', '-', '–', '—', '|')

    // Direct match (e.g. G13, A6, Dbdim, G7(b9), D/F#)
    if (word.isNotBlank() && ChordRegex.CHORD_TOKEN_REGEX.matches(word)) {
        return word
    }

    // Check if enclosed in outer parentheses like (Am7)
    if (word.startsWith("(") && word.endsWith(")")) {
        val unwrapped = word.substring(1, word.length - 1).trim()
        if (ChordRegex.CHORD_TOKEN_REGEX.matches(unwrapped)) {
            return unwrapped
        }
    }

    // Clean any trailing delimiter punctuation like "/" or "."
    val cleaned = word.trim('(', ')', '/', '.')
    if (cleaned.isNotBlank() && ChordRegex.CHORD_TOKEN_REGEX.matches(cleaned)) {
        return cleaned
    }

    return null
}

@Composable
private fun SongLinesColumn(
    song: ParsedSong,
    fontSizeSp: Float,
    songFontStyle: SongFontStyle = SongFontStyle.MONOSPACE,
    scrollState: androidx.compose.foundation.ScrollState,
    columnCount: Int = 1,
    activeCapo: String = "No Capo",
    isFocusMode: Boolean = false,
    onChordClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (isFocusMode) 60.dp else 12.dp,
                bottom = 12.dp
            )
    ) {
        // Metadata header badges (Key, Capo, Format, Column badge)
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!song.key.isNullOrBlank()) {
                MetaBadge(label = "KEY: ${song.key}")
            }
            val effectiveCapo = if (activeCapo.isNotBlank() && !activeCapo.equals("No Capo", ignoreCase = true) && !activeCapo.equals("None", ignoreCase = true)) activeCapo else song.capo
            if (!effectiveCapo.isNullOrBlank() && !effectiveCapo.equals("No Capo", ignoreCase = true) && !effectiveCapo.equals("None", ignoreCase = true)) {
                val label = if (effectiveCapo.startsWith("Capo", ignoreCase = true) || effectiveCapo.startsWith("Fret", ignoreCase = true)) {
                    effectiveCapo.uppercase()
                } else {
                    "CAPO: $effectiveCapo"
                }
                MetaBadge(label = label)
            }
            if (columnCount == 2) {
                MetaBadge(label = "2 COLUMNS")
            } else {
                MetaBadge(label = song.format.name.replace("_", " "))
            }
        }

        HorizontalDivider(color = customColors.divider, thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))

        // Render 1 or 2 Columns
        if (columnCount == 2) {
            val (col1Lines, col2Lines) = remember(song.lines) {
                splitSongLinesForColumns(song.lines)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    col1Lines.forEach { line ->
                        RenderSongLine(line, fontSizeSp, songFontStyle, onChordClick)
                    }
                }
                VerticalDivider(
                    color = customColors.divider.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    col2Lines.forEach { line ->
                        RenderSongLine(line, fontSizeSp, songFontStyle, onChordClick)
                    }
                }
            }
        } else {
            song.lines.forEach { line ->
                RenderSongLine(line, fontSizeSp, songFontStyle, onChordClick)
            }
        }

        // Bottom padding for scroll clearance
        Spacer(modifier = Modifier.height(140.dp))
    }
}
