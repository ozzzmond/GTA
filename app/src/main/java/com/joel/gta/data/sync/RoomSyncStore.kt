package com.joel.gta.data.sync

import androidx.room.withTransaction
import com.joel.gta.BuildConfig
import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import org.json.JSONArray
import org.json.JSONObject

class RoomSyncStore(private val db: GtaDatabase) {
    /** Repeatable migration: transactionally retain primary rows and repoint all memberships. */
    suspend fun cleanup(): JSONObject = db.withTransaction {
        val before = read()
        val repaired = SyncDedup.deduplicate(before)
        if (SyncPayload.sameLibrary(before, repaired)) before else apply(before, repaired)
    }
    suspend fun snapshot(): JSONObject = db.withTransaction { read() }
    private suspend fun read(): JSONObject {
        val songs = db.songDao().getAllSongsDirect().sortedWith(compareBy<SongEntity> { it.isDeleted }.thenBy { it.id })
        val byId = songs.associateBy { it.id }
        val songJson = songs.map { s -> JSONObject().apply {
            put("id", s.syncId); put("title", s.title); put("artist", s.artist ?: "")
            put("rawContent", s.rawContent); put("key", s.key ?: ""); put("capo", s.capo ?: "")
            put("bpm", s.bpm); put("format", s.format); put("transposeOffset", s.transposeOffset)
            put("tags", s.tags); put("isFavorite", s.isFavorite); put("isDeleted", s.isDeleted)
            put("createdAt", s.createdAt); put("lastOpenedAt", s.lastOpenedAt)
        } }
        val setlists = db.setlistDao().getAllSetlistsDirect().sortedBy { it.id }.map { s ->
            val refs = db.setlistDao().getCrossRefsForSetlist(s.id).mapNotNull { ref -> byId[ref.songId]?.let { song ->
                JSONObject().put("id", song.syncId).put("title", song.title).put("artist", song.artist ?: "")
            } }
            JSONObject().put("id", s.syncId).put("name", s.name).put("createdAt", s.createdAt).put("songs", JSONArray(refs))
        }
        return JSONObject().put("app", "GTAR").put("version", BuildConfig.VERSION_NAME)
            .put("exportType", "FULL_BACKUP").put("songs", JSONArray(songJson)).put("setlists", JSONArray(setlists))
    }
    /** A single transaction checks for in-flight local edits before changing any records. */
    suspend fun apply(expected: JSONObject, incoming: JSONObject): JSONObject = db.withTransaction {
        check(SyncPayload.sameLibrary(read(), expected)) { "Library changed during download. Sync again." }
        val payload = SyncPayload.parse(SyncDedup.align(listOf(expected, SyncPayload.parse(incoming.toString())))[1].toString())
        val existingSongs = db.songDao().getAllSongsDirect().associateBy { it.syncId }
        val songIds = mutableMapOf<String, Long>()
        for (s in SyncPayload.objects(payload.getJSONArray("songs"))) {
            val syncId = SyncPayload.id(s)
            val entity = SongEntity(id = existingSongs[syncId]?.id ?: 0, syncId = syncId,
                title = s.getString("title"), artist = s.optString("artist"), key = s.optString("key", "G"),
                capo = s.optString("capo"), bpm = s.optString("bpm", "120"), rawContent = s.getString("rawContent"),
                format = s.optString("format", "CHORD_PRO"), transposeOffset = s.optInt("transposeOffset"),
                tags = s.optString("tags"), isFavorite = s.optBoolean("isFavorite"), isDeleted = s.optBoolean("isDeleted"),
                createdAt = s.optLong("createdAt"), lastOpenedAt = s.optLong("lastOpenedAt"))
            if (entity.id == 0L) songIds[syncId] = db.songDao().insertSong(entity)
            else { if (existingSongs[syncId] != entity) db.songDao().updateSong(entity); songIds[syncId] = entity.id }
        }
        val existingSetlists = db.setlistDao().getAllSetlistsDirect().associateBy { it.syncId }
        val incomingIds = mutableSetOf<String>()
        for (s in SyncPayload.objects(payload.getJSONArray("setlists"))) {
            val syncId = SyncPayload.id(s); incomingIds.add(syncId)
            val entity = SetlistEntity(id = existingSetlists[syncId]?.id ?: 0, syncId = syncId, name = s.getString("name"), createdAt = s.optLong("createdAt"))
            val localId = if (entity.id == 0L) db.setlistDao().insertSetlist(entity) else {
                if (existingSetlists[syncId] != entity) db.setlistDao().updateSetlist(entity); entity.id
            }
            val refs = SyncPayload.objects(s.getJSONArray("songs")).mapIndexed { index, ref -> SetlistSongCrossRef(localId, songIds.getValue(SyncPayload.id(ref)), index) }
            if (db.setlistDao().getCrossRefsForSetlist(localId) != refs) {
                db.setlistDao().clearSongsFromSetlist(localId)
                refs.forEach { db.setlistDao().addSongToSetlist(it) }
            }
        }
        existingSetlists.filterKeys { it !in incomingIds }.values.forEach { db.setlistDao().clearSongsFromSetlist(it.id); db.setlistDao().deleteSetlist(it) }
        existingSongs.filterKeys { it !in songIds }.values.forEach { db.songDao().deleteSong(it) }
        read()
    }
}
