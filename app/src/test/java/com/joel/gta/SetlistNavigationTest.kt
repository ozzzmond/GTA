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
}
