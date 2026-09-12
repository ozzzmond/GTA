package com.joel.gta.data.sync

import org.json.JSONArray
import org.json.JSONObject

/** Exact IDs take precedence over legacy names, matching Web syncMerge.ts. */
object SyncDedup {
    // Legacy names resolve missing references in SyncPayload.parse, never stable IDs.
    private fun aliases(groups: List<List<JSONObject>>): Map<String, String> =
        groups.flatten().associate { SyncPayload.id(it) to SyncPayload.id(it) }
    fun union(a: JSONArray, b: JSONArray): JSONArray = JSONArray((SyncPayload.objects(a) + SyncPayload.objects(b)).distinctBy(SyncPayload::id))
    fun align(libraries: List<JSONObject>): List<JSONObject> {
        val songIds = aliases(libraries.map { SyncPayload.objects(it.getJSONArray("songs")) })
        val setlistIds = aliases(libraries.map { SyncPayload.objects(it.getJSONArray("setlists")) })
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
