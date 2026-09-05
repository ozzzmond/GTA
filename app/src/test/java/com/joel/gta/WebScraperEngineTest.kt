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
}
