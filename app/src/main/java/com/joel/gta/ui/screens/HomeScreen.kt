@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.joel.gta.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    IMPORT,
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
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var editingSongForTags by remember { mutableStateOf<SongEntity?>(null) }

    var browserInitialUrl by remember { mutableStateOf<String?>(null) }
    var browserSourceName by remember { mutableStateOf("") }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var customSourceUrlInput by remember { mutableStateOf("") }

    var songSortOrder by remember { mutableStateOf(SongSortOrder.TITLE_ASC) }
    var showSongSortMenu by remember { mutableStateOf(false) }

    var setlistSortOrder by remember { mutableStateOf(SetlistSortOrder.NEWEST) }
    var showSetlistSortMenu by remember { mutableStateOf(false) }

    var showCreateSetlistDialog by remember { mutableStateOf(false) }
    var newSetlistName by remember { mutableStateOf("") }
    var showStageToolsDialog by remember { mutableStateOf(false) }


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

    Scaffold(
        containerColor = customColors.canvasBackground,
        topBar = {
            Column {
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
                                    text = "GTA",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = customColors.textPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = customColors.surfaceBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = customColors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Guitar Tool Application",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.textSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                        Spacer(modifier = Modifier.width(6.dp))

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
                    }
                }

                // 3-Tab Segmented Switcher
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
                        selected = selectedTab == HomeTab.IMPORT,
                        onClick = { onTabSelected(HomeTab.IMPORT) },
                        text = {
                            Text(
                                text = "Import",
                                fontWeight = if (selectedTab == HomeTab.IMPORT) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) }
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
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search songs...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = customColors.textSecondary)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            FilterChip(
                                selected = showOnlyFavorites,
                                onClick = {
                                    showOnlyFavorites = !showOnlyFavorites
                                    if (showOnlyFavorites) selectedTag = null
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
                                                songSortOrder = order
                                                showSongSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Horizontal Tag Filter Chips Row
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
                                        selectedTag = null
                                        showOnlyFavorites = false
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
                                        selectedTag = if (selectedTag == tag) null else tag
                                        showOnlyFavorites = false
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
                                        onClick = { onTabSelected(HomeTab.IMPORT) },
                                        colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent)
                                    ) {
                                        Text("Go to Import", color = Color.Black)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
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
                                                    setlistSortOrder = order
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
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(sortedSetlists, key = { it.setlist.id }) { setlistWithSongs ->
                                    SetlistCard(
                                        setlistWithSongs = setlistWithSongs,
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
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HomeTab.IMPORT -> {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    var webSearchInput by remember { mutableStateOf(webImportState.searchQuery) }
                    var urlInput by remember { mutableStateOf("") }
                    var showDirectUrlSection by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. Online Song & Chord Search Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(customColors.chordAccent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TravelExplore,
                                            contentDescription = null,
                                            tint = customColors.chordAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Song & Chords Web Search",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.textPrimary
                                        )
                                        Text(
                                            text = "Type Title or Artist to fetch versions & chords online",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.textSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Search Bar with clear and search actions
                                OutlinedTextField(
                                    value = webSearchInput,
                                    onValueChange = { webSearchInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text("Search song title or artist (e.g. Hotel California)...", fontSize = 13.sp)
                                    },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = customColors.chordAccent)
                                    },
                                    trailingIcon = {
                                        if (webSearchInput.isNotBlank()) {
                                            IconButton(onClick = {
                                                webSearchInput = ""
                                                onSearchWeb("")
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = customColors.textSecondary)
                                            }
                                        }
                                    },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onSearch = {
                                            if (webSearchInput.isNotBlank()) {
                                                onSearchWeb(webSearchInput)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = customColors.chordAccent,
                                        unfocusedBorderColor = customColors.divider,
                                        focusedTextColor = customColors.textPrimary,
                                        unfocusedTextColor = customColors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (webSearchInput.isNotBlank()) {
                                            onSearchWeb(webSearchInput)
                                        }
                                    },
                                    enabled = webSearchInput.isNotBlank() && !webImportState.isSearching,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Find Online Versions", fontWeight = FontWeight.Bold)
                                }

                                // Quick suggestion chips
                                Spacer(modifier = Modifier.height(10.dp))
                                val sampleQueries = listOf("Hotel California", "Ang Huling El Bimbo", "Torete", "Creep", "With or Without You")
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(sampleQueries.size) { idx ->
                                        val sample = sampleQueries[idx]
                                        SuggestionChip(
                                            onClick = {
                                                webSearchInput = sample
                                                onSearchWeb(sample)
                                            },
                                            label = { Text(sample, fontSize = 11.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = customColors.canvasBackground,
                                                labelColor = customColors.textSecondary
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                                        )
                                    }
                                }

                                // Searching progress indicator
                                if (webImportState.isSearching) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = customColors.chordAccent, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Searching online databases for chord versions...", style = MaterialTheme.typography.bodySmall, color = customColors.chordAccent)
                                    }
                                }

                                // Search error banner
                                if (webImportState.searchError != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = webImportState.searchError, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                                        }
                                    }
                                }

                                // Search Results List
                                if (webImportState.searchResults.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "CHORD VERSIONS (${webImportState.searchResults.size}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    webImportState.searchResults.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = customColors.canvasBackground,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable { onSelectSearchResult(item) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = item.songName, fontWeight = FontWeight.Bold, color = customColors.textPrimary)
                                                    Text(text = item.artistName, style = MaterialTheme.typography.bodySmall, color = customColors.textSecondary)

                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = customColors.chordAccent.copy(alpha = 0.2f)
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
                                                            color = customColors.surfaceBackground
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

                                                Button(
                                                    onClick = { onSelectSearchResult(item) },
                                                    enabled = !webImportState.isLoading,
                                                    colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Preview", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Browse External Song Sources (CCLI SongSelect, UG, Chordie, OPMTunes, Custom)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(customColors.chordAccent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = customColors.chordAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Browse External Song Sources",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.textPrimary
                                        )
                                        Text(
                                            text = "In-app browser with 1-tap 'Import Song' button",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.textSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val externalSources = listOf(
                                    Triple("songselect.ccli.com", "https://songselect.ccli.com", "Praise & Worship / Church Chords"),
                                    Triple("ultimate-guitar.com", "https://www.ultimate-guitar.com", "Global chords & tabs catalog"),
                                    Triple("chordie.com", "https://www.chordie.com", "Direct ChordPro catalog"),
                                    Triple("opmtunes.com", "https://www.opmtunes.com", "OPM hits & Pinoy classics")
                                )

                                externalSources.forEach { (name, url, desc) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                browserSourceName = name
                                                browserInitialUrl = url
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

                                // + Add Source (Custom URL)
                                OutlinedButton(
                                    onClick = { showAddSourceDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = customColors.chordAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ Add Source (Custom URL)", color = customColors.chordAccent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Collapsible Direct Link or Clipboard Ingest Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showDirectUrlSection = !showDirectUrlSection }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = customColors.chordAccent, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Direct URL or Clipboard Ingest",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.textPrimary
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showDirectUrlSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = customColors.textSecondary
                                    )
                                }

                                if (showDirectUrlSection) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Enter chord URL (e.g. tabs.ultimate-guitar.com/...)", fontSize = 13.sp) },
                                        singleLine = true,
                                        trailingIcon = {
                                            if (urlInput.isNotBlank()) {
                                                IconButton(onClick = { urlInput = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = customColors.textSecondary)
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = customColors.chordAccent,
                                            unfocusedBorderColor = customColors.divider,
                                            focusedTextColor = customColors.textPrimary,
                                            unfocusedTextColor = customColors.textPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (urlInput.isNotBlank()) {
                                                    onFetchUrl(urlInput.trim())
                                                }
                                            },
                                            enabled = urlInput.isNotBlank() && !webImportState.isLoading,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = customColors.chordAccent, contentColor = Color.Black)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Import URL", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val clip = clipboardManager.getText()?.text ?: ""
                                                if (clip.isNotBlank()) {
                                                    if (clip.startsWith("http://", ignoreCase = true) || clip.startsWith("https://", ignoreCase = true)) {
                                                        urlInput = clip.trim()
                                                    }
                                                    onPasteClipboard(clip)
                                                }
                                            },
                                            enabled = !webImportState.isLoading,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.5.dp, customColors.chordAccent),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = customColors.chordAccent)
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Paste Clipboard", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (webImportState.isLoading) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = customColors.chordAccent, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Scraping chords and cleaning web ads...", style = MaterialTheme.typography.bodySmall, color = customColors.chordAccent)
                                        }
                                    }

                                    if (webImportState.error != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(text = webImportState.error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }


                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Local Storage Files Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Clean Chord & Tab Viewer",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.textPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Offline-first performance stage tool. Fast dual-format parsing for 2-line tabs & ChordPro songs. Auto-saves directly to your local Songbook.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = customColors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Main SAF Open Button
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "text/plain",
                                                "text/*",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.chordAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Open Single File (.txt / .chordpro)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Massive Folder Import Button via ACTION_OPEN_DOCUMENT_TREE
                                OutlinedButton(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, customColors.chordAccent),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = customColors.chordAccent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DriveFolderUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Import Song Folder",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                HomeTab.TRASH -> {
                    TrashTabContent(
                        deletedSongs = deletedSongs,
                        onRestoreSong = onRestoreSong,
                        onPermanentDeleteSong = onPermanentDeleteSong,
                        onEmptyTrash = onEmptyTrash
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
    onOpenSong: (Int) -> Unit,
    onMoveSong: (Long, Boolean) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onDeleteSetlist: () -> Unit
) {
    val customColors = LocalGtaColors.current
    var isExpanded by remember { mutableStateOf(false) }

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
                                isExpanded = !isExpanded
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

                IconButton(onClick = { isExpanded = !isExpanded }) {
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
