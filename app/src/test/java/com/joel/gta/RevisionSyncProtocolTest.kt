package com.joel.gta

import com.joel.gta.data.sync.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RevisionSyncProtocolTest {
    private class Server : RevisionTransport {
        val files = linkedMapOf<CloudRevision, String>()
        var loseResponse = false
        override fun list() = files.keys.toList()
        override fun read(file: CloudRevision) = files.getValue(file)
        override fun metadata(file: CloudRevision) = file
        override fun create(payload: JSONObject): CloudRevision {
            val file = CloudRevision("file-${files.size + 1}", DriveAppDataClient.REVISION_NAME, "version:1")
            files[file] = payload.toString()
            if (loseResponse) { loseResponse = false; error("response lost") }
            return file
        }
    }
    private fun payload(content: String) = SyncPayload.parse("""{"songs":[{"id":"A","title":"Song","rawContent":"$content"}],"setlists":[]}""")
    @Test fun interleavedWritersRetainBothBranches() {
        val server = Server(); val one = RevisionSyncProtocol(server); val two = RevisionSyncProtocol(server)
        assertNull(one.pull()); assertNull(two.pull())
        one.push(payload("First")); two.push(payload("Second"))
        assertEquals(2, server.files.size)
        try { one.pull(); fail("Must stop on divergent branches") } catch (error: IllegalStateException) { assertTrue(error.message!!.contains("Conflicting")) }
        assertEquals(2, one.recovery().length())
        one.prepareResolution(); one.push(payload("Resolved both"))
        assertEquals(3, server.files.size)
        assertEquals(2, JSONObject(server.files.values.last()).getJSONArray("syncParents").length())
        assertTrue(SyncPayload.sameLibrary(payload("Resolved both"), one.pull()!!))
    }
    @Test fun uncertainCreateRequiresPullAndRetainsHistoryWithoutEtags() {
        val server = Server(); val client = RevisionSyncProtocol(server)
        client.pull(); server.loseResponse = true
        try { client.push(payload("First")); fail("Simulated response loss") } catch (_: IllegalStateException) { }
        try { client.push(payload("First")); fail("Must require another pull") } catch (_: IllegalStateException) { }
        assertTrue(SyncPayload.sameLibrary(payload("First"), client.pull()!!))
        client.push(payload("Second"))
        assertEquals(2, server.files.size)
        assertEquals("file-1@version:1", JSONObject(server.files.values.last()).getJSONArray("syncParents").getString(0))
        assertTrue(SyncPayload.sameLibrary(payload("Second"), client.pull()!!))
    }
    @Test fun identicalInitialSiblingsCanBeAcknowledgedTogether() {
        val server = Server(); val one = RevisionSyncProtocol(server); val two = RevisionSyncProtocol(server)
        one.pull(); two.pull(); one.push(payload("Same")); two.push(payload("Same"))
        one.pull(); one.push(payload("Same"))
        assertEquals(2, JSONObject(server.files.values.last()).getJSONArray("syncParents").length())
        assertEquals(3, server.files.size)
    }
}
