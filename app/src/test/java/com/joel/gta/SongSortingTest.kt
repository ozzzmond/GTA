package com.joel.gta

import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSortingTest {

    private val songA = SongEntity(
        id = 1,
        title = "Ang Huling El Bimbo",
        artist = "Eraserheads",
        rawContent = "",
        createdAt = 1000L,
        lastOpenedAt = 5000L
    )

    private val songB = SongEntity(
        id = 2,
        title = "Stand By Me",
        artist = "Ben E. King",
        rawContent = "",
        createdAt = 2000L,
        lastOpenedAt = 1000L
    )

    private val songC = SongEntity(
        id = 3,
        title = "214",
        artist = "Rivermaya",
        rawContent = "",
        createdAt = 3000L,
        lastOpenedAt = 9000L
    )

    private val allSongs = listOf(songA, songB, songC)

    @Test
    fun testSortTitleAscending() {
        val sorted = allSongs.sortedBy { it.title.lowercase() }
        assertEquals("214", sorted[0].title)
        assertEquals("Ang Huling El Bimbo", sorted[1].title)
        assertEquals("Stand By Me", sorted[2].title)
    }

    @Test
    fun testSortTitleDescending() {
        val sorted = allSongs.sortedByDescending { it.title.lowercase() }
        assertEquals("Stand By Me", sorted[0].title)
        assertEquals("Ang Huling El Bimbo", sorted[1].title)
        assertEquals("214", sorted[2].title)
    }

    @Test
    fun testSortRecentlyPlayed() {
        val sorted = allSongs.sortedByDescending { it.lastOpenedAt }
        assertEquals("214", sorted[0].title) // 9000L
        assertEquals("Ang Huling El Bimbo", sorted[1].title) // 5000L
        assertEquals("Stand By Me", sorted[2].title) // 1000L
    }

    @Test
    fun testSortDateAddedNewest() {
        val sorted = allSongs.sortedByDescending { it.createdAt }
        assertEquals("214", sorted[0].title) // 3000L
        assertEquals("Stand By Me", sorted[1].title) // 2000L
        assertEquals("Ang Huling El Bimbo", sorted[2].title) // 1000L
    }

    @Test
    fun testSortDateAddedOldest() {
        val sorted = allSongs.sortedBy { it.createdAt }
        assertEquals("Ang Huling El Bimbo", sorted[0].title) // 1000L
        assertEquals("Stand By Me", sorted[1].title) // 2000L
        assertEquals("214", sorted[2].title) // 3000L
    }

    @Test
    fun testSetlistSorting() {
        val setlist1 = SetlistWithSongs(
            setlist = SetlistEntity(id = 1, name = "Bar Gig Friday", createdAt = 1000L),
            songs = listOf(songA, songB)
        )
        val setlist2 = SetlistWithSongs(
            setlist = SetlistEntity(id = 2, name = "Acoustic Sunday", createdAt = 3000L),
            songs = listOf(songC)
        )
        val setlists = listOf(setlist1, setlist2)

        val byNameAsc = setlists.sortedBy { it.setlist.name.lowercase() }
        assertEquals("Acoustic Sunday", byNameAsc[0].setlist.name)
        assertEquals("Bar Gig Friday", byNameAsc[1].setlist.name)

        val byNewest = setlists.sortedByDescending { it.setlist.createdAt }
        assertEquals("Acoustic Sunday", byNewest[0].setlist.name)
        assertEquals("Bar Gig Friday", byNewest[1].setlist.name)

        val bySongCount = setlists.sortedByDescending { it.songs.size }
        assertEquals("Bar Gig Friday", bySongCount[0].setlist.name) // 2 songs
        assertEquals("Acoustic Sunday", bySongCount[1].setlist.name) // 1 song
    }
}
