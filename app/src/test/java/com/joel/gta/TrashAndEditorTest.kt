package com.joel.gta

import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.parser.SongParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashAndEditorTest {

    @Test
    fun testSongEntityDefaultIsNotDeleted() {
        val song = SongEntity(
            id = 1,
            title = "With or Without You",
            artist = "U2",
            rawContent = "[D] With or [A] without you",
            createdAt = System.currentTimeMillis()
        )
        assertFalse(song.isDeleted)
    }

    @Test
    fun testSongEntitySoftDeleteFiltering() {
        val song1 = SongEntity(
            id = 1,
            title = "Song Active 1",
            artist = "Artist A",
            rawContent = "",
            isDeleted = false
        )
        val song2 = SongEntity(
            id = 2,
            title = "Song In Trash",
            artist = "Artist B",
            rawContent = "",
            isDeleted = true
        )
        val song3 = SongEntity(
            id = 3,
            title = "Song Active 2",
            artist = "Artist C",
            rawContent = "",
            isDeleted = false
        )

        val allSongs = listOf(song1, song2, song3)
        val activeSongs = allSongs.filter { !it.isDeleted }
        val trashSongs = allSongs.filter { it.isDeleted }

        assertEquals(2, activeSongs.size)
        assertEquals(listOf(1L, 3L), activeSongs.map { it.id })

        assertEquals(1, trashSongs.size)
        assertEquals(2L, trashSongs.first().id)
    }

    @Test
    fun testRestoreFromTrash() {
        var song = SongEntity(
            id = 10,
            title = "Noypi",
            artist = "Bamboo",
            rawContent = "Tingnan mo ang iyong palad",
            isDeleted = true
        )
        assertTrue(song.isDeleted)

        // Simulate restore
        song = song.copy(isDeleted = false)
        assertFalse(song.isDeleted)
    }

    @Test
    fun testSongbookEditorUpdateDetails() {
        val originalRaw = "[G] Original line [C]"
        val originalSong = SongEntity(
            id = 5,
            title = "Original Title",
            artist = "Old Artist",
            tags = "OPM",
            rawContent = originalRaw,
            key = "G",
            capo = "None"
        )

        // User edits in Songbook Editor
        val updatedTitle = "New Title"
        val updatedArtist = "New Artist"
        val updatedTags = "OPM, Acoustic, Rock"
        val updatedRaw = "{title: New Title}\n[D] New chord line [A]"
        val updatedKey = "D"
        val updatedCapo = "Fret 2"

        val editedEntity = originalSong.copy(
            title = updatedTitle,
            artist = updatedArtist,
            tags = updatedTags,
            rawContent = updatedRaw,
            key = updatedKey,
            capo = updatedCapo
        )

        assertEquals("New Title", editedEntity.title)
        assertEquals("New Artist", editedEntity.artist)
        assertEquals(listOf("OPM", "Acoustic", "Rock"), editedEntity.getTagsList())
        assertEquals("D", editedEntity.key)
        assertEquals("Fret 2", editedEntity.capo)

        // Parse edited raw content
        val parsed = SongParser.parse(editedEntity.rawContent, defaultTitle = editedEntity.title)
        assertEquals("New Title", parsed.title)
        assertTrue(parsed.lines.isNotEmpty())
    }
}
