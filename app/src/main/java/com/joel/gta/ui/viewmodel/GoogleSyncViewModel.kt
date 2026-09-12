package com.joel.gta.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.InvalidationTracker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.sync.DriveAppDataClient
import com.joel.gta.data.sync.RoomSyncStore
import com.joel.gta.data.sync.SyncJournal
import com.joel.gta.data.sync.SyncPayload
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

data class GoogleSyncState(val email: String? = null, val status: String = "Idle", val busy: Boolean = false)

@Suppress("DEPRECATION")
class GoogleSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail().requestProfile().requestScopes(Scope(DriveAppDataClient.SCOPE)).build()
    private val auth = GoogleSignIn.getClient(application, options)
    private val database = GtaDatabase.getDatabase(application)
    private val store = RoomSyncStore(database)
    private val mutableState = MutableStateFlow(GoogleSyncState())
    val state = mutableState.asStateFlow()
    private var client: DriveAppDataClient? = null
    private var accountId: String? = null
    private var generation = 0
    private val mutex = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val observer = object : InvalidationTracker.Observer("songs", "setlists", "setlist_songs") {
        override fun onInvalidated(tables: Set<String>) { changes.trySend(Unit) }
    }
    private val connectivity = application.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { syncNow() }
    }
    init {
        connectivity.registerDefaultNetworkCallback(networkCallback)
        database.invalidationTracker.addObserver(observer)
        syncNow() // Also repairs offline libraries without a signed-in account.
        viewModelScope.launch {
            for (change in changes) {
                delay(300_000L)
                while (changes.tryReceive().isSuccess) delay(300_000L)
                sync()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(3_600_000L)
                sync()
            }
        }
        GoogleSignIn.getLastSignedInAccount(application)?.let { account ->
            if (GoogleSignIn.hasPermissions(account, Scope(DriveAppDataClient.SCOPE))) {
                accountId = account.id
                client = DriveAppDataClient(application, account)
                mutableState.value = GoogleSyncState(account.email)
                syncNow()
            }
        }
    }
    fun signInIntent(): Intent = auth.signInIntent
    fun signInResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)
            check(GoogleSignIn.hasPermissions(account, Scope(DriveAppDataClient.SCOPE))) { "Allow Drive application data access." }
            generation++; accountId = account.id
            client = DriveAppDataClient(getApplication(), account)
            mutableState.value = GoogleSyncState(account.email)
            syncNow()
        } catch (_: Exception) { mutableState.value = GoogleSyncState(status = "Error: Sign-in cancelled or unavailable. Local editing is available.") }
    }
    fun signOut() {
        generation++; client = null; accountId = null
        mutableState.value = GoogleSyncState()
        auth.signOut()
    }
    fun publishResolvedLibrary() { viewModelScope.launch {
        mutex.lock()
        val epoch = generation
        try {
            val service = client ?: return@launch
            val local = withContext(Dispatchers.IO) { store.snapshot() }
            val journal = SyncJournal(getApplication(), requireNotNull(accountId))
            journal.archive(local, null)
            mutableState.value = mutableState.value.copy(status = "Publishing resolved library", busy = true)
            withContext(Dispatchers.IO) { service.prepareResolution() }
            if (epoch != generation) return@launch
            journal.prepare(local, local)
            withContext(Dispatchers.IO) { service.push(local) }
            if (epoch != generation) return@launch
            journal.acknowledge(); journal.complete()
            mutableState.value = mutableState.value.copy(status = "Resolved library published; previous revisions retained", busy = false)
        } catch (error: Exception) {
            if (epoch == generation) mutableState.value = mutableState.value.copy(status = "Resolution failed: ${error.message}", busy = false)
        } finally { mutex.unlock() }
    } }
    fun exportRecovery(uri: android.net.Uri) {
        val account = accountId
        val service = client
        viewModelScope.launch {
            try {
                val archive = withContext(Dispatchers.IO) {
                    org.json.JSONObject().put("exportType", "RECOVERY_ARCHIVE").put("local", store.snapshot()).apply {
                        if (account != null) put("snapshots", SyncJournal(getApplication(), account).recovery())
                        try { if (service != null) put("cloud", service.recovery()) }
                        catch (error: Exception) { put("cloudError", error.message) }
                    }
                }
                withContext(Dispatchers.IO) {
                    requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(archive.toString(2)) }
                }
                mutableState.value = mutableState.value.copy(status = "Recovery archive exported; inspect cloudError for download failures.")
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(status = "Recovery export failed: ${error.message}") }
        }
    }
    fun syncNow() { viewModelScope.launch { sync() } }
    private suspend fun sync() {
        mutex.lock()
        val epoch = generation
        try {
            val local = withContext(Dispatchers.IO) { store.cleanup() }
            val service = client ?: return
            val journal = SyncJournal(getApplication(), requireNotNull(accountId))
            val resumed = journal.resume(local)
            mutableState.value = mutableState.value.copy(status = "Syncing", busy = true)
            journal.archive(local, null)
            val cloud = withContext(Dispatchers.IO) { service.pull() }
            if (epoch != generation) return
            journal.archive(local, cloud)
            val merged = if (cloud == null) resumed else SyncPayload.merge(resumed, cloud, journal.baseline())
            journal.prepare(local, merged)
            merged.put("exportedAt", java.time.Instant.now().toString())
            for (setlist in SyncPayload.objects(merged.getJSONArray("setlists"))) {
                setlist.put("songIds", org.json.JSONArray(SyncPayload.objects(setlist.getJSONArray("songs")).map { SyncPayload.id(it) }))
            }
            withContext(Dispatchers.IO) { service.push(merged) }
            if (epoch != generation) return
            journal.acknowledge()
            withContext(Dispatchers.IO) { store.apply(local, merged) }
            journal.complete()
            mutableState.value = mutableState.value.copy(status = "Synced", busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (epoch == generation) mutableState.value = mutableState.value.copy(status = "Error: ${error.message ?: "Sync unavailable. Sign in again."}", busy = false)
        } finally { mutex.unlock() }
    }
    override fun onCleared() {
        connectivity.unregisterNetworkCallback(networkCallback)
        database.invalidationTracker.removeObserver(observer)
        changes.close()
        super.onCleared()
    }
}
