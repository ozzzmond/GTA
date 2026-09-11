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
        const val REVISION_NAME = "gtar_songbook_revision_v1.json"
        const val FIELDS = "id,name,version,modifiedTime,md5Checksum"
    }
    private val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SCOPE)).apply { selectedAccount = account.account }
    private val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(),
        { request -> credential.initialize(request); request.connectTimeout = 15000; request.readTimeout = 30000 })
        .setApplicationName("GTAR").build()
    private fun revision(file: File): CloudRevision {
        val version = file.version?.let { "version:$it" }
            ?: file.modifiedTime?.let { "modifiedTime:$it" } ?: file.md5Checksum?.let { "md5Checksum:$it" }
            ?: error("Drive revision unavailable. Try again.")
        return CloudRevision(requireNotNull(file.id), file.name ?: "", version)
    }
    private val protocol = RevisionSyncProtocol(object : RevisionTransport {
        override fun list(): List<CloudRevision> {
            val files = mutableListOf<CloudRevision>()
            var page: String? = null
            do {
                val result = drive.files().list().setSpaces("appDataFolder")
                    .setQ("(name = '$NAME' or name = '$REVISION_NAME') and trashed = false")
                    .setFields("files($FIELDS),nextPageToken").setPageToken(page).execute()
                files.addAll(result.files.map(::revision))
                page = result.nextPageToken
            } while (page != null)
            return files
        }
        override fun read(file: CloudRevision): String = drive.files().get(file.id).executeMediaAsInputStream().bufferedReader().use { it.readText() }
        override fun metadata(file: CloudRevision) = revision(drive.files().get(file.id).setFields(FIELDS).execute())
        override fun create(payload: JSONObject): CloudRevision {
            val content = ByteArrayContent.fromString("application/json", payload.toString())
            return revision(drive.files().create(File().setName(REVISION_NAME).setParents(listOf("appDataFolder")), content).setFields(FIELDS).execute())
        }
    })
    fun pull(): JSONObject? = protocol.pull()
    fun prepareResolution() = protocol.prepareResolution()
    fun push(payload: JSONObject) = protocol.push(payload)
    fun recovery(): org.json.JSONArray = protocol.recovery()
}
