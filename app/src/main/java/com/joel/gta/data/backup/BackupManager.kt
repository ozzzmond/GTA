package com.joel.gta.data.backup

import androidx.room.withTransaction
import com.joel.gta.data.local.GtaDatabase
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
import org.json.JSONTokener
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
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "gtar_backup_$dateStr.json"
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
            put("appName", "GTAR")
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
     * Inspects a JSON string to determine if it is a GTAR Backup payload.
     * Checks metadata.appName == "GTAR" or the presence of a "songs" JSON array.
     */
    fun isBackupJson(jsonString: String): Boolean {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{")) return false
        return try {
            val root = JSONObject(trimmed)
            val meta = root.optJSONObject("metadata")
            val appName = meta?.optString("appName", "")
            appName.equals("GTAR", ignoreCase = true) || (root.has("songs") && root.optJSONArray("songs") != null)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Full Restore (Wipe & Replace) Strategy:
     * - Drops/resets existing songs and setlists in Room DB.
     * - Imports the complete backup (songs, setlists).
     */
    suspend fun fullRestoreWipeAndReplace(
        jsonString: String,
        database: GtaDatabase
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val root = validateBackup(jsonString)
        database.withTransaction {
            replaceValidatedBackup(root, database.songDao(), database.setlistDao())
        }
    }

    private suspend fun replaceValidatedBackup(
        root: JSONObject,
        songDao: SongDao,
        setlistDao: SetlistDao
    ): RestoreSummary {
        val songsArray = root.getJSONArray("songs")
        val setlistsArray = root.optJSONArray("setlists") ?: JSONArray()

        // 1. Wipe existing songs and setlists
        setlistDao.deleteAllCrossRefs()
        setlistDao.deleteAllSetlists()
        songDao.deleteAllSongs()

        var songsRestoredCount = 0
        val resolvedSongIdMap = mutableMapOf<String, Long>()

        // 2. Insert all songs from backup
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
            val createdAt = sObj.optLong("createdAt", System.currentTimeMillis())
            val lastOpenedAt = sObj.optLong("lastOpenedAt", 0L)

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
            resolvedSongIdMap[normalizeKey(title, artist)] = newId
            songsRestoredCount++
        }

        // 3. Insert all setlists and associate songs
        var setlistsRestoredCount = 0
        for (j in 0 until setlistsArray.length()) {
            val setlistObj = setlistsArray.getJSONObject(j)
            val name = setlistObj.getString("name").trim()
            val createdAt = setlistObj.optLong("createdAt", System.currentTimeMillis())
            val songsRefArray = setlistObj.optJSONArray("songs") ?: JSONArray()

            val newSetlist = SetlistEntity(name = name, createdAt = createdAt)
            val targetSetlistId = setlistDao.insertSetlist(newSetlist)
            setlistsRestoredCount++

            var nextPos = 0
            for (k in 0 until songsRefArray.length()) {
                val sRef = songsRefArray.getJSONObject(k)
                val songTitle = sRef.getString("title").trim()
                val songArtist = sRef.optString("artist").takeIf { it.isNotBlank() }

                val songLookupKey = normalizeKey(songTitle, songArtist)
                val resolvedSongId = resolvedSongIdMap.getValue(songLookupKey)
                setlistDao.addSongToSetlist(
                    SetlistSongCrossRef(
                        setlistId = targetSetlistId,
                        songId = resolvedSongId,
                        position = nextPos++
                    )
                )
            }
        }

        return RestoreSummary(
            songsRestored = songsRestoredCount,
            songsUpdated = 0,
            setlistsRestored = setlistsRestoredCount,
            totalSongsInBackup = songsArray.length(),
            message = "Successfully restored $songsRestoredCount songs and $setlistsRestoredCount setlists"
        )
    }

    /** Fully validate before opening a transaction or touching any DAO. Explicit songs: [] is empty. */
    internal fun validateBackup(jsonString: String): JSONObject {
        val tokener = JSONTokener(jsonString)
        val root = tokener.nextValue() as? JSONObject
            ?: throw IllegalArgumentException("Backup must be a JSON object")
        require(tokener.nextClean() == '\u0000') { "Unexpected data after backup JSON" }
        require(root.opt("songs") is JSONArray) { "songs must be an array (use [] for an empty backup)" }
        require(!root.has("setlists") || root.opt("setlists") is JSONArray) { "setlists must be an array" }
        fun string(obj: JSONObject, field: String, path: String, required: Boolean = false) {
            require((!required && !obj.has(field)) || obj.opt(field) is String) { "$path.$field must be a string" }
            if (required) require(obj.getString(field).isNotBlank()) { "$path.$field must not be blank" }
        }
        fun number(obj: JSONObject, field: String, path: String) {
            if (obj.has(field)) {
                val value = obj.opt(field)
                require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0) {
                    "$path.$field must be an integer"
                }
                if (field == "transposeOffset" || field == "position") {
                    require((value as Number).toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                        "$path.$field is out of range"
                    }
                }
            }
        }
        val keys = mutableSetOf<String>()
        val songs = root.getJSONArray("songs")
        for (i in 0 until songs.length()) {
            val path = "songs[$i]"
            val song = songs.optJSONObject(i) ?: throw IllegalArgumentException("$path must be an object")
            string(song, "title", path, true)
            require(song.has("rawContent")) { "$path.rawContent is required" }
            for (field in listOf("artist", "key", "capo", "rawContent", "format", "tags")) string(song, field, path)
            for (field in listOf("createdAt", "lastOpenedAt", "transposeOffset")) number(song, field, path)
            for (field in listOf("isFavorite", "isDeleted")) {
                require(!song.has(field) || song.opt(field) is Boolean) { "$path.$field must be a boolean" }
            }
            keys.add(normalizeKey(song.getString("title"), song.optString("artist")))
        }
        val setlists = root.optJSONArray("setlists") ?: JSONArray()
        for (i in 0 until setlists.length()) {
            val path = "setlists[$i]"
            val setlist = setlists.optJSONObject(i) ?: throw IllegalArgumentException("$path must be an object")
            string(setlist, "name", path, true)
            number(setlist, "createdAt", path)
            val refs = setlist.optJSONArray("songs") ?: throw IllegalArgumentException("$path.songs must be an array")
            for (j in 0 until refs.length()) {
                val refPath = "$path.songs[$j]"
                val ref = refs.optJSONObject(j) ?: throw IllegalArgumentException("$refPath must be an object")
                string(ref, "title", refPath, true)
                string(ref, "artist", refPath)
                number(ref, "position", refPath)
                require(normalizeKey(ref.getString("title"), ref.optString("artist")) in keys) {
                    "$refPath refers to a song missing from the backup"
                }
            }
        }
        return root
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
        val root = validateBackup(jsonString)
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
            putExtra(Intent.EXTRA_SUBJECT, fileName)
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
