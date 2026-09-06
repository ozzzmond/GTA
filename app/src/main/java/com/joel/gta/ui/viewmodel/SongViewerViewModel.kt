package com.joel.gta.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joel.gta.data.engine.TransposeEngine
import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.parser.SongParser
import com.joel.gta.data.repository.ChordRepository
import com.joel.gta.data.repository.SongRepository
import com.joel.gta.ui.screens.HomeTab
import com.joel.gta.ui.screens.SongSortOrder
import com.joel.gta.ui.screens.SetlistSortOrder
import com.joel.gta.BuildConfig
import com.joel.gta.data.backup.BackupManager
import com.joel.gta.data.backup.RestoreSummary
import com.joel.gta.data.update.ReleaseInfo
import com.joel.gta.data.update.UpdateCheckResult
import com.joel.gta.data.update.UpdateManager
import com.joel.gta.ui.theme.AppThemeMode
import com.joel.gta.ui.theme.CustomStageColors
import com.joel.gta.ui.theme.SongFontStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

import com.joel.gta.data.scraper.ScrapedSong
import com.joel.gta.data.scraper.WebSearchResult
import com.joel.gta.data.scraper.WebScraperEngine
import com.joel.gta.data.sync.BandSyncManager
import com.joel.gta.data.sync.BandSyncRole
import com.joel.gta.data.sync.BandSyncState
import com.joel.gta.data.sync.SyncMessage

enum class FootswitchAction {
    NEXT_SONG_OR_PAGE_DOWN,
    PREV_SONG_OR_PAGE_UP,
    SCROLL_DOWN,
    SCROLL_UP,
    TOGGLE_SCROLL
}

data class BulkImportState(
    val isImporting: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentSongTitle: String = "",
    val finished: Boolean = false,
    val importedCount: Int = 0
)

data class WebImportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingSong: ScrapedSong? = null,
    val searchQuery: String = "",
    val searchResults: List<WebSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null
)


class SongViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SongRepository(GtaDatabase.getDatabase(application))
    private val chordRepository = ChordRepository(GtaDatabase.getDatabase(application))

    private val _footswitchAction = MutableSharedFlow<FootswitchAction>(extraBufferCapacity = 1)
    val footswitchAction: SharedFlow<FootswitchAction> = _footswitchAction.asSharedFlow()

    fun sendFootswitchAction(action: FootswitchAction) {
        _footswitchAction.tryEmit(action)
    }

    val allSavedSongs: StateFlow<List<SongEntity>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongs: StateFlow<List<SongEntity>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allSetlists: StateFlow<List<SetlistWithSongs>> = repository.allSetlists
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val deletedSongs: StateFlow<List<SongEntity>> = repository.deletedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeHomeTab = MutableStateFlow(HomeTab.SONGBOOK)
    val activeHomeTab: StateFlow<HomeTab> = _activeHomeTab.asStateFlow()

    fun setActiveHomeTab(tab: HomeTab) {
        _activeHomeTab.value = tab
    }

    // Persistent Home View & Navigation Filter States
    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _homeShowOnlyFavorites = MutableStateFlow(false)
    val homeShowOnlyFavorites: StateFlow<Boolean> = _homeShowOnlyFavorites.asStateFlow()

    private val _homeSelectedTag = MutableStateFlow<String?>(null)
    val homeSelectedTag: StateFlow<String?> = _homeSelectedTag.asStateFlow()

    private val _songSortOrder = MutableStateFlow(SongSortOrder.TITLE_ASC)
    val songSortOrder: StateFlow<SongSortOrder> = _songSortOrder.asStateFlow()

    private val _setlistSortOrder = MutableStateFlow(SetlistSortOrder.NEWEST)
    val setlistSortOrder: StateFlow<SetlistSortOrder> = _setlistSortOrder.asStateFlow()

    private val _expandedSetlistIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedSetlistIds: StateFlow<Set<Long>> = _expandedSetlistIds.asStateFlow()

    // Scroll tracking across screen navigations
    var songbookScrollIndex: Int = 0
    var songbookScrollOffset: Int = 0
    var setlistsScrollIndex: Int = 0
    var setlistsScrollOffset: Int = 0
    var trashScrollIndex: Int = 0
    var trashScrollOffset: Int = 0

    fun setHomeSearchQuery(query: String) {
        _homeSearchQuery.value = query
    }

    fun setHomeShowOnlyFavorites(show: Boolean) {
        _homeShowOnlyFavorites.value = show
    }

    fun setHomeSelectedTag(tag: String?) {
        _homeSelectedTag.value = tag
    }

    fun setSongSortOrder(order: SongSortOrder) {
        _songSortOrder.value = order
    }

    fun setSetlistSortOrder(order: SetlistSortOrder) {
        _setlistSortOrder.value = order
    }

    fun toggleSetlistExpanded(setlistId: Long) {
        _expandedSetlistIds.value = if (_expandedSetlistIds.value.contains(setlistId)) {
            _expandedSetlistIds.value - setlistId
        } else {
            _expandedSetlistIds.value + setlistId
        }
    }

    fun setSetlistExpanded(setlistId: Long, expanded: Boolean) {
        _expandedSetlistIds.value = if (expanded) {
            _expandedSetlistIds.value + setlistId
        } else {
            _expandedSetlistIds.value - setlistId
        }
    }

    fun updateSongbookScroll(index: Int, offset: Int) {
        songbookScrollIndex = index
        songbookScrollOffset = offset
    }

    fun updateSetlistsScroll(index: Int, offset: Int) {
        setlistsScrollIndex = index
        setlistsScrollOffset = offset
    }

    fun updateTrashScroll(index: Int, offset: Int) {
        trashScrollIndex = index
        trashScrollOffset = offset
    }

    private val _uiState = MutableStateFlow<SongViewerState>(SongViewerState.Empty)
    val uiState: StateFlow<SongViewerState> = _uiState.asStateFlow()

    private val _isAutoScrolling = MutableStateFlow(false)
    val isAutoScrolling: StateFlow<Boolean> = _isAutoScrolling.asStateFlow()

    private val _scrollSpeed = MutableStateFlow(30f)
    val scrollSpeed: StateFlow<Float> = _scrollSpeed.asStateFlow()

    private val _speedLevel = MutableStateFlow(3)
    val speedLevel: StateFlow<Int> = _speedLevel.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(15f)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _webImportState = MutableStateFlow(WebImportUiState())
    val webImportState: StateFlow<WebImportUiState> = _webImportState.asStateFlow()

    private val themePrefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences("gta_stage_theme_prefs", Context.MODE_PRIVATE)
    }

    private val stageSettingsPrefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences("gta_stage_settings_prefs", Context.MODE_PRIVATE)
    }

    private val _keepScreenOn: MutableStateFlow<Boolean>
    val keepScreenOn: StateFlow<Boolean>

    private val _songFontStyle: MutableStateFlow<SongFontStyle>
    val songFontStyle: StateFlow<SongFontStyle>

    private val _themeMode: MutableStateFlow<AppThemeMode>
    val themeMode: StateFlow<AppThemeMode>

    private val _customStageColors: MutableStateFlow<CustomStageColors>
    val customStageColors: StateFlow<CustomStageColors>

    val bandSyncManager = BandSyncManager(application)
    val bandSyncState: StateFlow<BandSyncState> = bandSyncManager.syncState
    val bandScrollOffset = MutableSharedFlow<Float>(extraBufferCapacity = 1)

    init {
        val savedKeepScreenOn = stageSettingsPrefs.getBoolean("pref_keep_screen_on", true)
        _keepScreenOn = MutableStateFlow(savedKeepScreenOn)
        keepScreenOn = _keepScreenOn.asStateFlow()

        val savedFontName = stageSettingsPrefs.getString("pref_song_font_style", SongFontStyle.MONOSPACE.name)
        val loadedFont = try {
            SongFontStyle.valueOf(savedFontName ?: SongFontStyle.MONOSPACE.name)
        } catch (_: Exception) {
            SongFontStyle.MONOSPACE
        }
        _songFontStyle = MutableStateFlow(loadedFont)
        songFontStyle = _songFontStyle.asStateFlow()

        val savedModeName = themePrefs.getString("pref_theme_mode", AppThemeMode.AMOLED_DARK.name)
        val loadedMode = try {
            AppThemeMode.valueOf(savedModeName ?: AppThemeMode.AMOLED_DARK.name)
        } catch (_: Exception) {
            AppThemeMode.AMOLED_DARK
        }
        _themeMode = MutableStateFlow(loadedMode)
        themeMode = _themeMode.asStateFlow()

        val loadedColors = CustomStageColors(
            canvasBackgroundHex = themePrefs.getString("pref_custom_bg", "#002B36") ?: "#002B36",
            chordAccentHex = themePrefs.getString("pref_custom_chord", "#B58900") ?: "#B58900",
            textPrimaryHex = themePrefs.getString("pref_custom_text", "#EEE8D5") ?: "#EEE8D5",
            sectionHeaderHex = themePrefs.getString("pref_custom_header", "#8B5CF6") ?: "#8B5CF6"
        )
        _customStageColors = MutableStateFlow(loadedColors)
        customStageColors = _customStageColors.asStateFlow()

        viewModelScope.launch {
            bandSyncManager.incomingMessages.collect { msg ->
                handleIncomingSyncMessage(msg)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            chordRepository.preloadIfNeeded(application)
        }
    }


    /**
     * Loads a song from Storage Access Framework (SAF) URI and
     * automatically saves/updates it in the local Room database.
     */
    fun loadSongFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _activeHomeTab.value = HomeTab.SONGBOOK
            _uiState.value = SongViewerState.Loading
            _isAutoScrolling.value = false

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = queryFileName(context, uri) ?: "Selected Song"
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    } ?: throw IllegalStateException("Could not read file from storage.")

                    val defaultTitle = fileName.substringBeforeLast(".")
                    val parsed = SongParser.parse(content, defaultTitle = defaultTitle)

                    // Auto-save into Room DB
                    val entityId = repository.saveOrUpdateSong(parsed, content, transposeOffset = 0)
                    val savedEntity = repository.getSongById(entityId)

                    SongViewerState.Loaded(
                        song = parsed,
                        originalSong = parsed,
                        fileName = fileName,
                        songEntityId = entityId,
                        isFavorite = savedEntity?.isFavorite ?: false,
                        transposeOffset = 0,
                        rawContent = content,
                        tags = ""
                    )
                }
            }

            result.fold(
                onSuccess = { _uiState.value = it },
                onFailure = { _uiState.value = SongViewerState.Error(it.localizedMessage ?: "Failed to open file.") }
            )
        }
    }

    /**
     * Loads a song directly from Room DB entity.
     */
    fun loadSongFromEntity(entity: SongEntity) {
        viewModelScope.launch {
            _uiState.value = SongViewerState.Loading
            _isAutoScrolling.value = false

            withContext(Dispatchers.IO) {
                repository.saveOrUpdateSong(
                    SongParser.parse(entity.rawContent, entity.title),
                    entity.rawContent,
                    entity.transposeOffset
                )
            }

            val parsedOriginal = SongParser.parse(entity.rawContent, defaultTitle = entity.title)
            val transposed = if (entity.transposeOffset != 0) {
                TransposeEngine.transposeSong(parsedOriginal, entity.transposeOffset)
            } else {
                parsedOriginal
            }

            _uiState.value = SongViewerState.Loaded(
                song = transposed,
                originalSong = parsedOriginal,
                fileName = entity.title,
                songEntityId = entity.id,
                isFavorite = entity.isFavorite,
                transposeOffset = entity.transposeOffset,
                rawContent = entity.rawContent,
                tags = entity.tags
            )
            broadcastSongIfHost(transposed, entity.rawContent, entity.id)
        }
    }

    /**
     * Opens a song within a Setlist context (Gig Performance Mode).
     */
    fun openSongFromSetlist(setlist: SetlistEntity, songs: List<SongEntity>, index: Int) {
        if (index !in songs.indices) return
        val entity = songs[index]
        viewModelScope.launch {
            _activeHomeTab.value = HomeTab.SETLISTS
            _expandedSetlistIds.value = _expandedSetlistIds.value + setlist.id
            _uiState.value = SongViewerState.Loading
            _isAutoScrolling.value = false

            val parsedOriginal = SongParser.parse(entity.rawContent, defaultTitle = entity.title)
            val transposed = if (entity.transposeOffset != 0) {
                TransposeEngine.transposeSong(parsedOriginal, entity.transposeOffset)
            } else {
                parsedOriginal
            }

            withContext(Dispatchers.IO) {
                repository.updateLastOpened(entity.id)
            }

            val sessionOffsets = songs.associate { it.id to it.transposeOffset }

            _uiState.value = SongViewerState.Loaded(
                song = transposed,
                originalSong = parsedOriginal,
                fileName = entity.title,
                songEntityId = entity.id,
                isFavorite = entity.isFavorite,
                transposeOffset = entity.transposeOffset,
                rawContent = entity.rawContent,
                tags = entity.tags,
                setlistId = setlist.id,
                setlistName = setlist.name,
                setlistSongs = songs,
                currentSetlistIndex = index,
                setlistTransposeOffsets = sessionOffsets
            )
            broadcastSongIfHost(transposed, entity.rawContent, entity.id, setlistIndex = index)
            broadcastSongChangeIfHost(
                songId = entity.id,
                setlistIndex = index,
                song = transposed,
                rawContent = entity.rawContent
            )
        }
    }


    /**
     * Gig Mode: Switch to any specific song index in the active setlist (for HorizontalPager swipes or direct navigation).
     */
    fun goToSetlistSong(targetIndex: Int) {
        val current = _uiState.value as? SongViewerState.Loaded ?: return
        if (targetIndex !in current.setlistSongs.indices) return
        if (targetIndex == current.currentSetlistIndex) return

        val targetSongEntity = current.setlistSongs[targetIndex]
        val targetOffset = current.setlistTransposeOffsets[targetSongEntity.id] ?: targetSongEntity.transposeOffset

        viewModelScope.launch {
            _isAutoScrolling.value = false
            withContext(Dispatchers.IO) {
                repository.updateLastOpened(targetSongEntity.id)
            }
            val parsedOriginal = SongParser.parse(targetSongEntity.rawContent, defaultTitle = targetSongEntity.title)
            val transposed = if (targetOffset != 0) {
                TransposeEngine.transposeSong(parsedOriginal, targetOffset)
            } else {
                parsedOriginal
            }

            _uiState.value = current.copy(
                song = transposed,
                originalSong = parsedOriginal,
                fileName = targetSongEntity.title,
                songEntityId = targetSongEntity.id,
                isFavorite = targetSongEntity.isFavorite,
                transposeOffset = targetOffset,
                currentSetlistIndex = targetIndex
            )

            // Live-Stage Band Sync: broadcast song change to connected band members
            broadcastSongChangeIfHost(
                songId = targetSongEntity.id,
                setlistIndex = targetIndex,
                song = transposed,
                rawContent = targetSongEntity.rawContent
            )
        }
    }

    /**
     * Gig Mode: Seamlessly jump to the next song in the active setlist.
     */
    fun goToNextSetlistSong() {
        val current = _uiState.value as? SongViewerState.Loaded ?: return
        if (current.hasNextSong) {
            goToSetlistSong(current.currentSetlistIndex + 1)
        }
    }

    /**
     * Gig Mode: Jump to the previous song in the active setlist.
     */
    fun goToPreviousSetlistSong() {
        val current = _uiState.value as? SongViewerState.Loaded ?: return
        if (current.hasPreviousSong) {
            goToSetlistSong(current.currentSetlistIndex - 1)
        }
    }

    fun loadSampleSong(useChordPro: Boolean = false) {
        viewModelScope.launch {
            _activeHomeTab.value = HomeTab.SONGBOOK
            _uiState.value = SongViewerState.Loading
            _isAutoScrolling.value = false

            val raw = if (useChordPro) SongParser.SAMPLE_SONG_CHORDPRO else SongParser.SAMPLE_SONG_TWO_LINE
            val defaultTitle = if (useChordPro) "Stand By Me" else "Ang Huling El Bimbo"
            val parsed = SongParser.parse(raw, defaultTitle = defaultTitle)

            val entityId = repository.saveOrUpdateSong(parsed, raw, transposeOffset = 0)

            _uiState.value = SongViewerState.Loaded(
                song = parsed,
                originalSong = parsed,
                fileName = if (useChordPro) "Stand By Me (ChordPro)" else "Ang Huling El Bimbo (Tabs)",
                songEntityId = entityId,
                isFavorite = false,
                transposeOffset = 0
            )
        }
    }

    /**
     * Real-time Transpose Engine controller:
     * Steps semitones up (+1) or down (-1) and instantly updates viewer.
     */
    fun transposeBy(delta: Int) {
        val current = _uiState.value as? SongViewerState.Loaded ?: return
        transposeTo(current.transposeOffset + delta)
    }

    fun transposeTo(targetOffset: Int) {
        val current = _uiState.value as? SongViewerState.Loaded ?: return
        val clamped = targetOffset.coerceIn(-11, 11)
        val transposedSong = if (clamped == 0) {
            current.originalSong
        } else {
            TransposeEngine.transposeSong(current.originalSong, clamped)
        }

        val updatedSetlistSongs = if (current.isInSetlistMode) {
            current.setlistSongs.mapIndexed { idx, songEntity ->
                if (idx == current.currentSetlistIndex || (current.songEntityId != null && songEntity.id == current.songEntityId)) {
                    songEntity.copy(transposeOffset = clamped)
                } else {
                    songEntity
                }
            }
        } else {
            current.setlistSongs
        }

        val updatedOffsets = if (current.songEntityId != null) {
            current.setlistTransposeOffsets + (current.songEntityId to clamped)
        } else {
            current.setlistTransposeOffsets
        }

        _uiState.value = current.copy(
            song = transposedSong,
            transposeOffset = clamped,
            setlistSongs = updatedSetlistSongs,
            setlistTransposeOffsets = updatedOffsets
        )

        current.songEntityId?.let { id ->
            viewModelScope.launch {
                repository.updateTransposeOffset(id, clamped)
            }
        }
    }

    fun resetTranspose() {
        transposeTo(0)
    }

    fun toggleFavorite() {
        val current = _uiState.value as? SongViewerState.Loaded ?: return
        val newStatus = !current.isFavorite
        _uiState.value = current.copy(isFavorite = newStatus)

        current.songEntityId?.let { id ->
            viewModelScope.launch {
                repository.toggleFavorite(id, newStatus)
            }
        }
    }

    fun toggleFavoriteForSong(songId: Long, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(songId, !currentStatus)
        }
    }

    fun deleteSong(entity: SongEntity) {
        softDeleteSong(entity.id)
    }

    fun softDeleteSong(id: Long) {
        viewModelScope.launch {
            repository.softDeleteSong(id)
            val current = _uiState.value as? SongViewerState.Loaded
            if (current?.songEntityId == id) {
                clearSong()
            }
        }
    }

    fun restoreSong(id: Long) {
        viewModelScope.launch {
            repository.restoreSong(id)
        }
    }

    fun permanentDeleteSong(id: Long) {
        viewModelScope.launch {
            repository.permanentDeleteSong(id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun updateSongDetails(
        id: Long,
        title: String,
        artist: String?,
        tags: String,
        rawContent: String,
        key: String?,
        capo: String?
    ) {
        viewModelScope.launch {
            repository.updateSongDetails(id, title, artist, tags, rawContent, key, capo)
            val current = _uiState.value as? SongViewerState.Loaded
            if (current != null && current.songEntityId == id) {
                val parsedOriginal = SongParser.parse(rawContent, defaultTitle = title).let { base ->
                    base.copy(
                        title = title,
                        artist = artist?.takeIf { it.isNotBlank() } ?: base.artist,
                        key = key?.takeIf { it.isNotBlank() } ?: base.key,
                        capo = capo?.takeIf { it.isNotBlank() } ?: base.capo
                    )
                }
                val transposed = if (current.transposeOffset != 0) {
                    TransposeEngine.transposeSong(parsedOriginal, current.transposeOffset)
                } else {
                    parsedOriginal
                }
                _uiState.value = current.copy(
                    song = transposed,
                    originalSong = parsedOriginal,
                    fileName = title,
                    rawContent = rawContent,
                    tags = tags
                )
                broadcastSongIfHost(transposed, rawContent, id)
            }
        }
    }

    fun createSetlist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createSetlist(name.trim())
            }
        }
    }

    fun addSongToSetlist(setlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToSetlist(setlistId, songId)
        }
    }

    fun removeSongFromSetlist(setlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromSetlist(setlistId, songId)
        }
    }

    fun moveSongInSetlist(setlistId: Long, songId: Long, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveSongInSetlist(setlistId, songId, moveUp)
        }
    }

    fun deleteSetlist(setlist: SetlistEntity) {
        viewModelScope.launch {
            repository.deleteSetlist(setlist)
        }
    }

    fun toggleAutoScroll() {
        _isAutoScrolling.value = !_isAutoScrolling.value
    }

    fun stopAutoScroll() {
        _isAutoScrolling.value = false
    }

    fun adjustScrollSpeed(delta: Float) {
        val newSpeed = (_scrollSpeed.value + delta).coerceIn(5f, 200f)
        _scrollSpeed.value = newSpeed
    }

    fun setScrollSpeed(speedDp: Float) {
        _scrollSpeed.value = speedDp.coerceIn(5f, 200f)
    }

    fun adjustSpeedLevel(delta: Int) {
        adjustScrollSpeed((delta * 5).toFloat())
    }

    fun adjustFontSize(delta: Float) {
        _fontSizeSp.value = (_fontSizeSp.value + delta).coerceIn(11f, 26f)
    }

    fun saveThemeSettings(mode: AppThemeMode, colors: CustomStageColors) {
        _customStageColors.value = colors
        _themeMode.value = mode
        themePrefs.edit()
            .putString("pref_theme_mode", mode.name)
            .putString("pref_custom_bg", colors.canvasBackgroundHex)
            .putString("pref_custom_chord", colors.chordAccentHex)
            .putString("pref_custom_text", colors.textPrimaryHex)
            .putString("pref_custom_header", colors.sectionHeaderHex)
            .apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        themePrefs.edit().putString("pref_theme_mode", mode.name).apply()
    }

    fun setCustomStageColors(colors: CustomStageColors) {
        saveThemeSettings(AppThemeMode.CUSTOM_STAGE, colors)
    }

    fun resetCustomStageColors() {
        val defaultColors = CustomStageColors(
            canvasBackgroundHex = "#002B36",
            chordAccentHex = "#B58900",
            textPrimaryHex = "#EEE8D5",
            sectionHeaderHex = "#8B5CF6"
        )
        saveThemeSettings(AppThemeMode.AMOLED_DARK, defaultColors)
    }

    fun cycleThemeMode() {
        val nextMode = when (_themeMode.value) {
            AppThemeMode.AMOLED_DARK -> AppThemeMode.PAPER_LIGHT
            AppThemeMode.PAPER_LIGHT -> AppThemeMode.AMOLED_CYAN
            AppThemeMode.AMOLED_CYAN -> AppThemeMode.CUSTOM_STAGE
            AppThemeMode.CUSTOM_STAGE -> AppThemeMode.AMOLED_DARK
        }
        setThemeMode(nextMode)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        stageSettingsPrefs.edit().putBoolean("pref_keep_screen_on", enabled).apply()
    }

    fun setSongFontStyle(fontStyle: SongFontStyle) {
        _songFontStyle.value = fontStyle
        stageSettingsPrefs.edit().putString("pref_song_font_style", fontStyle.name).apply()
    }

    fun clearSong() {
        _isAutoScrolling.value = false
        _uiState.value = SongViewerState.Empty
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = it.getString(nameIdx)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private val _bulkImportState = MutableStateFlow(BulkImportState())
    val bulkImportState: StateFlow<BulkImportState> = _bulkImportState.asStateFlow()

    fun dismissBulkImportDialog() {
        _bulkImportState.value = BulkImportState()
    }

    /**
     * Traverses a folder selected via SAF ACTION_OPEN_DOCUMENT_TREE,
     * reads all chord/lyrics files (.txt, .chordpro, .chopro, .cho, .crd, .chordtxt),
     * parses them, and batch inserts into Room DB with real-time progress updates.
     */
    fun importFolderUri(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore if permission already granted or restricted
            }

            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            _bulkImportState.value = BulkImportState(
                isImporting = true,
                current = 0,
                total = 0,
                currentSongTitle = "Scanning directory for chord sheets..."
            )

            val validExtensions = setOf("txt", "chordpro", "chopro", "cho", "crd", "chordtxt")
            val matchingFiles = mutableListOf<DocumentFile>()

            fun scanDirectory(dir: DocumentFile) {
                val files = dir.listFiles()
                for (file in files) {
                    if (file.isDirectory) {
                        scanDirectory(file)
                    } else if (file.isFile) {
                        val ext = file.name?.substringAfterLast('.', "")?.lowercase() ?: ""
                        if (ext in validExtensions) {
                            matchingFiles.add(file)
                        }
                    }
                }
            }

            scanDirectory(rootDoc)
            val totalFiles = matchingFiles.size
            if (totalFiles == 0) {
                _bulkImportState.value = BulkImportState(
                    isImporting = false,
                    finished = true,
                    total = 0,
                    importedCount = 0
                )
                return@launch
            }

            _bulkImportState.value = BulkImportState(
                isImporting = true,
                current = 0,
                total = totalFiles,
                currentSongTitle = "Beginning batch import..."
            )

            val batchEntities = mutableListOf<SongEntity>()
            var processed = 0

            for (file in matchingFiles) {
                try {
                    val rawContent = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""

                    if (rawContent.isNotBlank()) {
                        val fileName = file.name?.substringBeforeLast('.') ?: "Untitled"
                        val parsed = SongParser.parse(rawContent, defaultTitle = fileName)
                        batchEntities.add(
                            SongEntity(
                                title = parsed.title,
                                artist = parsed.artist,
                                key = parsed.key,
                                capo = parsed.capo,
                                rawContent = rawContent,
                                format = parsed.format.name,
                                transposeOffset = 0,
                                createdAt = System.currentTimeMillis(),
                                lastOpenedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Skip unreadable files
                }

                processed++
                if (batchEntities.size >= 50) {
                    repository.insertSongsBatch(batchEntities.toList())
                    batchEntities.clear()
                }

                _bulkImportState.value = BulkImportState(
                    isImporting = true,
                    current = processed,
                    total = totalFiles,
                    currentSongTitle = file.name ?: ""
                )
            }

            if (batchEntities.isNotEmpty()) {
                repository.insertSongsBatch(batchEntities.toList())
                batchEntities.clear()
            }

            _bulkImportState.value = BulkImportState(
                isImporting = false,
                finished = true,
                total = totalFiles,
                importedCount = processed
            )
        }
    }

    /**
     * Web Import Suite: Fetches and scrapes chords from a remote URL.
     */
    fun fetchSongFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _webImportState.value = _webImportState.value.copy(isLoading = true, error = null, pendingSong = null)
            val result = WebScraperEngine.scrapeUrl(url)
            result.fold(
                onSuccess = { song ->
                    _webImportState.value = _webImportState.value.copy(isLoading = false, error = null, pendingSong = song)
                },
                onFailure = { err ->
                    _webImportState.value = _webImportState.value.copy(
                        isLoading = false,
                        error = err.localizedMessage ?: "Failed to import song from URL.",
                        pendingSong = null
                    )
                }
            )
        }
    }

    /**
     * Web Import Suite: Inspects clipboard text.
     * If URL -> triggers scraper. If chord text -> prepares for review dialog.
     */
    fun prepareSongFromClipboard(rawText: String) {
        if (rawText.isBlank()) return
        val trimmed = rawText.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            fetchSongFromUrl(trimmed)
        } else {
            val song = WebScraperEngine.parseFromClipboard(trimmed)
            _webImportState.value = _webImportState.value.copy(isLoading = false, error = null, pendingSong = song)
        }
    }

    /**
     * Web Import Suite: Searches for chord versions across the web.
     */
    fun searchWebSongs(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _webImportState.value = _webImportState.value.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
            return
        }

        viewModelScope.launch {
            _webImportState.value = _webImportState.value.copy(
                searchQuery = trimmed,
                isSearching = true,
                searchError = null
            )
            val result = WebScraperEngine.searchSongs(trimmed)
            result.fold(
                onSuccess = { list ->
                    _webImportState.value = _webImportState.value.copy(
                        isSearching = false,
                        searchResults = list,
                        searchError = if (list.isEmpty()) "No chord versions found for \"$trimmed\"." else null
                    )
                },
                onFailure = { err ->
                    _webImportState.value = _webImportState.value.copy(
                        isSearching = false,
                        searchResults = emptyList(),
                        searchError = err.localizedMessage ?: "Failed to search web."
                    )
                }
            )
        }
    }

    /**
     * Web Import Suite: Selects an online search result version and fetches the chord content.
     */
    fun selectSearchResult(result: WebSearchResult) {
        fetchSongFromUrl(result.tabUrl)
    }

    /**
     * Updates custom tags for a song in Room DB.
     */
    fun updateSongTags(songId: Long, tags: String) {
        viewModelScope.launch {
            repository.updateSongTags(songId, tags)
        }
    }

    /**
     * Closes the Pre-Save Review Dialog and clears pending import state.
     */
    fun dismissWebImportReview() {
        _webImportState.value = _webImportState.value.copy(pendingSong = null, isLoading = false, error = null)
    }

    /**
     * Web Import Suite: Sets captured or scraped song to trigger the PreSaveSongReviewDialog.
     */
    fun prepareSongFromScraped(scraped: com.joel.gta.data.scraper.ScrapedSong) {
        _webImportState.value = _webImportState.value.copy(
            isLoading = false,
            error = null,
            pendingSong = scraped
        )
    }

    /**
     * Saves reviewed song into local Room DB and optionally loads into viewer.
     */
    fun saveSongFromReview(
        title: String,
        artist: String?,
        rawContent: String,
        key: String?,
        capo: String?,
        tags: String = "",
        openAfterSave: Boolean = true
    ) {
        viewModelScope.launch {
            val defaultTitle = title.ifBlank { "Untitled Song" }
            val parsed = SongParser.parse(rawContent, defaultTitle = defaultTitle).let { base ->
                base.copy(
                    title = defaultTitle,
                    artist = artist?.takeIf { it.isNotBlank() } ?: base.artist,
                    key = key?.takeIf { it.isNotBlank() } ?: base.key,
                    capo = capo?.takeIf { it.isNotBlank() } ?: base.capo
                )
            }

            val entityId = withContext(Dispatchers.IO) {
                repository.saveOrUpdateSong(parsed, rawContent, transposeOffset = 0, tags = tags)
            }

            _webImportState.value = _webImportState.value.copy(pendingSong = null, isLoading = false, error = null)

            if (openAfterSave) {
                val savedEntity = repository.getSongById(entityId)
                _activeHomeTab.value = HomeTab.SONGBOOK
                _uiState.value = SongViewerState.Loaded(
                    song = parsed,
                    originalSong = parsed,
                    fileName = defaultTitle,
                    songEntityId = entityId,
                    isFavorite = savedEntity?.isFavorite ?: false,
                    transposeOffset = 0,
                    rawContent = rawContent,
                    tags = tags
                )
                broadcastSongIfHost(parsed, rawContent, entityId)
            }
        }
    }

    // --- Band Sync (Play Together) Handlers ---

    private fun handleIncomingSyncMessage(msg: SyncMessage) {
        when (msg) {
            is SyncMessage.SongChange -> {
                handleSongChangeMessage(
                    songId = msg.songId,
                    setlistIndex = msg.setlistIndex,
                    title = msg.title,
                    artist = msg.artist,
                    rawContent = msg.rawContent,
                    key = msg.key,
                    capo = msg.capo
                )
            }
            is SyncMessage.SongSync -> {
                handleSongChangeMessage(
                    songId = msg.songId,
                    setlistIndex = msg.setlistIndex,
                    title = msg.title,
                    artist = msg.artist,
                    rawContent = msg.rawContent,
                    key = msg.key,
                    capo = msg.capo
                )
            }
            is SyncMessage.ScrollSync -> {
                // Band Member mode: Auto-scroll according to Leader's position
                bandScrollOffset.tryEmit(msg.scrollFraction)
            }
            is SyncMessage.TempoSync -> {
                // Tempo sync
            }
            else -> {}
        }
    }

    private fun handleSongChangeMessage(
        songId: Long?,
        setlistIndex: Int?,
        title: String,
        artist: String?,
        rawContent: String,
        key: String?,
        capo: String?
    ) {
        viewModelScope.launch {
            _isAutoScrolling.value = false
            bandScrollOffset.tryEmit(0f)

            val current = _uiState.value
            // Scenario 1: Client is already in Setlist Mode
            if (current is SongViewerState.Loaded && current.isInSetlistMode && current.setlistSongs.isNotEmpty()) {
                val targetIndex = when {
                    songId != null && current.setlistSongs.any { it.id == songId } -> {
                        current.setlistSongs.indexOfFirst { it.id == songId }
                    }
                    setlistIndex != null && setlistIndex in current.setlistSongs.indices -> {
                        setlistIndex
                    }
                    else -> -1
                }

                if (targetIndex in current.setlistSongs.indices) {
                    val targetSongEntity = current.setlistSongs[targetIndex]
                    withContext(Dispatchers.IO) {
                        repository.updateLastOpened(targetSongEntity.id)
                    }
                    val targetOffset = current.setlistTransposeOffsets[targetSongEntity.id] ?: targetSongEntity.transposeOffset
                    val parsedOriginal = SongParser.parse(targetSongEntity.rawContent, defaultTitle = targetSongEntity.title)
                    val transposed = if (targetOffset != 0) {
                        TransposeEngine.transposeSong(parsedOriginal, targetOffset)
                    } else {
                        parsedOriginal
                    }

                    _uiState.value = current.copy(
                        song = transposed,
                        originalSong = parsedOriginal,
                        fileName = targetSongEntity.title,
                        songEntityId = targetSongEntity.id,
                        isFavorite = targetSongEntity.isFavorite,
                        transposeOffset = targetOffset,
                        currentSetlistIndex = targetIndex
                    )
                    return@launch
                }
            }

            // Scenario 2: Fallback for standalone / client not in active setlist mode
            if (rawContent.isNotBlank() || title.isNotBlank()) {
                val defaultTitle = title.ifBlank { "Synced Song" }
                val parsed = SongParser.parse(rawContent, defaultTitle = defaultTitle).let { base ->
                    base.copy(
                        title = defaultTitle,
                        artist = artist ?: base.artist,
                        key = key ?: base.key,
                        capo = capo ?: base.capo
                    )
                }

                _uiState.value = SongViewerState.Loaded(
                    song = parsed,
                    originalSong = parsed,
                    fileName = defaultTitle,
                    songEntityId = songId ?: 0L,
                    isFavorite = false,
                    transposeOffset = 0,
                    rawContent = rawContent,
                    tags = "",
                    setlistId = (current as? SongViewerState.Loaded)?.setlistId,
                    setlistName = (current as? SongViewerState.Loaded)?.setlistName,
                    setlistSongs = (current as? SongViewerState.Loaded)?.setlistSongs ?: emptyList(),
                    currentSetlistIndex = setlistIndex ?: (current as? SongViewerState.Loaded)?.currentSetlistIndex ?: -1
                )
            }
        }
    }

    fun broadcastSongIfHost(
        song: com.joel.gta.data.model.ParsedSong,
        rawContent: String,
        songId: Long? = null,
        setlistIndex: Int? = null
    ) {
        if (bandSyncState.value.role == BandSyncRole.HOST) {
            bandSyncManager.broadcast(
                SyncMessage.SongSync(
                    title = song.title,
                    artist = song.artist,
                    rawContent = rawContent,
                    key = song.key,
                    capo = song.capo,
                    songId = songId,
                    setlistIndex = setlistIndex
                )
            )
        }
    }

    fun broadcastSongChangeIfHost(
        songId: Long?,
        setlistIndex: Int,
        song: com.joel.gta.data.model.ParsedSong? = null,
        rawContent: String? = null
    ) {
        if (bandSyncState.value.role == BandSyncRole.HOST) {
            bandSyncManager.broadcast(
                SyncMessage.SongChange(
                    songId = songId,
                    setlistIndex = setlistIndex,
                    title = song?.title ?: "",
                    artist = song?.artist,
                    rawContent = rawContent ?: "",
                    key = song?.key,
                    capo = song?.capo
                )
            )
        }
    }

    fun broadcastScrollIfHost(scrollFraction: Float) {
        if (bandSyncState.value.role == BandSyncRole.HOST) {
            bandSyncManager.broadcast(SyncMessage.ScrollSync(scrollFraction))
        }
    }

    fun startBandHost(port: Int = 8765) {
        bandSyncManager.startHost(port)
    }

    fun startBandClient() {
        bandSyncManager.startClient()
    }

    fun connectToBandHost(ip: String, port: Int = 8765) {
        bandSyncManager.connectToHost(ip, port)
    }

    fun stopBandSync() {
        bandSyncManager.stopAll()
    }

    fun exportBackup(context: Context, onShareReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val jsonString = repository.createBackupPayload(BuildConfig.VERSION_NAME)
            val shareIntent = BackupManager.createShareIntent(context, jsonString)
            onShareReady(shareIntent)
        }
    }

    fun exportBackupToSaf(context: Context, destinationUri: Uri, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = repository.createBackupPayload(BuildConfig.VERSION_NAME)
                val success = BackupManager.writeToSafUri(context, destinationUri, jsonString)
                if (success) {
                    onSuccess("Backup saved successfully!")
                } else {
                    onError("Failed to save backup file.")
                }
            } catch (e: Exception) {
                onError("Export error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri, onResult: (RestoreSummary) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("Cannot open backup file")

                val summary = repository.restoreBackup(jsonString)
                onResult(summary)
            } catch (e: Exception) {
                onError("Restore error: ${e.localizedMessage ?: "Invalid JSON backup"}")
            }
        }
    }

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    fun checkForUpdates(
        onUpToDate: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isCheckingUpdates.value) return
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            try {
                val result = UpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                _updateCheckResult.value = result
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        onUpToDate("GTAR is up to date (v${BuildConfig.VERSION_NAME})!")
                    }
                    is UpdateCheckResult.Error -> {
                        onError(result.message)
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        // Will show dialog via updateCheckResult state
                    }
                }
            } catch (e: Exception) {
                onError("Unable to check updates. Check your connection.")
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateCheckResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        bandSyncManager.stopAll()
    }
}

