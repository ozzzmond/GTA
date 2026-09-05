package com.joel.gta

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joel.gta.ui.components.StageThemeDialog
import com.joel.gta.ui.screens.HomeScreen
import com.joel.gta.ui.screens.SongViewerScreen
import com.joel.gta.ui.theme.GTATheme
import com.joel.gta.ui.theme.LocalGtaColors
import com.joel.gta.ui.viewmodel.FootswitchAction
import com.joel.gta.ui.viewmodel.SongViewerState
import com.joel.gta.ui.viewmodel.SongViewerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SongViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val customStageColors by viewModel.customStageColors.collectAsState()
            var showStageThemeDialog by remember { mutableStateOf(false) }

            val uiState by viewModel.uiState.collectAsState()
            val isAutoScrolling by viewModel.isAutoScrolling.collectAsState()
            val scrollSpeed by viewModel.scrollSpeed.collectAsState()
            val speedLevel by viewModel.speedLevel.collectAsState()
            val fontSizeSp by viewModel.fontSizeSp.collectAsState()
            val allSavedSongs by viewModel.allSavedSongs.collectAsState()
            val favoriteSongs by viewModel.favoriteSongs.collectAsState()
            val allSetlists by viewModel.allSetlists.collectAsState()
            val activeHomeTab by viewModel.activeHomeTab.collectAsState()
            val bulkImportState by viewModel.bulkImportState.collectAsState()
            val webImportState by viewModel.webImportState.collectAsState()
            val bandSyncState by viewModel.bandSyncState.collectAsState()
            val bandScrollOffset by viewModel.bandScrollOffset.collectAsState(initial = null)
            val context = LocalContext.current

            GTATheme(themeMode = themeMode, customStageColors = customStageColors) {
                val customColors = LocalGtaColors.current

                when (val state = uiState) {
                    is SongViewerState.Empty -> {
                        HomeScreen(
                            savedSongs = allSavedSongs,
                            favoriteSongs = favoriteSongs,
                            setlists = allSetlists,
                            selectedTab = activeHomeTab,
                            onTabSelected = { tab -> viewModel.setActiveHomeTab(tab) },
                            onSelectSongEntity = { entity ->
                                viewModel.loadSongFromEntity(entity)
                            },
                            onOpenSongFromSetlist = { setlist, songs, index ->
                                viewModel.openSongFromSetlist(setlist, songs, index)
                            },
                            onMoveSongInSetlist = { setlistId, songId, moveUp ->
                                viewModel.moveSongInSetlist(setlistId, songId, moveUp)
                            },
                            onFileSelected = { uri ->
                                viewModel.loadSongFromUri(context, uri)
                            },
                            onFolderSelected = { uri ->
                                viewModel.importFolderUri(context, uri)
                            },
                            onDismissBulkImport = {
                                viewModel.dismissBulkImportDialog()
                            },
                            bulkImportState = bulkImportState,
                            webImportState = webImportState,
                            onFetchUrl = { url -> viewModel.fetchSongFromUrl(url) },
                            onPasteClipboard = { text -> viewModel.prepareSongFromClipboard(text) },
                            onSearchWeb = { query -> viewModel.searchWebSongs(query) },
                            onSelectSearchResult = { result -> viewModel.selectSearchResult(result) },
                            onDismissWebReview = { viewModel.dismissWebImportReview() },
                            onSaveWebReviewSong = { title, artist, raw, key, capo, tags ->
                                viewModel.saveSongFromReview(title, artist, raw, key, capo, tags)
                            },
                            onUpdateSongTags = { songId, tags ->
                                viewModel.updateSongTags(songId, tags)
                            },
                            onPrepareScrapedSong = { scraped ->
                                viewModel.prepareSongFromScraped(scraped)
                            },
                            bandSyncState = bandSyncState,
                            onStartBandHost = { viewModel.startBandHost() },
                            onStartBandClient = { viewModel.startBandClient() },
                            onConnectBandHost = { hostAddress -> viewModel.connectToBandHost(hostAddress) },
                            onStopBandSync = { viewModel.stopBandSync() },
                            onOpenSampleTwoLine = {
                                viewModel.loadSampleSong(useChordPro = false)
                            },
                            onOpenSampleChordPro = {
                                viewModel.loadSampleSong(useChordPro = true)
                            },
                            onToggleFavoriteSong = { songId, currentFav ->
                                viewModel.toggleFavoriteForSong(songId, currentFav)
                            },
                            onDeleteSong = { entity ->
                                viewModel.deleteSong(entity)
                            },
                            onCreateSetlist = { name ->
                                viewModel.createSetlist(name)
                            },
                            onRemoveSongFromSetlist = { setlistId, songId ->
                                viewModel.removeSongFromSetlist(setlistId, songId)
                            },
                            onDeleteSetlist = { setlist ->
                                viewModel.deleteSetlist(setlist)
                            },
                            onToggleTheme = {
                                showStageThemeDialog = true
                            },
                            currentThemeName = themeMode.name
                        )
                    }

                    is SongViewerState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(customColors.canvasBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = customColors.chordAccent)
                        }
                    }

                    is SongViewerState.Loaded -> {
                        SongViewerScreen(
                            song = state.song,
                            originalKey = state.originalSong.key,
                            fileName = state.fileName,
                            isFavorite = state.isFavorite,
                            transposeOffset = state.transposeOffset,
                            isAutoScrolling = isAutoScrolling,
                            scrollSpeed = scrollSpeed,
                            speedLevel = speedLevel,
                            fontSizeSp = fontSizeSp,
                            currentThemeName = themeMode.name,
                            allSetlists = allSetlists,
                            isInSetlistMode = state.isInSetlistMode,
                            setlistSongs = state.setlistSongs,
                            currentSetlistIndex = state.currentSetlistIndex,
                            setlistProgressText = state.setlistProgressText,
                            hasPreviousSong = state.hasPreviousSong,
                            hasNextSong = state.hasNextSong,
                            onSelectSetlistIndex = { index -> viewModel.goToSetlistSong(index) },
                            onNextSong = { viewModel.goToNextSetlistSong() },
                            onPreviousSong = { viewModel.goToPreviousSetlistSong() },
                            onToggleAutoScroll = { viewModel.toggleAutoScroll() },
                            onStopAutoScroll = { viewModel.stopAutoScroll() },
                            onAdjustSpeedLevel = { delta -> viewModel.adjustSpeedLevel(delta) },
                            onAdjustScrollSpeed = { delta -> viewModel.adjustScrollSpeed(delta) },
                            onSetScrollSpeed = { speed -> viewModel.setScrollSpeed(speed) },
                            onAdjustFontSize = { delta -> viewModel.adjustFontSize(delta) },
                            onCycleTheme = { showStageThemeDialog = true },
                            onTranspose = { delta -> viewModel.transposeBy(delta) },
                            onSelectTransposeOffset = { offset -> viewModel.transposeTo(offset) },
                            onResetTranspose = { viewModel.resetTranspose() },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onAddToSetlist = { setlistId ->
                                state.songEntityId?.let { songId ->
                                    viewModel.addSongToSetlist(setlistId, songId)
                                }
                            },
                            onCreateSetlist = { name ->
                                viewModel.createSetlist(name)
                            },
                            onBack = { viewModel.clearSong() },
                            footswitchActionFlow = viewModel.footswitchAction,
                            bandSyncState = bandSyncState,
                            bandScrollOffset = bandScrollOffset,
                            onScrollFractionChanged = { fraction -> viewModel.broadcastScrollIfHost(fraction) },
                            onStartBandHost = { viewModel.startBandHost() },
                            onStartBandClient = { viewModel.startBandClient() },
                            onConnectBandHost = { hostAddress -> viewModel.connectToBandHost(hostAddress) },
                            onStopBandSync = { viewModel.stopBandSync() }
                        )
                    }

                    is SongViewerState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(customColors.canvasBackground)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Failed to open song",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = customColors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = { viewModel.clearSong() },
                                        colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                                    ) {
                                        Text("Back to Home", color = androidx.compose.ui.graphics.Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }

                if (showStageThemeDialog) {
                    StageThemeDialog(
                        currentThemeMode = themeMode,
                        currentCustomColors = customStageColors,
                        onDismissRequest = { showStageThemeDialog = false },
                        onSelectPresetMode = { mode -> viewModel.setThemeMode(mode) },
                        onApplyCustomColors = { colors -> viewModel.setCustomStageColors(colors) },
                        onResetDefaults = { viewModel.resetCustomStageColors() }
                    )
                }
            }
        }
    }

    /**
     * Intercepts hardware KeyEvents from Bluetooth footswitches & HID page turners (AirTurn, Donner, PageFlip).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.repeatCount == 0 && viewModel.uiState.value is SongViewerState.Loaded) {
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_R1 -> {
                    viewModel.sendFootswitchAction(FootswitchAction.NEXT_SONG_OR_PAGE_DOWN)
                    return true
                }
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_L1 -> {
                    viewModel.sendFootswitchAction(FootswitchAction.PREV_SONG_OR_PAGE_UP)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.sendFootswitchAction(FootswitchAction.SCROLL_DOWN)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    viewModel.sendFootswitchAction(FootswitchAction.SCROLL_UP)
                    return true
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    viewModel.sendFootswitchAction(FootswitchAction.TOGGLE_SCROLL)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
