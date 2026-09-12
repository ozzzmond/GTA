package com.joel.gta.data.sync

import android.content.Context
import org.json.JSONObject
import java.util.UUID

class SyncJournal(context: Context, private val account: String) {
    private val prefs = context.getSharedPreferences("gtar_sync_v1", Context.MODE_PRIVATE)
    private var state = JSONObject()
    init {
        require(account.isNotBlank()) { "Verified account ID is required." }
        val owner = prefs.getString("owner", null)
        check(owner == null || owner == account) { "Device library belongs to another Google account. Sign in to that account and export a backup before moving data." }
        check(prefs.edit().putString("owner", account).commit()) { "Cannot persist sync account." }
        state = prefs.getString("state:$account", null)?.let(::JSONObject) ?: JSONObject().put("version", 1)
        check(state.getInt("version") == 1) { "Unsupported sync recovery state." }
    }
    fun baseline(): JSONObject? = state.optJSONObject("pending")?.takeIf { it.optBoolean("acknowledged") }?.getJSONObject("merged")
        ?: state.optJSONObject("baseline")
    fun resume(local: JSONObject): JSONObject {
        val pending = state.optJSONObject("pending") ?: return local
        return if (pending.optBoolean("acknowledged")) SyncPayload.merge(local, pending.getJSONObject("merged"), pending.getJSONObject("before")) else local
    }
    fun archive(local: JSONObject, remote: JSONObject?) {
        val snapshot = JSONObject().put("local", local).put("remote", remote ?: JSONObject.NULL).put("journal", state)
        check(prefs.edit().putString("recovery:$account:${UUID.randomUUID()}", snapshot.toString()).commit()) { "Cannot save recovery snapshot; sync stopped." }
    }
    fun prepare(before: JSONObject, merged: JSONObject) {
        state.put("baseline", baseline() ?: JSONObject.NULL)
        state.put("pending", JSONObject().put("before", before).put("merged", merged).put("acknowledged", false))
        save()
    }
    fun recovery(): JSONObject = JSONObject().apply {
        prefs.all.filterKeys { it.startsWith("recovery:$account:") }.forEach { (key, value) -> put(key, JSONObject(value as String)) }
    }
    fun complete() { state.put("baseline", state.getJSONObject("pending").getJSONObject("merged")); state.remove("pending"); save() }
    fun acknowledge() { state.getJSONObject("pending").put("acknowledged", true); save() }
    private fun save() { check(prefs.edit().putString("state:$account", state.toString()).commit()) { "Cannot save sync recovery state." } }
}
