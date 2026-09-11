package com.joel.gta.data.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Same identity equivalence and first-record retention policy as Web syncMerge.ts. */
object SyncDedup {
    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT)
    private fun identity(item: JSONObject, field: String): String = if (field == "songs")
        JSONArray(listOf(normalized(item.getString("title")), normalized(item.optString("artist")))).toString()
        else normalized(item.getString("name"))
    private fun aliases(items: List<JSONObject>, field: String): Map<String, String> {
        val parent = linkedMapOf<String, String>()
        val firstIdentity = mutableMapOf<String, String>()
        fun root(id: String): String {
            val next = parent.getValue(id)
            if (next == id) return id
            return root(next).also { parent[id] = it }
        }
        for (item in items) {
            val id = SyncPayload.id(item)
            parent.putIfAbsent(id, id)
            val key = identity(item, field)
            val previous = firstIdentity[key]
            if (previous == null) firstIdentity[key] = id else parent[root(id)] = root(previous)
        }
        val primary = mutableMapOf<String, String>()
        items.forEach { primary.putIfAbsent(root(SyncPayload.id(it)), SyncPayload.id(it)) }
        return items.associate { SyncPayload.id(it) to primary.getValue(root(SyncPayload.id(it))) }
    }
    fun union(a: JSONArray, b: JSONArray): JSONArray = JSONArray((SyncPayload.objects(a) + SyncPayload.objects(b)).distinctBy(SyncPayload::id))
    fun align(libraries: List<JSONObject>): List<JSONObject> {
        val songIds = aliases(libraries.flatMap { SyncPayload.objects(it.getJSONArray("songs")) }, "songs")
        val setlistIds = aliases(libraries.flatMap { SyncPayload.objects(it.getJSONArray("setlists")) }, "setlists")
        return libraries.map { source ->
            val songs = linkedMapOf<String, JSONObject>()
            for (song in SyncPayload.objects(source.getJSONArray("songs"))) {
                val id = songIds.getValue(SyncPayload.id(song))
                songs.putIfAbsent(id, JSONObject(song.toString()).put("id", id))
            }
            val setlists = linkedMapOf<String, JSONObject>()
            for (setlist in SyncPayload.objects(source.getJSONArray("setlists"))) {
                val id = setlistIds.getValue(SyncPayload.id(setlist))
                val refs = JSONArray(SyncPayload.objects(setlist.getJSONArray("songs")).map { ref ->
                    val songId = songIds[SyncPayload.id(ref)] ?: SyncPayload.id(ref)
                    JSONObject(ref.toString()).put("id", songId).also { mapped ->
                        songs[songId]?.let { song -> mapped.put("title", song.getString("title")).put("artist", song.optString("artist")) }
                    }
                })
                val previous = setlists[id]
                setlists[id] = JSONObject((previous ?: setlist).toString()).put("id", id)
                    .put("songs", union(previous?.getJSONArray("songs") ?: JSONArray(), refs)).also { it.remove("songIds") }
            }
            JSONObject(source.toString()).put("songs", JSONArray(songs.values.toList())).put("setlists", JSONArray(setlists.values.toList()))
        }
    }
    fun deduplicate(library: JSONObject): JSONObject = align(listOf(library)).single()
}
