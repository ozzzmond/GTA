package com.joel.gta.ui.viewmodel

import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.model.ParsedSong

sealed class SongViewerState {
    object Empty : SongViewerState()
    object Loading : SongViewerState()
    data class Loaded(
        val song: ParsedSong,
        val originalSong: ParsedSong,
        val fileName: String? = null,
        val songEntityId: Long? = null,
        val isFavorite: Boolean = false,
        val transposeOffset: Int = 0,
        val rawContent: String = "",
        val tags: String = "",
        val setlistId: Long? = null,
        val setlistName: String? = null,
        val setlistSongs: List<SongEntity> = emptyList(),
        val currentSetlistIndex: Int = -1
    ) : SongViewerState() {
        val isInSetlistMode: Boolean get() = setlistId != null && setlistSongs.isNotEmpty() && currentSetlistIndex >= 0
        val hasPreviousSong: Boolean get() = isInSetlistMode && currentSetlistIndex > 0
        val hasNextSong: Boolean get() = isInSetlistMode && currentSetlistIndex < setlistSongs.size - 1
        val setlistProgressText: String? get() = if (isInSetlistMode) "Track ${currentSetlistIndex + 1} of ${setlistSongs.size}" else null
    }
    data class Error(val message: String) : SongViewerState()
}
