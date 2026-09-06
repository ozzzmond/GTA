@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.joel.gta.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joel.gta.BuildConfig
import com.joel.gta.data.backup.BackupManager
import com.joel.gta.data.local.entity.SearchHistoryEntity
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.ui.components.GtaBrandLogo
import com.joel.gta.ui.components.PreSaveSongReviewDialog
import com.joel.gta.ui.components.StageToolsDialog
import com.joel.gta.ui.theme.LocalGtaColors
import com.joel.gta.ui.viewmodel.WebImportUiState

enum class HomeTab {
    SONGBOOK,
    SETLISTS,
    HISTORY,
    TRASH
}

enum class SongSortOrder(val label: String) {
    TITLE_ASC("Title (A to Z)"),
    TITLE_DESC("Title (Z to A)"),
    RECENTLY_PLAYED("Recently Played"),
    DATE_ADDED_DESC("Date Added (Newest)"),
    DATE_ADDED_ASC("Date Added (Oldest)")
}

enum class SetlistSortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SONG_COUNT("Most Songs")
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    savedSongs: List<SongEntity>,
    favoriteSongs: List<SongEntity>,
    setlists: List<SetlistWithSongs>,
    selectedTab: HomeTab = HomeTab.SONGBOOK,
    onTabSelected: (HomeTab) -> Unit = {},
    onSelectSongEntity: (SongEntity) -> Unit,
    onOpenSongFromSetlist: (com.joel.gta.data.local.entity.SetlistEntity, List<SongEntity>, Int) -> Unit,
    onMoveSongInSetlist: (Long, Long, Boolean) -> Unit,
    onFileSelected: (Uri) -> Unit,
    onOpenSampleTwoLine: () -> Unit,
    onOpenSampleChordPro: () -> Unit,
    onToggleFavoriteSong: (Long, Boolean) -> Unit,
    onDeleteSong: (SongEntity) -> Unit,
    onCreateSetlist: (String) -> Unit,
    onRemoveSongFromSetlist: (Long, Long) -> Unit,
    onDeleteSetlist: (com.joel.gta.data.local.entity.SetlistEntity) -> Unit,
    onToggleTheme: () -> Unit,
    currentThemeName: String,
    bulkImportState: com.joel.gta.ui.viewmodel.BulkImportState = com.joel.gta.ui.viewmodel.BulkImportState(),
    onFolderSelected: (Uri) -> Unit = {},
    onDismissBulkImport: () -> Unit = {},
    webImportState: WebImportUiState = WebImportUiState(),
    onFetchUrl: (String) -> Unit = {},
    onPasteClipboard: (String) -> Unit = {},
    onSearchWeb: (String) -> Unit = {},
    onSelectSearchResult: (com.joel.gta.data.scraper.WebSearchResult) -> Unit = {},
    onDismissWebReview: () -> Unit = {},
    onSaveWebReviewSong: (title: String, artist: String?, rawContent: String, key: String?, capo: String?, tags: String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateSongTags: (Long, String) -> Unit = { _, _ -> },
    onPrepareScrapedSong: (com.joel.gta.data.scraper.ScrapedSong) -> Unit = {},
    bandSyncState: com.joel.gta.data.sync.BandSyncState = com.joel.gta.data.sync.BandSyncState(),
    onStartBandHost: () -> Unit = {},
    onStartBandClient: () -> Unit = {},
    onConnectBandHost: (String) -> Unit = {},
    onStopBandSync: () -> Unit = {},
    deletedSongs: List<SongEntity> = emptyList(),
    onRestoreSong: (Long) -> Unit = {},
    onPermanentDeleteSong: (Long) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    onExportBackupShare: () -> Unit = {},
    onExportBackupSaf: (Uri) -> Unit = {},
    onExportSetlistShare: (SetlistWithSongs) -> Unit = {},
    onExportSetlistSaf: (SetlistWithSongs, Uri) -> Unit = { _, _ -> },
    onRestoreBackup: (Uri) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    showOnlyFavorites: Boolean = false,
    onToggleShowOnlyFavorites: (Boolean) -> Unit = {},
    selectedTag: String? = null,
    onSelectTag: (String?) -> Unit = {},
    songSortOrder: SongSortOrder = SongSortOrder.TITLE_ASC,
    onSongSortOrderChange: (SongSortOrder) -> Unit = {},
    setlistSortOrder: SetlistSortOrder = SetlistSortOrder.NEWEST,
    onSetlistSortOrderChange: (SetlistSortOrder) -> Unit = {},
    expandedSetlistIds: Set<Long> = emptySet(),
    onToggleSetlistExpanded: (Long) -> Unit = {},
    songbookScrollIndex: Int = 0,
    songbookScrollOffset: Int = 0,
    onUpdateSongbookScroll: (Int, Int) -> Unit = { _, _ -> },
    setlistsScrollIndex: Int = 0,
    setlistsScrollOffset: Int = 0,
    onUpdateSetlistsScroll: (Int, Int) -> Unit = { _, _ -> },
    trashScrollIndex: Int = 0,
    trashScrollOffset: Int = 0,
    onUpdateTrashScroll: (Int, Int) -> Unit = { _, _ -> },
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    onDeleteSearchHistoryItem: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    isCheckingUpdates: Boolean = false,
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current

    // Hardware/System Back Button handler in HomeScreen for clean filter/subview unrolling
    val canGoBackInHome = searchQuery.isNotBlank() || selectedTag != null || showOnlyFavorites || selectedTab != HomeTab.SONGBOOK
    BackHandler(enabled = canGoBackInHome) {
        when {
            searchQuery.isNotBlank() -> onSearchQueryChange("")
            selectedTag != null -> onSelectTag(null)
            showOnlyFavorites -> onToggleShowOnlyFavorites(false)
            selectedTab != HomeTab.SONGBOOK -> onTabSelected(HomeTab.SONGBOOK)
        }
    }

    // Preserved LazyListStates synced with ViewModel
    val songbookListState = rememberLazyListState(
        initialFirstVisibleItemIndex = songbookScrollIndex,
        initialFirstVisibleItemScrollOffset = songbookScrollOffset
    )
    LaunchedEffect(songbookListState.firstVisibleItemIndex, songbookListState.firstVisibleItemScrollOffset) {
        onUpdateSongbookScroll(songbookListState.firstVisibleItemIndex, songbookListState.firstVisibleItemScrollOffset)
    }

    val setlistsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = setlistsScrollIndex,
        initialFirstVisibleItemScrollOffset = setlistsScrollOffset
    )
    LaunchedEffect(setlistsListState.firstVisibleItemIndex, setlistsListState.firstVisibleItemScrollOffset) {
        onUpdateSetlistsScroll(setlistsListState.firstVisibleItemIndex, setlistsListState.firstVisibleItemScrollOffset)
    }

    val trashListState = rememberLazyListState(
        initialFirstVisibleItemIndex = trashScrollIndex,
        initialFirstVisibleItemScrollOffset = trashScrollOffset
    )
    LaunchedEffect(trashListState.firstVisibleItemIndex, trashListState.firstVisibleItemScrollOffset) {
        onUpdateTrashScroll(trashListState.firstVisibleItemIndex, trashListState.firstVisibleItemScrollOffset)
    }

    var editingSongForTags by remember { mutableStateOf<SongEntity?>(null) }

    var browserInitialUrl by remember { mutableStateOf<String?>(null) }
    var browserSourceName by remember { mutableStateOf("") }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var customSourceUrlInput by remember { mutableStateOf("") }
    var showDirectImportDialog by remember { mutableStateOf(false) }
    var showBrowseSourcesDialog by remember { mutableStateOf(false) }
    var directUrlInput by remember { mutableStateOf("") }

    var showSongSortMenu by remember { mutableStateOf(false) }
    var showSetlistSortMenu by remember { mutableStateOf(false) }

    var showCreateSetlistDialog by remember { mutableStateOf(false) }
    var newSetlistName by remember { mutableStateOf("") }
    var showStageToolsDialog by remember { mutableStateOf(false) }
    var showBackupRestoreMenu by remember { mutableStateOf(false) }
    var showClearHistoryTopBarDialog by remember { mutableStateOf(false) }

    // SAF Document Picker launcher - accepts .txt, .chordtxt, or all text formats
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onFileSelected(uri)
        }
    }

    // SAF Folder Picker launcher - ACTION_OPEN_DOCUMENT_TREE for massive folder import
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onFolderSelected(uri)
        }
    }

    // SAF Backup Restore Picker launcher - accepts .json files
    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onRestoreBackup(uri)
        }
    }

    // SAF Backup Save launcher - creates .json file in user selected directory
    val backupSaveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportBackupSaf(uri)
        }
    }

    // SAF Setlist Save launcher - creates .json file in user selected directory
    var pendingExportSetlist by remember { mutableStateOf<SetlistWithSongs?>(null) }
    val setlistExportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val target = pendingExportSetlist
        if (uri != null && target != null) {
            onExportSetlistSaf(target, uri)
        }
        pendingExportSetlist = null
    }

    Scaffold(
        containerColor = customColors.canvasBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GtaBrandLogo(size = 38.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "GTAR",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = customColors.textPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = customColors.surfaceBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                                    modifier = Modifier.clickable { onCheckForUpdates() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        if (isCheckingUpdates) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(10.dp),
                                                strokeWidth = 1.5.dp,
                                                color = customColors.chordAccent
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = customColors.textSecondary
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Guitar Tool App Republic",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.textSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Clear All Search History button when in History tab
                        if (selectedTab == HomeTab.HISTORY && searchHistory.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearHistoryTopBarDialog = true },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935).copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear all search history",
                                    tint = Color(0xFFEF5350)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Stage Tools (Metronome / Tuner)
                        FilledTonalIconButton(
                            onClick = { showStageToolsDialog = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = customColors.surfaceBackground,
                                contentColor = customColors.chordAccent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Stage Tools (Metronome / Tuner)",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Theme Mode Switcher
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(customColors.surfaceBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Toggle Theme: $currentThemeName",
                                tint = customColors.chordAccent
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Backup & Restore Overflow Menu
                        Box {
                            IconButton(
                                onClick = { showBackupRestoreMenu = true },
                                modifier = Modifier
                                .clip(CircleShape)
                                .background(customColors.surfaceBackground)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options & Updates",
                                    tint = customColors.chordAccent
                                )
                            }

                            DropdownMenu(
                                expanded = showBackupRestoreMenu,
                                onDismissRequest = { showBackupRestoreMenu = false },
                                containerColor = customColors.surfaceBackground
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Stage Settings") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        onOpenSettings()
                                    }
                                )
                                HorizontalDivider(color = customColors.divider)
                                DropdownMenuItem(
                                    text = { Text("Import Single File (.txt / .chordpro / .json)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "text/plain",
                                                "text/*",
                                                "application/json",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import Song Folder (Batch)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DriveFolderUpload,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        folderPickerLauncher.launch(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Direct Link / Paste Clipboard") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        showDirectImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Browse External Chords") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        showBrowseSourcesDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Backup (Share / Google Drive)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        onExportBackupShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Backup to Device (.json)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        backupSaveSafLauncher.launch(BackupManager.generateBackupFileName())
                                    }
                                )
                                HorizontalDivider(color = customColors.divider)
                                DropdownMenuItem(
                                    text = { Text("Restore from Backup (Smart Merge)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        backupPickerLauncher.launch(
                                            arrayOf(
                                                "application/json",
                                                "application/octet-stream",
                                                "text/plain",
                                                "*/*"
                                            )
                                        )
                                    }
                                )
                                HorizontalDivider(color = customColors.divider)
                                DropdownMenuItem(
                                    text = { Text("Check for Updates") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    onClick = {
                                        showBackupRestoreMenu = false
                                        onCheckForUpdates()
                                    }
                                )
                            }
                        }
                    }
                }

                // 4-Tab Segmented Switcher (Songbook, Setlists, History, Trash)
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = customColors.surfaceBackground,
                    contentColor = customColors.chordAccent,
                    divider = { HorizontalDivider(color = customColors.divider) }
                ) {
                    Tab(
                        selected = selectedTab == HomeTab.SONGBOOK,
                        onClick = { onTabSelected(HomeTab.SONGBOOK) },
                        text = {
                            Text(
                                text = "Songbook (${savedSongs.size})",
                                fontWeight = if (selectedTab == HomeTab.SONGBOOK) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == HomeTab.SETLISTS,
                        onClick = { onTabSelected(HomeTab.SETLISTS) },
                        text = {
                            Text(
                                text = "Setlists (${setlists.size})",
                                fontWeight = if (selectedTab == HomeTab.SETLISTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == HomeTab.HISTORY,
                        onClick = { onTabSelected(HomeTab.HISTORY) },
                        text = {
                            Text(
                                text = "History (${searchHistory.size})",
                                fontWeight = if (selectedTab == HomeTab.HISTORY) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == HomeTab.TRASH,
                        onClick = { onTabSelected(HomeTab.TRASH) },
                        text = {
                            Text(
                                text = "Trash (${deletedSongs.size})",
                                fontWeight = if (selectedTab == HomeTab.TRASH) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.SONGBOOK -> {
                    val listToShow = if (showOnlyFavorites) favoriteSongs else savedSongs

                    val allDistinctTags = remember(savedSongs) {
                        val set = linkedSetOf("OPM", "Acoustic", "Rock", "Slow Rock", "Pop", "Encore")
                        savedSongs.forEach { s ->
                            set.addAll(s.getTagsList())
                        }
                        set.toList()
                    }

                    val filteredSongs = listToShow.filter { song ->
                        val matchesSearch = song.title.contains(searchQuery, ignoreCase = true) ||
                                (song.artist?.contains(searchQuery, ignoreCase = true) == true)
                        val matchesTag = selectedTag == null || song.hasTag(selectedTag!!)
                        matchesSearch && matchesTag
                    }
                    val sortedSongs = remember(filteredSongs, songSortOrder) {
                        when (songSortOrder) {
                            SongSortOrder.TITLE_ASC -> filteredSongs.sortedBy { it.title.lowercase() }
                            SongSortOrder.TITLE_DESC -> filteredSongs.sortedByDescending { it.title.lowercase() }
                            SongSortOrder.RECENTLY_PLAYED -> filteredSongs.sortedByDescending { it.lastOpenedAt }
                            SongSortOrder.DATE_ADDED_DESC -> filteredSongs.sortedByDescending { it.createdAt }
                            SongSortOrder.DATE_ADDED_ASC -> filteredSongs.sortedBy { it.createdAt }
                        }
                    }

                    LaunchedEffect(searchQuery) {
                        val trimmed = searchQuery.trim()
                        if (trimmed.length >= 2) {
                            kotlinx.coroutines.delay(450)
                            onSearchWeb(trimmed)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Search Bar, Favorites Filter, and Sort Menu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search songs & chords online...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = customColors.textSecondary)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = customColors.textSecondary)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = customColors.textPrimary,
                                    unfocusedTextColor = customColors.textPrimary,
                                    focusedBorderColor = customColors.chordAccent,
                                    unfocusedBorderColor = customColors.divider,
                                    cursorColor = customColors.chordAccent,
                                    selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                                        handleColor = customColors.chordAccent,
                                        backgroundColor = customColors.chordAccent.copy(alpha = 0.35f)
                                    )
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            FilterChip(
                                selected = showOnlyFavorites,
                                onClick = {
                                    val nextFav = !showOnlyFavorites
                                    onToggleShowOnlyFavorites(nextFav)
                                    if (nextFav) onSelectTag(null)
                                },
                                label = { Text("★") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showOnlyFavorites) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "Filter Favorites",
                                        tint = if (showOnlyFavorites) customColors.chordAccent else customColors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = customColors.chordAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = customColors.chordAccent
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort Songs Dropdown Button
                            Box {
                                IconButton(
                                    onClick = { showSongSortMenu = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(customColors.surfaceBackground)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort Songs (${songSortOrder.label})",
                                        tint = customColors.chordAccent
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSongSortMenu,
                                    onDismissRequest = { showSongSortMenu = false },
                                    modifier = Modifier.background(customColors.surfaceBackground)
                                ) {
                                    Text(
                                        text = "SORT SONGS BY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    HorizontalDivider(color = customColors.divider)
                                    SongSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = order.label,
                                                        fontWeight = if (songSortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (songSortOrder == order) customColors.chordAccent else customColors.textPrimary
                                                    )
                                                    if (songSortOrder == order) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = customColors.chordAccent,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onSongSortOrderChange(order)
                                                showSongSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (searchQuery.isNotBlank()) {
                            // UNIFIED SEARCH RESULTS VIEW
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // --- 1. LOCAL SONGBOOK SECTION (Offline) ---
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.LibraryMusic,
                                                contentDescription = null,
                                                tint = customColors.chordAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "LOCAL SONGBOOK (${sortedSongs.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = customColors.textPrimary
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = customColors.surfaceBackground,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                                        ) {
                                            Text(
                                                text = "OFFLINE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = customColors.textSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (sortedSongs.isEmpty()) {
                                    item {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            color = customColors.surfaceBackground.copy(alpha = 0.6f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = customColors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "No saved songs found in your local songbook.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = customColors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(sortedSongs, key = { "local_${it.id}" }) { entity ->
                                        SongCard(
                                            entity = entity,
                                            onSelect = { onSelectSongEntity(entity) },
                                            onToggleFavorite = { onToggleFavoriteSong(entity.id, entity.isFavorite) },
                                            onDelete = { onDeleteSong(entity) },
                                            onEditTags = { editingSongForTags = entity }
                                        )
                                    }
                                }

                                // --- SECTION DIVIDER ---
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = customColors.divider, thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // --- 2. ONLINE RESULTS SECTION ---
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.TravelExplore,
                                                contentDescription = null,
                                                tint = customColors.chordAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "ONLINE RESULTS (${webImportState.searchResults.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = customColors.textPrimary
                                            )
                                        }

                                        if (!webImportState.isSearching) {
                                            TextButton(
                                                onClick = { onSearchWeb(searchQuery.trim()) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = customColors.chordAccent
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Search Online",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = customColors.chordAccent
                                                )
                                            }
                                        }
                                    }
                                }

                                if (webImportState.isSearching) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = customColors.chordAccent,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Searching online databases for chords...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = customColors.chordAccent
                                            )
                                        }
                                    }
                                }

                                if (webImportState.searchError != null && !webImportState.isSearching) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = webImportState.searchError,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFEF4444),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(onClick = { onSearchWeb(searchQuery.trim()) }) {
                                                    Text("Retry", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!webImportState.isSearching && webImportState.searchResults.isEmpty() && webImportState.searchError == null) {
                                    item {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            color = customColors.surfaceBackground.copy(alpha = 0.6f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Search online chord databases for \"$searchQuery\".",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = customColors.textSecondary,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(
                                                    onClick = { onSearchWeb(searchQuery.trim()) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = customColors.chordAccent,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Search Online Chords", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                items(webImportState.searchResults, key = { "web_${it.id}_${it.tabUrl}_${it.version}" }) { item ->
                                    val localMatch = remember(item, savedSongs) {
                                        savedSongs.firstOrNull { local ->
                                            val cleanLocal = local.title.trim().lowercase()
                                            val cleanItem = item.songName.trim().lowercase()
                                            val titleMatch = cleanLocal == cleanItem ||
                                                    cleanLocal.contains(cleanItem) ||
                                                    cleanItem.contains(cleanLocal)
                                            val artistMatch = local.artist.isNullOrBlank() || item.artistName.isBlank() ||
                                                    local.artist.trim().equals(item.artistName.trim(), ignoreCase = true)
                                            titleMatch && artistMatch
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = customColors.surfaceBackground,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (localMatch != null) 1.5.dp else 1.dp,
                                            color = if (localMatch != null) customColors.chordAccent else customColors.divider
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (localMatch != null) {
                                                    onSelectSongEntity(localMatch)
                                                } else {
                                                    onSelectSearchResult(item)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = item.songName,
                                                        fontWeight = FontWeight.Bold,
                                                        color = customColors.textPrimary,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    if (localMatch != null) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = customColors.chordAccent.copy(alpha = 0.2f),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = customColors.chordAccent,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = "In Songbook",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = customColors.chordAccent
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = item.artistName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = customColors.textSecondary
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = customColors.canvasBackground
                                                    ) {
                                                        Text(
                                                            text = "Ver ${item.version}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = customColors.chordAccent,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = customColors.canvasBackground
                                                    ) {
                                                        Text(
                                                            text = item.type,
                                                            fontSize = 10.sp,
                                                            color = customColors.textSecondary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    if (item.votes > 0) {
                                                        val votesStr = if (item.votes >= 1000) "${item.votes / 1000}k" else "${item.votes}"
                                                        Text(
                                                            text = "★ ${"%.1f".format(item.rating)} ($votesStr)",
                                                            fontSize = 10.sp,
                                                            color = Color(0xFFFBBF24),
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }

                                                    if (item.tonality != null) {
                                                        Text(
                                                            text = "Key: ${item.tonality}",
                                                            fontSize = 10.sp,
                                                            color = customColors.textSecondary
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            if (localMatch != null) {
                                                OutlinedButton(
                                                    onClick = { onSelectSongEntity(localMatch) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = customColors.chordAccent),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Open", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { onSelectSearchResult(item) },
                                                    enabled = !webImportState.isLoading,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = customColors.chordAccent,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // REGULAR SONGBOOK VIEW (When not searching)
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedTag == null && !showOnlyFavorites,
                                        onClick = {
                                            onSelectTag(null)
                                            onToggleShowOnlyFavorites(false)
                                        },
                                        label = { Text("All (${savedSongs.size})") },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = customColors.chordAccent.copy(alpha = 0.2f),
                                            selectedLabelColor = customColors.chordAccent
                                        )
                                    )
                                }

                                items(allDistinctTags.size) { idx ->
                                    val tag = allDistinctTags[idx]
                                    val count = savedSongs.count { it.hasTag(tag) }
                                    FilterChip(
                                        selected = selectedTag == tag,
                                        onClick = {
                                            val nextTag = if (selectedTag == tag) null else tag
                                            onSelectTag(nextTag)
                                            onToggleShowOnlyFavorites(false)
                                        },
                                        label = { Text(if (count > 0) "$tag ($count)" else tag) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = customColors.chordAccent.copy(alpha = 0.2f),
                                            selectedLabelColor = customColors.chordAccent
                                        )
                                    )
                                }
                            }

                            if (sortedSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.MusicOff,
                                            contentDescription = null,
                                            tint = customColors.textSecondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (showOnlyFavorites) "No favorite songs yet." else if (selectedTag != null) "No songs tagged \"$selectedTag\"." else "No songs in library.",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = customColors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Import .txt / .chordtxt files or try instant samples!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                filePickerLauncher.launch(
                                                    arrayOf("text/plain", "text/*", "application/octet-stream", "*/*")
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                                        ) {
                                            Text("Import Chords (.txt / .chordpro)", color = Color.Black)
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = songbookListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sortedSongs, key = { it.id }) { entity ->
                                        SongCard(
                                            entity = entity,
                                            onSelect = { onSelectSongEntity(entity) },
                                            onToggleFavorite = { onToggleFavoriteSong(entity.id, entity.isFavorite) },
                                            onDelete = { onDeleteSong(entity) },
                                            onEditTags = { editingSongForTags = entity }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                HomeTab.SETLISTS -> {
                    val sortedSetlists = remember(setlists, setlistSortOrder) {
                        when (setlistSortOrder) {
                            SetlistSortOrder.NEWEST -> setlists.sortedByDescending { it.setlist.createdAt }
                            SetlistSortOrder.OLDEST -> setlists.sortedBy { it.setlist.createdAt }
                            SetlistSortOrder.NAME_ASC -> setlists.sortedBy { it.setlist.name.lowercase() }
                            SetlistSortOrder.NAME_DESC -> setlists.sortedByDescending { it.setlist.name.lowercase() }
                            SetlistSortOrder.SONG_COUNT -> setlists.sortedByDescending { it.songs.size }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GIG SETLISTS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Sort Setlists Button
                                Box {
                                    IconButton(
                                        onClick = { showSetlistSortMenu = true },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(customColors.surfaceBackground)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Sort Setlists (${setlistSortOrder.label})",
                                            tint = customColors.chordAccent
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showSetlistSortMenu,
                                        onDismissRequest = { showSetlistSortMenu = false },
                                        modifier = Modifier.background(customColors.surfaceBackground)
                                    ) {
                                        Text(
                                            text = "SORT SETLISTS BY",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        HorizontalDivider(color = customColors.divider)
                                        SetlistSortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = order.label,
                                                            fontWeight = if (setlistSortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (setlistSortOrder == order) customColors.chordAccent else customColors.textPrimary
                                                        )
                                                        if (setlistSortOrder == order) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = customColors.chordAccent,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    onSetlistSortOrderChange(order)
                                                    showSetlistSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { showCreateSetlistDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Setlist", color = Color.Black, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (sortedSetlists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = customColors.textSecondary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No setlists yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = customColors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Create a setlist to organize songs for stage performances.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = customColors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = setlistsListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(sortedSetlists, key = { it.setlist.id }) { setlistWithSongs ->
                                    SetlistCard(
                                        setlistWithSongs = setlistWithSongs,
                                        isExpanded = expandedSetlistIds.contains(setlistWithSongs.setlist.id),
                                        onToggleExpand = { onToggleSetlistExpanded(setlistWithSongs.setlist.id) },
                                        onOpenSong = { index ->
                                            onOpenSongFromSetlist(setlistWithSongs.setlist, setlistWithSongs.songs, index)
                                        },
                                        onMoveSong = { songId, moveUp ->
                                            onMoveSongInSetlist(setlistWithSongs.setlist.id, songId, moveUp)
                                        },
                                        onRemoveSong = { songId ->
                                            onRemoveSongFromSetlist(setlistWithSongs.setlist.id, songId)
                                        },
                                        onDeleteSetlist = {
                                            onDeleteSetlist(setlistWithSongs.setlist)
                                        },
                                        onShareDirect = {
                                            onExportSetlistShare(setlistWithSongs)
                                        },
                                        onExportSaf = {
                                            pendingExportSetlist = setlistWithSongs
                                            val fileName = com.joel.gta.data.setlist.SetlistExportImportManager.generateSetlistFileName(setlistWithSongs.setlist.name)
                                            setlistExportSafLauncher.launch(fileName)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HomeTab.HISTORY -> {
                    HistoryTabContent(
                        searchHistory = searchHistory,
                        savedSongs = savedSongs,
                        onSelectSongEntity = onSelectSongEntity,
                        onSearchQueryChange = onSearchQueryChange,
                        onSearchWeb = onSearchWeb,
                        onTabSelected = onTabSelected,
                        onDeleteItem = onDeleteSearchHistoryItem,
                        onClearAll = onClearSearchHistory
                    )
                }

                HomeTab.TRASH -> {
                    TrashTabContent(
                        deletedSongs = deletedSongs,
                        onRestoreSong = onRestoreSong,
                        onPermanentDeleteSong = onPermanentDeleteSong,
                        onEmptyTrash = onEmptyTrash,
                        scrollState = trashListState
                    )
                }
            }
        }
    }

    // Loading overlay when fetching chords preview
    if (webImportState.isLoading) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = customColors.surfaceBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = customColors.chordAccent,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Loading chords preview...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = customColors.textPrimary
                    )
                }
            }
        }
    }

    // Error Alert if song scraping/fetching fails
    if (webImportState.error != null) {
        AlertDialog(
            onDismissRequest = onDismissWebReview,
            title = {
                Text("Import Preview", fontWeight = FontWeight.Bold, color = customColors.textPrimary)
            },
            text = {
                Text(webImportState.error, color = customColors.textSecondary)
            },
            confirmButton = {
                Button(
                    onClick = onDismissWebReview,
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Pre-Save Song Review & Preview Dialog
    if (webImportState.pendingSong != null) {
        PreSaveSongReviewDialog(
            scrapedSong = webImportState.pendingSong,
            onDismiss = onDismissWebReview,
            onSave = onSaveWebReviewSong
        )
    }

    // Bulk Import Progress Dialog
    if (bulkImportState.isImporting || bulkImportState.finished) {
        AlertDialog(
            onDismissRequest = {
                if (bulkImportState.finished) onDismissBulkImport()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (bulkImportState.finished) Icons.Default.CheckCircle else Icons.Default.DriveFolderUpload,
                        contentDescription = null,
                        tint = customColors.chordAccent,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (bulkImportState.finished) "Import Finished!" else "Importing Songs...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPrimary
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (bulkImportState.isImporting) {
                        Text(
                            text = if (bulkImportState.total > 0)
                                "Importing ${bulkImportState.current} of ${bulkImportState.total} songs..."
                            else
                                "Scanning folder for files...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = customColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = if (bulkImportState.total > 0)
                            bulkImportState.current.toFloat() / bulkImportState.total
                        else
                            0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = customColors.chordAccent,
                            trackColor = customColors.divider
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = bulkImportState.currentSongTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = customColors.textSecondary,
                            maxLines = 1
                        )
                    } else if (bulkImportState.finished) {
                        Text(
                            text = "Successfully processed and added ${bulkImportState.importedCount} songs to your local Songbook database! Fast indexed searching is now active.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = customColors.textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (bulkImportState.finished) {
                    Button(
                        onClick = {
                            onDismissBulkImport()
                            onTabSelected(HomeTab.SONGBOOK)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                    ) {
                        Text("View Songbook", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = customColors.surfaceBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Stage Tools Modal Dialog (Metronome & Guitar Tuner & Band Sync)
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

    // Edit Tags Dialog
    editingSongForTags?.let { song ->
        var currentTagsInput by remember(song.id) { mutableStateOf(song.tags) }
        val quickTags = listOf("OPM", "Acoustic", "Rock", "Slow Rock", "Pop", "Encore")

        AlertDialog(
            onDismissRequest = { editingSongForTags = null },
            title = {
                Text(
                    text = "Edit Tags: ${song.title}",
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Separate tags with commas (e.g. OPM, Acoustic):",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentTagsInput,
                        onValueChange = { currentTagsInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. OPM, Acoustic") }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Add:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickTags.forEach { quickTag ->
                            val currentList = currentTagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val isAdded = currentList.any { it.equals(quickTag, ignoreCase = true) }
                            FilterChip(
                                selected = isAdded,
                                onClick = {
                                    if (isAdded) {
                                        currentTagsInput = currentList.filterNot { it.equals(quickTag, ignoreCase = true) }.joinToString(", ")
                                    } else {
                                        currentTagsInput = if (currentTagsInput.isBlank()) quickTag else "$currentTagsInput, $quickTag"
                                    }
                                },
                                label = { Text(quickTag, style = MaterialTheme.typography.bodySmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSongTags(song.id, currentTagsInput.trim())
                        editingSongForTags = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("Save Tags", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSongForTags = null }) {
                    Text("Cancel")
                }
            },
            containerColor = customColors.surfaceBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Create Setlist Dialog
    if (showCreateSetlistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateSetlistDialog = false
                newSetlistName = ""
            },
            title = { Text("Create New Setlist") },
            text = {
                OutlinedTextField(
                    value = newSetlistName,
                    onValueChange = { newSetlistName = it },
                    label = { Text("Setlist Name") },
                    placeholder = { Text("e.g. Gig 2026 - Acoustic Night") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSetlistName.isNotBlank()) {
                            onCreateSetlist(newSetlistName.trim())
                            newSetlistName = ""
                            showCreateSetlistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSetlistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // In-App Song Browser Dialog (CCLI SongSelect, Ultimate-Guitar, Chordie, OPMTunes, etc.)
    browserInitialUrl?.let { url ->
        com.joel.gta.ui.components.InAppSongBrowserDialog(
            initialUrl = url,
            sourceName = browserSourceName,
            onDismissRequest = { browserInitialUrl = null },
            onSongCaptured = { captured ->
                browserInitialUrl = null
                onPrepareScrapedSong(captured)
            }
        )
    }

    // Add Custom Song Source Dialog
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = {
                Text(
                    text = "Open Custom Song Source",
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter website or chord page URL. You can browse and use the 1-tap 'Import Song' button on any page:",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customSourceUrlInput,
                        onValueChange = { customSourceUrlInput = it },
                        placeholder = { Text("https://my-chords-website.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = customSourceUrlInput.trim()
                        if (input.isNotBlank()) {
                            val validUrl = if (!input.startsWith("http://", ignoreCase = true) && !input.startsWith("https://", ignoreCase = true)) {
                                "https://$input"
                            } else input
                            browserSourceName = "Custom Source"
                            browserInitialUrl = validUrl
                            showAddSourceDialog = false
                            customSourceUrlInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                ) {
                    Text("Browse", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = customColors.surfaceBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Direct URL / Paste Clipboard Ingest Dialog
    if (showDirectImportDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showDirectImportDialog = false },
            containerColor = customColors.surfaceBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = customColors.chordAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Direct Link / Paste Chords", color = customColors.textPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Paste a URL from Ultimate-Guitar / CCLI or paste raw chord text directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = directUrlInput,
                        onValueChange = { directUrlInput = it },
                        placeholder = { Text("Enter chord URL...", fontSize = 13.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (directUrlInput.isNotBlank()) {
                                IconButton(onClick = { directUrlInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = customColors.textSecondary)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColors.chordAccent,
                            unfocusedBorderColor = customColors.divider,
                            focusedTextColor = customColors.textPrimary,
                            unfocusedTextColor = customColors.textPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (directUrlInput.isNotBlank()) {
                                    onFetchUrl(directUrlInput.trim())
                                    showDirectImportDialog = false
                                }
                            },
                            enabled = directUrlInput.isNotBlank() && !webImportState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fetch URL", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text ?: ""
                                if (clip.isNotBlank()) {
                                    if (clip.startsWith("http://", ignoreCase = true) || clip.startsWith("https://", ignoreCase = true)) {
                                        directUrlInput = clip.trim()
                                        onFetchUrl(clip.trim())
                                    } else {
                                        onPasteClipboard(clip)
                                    }
                                    showDirectImportDialog = false
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = customColors.chordAccent),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, customColors.chordAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Paste Clip", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (webImportState.isLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = customColors.chordAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching chords...", style = MaterialTheme.typography.bodySmall, color = customColors.chordAccent)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDirectImportDialog = false }) {
                    Text("Close", color = customColors.textSecondary)
                }
            }
        )
    }

    // Confirmation dialog for clearing Search History from Top Bar
    if (showClearHistoryTopBarDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryTopBarDialog = false },
            title = {
                Text(
                    text = "Clear all search history?",
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all search history records? This action cannot be undone.",
                    color = customColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearSearchHistory()
                        showClearHistoryTopBarDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryTopBarDialog = false }) {
                    Text("Cancel", color = customColors.textSecondary)
                }
            },
            containerColor = customColors.surfaceBackground
        )
    }

    // Browse External Song Sources Dialog
    if (showBrowseSourcesDialog) {
        val externalSources = listOf(
            Triple("songselect.ccli.com", "https://songselect.ccli.com", "Praise & Worship / Church Chords"),
            Triple("ultimate-guitar.com", "https://www.ultimate-guitar.com", "Global chords & tabs catalog"),
            Triple("chordie.com", "https://www.chordie.com", "Direct ChordPro catalog"),
            Triple("opmtunes.com", "https://www.opmtunes.com", "OPM hits & Pinoy classics")
        )
        AlertDialog(
            onDismissRequest = { showBrowseSourcesDialog = false },
            containerColor = customColors.surfaceBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = customColors.chordAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Song Sources", color = customColors.textPrimary)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Open chords in the in-app browser with 1-tap 'Import Song' button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    externalSources.forEach { (name, url, desc) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    browserSourceName = name
                                    browserInitialUrl = url
                                    showBrowseSourcesDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = customColors.canvasBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = customColors.chordAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = customColors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = customColors.textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open",
                                    tint = customColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showBrowseSourcesDialog = false
                            showAddSourceDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = customColors.chordAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Custom Website URL", color = customColors.chordAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBrowseSourcesDialog = false }) {
                    Text("Close", color = customColors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SongCard(
    entity: SongEntity,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit
) {
    val customColors = LocalGtaColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
                val subtitle = listOfNotNull(
                    entity.artist?.takeIf { it.isNotBlank() },
                    entity.key?.let { "Key: $it" },
                    if (entity.transposeOffset != 0) "(${if (entity.transposeOffset > 0) "+" else ""}${entity.transposeOffset})" else null
                ).joinToString(" • ")

                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                }

                // Tag Chips Row if any tags present
                val tagsList = entity.getTagsList()
                if (tagsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tagsList.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = customColors.chordAccent.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, customColors.chordAccent.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = customColors.chordAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Edit Tags button
            IconButton(onClick = onEditTags) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = "Edit Tags",
                    tint = if (entity.tags.isNotBlank()) customColors.chordAccent else customColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Favorite button
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (entity.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Toggle Favorite",
                    tint = if (entity.isFavorite) customColors.chordAccent else customColors.textSecondary
                )
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Song",
                    tint = customColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SetlistCard(
    setlistWithSongs: SetlistWithSongs,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onOpenSong: (Int) -> Unit,
    onMoveSong: (Long, Boolean) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onDeleteSetlist: () -> Unit,
    onShareDirect: () -> Unit = {},
    onExportSaf: () -> Unit = {}
) {
    val customColors = LocalGtaColors.current
    var showExportMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (setlistWithSongs.songs.isNotEmpty()) {
                                onOpenSong(0)
                            } else {
                                onToggleExpand()
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(customColors.chordAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = customColors.chordAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = setlistWithSongs.setlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = customColors.textPrimary
                        )
                        Text(
                            text = if (setlistWithSongs.songs.isNotEmpty())
                                "${setlistWithSongs.songs.size} tracks • Tap to start gig"
                            else
                                "0 tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textSecondary
                        )
                    }
                }

                // Quick Play All / Start Gig button from header
                if (setlistWithSongs.songs.isNotEmpty()) {
                    IconButton(
                        onClick = { onOpenSong(0) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Start Gig / Play Setlist",
                            tint = customColors.chordAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Export & Share Setlist Menu
                Box {
                    IconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export / Share Setlist",
                            tint = customColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                        containerColor = customColors.surfaceBackground
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share Setlist", color = customColors.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = customColors.chordAccent
                                )
                            },
                            onClick = {
                                showExportMenu = false
                                onShareDirect()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export to Local (.json)", color = customColors.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = customColors.chordAccent
                                )
                            },
                            onClick = {
                                showExportMenu = false
                                onExportSaf()
                            }
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Setlist",
                        tint = customColors.textSecondary
                    )
                }

                IconButton(onClick = onDeleteSetlist) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Setlist",
                        tint = customColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = customColors.divider, modifier = Modifier.padding(vertical = 10.dp))

                if (setlistWithSongs.songs.isEmpty()) {
                    Text(
                        text = "No songs in this setlist. Add songs from the Song Viewer!",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Quick Action Buttons for Export & Share in Expanded View
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onShareDirect,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = customColors.chordAccent),
                                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = customColors.chordAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Share Setlist",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            OutlinedButton(
                                onClick = onExportSaf,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = customColors.textSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = customColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Export (.json)",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        setlistWithSongs.songs.forEachIndexed { index, song ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = customColors.canvasBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider.copy(alpha = 0.5f)),
                                onClick = { onOpenSong(index) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Track Number Badge
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(customColors.chordAccent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = customColors.chordAccent
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Song Title & Key
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = customColors.textPrimary,
                                            maxLines = 1
                                        )
                                        val keyOrArtist = listOfNotNull(
                                            song.artist,
                                            song.key?.let { "Key: $it" }
                                        ).joinToString(" • ")
                                        if (keyOrArtist.isNotBlank()) {
                                            Text(
                                                text = keyOrArtist,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = customColors.textSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Sorting / Reordering Steppers: [ ▲ ] [ ▼ ]
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onMoveSong(song.id, true) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Song Up",
                                                tint = if (index > 0) customColors.textPrimary else customColors.textSecondary.copy(alpha = 0.25f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onMoveSong(song.id, false) },
                                            enabled = index < setlistWithSongs.songs.size - 1,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Song Down",
                                                tint = if (index < setlistWithSongs.songs.size - 1) customColors.textPrimary else customColors.textSecondary.copy(alpha = 0.25f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Remove button
                                    IconButton(
                                        onClick = { onRemoveSong(song.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove from setlist",
                                            tint = customColors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val customColors = LocalGtaColors.current

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(customColors.chordAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = customColors.chordAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = customColors.chordAccent.copy(alpha = 0.2f)
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = customColors.chordAccent
                )
            }
        }
    }
}

@Composable
private fun SpecPill(icon: ImageVector, label: String) {
    val customColors = LocalGtaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(customColors.surfaceBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = customColors.chordAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TrashTabContent(
    deletedSongs: List<SongEntity>,
    onRestoreSong: (Long) -> Unit,
    onPermanentDeleteSong: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    scrollState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var songToPermanentDelete by remember { mutableStateOf<SongEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (deletedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(customColors.surfaceBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = customColors.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Trash is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Songs deleted from your Songbook will appear here.\nYou can restore them anytime or delete them permanently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Header with count and Empty Trash button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recycle Bin",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPrimary
                    )
                    Text(
                        text = "${deletedSongs.size} deleted ${if (deletedSongs.size == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                }

                Button(
                    onClick = { showEmptyTrashConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935).copy(alpha = 0.15f),
                        contentColor = Color(0xFFEF5350)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Empty Trash",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(deletedSongs, key = { it.id }) { entity ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entity.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.textPrimary
                                )
                                val subtitle = listOfNotNull(
                                    entity.artist?.takeIf { it.isNotBlank() },
                                    entity.key?.let { "Key: $it" }
                                ).joinToString(" • ")

                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = customColors.textSecondary
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Restore Button
                                FilledTonalButton(
                                    onClick = { onRestoreSong(entity.id) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = customColors.chordAccent.copy(alpha = 0.2f),
                                        contentColor = customColors.chordAccent
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Restore",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                // Delete Forever Button
                                IconButton(onClick = { songToPermanentDelete = entity }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "Delete Permanently",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for Empty Trash
    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            title = {
                Text(
                    text = "Empty Trash?",
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all ${deletedSongs.size} songs in the Trash. This action cannot be undone.",
                    color = customColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showEmptyTrashConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete All Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                    Text("Cancel", color = customColors.textSecondary)
                }
            }
        )
    }

    // Confirmation dialog for single song permanent delete
    songToPermanentDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToPermanentDelete = null },
            title = {
                Text(
                    text = "Delete Permanently?",
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${song.title}\"? This cannot be undone.",
                    color = customColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDeleteSong(song.id)
                        songToPermanentDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToPermanentDelete = null }) {
                    Text("Cancel", color = customColors.textSecondary)
                }
            }
        )
    }
}

/**
 * Formats a timestamp in epoch milliseconds to human-friendly relative time string.
 */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> if (minutes == 1L) "1 min ago" else "$minutes mins ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTabContent(
    searchHistory: List<SearchHistoryEntity>,
    savedSongs: List<SongEntity>,
    onSelectSongEntity: (SongEntity) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchWeb: (String) -> Unit,
    onTabSelected: (HomeTab) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (searchHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(customColors.surfaceBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = customColors.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Search History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Searches for songs and online chord databases will appear here.\nTap any past query to quickly re-search or jump directly to imported songs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Header with count and Clear All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Search History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPrimary
                    )
                    Text(
                        text = "${searchHistory.size} past ${if (searchHistory.size == 1) "search" else "searches"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textSecondary
                    )
                }

                Button(
                    onClick = { showClearConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935).copy(alpha = 0.15f),
                        contentColor = Color(0xFFEF5350)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear All",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchHistory, key = { it.id }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                onDeleteItem(item.id)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE53935).copy(alpha = 0.85f))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Delete",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    ) {
                        val importedSong = remember(item.importedSongId, savedSongs) {
                            if (item.isImported && item.importedSongId != null) {
                                savedSongs.firstOrNull { it.id == item.importedSongId }
                            } else null
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isImported && importedSong != null) {
                                        onSelectSongEntity(importedSong)
                                    } else {
                                        onSearchQueryChange(item.query)
                                        onTabSelected(HomeTab.SONGBOOK)
                                        onSearchWeb(item.query)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (item.isImported) Color(0xFF10B981).copy(alpha = 0.35f) else customColors.divider
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (item.isImported) Color(0xFF10B981).copy(alpha = 0.15f)
                                                else customColors.canvasBackground
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.isImported) Icons.Default.CheckCircle else Icons.Default.Search,
                                            contentDescription = null,
                                            tint = if (item.isImported) Color(0xFF10B981) else customColors.chordAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.query,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = formatRelativeTime(item.timestamp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = customColors.textSecondary
                                            )
                                            if (item.isImported && importedSong != null) {
                                                Text(
                                                    text = "• In Songbook: ${importedSong.title}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF10B981),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Status Badge
                                    if (item.isImported) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                                            modifier = Modifier.clickable {
                                                if (importedSong != null) {
                                                    onSelectSongEntity(importedSong)
                                                } else {
                                                    onSearchQueryChange(item.query)
                                                    onTabSelected(HomeTab.SONGBOOK)
                                                    onSearchWeb(item.query)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Imported",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = customColors.surfaceBackground,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                                            modifier = Modifier.clickable {
                                                onSearchQueryChange(item.query)
                                                onTabSelected(HomeTab.SONGBOOK)
                                                onSearchWeb(item.query)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TravelExplore,
                                                    contentDescription = null,
                                                    tint = customColors.textSecondary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Not Imported",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = customColors.textSecondary
                                                )
                                            }
                                        }
                                    }

                                    // Quick Re-Search Action Button
                                    IconButton(
                                        onClick = {
                                            onSearchQueryChange(item.query)
                                            onTabSelected(HomeTab.SONGBOOK)
                                            onSearchWeb(item.query)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search this query",
                                            tint = customColors.chordAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Individual Delete Icon Button
                                    IconButton(
                                        onClick = { onDeleteItem(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete search record",
                                            tint = customColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear all search history?",
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all search history records? This action cannot be undone.",
                    color = customColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = customColors.textSecondary)
                }
            },
            containerColor = customColors.surfaceBackground
        )
    }
}
