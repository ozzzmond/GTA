package com.joel.gta.data.sync

import org.json.JSONArray
import org.json.JSONObject

data class CloudRevision(val id: String, val name: String, val revision: String) {
    val key: String get() = "$id@$revision"
}
interface RevisionTransport {
    fun list(): List<CloudRevision>
    fun read(file: CloudRevision): String
    fun metadata(file: CloudRevision): CloudRevision
    fun create(payload: JSONObject): CloudRevision
}
/** Create-only revisions retain every writer's data. Ancestry acknowledges only observed versions. */
class RevisionSyncProtocol(private val transport: RevisionTransport) {
    private var parents: List<String>? = null
    fun pull(): JSONObject? = readState(false)
    fun prepareResolution() { readState(true) }
    private fun readState(allowConflicts: Boolean): JSONObject? {
        parents = null
        data class Record(val key: String, val payload: JSONObject, val parents: List<String>)
        val records = transport.list().map { file ->
            val raw = transport.read(file)
            val envelope = JSONObject(raw)
            val payload = SyncPayload.parse(raw)
            val ancestors = if (file.name == DriveAppDataClient.REVISION_NAME) {
                check(envelope.optInt("syncProtocol") == 1) { "Unsupported cloud revision." }
                val array = envelope.getJSONArray("syncParents")
                (0 until array.length()).map { index ->
                    check(array.get(index) is String) { "Invalid cloud ancestry." }
                    array.getString(index)
                }
            } else emptyList()
            check(file.key == transport.metadata(file).key) { "Cloud changed during download. Sync again." }
            Record(file.key, payload, ancestors)
        }
        val superseded = records.flatMap { it.parents }.toSet()
        val heads = records.filter { it.key !in superseded }
        check(records.isEmpty() || heads.isNotEmpty()) { "Invalid cloud revision history." }
        check(allowConflicts || heads.all { SyncPayload.sameLibrary(it.payload, heads.first().payload) }) {
            "Conflicting cloud revisions retained: ${heads.map { it.key }}. Export and reconcile backups before syncing."
        }
        parents = heads.map { it.key }
        return heads.firstOrNull()?.payload
    }
    fun push(payload: JSONObject) {
        val ancestors = checkNotNull(parents) { "Pull and validate before uploading." }
        SyncPayload.parse(payload.toString())
        val revisionPayload = JSONObject(payload.toString()).put("syncProtocol", 1).put("syncParents", JSONArray(ancestors))
        parents = null
        val uploaded = transport.create(revisionPayload)
        check(uploaded.id.isNotBlank() && uploaded.revision.isNotBlank()) { "Unexpected upload metadata." }
    }
    fun recovery(): JSONArray = JSONArray(transport.list().map { file ->
        JSONObject().put("file", file.key).put("raw", transport.read(file))
    })
}
