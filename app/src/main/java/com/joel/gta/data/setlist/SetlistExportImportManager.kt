package com.joel.gta.data.setlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.joel.gta.data.local.dao.SetlistDao
import com.joel.gta.data.local.dao.SongDao
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.parser.SongParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SetlistImportResult(
    val setlistName: String,
    val songsImportedCount: Int,
    val setlistId: Long
)

object SetlistExportImportManager {

    private const val SETLIST_TYPE = "GTAR_SETLIST"
    private const val CURRENT_VERSION = 1

    /**
     * Formats a timestamp into an ISO-8601 UTC string (e.g. 2026-09-06T08:45:00Z).
     */
    fun formatIsoTimestamp(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMillis))
    }

    /**
     * Sanitizes a setlist name for safe filename creation.
     */
    fun generateSetlistFileName(setlistName: String): String {
        val cleanName = setlistName.trim()
            .replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            .ifBlank { "setlist" }
        return "$cleanName.setlist.json"
    }

    /**
     * Creates the GTAR Setlist JSON payload matching the requested schema.
     */
    fun createSetlistJson(
        setlist: SetlistEntity,
        songs: List<SongEntity>
    ): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("type", SETLIST_TYPE)
        root.put("name", setlist.name)
        root.put("createdAt", formatIsoTimestamp(setlist.createdAt))

        val songsArray = JSONArray()
        songs.forEachIndexed { index, song ->
            val sObj = JSONObject().apply {
                put("title", song.title)
                put("artist", song.artist ?: "")
                put("key", song.key ?: "")
                put("chordsContent", song.rawContent)
                put("order", index + 1)
            }
            songsArray.put(sObj)
        }
        root.put("songs", songsArray)

        return root.toString(2)
    }

    /**
     * Saves setlist JSON to cache and builds an ACTION_SEND Intent via FileProvider.
     */
    fun createShareIntent(
        context: Context,
        setlist: SetlistEntity,
        songs: List<SongEntity>
    ): Intent {
        val jsonString = createSetlistJson(setlist, songs)
        val setlistDir = File(context.cacheDir, "setlists").apply { mkdirs() }
        val fileName = generateSetlistFileName(setlist.name)
        val file = File(setlistDir, fileName)
        file.writeText(jsonString)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GTAR Setlist: ${setlist.name}")
            putExtra(Intent.EXTRA_TEXT, "GTAR Setlist: ${setlist.name} (${songs.size} songs)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Writes JSON payload to a Storage Access Framework (SAF) URI.
     */
    suspend fun writeToSafUri(
        context: Context,
        uri: Uri,
        jsonString: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a given text content is a GTAR Setlist JSON.
     */
    fun isSetlistJson(content: String): Boolean {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val json = JSONObject(trimmed)
            json.optString("type") == SETLIST_TYPE
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Imports a GTAR Setlist JSON into the local Room database:
     * - Checks for existing songs by Title + Artist (case-insensitive).
     * - Appends incremental number suffix "(1)", "(2)", etc. if duplicate exists.
     * - Reconstructs the Setlist with imported name (or "(1)" suffix if name exists).
     * - Links imported songs in exact defined order.
     */
    suspend fun importSetlist(
        jsonString: String,
        songDao: SongDao,
        setlistDao: SetlistDao
    ): SetlistImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        val rawName = root.optString("name").trim().ifBlank { "Imported Setlist" }
        val songsArray = root.optJSONArray("songs") ?: JSONArray()

        // 1. Resolve Setlist Name
        val existingSetlists = setlistDao.getAllSetlistsDirect()
        val existingSetlistNames = existingSetlists.map { it.name.trim().lowercase() }.toMutableSet()
        val finalSetlistName = findAvailableName(rawName, existingSetlistNames)
        existingSetlistNames.add(finalSetlistName.lowercase())

        val newSetlistId = setlistDao.insertSetlist(
            SetlistEntity(name = finalSetlistName, createdAt = System.currentTimeMillis())
        )

        // 2. Fetch existing songs for duplicate detection (case-insensitive title + artist)
        val allExistingSongs = songDao.getAllSongsDirect()
        // Map of normalized artist (or empty) -> MutableSet of lowercase titles
        val artistToTitlesMap = mutableMapOf<String, MutableSet<String>>()
        for (song in allExistingSongs) {
            val normArtist = (song.artist ?: "").trim().lowercase()
            artistToTitlesMap.getOrPut(normArtist) { mutableSetOf() }.add(song.title.trim().lowercase())
        }

        // Parse song items and sort by 'order'
        data class SongImportItem(
            val title: String,
            val artist: String?,
            val key: String?,
            val chordsContent: String,
            val order: Int
        )

        val itemsToImport = mutableListOf<SongImportItem>()
        for (i in 0 until songsArray.length()) {
            val sObj = songsArray.getJSONObject(i)
            val title = sObj.optString("title").trim().ifBlank { "Untitled Song" }
            val artist = sObj.optString("artist").trim().takeIf { it.isNotBlank() }
            val key = sObj.optString("key").trim().takeIf { it.isNotBlank() }
            val content = sObj.optString("chordsContent")
            val order = sObj.optInt("order", i + 1)

            itemsToImport.add(SongImportItem(title, artist, key, content, order))
        }

        itemsToImport.sortBy { it.order }

        // 3. Import songs with incremental duplicate renaming
        var importedCount = 0
        itemsToImport.forEachIndexed { index, item ->
            val normArtist = (item.artist ?: "").trim().lowercase()
            val existingTitles = artistToTitlesMap.getOrPut(normArtist) { mutableSetOf() }

            val resolvedTitle = findAvailableName(item.title, existingTitles)
            existingTitles.add(resolvedTitle.lowercase())

            val parsed = SongParser.parse(item.chordsContent, defaultTitle = resolvedTitle)
            val entity = SongEntity(
                title = resolvedTitle,
                artist = item.artist ?: parsed.artist,
                key = item.key ?: parsed.key,
                capo = parsed.capo,
                rawContent = item.chordsContent,
                format = parsed.format.name,
                transposeOffset = 0,
                tags = "",
                lastOpenedAt = System.currentTimeMillis()
            )

            val songId = songDao.insertSong(entity)
            setlistDao.addSongToSetlist(
                SetlistSongCrossRef(
                    setlistId = newSetlistId,
                    songId = songId,
                    position = index
                )
            )
            importedCount++
        }

        SetlistImportResult(
            setlistName = finalSetlistName,
            songsImportedCount = importedCount,
            setlistId = newSetlistId
        )
    }

    /**
     * Finds an available name by checking existing names case-insensitively.
     * If base exists, checks base (1), base (2), etc.
     */
    fun findAvailableName(baseName: String, existingNamesLower: Set<String>): String {
        if (!existingNamesLower.contains(baseName.trim().lowercase())) {
            return baseName.trim()
        }

        var suffix = 1
        while (true) {
            val candidate = "${baseName.trim()} ($suffix)"
            if (!existingNamesLower.contains(candidate.lowercase())) {
                return candidate
            }
            suffix++
        }
    }
}
