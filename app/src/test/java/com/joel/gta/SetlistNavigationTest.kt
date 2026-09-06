package com.joel.gta

import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.data.model.SongFormat
import com.joel.gta.ui.viewmodel.SongViewerState
import org.junit.Assert.*
import org.junit.Test

class SetlistNavigationTest {

    private fun createDummySong(id: Long, title: String): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            rawContent = "C G\nSample"
        )
    }

    private val dummyParsedSong = ParsedSong(
        title = "Test Song",
        format = SongFormat.TWO_LINE,
        lines = emptyList()
    )

    @Test
    fun testSetlistNavigationFirstSong() {
        val songs = listOf(
            createDummySong(1, "Song 1"),
            createDummySong(2, "Song 2"),
            createDummySong(3, "Song 3")
        )

        val state = SongViewerState.Loaded(
            song = dummyParsedSong,
            originalSong = dummyParsedSong,
            setlistId = 100L,
            setlistName = "Gig Night",
            setlistSongs = songs,
            currentSetlistIndex = 0
        )

        assertTrue(state.isInSetlistMode)
        assertFalse("First song should not have previous song", state.hasPreviousSong)
        assertTrue("First song should have next song", state.hasNextSong)
        assertEquals("Track 1 of 3", state.setlistProgressText)
    }

    @Test
    fun testSetlistNavigationMiddleSong() {
        val songs = listOf(
            createDummySong(1, "Song 1"),
            createDummySong(2, "Song 2"),
            createDummySong(3, "Song 3")
        )

        val state = SongViewerState.Loaded(
            song = dummyParsedSong,
            originalSong = dummyParsedSong,
            setlistId = 100L,
            setlistName = "Gig Night",
            setlistSongs = songs,
            currentSetlistIndex = 1
        )

        assertTrue(state.isInSetlistMode)
        assertTrue("Middle song should have previous song", state.hasPreviousSong)
        assertTrue("Middle song should have next song", state.hasNextSong)
        assertEquals("Track 2 of 3", state.setlistProgressText)
    }

    @Test
    fun testSetlistNavigationLastSong() {
        val songs = listOf(
            createDummySong(1, "Song 1"),
            createDummySong(2, "Song 2"),
            createDummySong(3, "Song 3")
        )

        val state = SongViewerState.Loaded(
            song = dummyParsedSong,
            originalSong = dummyParsedSong,
            setlistId = 100L,
            setlistName = "Gig Night",
            setlistSongs = songs,
            currentSetlistIndex = 2
        )

        assertTrue(state.isInSetlistMode)
        assertTrue("Last song should have previous song", state.hasPreviousSong)
        assertFalse("Last song should not have next song", state.hasNextSong)
        assertEquals("Track 3 of 3", state.setlistProgressText)
    }

    @Test
    fun testStandaloneSongNotInSetlistMode() {
        val state = SongViewerState.Loaded(
            song = dummyParsedSong,
            originalSong = dummyParsedSong,
            setlistId = null,
            setlistSongs = emptyList(),
            currentSetlistIndex = -1
        )

        assertFalse(state.isInSetlistMode)
        assertFalse(state.hasPreviousSong)
        assertFalse(state.hasNextSong)
        assertNull(state.setlistProgressText)
    }

    @Test
    fun testSetlistTransposePersistenceAcrossNavigation() {
        val song1 = createDummySong(1L, "Song 1")
        val song2 = createDummySong(2L, "Song 2")
        val songs = listOf(song1, song2)

        // Initial setlist session state
        val initialOffsets = songs.associate { it.id to it.transposeOffset }
        val initialState = SongViewerState.Loaded(
            song = dummyParsedSong,
            originalSong = dummyParsedSong,
            songEntityId = song1.id,
            setlistId = 50L,
            setlistName = "Acoustic Night",
            setlistSongs = songs,
            currentSetlistIndex = 0,
            transposeOffset = 0,
            setlistTransposeOffsets = initialOffsets
        )

        assertEquals(0, initialState.transposeOffset)
        assertEquals(0, initialState.setlistTransposeOffsets[1L])
        assertEquals(0, initialState.setlistTransposeOffsets[2L])

        // User transposes Song 1 to +3 semitones
        val transposedOffset = 3
        val updatedSongs = initialState.setlistSongs.mapIndexed { idx, s ->
            if (idx == 0) s.copy(transposeOffset = transposedOffset) else s
        }
        val updatedOffsets = initialState.setlistTransposeOffsets + (1L to transposedOffset)
        val stateAfterTranspose = initialState.copy(
            transposeOffset = transposedOffset,
            setlistSongs = updatedSongs,
            setlistTransposeOffsets = updatedOffsets
        )

        assertEquals(3, stateAfterTranspose.transposeOffset)
        assertEquals(3, stateAfterTranspose.setlistTransposeOffsets[1L])
        assertEquals(3, stateAfterTranspose.setlistSongs[0].transposeOffset)

        // User navigates to Song 2 (targetIndex = 1)
        val targetSong2 = stateAfterTranspose.setlistSongs[1]
        val song2ActiveOffset = stateAfterTranspose.setlistTransposeOffsets[targetSong2.id] ?: targetSong2.transposeOffset
        val stateAtSong2 = stateAfterTranspose.copy(
            songEntityId = targetSong2.id,
            transposeOffset = song2ActiveOffset,
            currentSetlistIndex = 1
        )

        assertEquals(0, stateAtSong2.transposeOffset)
        assertEquals(1, stateAtSong2.currentSetlistIndex)

        // User navigates BACK to Song 1 (targetIndex = 0)
        val targetSong1 = stateAtSong2.setlistSongs[0]
        val song1ActiveOffset = stateAtSong2.setlistTransposeOffsets[targetSong1.id] ?: targetSong1.transposeOffset
        val stateBackAtSong1 = stateAtSong2.copy(
            songEntityId = targetSong1.id,
            transposeOffset = song1ActiveOffset,
            currentSetlistIndex = 0
        )

        // Verify Song 1 preserved its transposed key (+3) instead of resetting to 0
        assertEquals(3, stateBackAtSong1.transposeOffset)
        assertEquals(3, stateBackAtSong1.setlistTransposeOffsets[1L])
        assertEquals(3, stateBackAtSong1.setlistSongs[0].transposeOffset)
        assertEquals(0, stateBackAtSong1.currentSetlistIndex)
    }
}
