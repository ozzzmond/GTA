package com.joel.gta.data.sync

import com.joel.gta.data.backup.BackupManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Wire format shared with web/src/utils/jsonBackup.ts. Room IDs never leave the device. */
object SyncPayload {
    fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map { array.getJSONObject(it) }
    fun id(obj: JSONObject): String {
        val value = obj.get("id")
        require(value is String && value.isNotBlank() || value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && kotlin.math.abs(value.toDouble()) <= 9007199254740991.0) { "Invalid sync ID" }
        return value.toString()
    }
    fun parse(raw: String): JSONObject {
        val root = JSONObject(raw)
        require(root.optString("exportType") != "SINGLE_SETLIST") { "Expected full backup" }
        val songs = objects(root.getJSONArray("songs"))
        val used = mutableSetOf<String>()
        for (song in songs) {
            if (!song.has("id")) song.put("id", UUID.randomUUID().toString())
            require(used.add(id(song))) { "Duplicate song ID" }
            if (!song.has("rawContent") && song.has("content")) song.put("rawContent", song.get("content"))
            require(song.optString("format", "CHORD_PRO") in listOf("CHORD_PRO", "TWO_LINE", "PLAIN")) { "Unsupported format" }
            val transpose = song.opt("transposeOffset") ?: 0
            require(transpose is Number && transpose.toDouble() % 1.0 == 0.0 && transpose.toInt() in -11..11) { "Invalid transpose" }
            require(!song.has("bpm") || song.opt("bpm") is String) { "Invalid bpm" }
        }
        val byId = songs.associateBy(::id)
        val setlists = root.optJSONArray("setlists") ?: JSONArray().also { root.put("setlists", it) }
        used.clear()
        for (setlist in objects(setlists)) {
            if (!setlist.has("id")) setlist.put("id", UUID.randomUUID().toString())
            require(used.add(id(setlist))) { "Duplicate setlist ID" }
            if (!setlist.has("songs") && setlist.has("songIds")) {
                val ids = setlist.getJSONArray("songIds")
                setlist.put("songs", JSONArray((0 until ids.length()).map { JSONObject().put("id", ids.get(it)) }))
            }
            val refs = setlist.getJSONArray("songs")
            for (ref in objects(refs)) {
                val song = if (ref.has("id")) byId[id(ref)] else songs.singleOrNull {
                    it.getString("title").trim().equals(ref.optString("title").trim(), true) &&
                        (!ref.has("artist") || it.optString("artist").trim().equals(ref.optString("artist").trim(), true))
                }
                requireNotNull(song) { "Missing or ambiguous setlist song" }
                ref.put("id", song.get("id")).put("title", song.getString("title")).put("artist", song.optString("artist"))
            }
            setlist.put("songs", JSONArray(objects(refs).distinctBy(::id)))
        }
        BackupManager.validateBackup(root.toString())
        // Canonicalize optional web defaults and numeric IDs to Room's wire representation.
        root.put("songs", JSONArray(songs.map { song -> JSONObject().apply {
            put("id", id(song)); put("title", song.getString("title"))
            for (field in listOf("artist", "capo", "tags")) put(field, song.optString(field))
            put("rawContent", song.getString("rawContent")); put("key", song.optString("key", "G"))
            put("bpm", song.optString("bpm", "120")); put("format", song.optString("format", "CHORD_PRO"))
            put("transposeOffset", song.optInt("transposeOffset"))
            for (field in listOf("isFavorite", "isDeleted")) put(field, song.optBoolean(field))
            for (field in listOf("createdAt", "lastOpenedAt")) put(field, song.optLong(field))
        } }))
        root.put("setlists", JSONArray(objects(setlists).map { setlist -> JSONObject()
            .put("id", id(setlist)).put("name", setlist.getString("name")).put("createdAt", setlist.optLong("createdAt"))
            .put("songs", JSONArray(objects(setlist.getJSONArray("songs")).map { ref -> JSONObject()
                .put("id", id(ref)).put("title", ref.getString("title")).put("artist", ref.optString("artist")) })) }))
        return root
    }
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value?.toString() ?: "null"
    }
    fun same(a: JSONObject?, b: JSONObject?): Boolean = canonical(a) == canonical(b)
    fun sameLibrary(a: JSONObject, b: JSONObject): Boolean =
        listOf("songs", "setlists").all { field ->
            objects(a.getJSONArray(field)).associate { id(it) to canonical(it) } == objects(b.getJSONArray(field)).associate { id(it) to canonical(it) }
        }
    fun merge(local: JSONObject, remote: JSONObject, base: JSONObject?): JSONObject {
        val aligned = SyncDedup.align(listOf(local, remote) + listOfNotNull(base))
        val cleanLocal = aligned[0]; val cleanRemote = aligned[1]; val cleanBase = aligned.getOrNull(2)
        val result = JSONObject(cleanRemote.toString()) // Preserve web-only settings.
        for (field in listOf("songs", "setlists")) {
            val l = objects(cleanLocal.getJSONArray(field)).associateBy(::id)
            val r = objects(cleanRemote.getJSONArray(field)).associateBy(::id)
            val b = cleanBase?.let { objects(it.getJSONArray(field)).associateBy(::id) }.orEmpty()
            val values = (l.keys + r.keys + b.keys).mapNotNull { key ->
                val left = l[key]; val right = r[key]; val before = b[key]
                when {
                    same(left, right) -> left
                    same(left, before) -> right
                    same(right, before) -> left
                    left != null && right != null && field == "setlists" -> JSONObject(right.toString())
                        .put("songs", SyncDedup.union(left.getJSONArray("songs"), right.getJSONArray("songs")))
                    before == null -> right ?: left
                    else -> error("Conflicting edits to $field. Resolve backups before syncing.")
                }
            }
            result.put(field, JSONArray(values))
        }
        return parse(SyncDedup.deduplicate(result).toString())
    }
}
