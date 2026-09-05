package com.joel.gta.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.joel.gta.data.local.dao.SetlistDao
import com.joel.gta.data.local.dao.SongDao
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RestoreSummary(
    val songsRestored: Int,
    val songsUpdated: Int,
    val setlistsRestored: Int,
    val totalSongsInBackup: Int,
    val message: String
)

object BackupManager {

    fun generateBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "gta_backup_$dateStr.json"
    }

    /**
     * Serializes all active and soft-deleted songs, and all setlists with their song references, into JSON.
     */
    fun createBackupJson(
        songs: List<SongEntity>,
        setlists: List<SetlistWithSongs>,
        appVersion: String
    ): String {
        val root = JSONObject()

        // 1. Metadata
        val meta = JSONObject().apply {
            put("appName", "GTA")
            put("appVersion", appVersion)
            put("exportTimestamp", System.currentTimeMillis())
        }
        root.put("metadata", meta)

        // 2. Songs (Active + Soft-deleted)
        val songsArray = JSONArray()
        for (song in songs) {
            val sObj = JSONObject().apply {
                put("title", song.title)
                put("artist", song.artist ?: "")
                put("key", song.key ?: "")
                put("capo", song.capo ?: "")
                put("rawContent", song.rawContent)
                put("format", song.format)
                put("isFavorite", song.isFavorite)
                put("transposeOffset", song.transposeOffset)
                put("tags", song.tags)
                put("isDeleted", song.isDeleted)
                put("createdAt", song.createdAt)
                put("lastOpenedAt", song.lastOpenedAt)
            }
            songsArray.put(sObj)
        }
        root.put("songs", songsArray)

        // 3. Setlists
        val setlistsArray = JSONArray()
        for (setlistWithSongs in setlists) {
            val setlistObj = JSONObject().apply {
                put("name", setlistWithSongs.setlist.name)
                put("createdAt", setlistWithSongs.setlist.createdAt)

                val songsRefArray = JSONArray()
                setlistWithSongs.songs.forEachIndexed { index, song ->
                    val refObj = JSONObject().apply {
                        put("title", song.title)
                        put("artist", song.artist ?: "")
                        put("position", index)
                    }
                    songsRefArray.put(refObj)
                }
                put("songs", songsRefArray)
            }
            setlistsArray.put(setlistObj)
        }
        root.put("setlists", setlistsArray)

        return root.toString(2)
    }

    /**
     * Smart Merge Restore Strategy:
     * - Never drops or wipes existing records in Room DB.
     * - Matches songs by Title + Artist.
     * - Updates chords/key/content if backup is newer, merges tags, preserves favorites, restores if backup was active.
     * - Inserts new songs and links them back to their setlists.
     */
    suspend fun restoreBackup(
        jsonString: String,
        songDao: SongDao,
        setlistDao: SetlistDao
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        val metaObj = root.optJSONObject("metadata")
        val exportTimestamp = metaObj?.optLong("exportTimestamp", 0L) ?: 0L
        val songsArray = root.optJSONArray("songs") ?: JSONArray()
        val setlistsArray = root.optJSONArray("setlists") ?: JSONArray()

        val existingSongs = songDao.getAllSongsDirect()
        val existingSongMap = mutableMapOf<String, SongEntity>()
        for (song in existingSongs) {
            existingSongMap[normalizeKey(song.title, song.artist)] = song
        }

        var songsRestoredCount = 0
        var songsUpdatedCount = 0

        val resolvedSongIdMap = mutableMapOf<String, Long>()
        for (song in existingSongs) {
            resolvedSongIdMap[normalizeKey(song.title, song.artist)] = song.id
        }

        // Merge Songs
        for (i in 0 until songsArray.length()) {
            val sObj = songsArray.getJSONObject(i)
            val title = sObj.getString("title").trim()
            val artist = sObj.optString("artist").takeIf { it.isNotBlank() }
            val key = sObj.optString("key").takeIf { it.isNotBlank() }
            val capo = sObj.optString("capo").takeIf { it.isNotBlank() }
            val rawContent = sObj.optString("rawContent")
            val format = sObj.optString("format", "TWO_LINE")
            val isFavorite = sObj.optBoolean("isFavorite", false)
            val transposeOffset = sObj.optInt("transposeOffset", 0)
            val tags = sObj.optString("tags", "")
            val isDeleted = sObj.optBoolean("isDeleted", false)
            val createdAt = sObj.optLong("createdAt", 0L)
            val lastOpenedAt = sObj.optLong("lastOpenedAt", 0L)

            val lookupKey = normalizeKey(title, artist)
            val existing = existingSongMap[lookupKey]

            if (existing != null) {
                val backupSongTimestamp = maxOf(lastOpenedAt, createdAt)
                val backupTimestamp = if (backupSongTimestamp > 0L) backupSongTimestamp else exportTimestamp
                val existingTimestamp = maxOf(existing.lastOpenedAt, existing.createdAt)

                val shouldUpdateContent = backupTimestamp >= existingTimestamp || existing.rawContent.isBlank()

                val mergedSong = if (shouldUpdateContent) {
                    existing.copy(
                        key = key ?: existing.key,
                        capo = capo ?: existing.capo,
                        rawContent = if (rawContent.isNotBlank()) rawContent else existing.rawContent,
                        format = format,
                        transposeOffset = transposeOffset,
                        tags = mergeTags(existing.tags, tags),
                        isFavorite = existing.isFavorite || isFavorite,
                        isDeleted = if (!isDeleted) false else existing.isDeleted,
                        lastOpenedAt = maxOf(existing.lastOpenedAt, lastOpenedAt)
                    )
                } else {
                    existing.copy(
                        tags = mergeTags(existing.tags, tags),
                        isFavorite = existing.isFavorite || isFavorite,
                        isDeleted = if (!isDeleted) false else existing.isDeleted
                    )
                }

                if (mergedSong != existing) {
                    songDao.updateSong(mergedSong)
                    songsUpdatedCount++
                }
                resolvedSongIdMap[lookupKey] = existing.id
            } else {
                val newEntity = SongEntity(
                    title = title,
                    artist = artist,
                    key = key,
                    capo = capo,
                    rawContent = rawContent,
                    format = format,
                    isFavorite = isFavorite,
                    transposeOffset = transposeOffset,
                    tags = tags,
                    isDeleted = isDeleted,
                    createdAt = createdAt,
                    lastOpenedAt = lastOpenedAt
                )
                val newId = songDao.insertSong(newEntity)
                resolvedSongIdMap[lookupKey] = newId
                songsRestoredCount++
            }
        }

        // Merge Setlists
        var setlistsRestoredCount = 0
        val existingSetlists = setlistDao.getAllSetlistsDirect()
        val existingSetlistMap = existingSetlists.associateBy { it.name.trim().lowercase() }.toMutableMap()

        for (j in 0 until setlistsArray.length()) {
            val setlistObj = setlistsArray.getJSONObject(j)
            val name = setlistObj.getString("name").trim()
            val createdAt = setlistObj.optLong("createdAt", System.currentTimeMillis())
            val songsRefArray = setlistObj.optJSONArray("songs") ?: JSONArray()

            val normName = name.lowercase()
            var targetSetlistId = existingSetlistMap[normName]?.id

            if (targetSetlistId == null) {
                val newSetlist = SetlistEntity(name = name, createdAt = createdAt)
                targetSetlistId = setlistDao.insertSetlist(newSetlist)
                existingSetlistMap[normName] = newSetlist.copy(id = targetSetlistId)
                setlistsRestoredCount++
            }

            val currentRefs = setlistDao.getCrossRefsForSetlist(targetSetlistId)
            val existingSongIds = currentRefs.map { it.songId }.toSet()
            var nextPos = setlistDao.getMaxPosition(targetSetlistId) + 1

            for (k in 0 until songsRefArray.length()) {
                val sRef = songsRefArray.getJSONObject(k)
                val songTitle = sRef.getString("title").trim()
                val songArtist = sRef.optString("artist").takeIf { it.isNotBlank() }

                val songLookupKey = normalizeKey(songTitle, songArtist)
                var resolvedSongId = resolvedSongIdMap[songLookupKey]

                if (resolvedSongId == null) {
                    resolvedSongId = resolvedSongIdMap.entries.firstOrNull {
                        it.key.startsWith(songTitle.lowercase() + "|")
                    }?.value
                }

                if (resolvedSongId != null && resolvedSongId !in existingSongIds) {
                    setlistDao.addSongToSetlist(
                        SetlistSongCrossRef(
                            setlistId = targetSetlistId,
                            songId = resolvedSongId,
                            position = nextPos++
                        )
                    )
                }
            }
        }

        val totalInBackup = songsArray.length()
        val summaryMsg = when {
            songsRestoredCount > 0 && songsUpdatedCount > 0 ->
                "$songsRestoredCount new songs restored, $songsUpdatedCount updated, and $setlistsRestoredCount setlists merged!"
            songsRestoredCount > 0 ->
                "$songsRestoredCount songs and $setlistsRestoredCount setlists restored/merged!"
            songsUpdatedCount > 0 ->
                "$songsUpdatedCount existing songs updated and $setlistsRestoredCount setlists merged!"
            else ->
                "Backup verified: all songs and setlists are already up to date!"
        }

        RestoreSummary(
            songsRestored = songsRestoredCount,
            songsUpdated = songsUpdatedCount,
            setlistsRestored = setlistsRestoredCount,
            totalSongsInBackup = totalInBackup,
            message = summaryMsg
        )
    }

    /**
     * Saves backup JSON to a cache file and creates an Android Share Sheet intent.
     * Enables saving directly to Google Drive, Files, Gmail, or cloud services.
     */
    fun createShareIntent(context: Context, jsonString: String): Intent {
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val fileName = generateBackupFileName()
        val backupFile = File(backupDir, fileName)
        backupFile.writeText(jsonString)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GTA Songbook Backup ($fileName)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Writes JSON directly to an Android Storage Access Framework (SAF) URI.
     */
    suspend fun writeToSafUri(context: Context, uri: Uri, jsonString: String): Boolean = withContext(Dispatchers.IO) {
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

    private fun normalizeKey(title: String, artist: String?): String {
        val normTitle = title.trim().lowercase()
        val normArtist = artist?.trim()?.lowercase() ?: ""
        return "$normTitle|$normArtist"
    }

    private fun mergeTags(existingTags: String, backupTags: String): String {
        val existingList = existingTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val backupList = backupTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val combined = (existingList + backupList).distinct()
        return combined.joinToString(", ")
    }
}
