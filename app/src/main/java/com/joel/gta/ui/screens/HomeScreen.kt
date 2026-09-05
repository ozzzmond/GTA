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
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.ui.components.PreSaveSongReviewDialog
import com.joel.gta.ui.components.StageToolsDialog
import com.joel.gta.ui.theme.LocalGtaColors
import com.joel.gta.ui.viewmodel.WebImportUiState

enum class HomeTab {
    SONGBOOK,
    SETLISTS,
    IMPORT
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismissWebReview: () -> Unit = {},
    onSaveWebReviewSong: (title: String, artist: String?, rawContent: String, key: String?, capo: String?) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }

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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(customColors.chordAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "GTA Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "GTA",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = customColors.textPrimary
                            )
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
                    val filteredSongs = listToShow.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                (it.artist?.contains(searchQuery, ignoreCase = true) == true)
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
                                .padding(bottom = 12.dp),
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
                                onClick = { showOnlyFavorites = !showOnlyFavorites },
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
                                        imageVector = Icons.Filled.Sort,
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
                                        text = if (showOnlyFavorites) "No favorite songs yet." else "No songs in library.",
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
                                        onDelete = { onDeleteSong(entity) }
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
                                            imageVector = Icons.Filled.Sort,
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
                    var urlInput by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. Web & Clipboard Ingest Suite Card
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
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = customColors.chordAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Web & Clipboard Ingest",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.textPrimary
                                        )
                                        Text(
                                            text = "Ultimate Guitar, Chordie, E-Chords, Songsterr, or raw text",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.textSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // URL input field with clear button
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            "Enter chord URL (e.g. https://tabs.ultimate-guitar.com/...)",
                                            fontSize = 13.sp
                                        )
                                    },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            tint = customColors.chordAccent
                                        )
                                    },
                                    trailingIcon = {
                                        if (urlInput.isNotBlank()) {
                                            IconButton(onClick = { urlInput = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = customColors.textSecondary
                                                )
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = customColors.chordAccent,
                                        unfocusedBorderColor = customColors.divider,
                                        focusedTextColor = customColors.textPrimary,
                                        unfocusedTextColor = customColors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Fetch from URL button
                                    Button(
                                        onClick = {
                                            if (urlInput.isNotBlank()) {
                                                onFetchUrl(urlInput.trim())
                                            }
                                        },
                                        enabled = urlInput.isNotBlank() && !webImportState.isLoading,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = customColors.chordAccent,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import URL", fontWeight = FontWeight.Bold)
                                    }

                                    // Paste from clipboard button
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
                                            .height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, customColors.chordAccent),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = customColors.chordAccent
                                        )
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Paste Clipboard", fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Loading state indicator
                                if (webImportState.isLoading) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = customColors.chordAccent,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Scraping chords and cleaning web ads...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = customColors.chordAccent,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Error banner
                                if (webImportState.error != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = webImportState.error,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
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
            }
        }
    }

    // Pre-Save Song Review Dialog
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

    // Stage Tools Modal Dialog (Metronome & Guitar Tuner)
    if (showStageToolsDialog) {
        StageToolsDialog(
            onDismissRequest = { showStageToolsDialog = false }
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
}

@Composable
private fun SongCard(
    entity: SongEntity,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
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
