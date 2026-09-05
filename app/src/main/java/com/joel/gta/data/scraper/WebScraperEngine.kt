package com.joel.gta.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

data class ScrapedSong(
    val title: String,
    val artist: String?,
    val rawContent: String,
    val key: String? = null,
    val capo: String? = null,
    val sourceUrl: String = ""
)

object WebScraperEngine {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Downloads and scrapes chords/lyrics from a supported site or generic chord site URL.
     */
    suspend fun scrapeUrl(rawUrl: String): Result<ScrapedSong> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(rawUrl)
            val doc = Jsoup.connect(normalizedUrl)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(15_000)
                .followRedirects(true)
                .get()

            val host = runCatching { URI(normalizedUrl).host?.lowercase() }.getOrNull() ?: ""
            val scraped = when {
                host.contains("ultimate-guitar.com") -> parseUltimateGuitar(doc, normalizedUrl)
                host.contains("chordie.com") -> parseChordie(doc, normalizedUrl)
                host.contains("e-chords.com") -> parseEChords(doc, normalizedUrl)
                host.contains("songsterr.com") -> parseSongsterr(doc, normalizedUrl)
                else -> parseGeneric(doc, normalizedUrl)
            }

            if (scraped.rawContent.isBlank()) {
                Result.failure(IllegalStateException("No lyrics or chord text found at the provided URL."))
            } else {
                Result.success(scraped)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses raw pasted clipboard text into a ScrapedSong object.
     */
    fun parseFromClipboard(rawText: String): ScrapedSong {
        val trimmed = rawText.trim()
        val lines = trimmed.lines()

        var detectedTitle = "Pasted Song"
        var detectedArtist: String? = null
        var detectedKey: String? = null
        var detectedCapo: String? = null

        // Check first few lines for ChordPro directives or Title: / Artist: labels
        for (line in lines.take(10)) {
            val lineTrim = line.trim()
            val lower = lineTrim.lowercase()
            when {
                lower.startsWith("{title:") || lower.startsWith("{t:") -> {
                    detectedTitle = lineTrim.substringAfter(":").substringBefore("}").trim()
                }
                lower.startsWith("{artist:") || lower.startsWith("{a:") || lower.startsWith("{st:") || lower.startsWith("{subtitle:") -> {
                    detectedArtist = lineTrim.substringAfter(":").substringBefore("}").trim()
                }
                lower.startsWith("{key:") -> {
                    detectedKey = lineTrim.substringAfter(":").substringBefore("}").trim()
                }
                lower.startsWith("{capo:") -> {
                    detectedCapo = lineTrim.substringAfter(":").substringBefore("}").trim()
                }
                lower.startsWith("title:") -> {
                    detectedTitle = lineTrim.substringAfter(":").trim()
                }
                lower.startsWith("artist:") -> {
                    detectedArtist = lineTrim.substringAfter(":").trim()
                }
                lower.startsWith("key:") -> {
                    detectedKey = lineTrim.substringAfter(":").trim()
                }
                lower.startsWith("capo:") -> {
                    detectedCapo = lineTrim.substringAfter(":").trim()
                }
            }
        }

        // If title wasn't found from directives, check if 1st non-empty line looks like a title (e.g. "Song - Artist")
        if (detectedTitle == "Pasted Song" && lines.isNotEmpty()) {
            val firstLine = lines.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (!firstLine.contains("[") && !firstLine.contains("/") && firstLine.length < 60) {
                val (title, artist) = splitTitleAndArtist(firstLine)
                if (title.isNotBlank()) {
                    detectedTitle = title
                    if (artist != null && detectedArtist == null) detectedArtist = artist
                }
            }
        }

        return ScrapedSong(
            title = detectedTitle,
            artist = detectedArtist,
            rawContent = trimmed,
            key = detectedKey,
            capo = detectedCapo,
            sourceUrl = "Clipboard"
        )
    }

    /**
     * Ultimate Guitar Scraper:
     * Extracts JSON payload embedded in <div class="js-store" data-content="...">
     * or parses preformatted tab-content.
     */
    internal fun parseUltimateGuitar(doc: Document, url: String): ScrapedSong {
        // Strategy A: Check for js-store data-content
        val jsStore = doc.selectFirst(".js-store")
        val dataContent = jsStore?.attr("data-content")
        if (!dataContent.isNullOrBlank()) {
            val contentMatch = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(dataContent)
            if (contentMatch != null) {
                val rawEscapedContent = contentMatch.groupValues[1]
                val unescapedContent = unescapeJson(rawEscapedContent)
                val cleanContent = sanitizeUgContent(unescapedContent)

                val songNameMatch = Regex(""""song_name"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(dataContent)
                val artistNameMatch = Regex(""""artist_name"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(dataContent)
                val tonalityMatch = Regex(""""tonality"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(dataContent)
                val capoMatch = Regex(""""capo"\s*:\s*([0-9]+)""").find(dataContent)

                val title = songNameMatch?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: extractTitleFromDoc(doc).first
                val artist = artistNameMatch?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: extractTitleFromDoc(doc).second

                val capoVal = capoMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
                    if (it > 0) "Fret $it" else null
                }

                return ScrapedSong(
                    title = title,
                    artist = artist,
                    rawContent = cleanContent,
                    key = tonalityMatch?.groupValues?.get(1),
                    capo = capoVal,
                    sourceUrl = url
                )
            }
        }

        // Strategy B: Direct pre tag fallback
        val pre = doc.selectFirst("pre")
        if (pre != null) {
            val (title, artist) = extractTitleFromDoc(doc)
            return ScrapedSong(
                title = title,
                artist = artist,
                rawContent = sanitizeUgContent(pre.wholeText()),
                sourceUrl = url
            )
        }

        return parseGeneric(doc, url)
    }

    /**
     * Chordie Scraper:
     * Chordie puts songs in ChordPro or preformatted text blocks.
     */
    internal fun parseChordie(doc: Document, url: String): ScrapedSong {
        val pre = doc.selectFirst("#chordpro, pre.chordpro, pre")
        val content = pre?.wholeText() ?: ""
        val (title, artist) = extractTitleFromDoc(doc)

        return ScrapedSong(
            title = title,
            artist = artist,
            rawContent = content.trim(),
            sourceUrl = url
        )
    }

    /**
     * E-Chords Scraper:
     * E-Chords puts chords/lyrics inside <pre id="core"> or <div id="viewcore">.
     */
    internal fun parseEChords(doc: Document, url: String): ScrapedSong {
        val pre = doc.selectFirst("pre#core, pre, #viewcore")
        val content = pre?.wholeText() ?: ""
        val (title, artist) = extractTitleFromDoc(doc)

        return ScrapedSong(
            title = title,
            artist = artist,
            rawContent = content.trim(),
            sourceUrl = url
        )
    }

    /**
     * Songsterr Scraper:
     * Extracts text tab view or <pre> elements.
     */
    internal fun parseSongsterr(doc: Document, url: String): ScrapedSong {
        val pre = doc.selectFirst("pre")
        if (pre != null && pre.wholeText().isNotBlank()) {
            val (title, artist) = extractTitleFromDoc(doc)
            return ScrapedSong(
                title = title,
                artist = artist,
                rawContent = pre.wholeText().trim(),
                sourceUrl = url
            )
        }

        return parseGeneric(doc, url)
    }

    /**
     * Generic Fallback Scraper:
     * Cleans ads, scripts, nav, and extracts all preformatted text or main article body.
     */
    internal fun parseGeneric(doc: Document, url: String): ScrapedSong {
        val (title, artist) = extractTitleFromDoc(doc)

        // Clean out irrelevant DOM elements
        doc.select("script, style, noscript, nav, header, footer, aside, .ad, .ads, .advertisement, iframe, svg, [role=banner], [role=navigation]")
            .remove()

        // 1. Look for <pre> tags
        val preElements = doc.select("pre")
        if (preElements.isNotEmpty()) {
            val preContent = preElements.joinToString("\n\n") { it.wholeText().trim() }
            if (preContent.isNotBlank()) {
                return ScrapedSong(
                    title = title,
                    artist = artist,
                    rawContent = preContent,
                    sourceUrl = url
                )
            }
        }

        // 2. Look for article, main, or chord/lyric containers
        val candidates = doc.select("article, main, .chord, .chords, .lyrics, .tab-content, .song-content, .entry-content, #content")
        for (candidate in candidates) {
            // Convert <br> to newline
            candidate.select("br").append("\\n")
            candidate.select("p").prepend("\\n\\n")
            val text = candidate.text().replace("\\n", "\n").trim()
            if (text.lines().size >= 5) {
                return ScrapedSong(
                    title = title,
                    artist = artist,
                    rawContent = text,
                    sourceUrl = url
                )
            }
        }

        // 3. Fallback to body text with basic newline restoration
        doc.body()?.select("br")?.append("\\n")
        doc.body()?.select("p")?.prepend("\\n\\n")
        val fallbackText = doc.body()?.text()?.replace("\\n", "\n")?.trim() ?: ""

        return ScrapedSong(
            title = title,
            artist = artist,
            rawContent = fallbackText,
            sourceUrl = url
        )
    }

    /**
     * Sanitizes Ultimate Guitar specific markup (e.g. [ch]Am[/ch], [tab]...[/tab]).
     */
    internal fun sanitizeUgContent(content: String): String {
        return content
            .replace(Regex("""\[ch\](.*?)\[/ch\]"""), "$1")
            .replace(Regex("""\[/?tab\]"""), "")
            .trim()
    }

    /**
     * Decodes escaped JSON strings (e.g. \r\n, \n, \", \\).
     */
    internal fun unescapeJson(escaped: String): String {
        return escaped
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }

    /**
     * Normalizes input URL by adding https:// if protocol is omitted.
     */
    internal fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Extracts Song Title and Artist from document <title> or meta tags.
     */
    internal fun extractTitleFromDoc(doc: Document): Pair<String, String?> {
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
        val pageTitle = ogTitle?.takeIf { it.isNotBlank() } ?: doc.title()
        val h1 = doc.selectFirst("h1")?.text()

        val candidate = when {
            !ogTitle.isNullOrBlank() -> ogTitle
            !h1.isNullOrBlank() -> h1
            else -> pageTitle
        }

        return splitTitleAndArtist(cleanWebPageTitle(candidate))
    }

    /**
     * Cleans common web title baggage (e.g. "Chords by Artist | Ultimate-Guitar.com", "@ Chordie").
     */
    internal fun cleanWebPageTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)\s*\|\s*Ultimate-Guitar(\.Com)?"""), "")
            .replace(Regex("""(?i)\s*@\s*Chordie(\.Com)?"""), "")
            .replace(Regex("""(?i)\s*\|\s*E-Chords(\.Com)?"""), "")
            .replace(Regex("""(?i)\s*\|\s*Songsterr"""), "")
            .replace(Regex("""(?i)\s*Chords\s*&\s*Tabs"""), "")
            .replace(Regex("""(?i)\s*Tabs\s*&\s*Chords"""), "")
            .replace(Regex("""(?i)\s*Guitar\s*(Chords|Tabs)"""), "")
            .trim()
    }

    /**
     * Heuristically splits a cleaned title string into Title and Artist.
     * Patterns handled:
     * - "Title Chords by Artist" -> (Title, Artist)
     * - "Title by Artist" -> (Title, Artist)
     * - "Artist - Title" -> (Title, Artist)
     */
    internal fun splitTitleAndArtist(text: String): Pair<String, String?> {
        val trimmed = text.trim()

        // 1. Check for "by" (use lastIndexOf so titles like "Stand By Me by Ben E. King" work)
        val byIdx = trimmed.lastIndexOf(" by ", ignoreCase = true)
        if (byIdx > 0) {
            val titlePart = trimmed.substring(0, byIdx).replace(Regex("""(?i)\s*Chords$"""), "").trim()
            val artistPart = trimmed.substring(byIdx + 4).trim()
            if (titlePart.isNotBlank() && artistPart.isNotBlank()) {
                return titlePart to artistPart
            }
        }

        // 2. Check for " - " delimiter (e.g. "Hotel California - Eagles" or "The Beatles - Let It Be Chords")
        if (trimmed.contains(" - ")) {
            val parts = trimmed.split(" - ", limit = 2)
            val p0 = parts[0].trim()
            val p1 = parts[1].trim()
            return when {
                p1.endsWith("Chords", ignoreCase = true) -> {
                    p1.replace(Regex("""(?i)\s*Chords$"""), "").trim() to p0
                }
                p0.endsWith("Chords", ignoreCase = true) -> {
                    p0.replace(Regex("""(?i)\s*Chords$"""), "").trim() to p1
                }
                else -> {
                    p0 to p1
                }
            }
        }

        // 3. Fallback: clean trailing "Chords" keyword
        val cleanTitle = trimmed.replace(Regex("""(?i)\s*Chords$"""), "").trim()
        return cleanTitle.ifBlank { "Imported Song" } to null
    }
}
