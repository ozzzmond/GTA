package com.joel.gta.data.sync

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import org.json.JSONObject

class DriveAppDataClient(context: Context, account: GoogleSignInAccount) {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val NAME = "gtar_songbook_sync.json"
        const val FIELDS = "id,name,version,modifiedTime,md5Checksum"
    }
    private val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SCOPE)).apply { selectedAccount = account.account }
    private val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(),
        { request -> credential.initialize(request); request.connectTimeout = 15000; request.readTimeout = 30000 })
        .setApplicationName("GTAR").build()
    private var pulled = false
    private var baseline: File? = null
    private fun revision(file: File): String = file.version?.let { "version:$it" }
        ?: file.modifiedTime?.let { "modified:$it" } ?: file.md5Checksum?.let { "md5:$it" }
        ?: error("Drive revision unavailable. Try again.")
    private fun find(): File? {
        val result = drive.files().list().setSpaces("appDataFolder")
            .setQ("name = '$NAME' and trashed = false").setFields("files($FIELDS),nextPageToken").execute()
        check(result.nextPageToken == null && result.files.size <= 1) { "Multiple sync backups found. Resolve duplicates before syncing." }
        return result.files.firstOrNull()
    }
    private fun unchanged(a: File?, b: File?): Boolean = if (a == null || b == null) a == b else a.id == b.id && revision(a) == revision(b)
    fun pull(): JSONObject? {
        pulled = false
        val file = find()
        if (file == null) { baseline = null; pulled = true; return null }
        val raw = drive.files().get(file.id).executeMediaAsInputStream().bufferedReader().use { it.readText() }
        val payload = SyncPayload.parse(raw)
        val after = drive.files().get(file.id).setFields(FIELDS).execute()
        check(unchanged(file, after)) { "Cloud changed during download. Sync again." }
        baseline = after; pulled = true
        return payload
    }
    fun push(payload: JSONObject) {
        check(pulled) { "Pull and validate before uploading." }
        SyncPayload.parse(payload.toString())
        val current = find()
        check(unchanged(current, baseline)) { "Cloud changed since download. Sync again." }
        if (current != null) check(unchanged(drive.files().get(current.id).setFields(FIELDS).execute(), baseline)) { "Cloud changed before upload." }
        val content = ByteArrayContent.fromString("application/json", payload.toString())
        pulled = false // An uncertain upload must be downloaded again.
        baseline = if (current == null) drive.files().create(File().setName(NAME).setParents(listOf("appDataFolder")), content).setFields(FIELDS).execute()
            else drive.files().update(current.id, File().setName(NAME), content).setFields(FIELDS).execute()
        revision(baseline!!)
        pulled = true
    }
}
