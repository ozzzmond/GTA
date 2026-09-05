package com.joel.gta

import com.joel.gta.data.scraper.WebScraperEngine
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class WebScraperEngineTest {

    @Test
    fun testNormalizeUrl() {
        assertEquals("https://tabs.ultimate-guitar.com/tab/123", WebScraperEngine.normalizeUrl("tabs.ultimate-guitar.com/tab/123"))
        assertEquals("https://chordie.com/song", WebScraperEngine.normalizeUrl("https://chordie.com/song"))
        assertEquals("http://example.com/chords", WebScraperEngine.normalizeUrl("http://example.com/chords"))
    }

    @Test
    fun testCleanWebPageTitle() {
        val ugTitle = "Ang Huling El Bimbo Chords by Eraserheads | Ultimate-Guitar.Com"
        assertEquals("Ang Huling El Bimbo Chords by Eraserheads", WebScraperEngine.cleanWebPageTitle(ugTitle))

        val chordieTitle = "Stand By Me by Ben E. King @ Chordie"
        assertEquals("Stand By Me by Ben E. King", WebScraperEngine.cleanWebPageTitle(chordieTitle))

        val eChordsTitle = "Creep Chords by Radiohead | E-Chords.com"
        assertEquals("Creep Chords by Radiohead", WebScraperEngine.cleanWebPageTitle(eChordsTitle))
    }

    @Test
    fun testSplitTitleAndArtist() {
        val (t1, a1) = WebScraperEngine.splitTitleAndArtist("Ang Huling El Bimbo Chords by Eraserheads")
        assertEquals("Ang Huling El Bimbo", t1)
        assertEquals("Eraserheads", a1)

        val (t2, a2) = WebScraperEngine.splitTitleAndArtist("Stand By Me by Ben E. King")
        assertEquals("Stand By Me", t2)
        assertEquals("Ben E. King", a2)

        val (t3, a3) = WebScraperEngine.splitTitleAndArtist("The Beatles - Let It Be Chords")
        assertEquals("Let It Be", t3)
        assertEquals("The Beatles", a3)
    }

    @Test
    fun testSanitizeUgContent() {
        val raw = "[tab][ch]G[/ch]   [ch]D[/ch]   [ch]Em[/ch]   [ch]C[/ch][/tab]\nKamukha mo si Paraluman"
        val clean = WebScraperEngine.sanitizeUgContent(raw)
        assertEquals("G   D   Em   C\nKamukha mo si Paraluman", clean)
    }

    @Test
    fun testUnescapeJson() {
        val escaped = "G\\r\\nD\\r\\nLine with \\\"quotes\\\""
        val unescaped = WebScraperEngine.unescapeJson(escaped)
        assertEquals("G\nD\nLine with \"quotes\"", unescaped)
    }

    @Test
    fun testParseFromClipboardDirectives() {
        val text = """
            {title: With or Without You}
            {artist: U2}
            {key: D}
            {capo: Fret 2}
            
            [D]See the stone set in your [A]eyes
            [Bm]See the thorn twist in your [G]side
        """.trimIndent()

        val song = WebScraperEngine.parseFromClipboard(text)
        assertEquals("With or Without You", song.title)
        assertEquals("U2", song.artist)
        assertEquals("D", song.key)
        assertEquals("Fret 2", song.capo)
        assertTrue(song.rawContent.contains("[D]See the stone"))
    }

    @Test
    fun testParseFromClipboardChordOverLyric() {
        val text = """
            Hotel California - Eagles
            
            Bm          F#
            On a dark desert highway
            A           E
            Cool wind in my hair
        """.trimIndent()

        val song = WebScraperEngine.parseFromClipboard(text)
        assertEquals("Hotel California", song.title)
        assertEquals("Eagles", song.artist)
        assertTrue(song.rawContent.contains("On a dark desert highway"))
    }

    @Test
    fun testParseUltimateGuitarHtml() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Torete Chords by Moonstar88 | Ultimate-Guitar.Com</title></head>
            <body>
              <div class="js-store" data-content="{&quot;store&quot;:{&quot;page&quot;:{&quot;data&quot;:{&quot;tab&quot;:{&quot;song_name&quot;:&quot;Torete&quot;,&quot;artist_name&quot;:&quot;Moonstar88&quot;},&quot;tab_view&quot;:{&quot;wiki_tab&quot;:{&quot;content&quot;:&quot;[ch]D[/ch]   [ch]F#m[/ch]   [ch]Bm[/ch]   [ch]G[/ch]\r\nSandali na lang&quot;},&quot;meta&quot;:{&quot;capo&quot;:1,&quot;tonality&quot;:&quot;D&quot;}}}}}}"></div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val song = WebScraperEngine.parseUltimateGuitar(doc, "https://tabs.ultimate-guitar.com/tab/moonstar88/torete-chords-123")

        assertEquals("Torete", song.title)
        assertEquals("Moonstar88", song.artist)
        assertEquals("D", song.key)
        assertEquals("Fret 1", song.capo)
        assertEquals("D   F#m   Bm   G\nSandali na lang", song.rawContent)
    }

    @Test
    fun testParseChordieHtml() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Wonderwall by Oasis @ Chordie</title></head>
            <body>
              <pre id="chordpro">
                {title: Wonderwall}
                {artist: Oasis}
                [Em7]Today is [G]gonna be the day
              </pre>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val song = WebScraperEngine.parseChordie(doc, "https://chordie.com/song/wonderwall")

        assertEquals("Wonderwall", song.title)
        assertEquals("Oasis", song.artist)
        assertTrue(song.rawContent.contains("[Em7]Today is [G]gonna be the day"))
    }

    @Test
    fun testParseEChordsHtml() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Yellow Chords by Coldplay | E-Chords.com</title></head>
            <body>
              <pre id="core">
                B                  F#
                Look at the stars, look how they shine for you
              </pre>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val song = WebScraperEngine.parseEChords(doc, "https://e-chords.com/chords/coldplay/yellow")

        assertEquals("Yellow", song.title)
        assertEquals("Coldplay", song.artist)
        assertTrue(song.rawContent.contains("Look at the stars"))
    }

    @Test
    fun testParseGenericHtmlWithAdsCleaned() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <title>Knockin on Heavens Door Chords - Bob Dylan</title>
            </head>
            <body>
              <nav><a href="/">Home</a></nav>
              <div class="ad">Banner Ad Click Here!</div>
              <script>console.log('tracker');</script>
              <pre>
                G        D           Am
                Mama, take this badge off of me
                G        D        C
                I can't use it anymore
              </pre>
              <footer>Copyright 2024</footer>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val song = WebScraperEngine.parseGeneric(doc, "https://random-guitar-blog.net/knockin")

        assertEquals("Knockin on Heavens Door", song.title)
        assertEquals("Bob Dylan", song.artist)
        assertFalse(song.rawContent.contains("Banner Ad"))
        assertFalse(song.rawContent.contains("tracker"))
        assertTrue(song.rawContent.contains("Mama, take this badge off of me"))
    }

    @Test
    fun testSearchSongsFromHtml() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
              <div class="js-store" data-content="{&quot;store&quot;:{&quot;page&quot;:{&quot;data&quot;:{&quot;results&quot;:[{&quot;id&quot;:101,&quot;song_name&quot;:&quot;Ang Huling El Bimbo&quot;,&quot;artist_name&quot;:&quot;Eraserheads&quot;,&quot;type&quot;:&quot;Chords&quot;,&quot;version&quot;:1,&quot;votes&quot;:250,&quot;rating&quot;:4.95,&quot;tab_url&quot;:&quot;https://tabs.ultimate-guitar.com/tab/eraserheads/ang-huling-el-bimbo-chords-101&quot;,&quot;tonality_name&quot;:&quot;G&quot;},{&quot;id&quot;:102,&quot;song_name&quot;:&quot;Ang Huling El Bimbo&quot;,&quot;artist_name&quot;:&quot;Eraserheads&quot;,&quot;type&quot;:&quot;Chords&quot;,&quot;version&quot;:2,&quot;votes&quot;:80,&quot;rating&quot;:4.8,&quot;tab_url&quot;:&quot;https://tabs.ultimate-guitar.com/tab/eraserheads/ang-huling-el-bimbo-chords-102&quot;,&quot;tonality_name&quot;:&quot;A&quot;}]}}}}"></div>
            </body>
            </html>
        """.trimIndent()

        val results = WebScraperEngine.searchSongsFromHtml(html)
        assertEquals(2, results.size)
        assertEquals("Ang Huling El Bimbo", results[0].songName)
        assertEquals("Eraserheads", results[0].artistName)
        assertEquals("Chords", results[0].type)
        assertEquals(1, results[0].version)
        assertEquals(250, results[0].votes)
        assertTrue(kotlin.math.abs(results[0].rating - 4.95) < 0.01)
        assertEquals("G", results[0].tonality)

        assertEquals(2, results[1].version)
        assertEquals("A", results[1].tonality)
    }

    @Test
    fun testBandSyncProtocolSerialization() {
        val songMsg = com.joel.gta.data.sync.SyncMessage.SongSync(
            songId = 42L,
            title = "Torete",
            artist = "Moonstar88",
            key = "D",
            capo = "Fret 2",
            rawContent = "[D]Kakayanin ba ang sarili"
        )
        val json = com.joel.gta.data.sync.SyncMessage.serialize(songMsg)
        val parsed = com.joel.gta.data.sync.SyncMessage.deserialize(json)
        assertTrue(parsed is com.joel.gta.data.sync.SyncMessage.SongSync)
        val casted = parsed as com.joel.gta.data.sync.SyncMessage.SongSync
        assertEquals(42L, casted.songId)
        assertEquals("Torete", casted.title)
        assertEquals("Moonstar88", casted.artist)
        assertEquals("Fret 2", casted.capo)

        val scrollMsg = com.joel.gta.data.sync.SyncMessage.ScrollSync(scrollFraction = 0.45f)
        val scrollJson = com.joel.gta.data.sync.SyncMessage.serialize(scrollMsg)
        val parsedScroll = com.joel.gta.data.sync.SyncMessage.deserialize(scrollJson)
        assertTrue(parsedScroll is com.joel.gta.data.sync.SyncMessage.ScrollSync)
        assertEquals(0.45f, (parsedScroll as com.joel.gta.data.sync.SyncMessage.ScrollSync).scrollFraction, 0.001f)
    }

    @Test
    fun testSongEntityTagsParsing() {
        val song = com.joel.gta.data.local.entity.SongEntity(
            title = "Alapaap",
            artist = "Eraserheads",
            rawContent = "A   E   F#m   D",
            tags = "OPM, 90s Rock, Pinoy"
        )
        val tags = song.getTagsList()
        assertEquals(3, tags.size)
        assertEquals("OPM", tags[0])
        assertEquals("90s Rock", tags[1])
        assertEquals("Pinoy", tags[2])

        assertTrue(song.hasTag("opm"))
        assertTrue(song.hasTag("90s rock"))
        assertFalse(song.hasTag("Acoustic"))
    }

    @Test
    fun testScrapeUrlInputSafety() = kotlinx.coroutines.runBlocking {
        // Plain text should safely fail without throwing unhandled URISyntaxException
        val plainTextResult = WebScraperEngine.scrapeUrl("Kamukha mo si Paraluman")
        assertTrue(plainTextResult.isFailure)
        val msg = plainTextResult.exceptionOrNull()?.message ?: ""
        assertTrue(msg.contains("not a valid web URL") || msg.contains("Song Search Bar"))

        // Blank input should safely fail
        val blankResult = WebScraperEngine.scrapeUrl("   ")
        assertTrue(blankResult.isFailure)
    }

    @Test
    fun testParseOpmTunes() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Harana Chords - Parokya ni Edgar | OPMTunes.com</title></head>
            <body>
              <pre>
                G                 C
                Uso pa ba ang harana?
                G                 C
                Marahil ikaw ay nagtataka
              </pre>
            </body>
            </html>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(html)
        val scraped = WebScraperEngine.parseOpmTunes(doc, "https://www.opmtunes.com/songs/parokya-ni-edgar/harana")
        assertEquals("Harana", scraped.title)
        assertEquals("Parokya ni Edgar", scraped.artist)
        assertTrue(scraped.rawContent.contains("Uso pa ba ang harana"))
    }
}
