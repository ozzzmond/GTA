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
import com.joel.gta.data.sync.SyncPayload
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

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
    private var baseline: JSONObject? = null
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
        viewModelScope.launch {
            for (change in changes) {
                delay(1500)
                while (changes.tryReceive().isSuccess) delay(1500)
                sync(false)
            }
        }
        GoogleSignIn.getLastSignedInAccount(application)?.let { account ->
            if (GoogleSignIn.hasPermissions(account, Scope(DriveAppDataClient.SCOPE))) {
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
            generation++; baseline = null
            client = DriveAppDataClient(getApplication(), account)
            mutableState.value = GoogleSyncState(account.email)
            syncNow()
        } catch (_: Exception) { mutableState.value = GoogleSyncState(status = "Error: Sign-in cancelled or unavailable. Local editing is available.") }
    }
    fun signOut() {
        generation++; client = null; baseline = null
        mutableState.value = GoogleSyncState()
        auth.signOut()
    }
    fun syncNow() { viewModelScope.launch { sync(true) } }
    private suspend fun sync(manual: Boolean) {
        mutex.lock()
        val epoch = generation
        try {
            val service = client ?: return
            val local = withContext(Dispatchers.IO) { store.snapshot() }
            if (!manual && baseline?.let { SyncPayload.sameLibrary(local, it) } == true) return
            mutableState.value = mutableState.value.copy(status = "Syncing", busy = true)
            val cloud = withContext(Dispatchers.IO) { service.pull() }
            if (epoch != generation) return
            val merged = if (cloud == null) local else SyncPayload.merge(local, cloud, baseline)
            // Restore to Room first, independently of upload success.
            val normalized = withContext(Dispatchers.IO) { store.apply(local, merged) }
            if (epoch != generation) return
            if (baseline == null && cloud != null) {
                baseline = cloud
                mutableState.value = mutableState.value.copy(status = "Synced: Cloud library restored", busy = false)
                changes.trySend(Unit)
                return
            }
            merged.put("songs", normalized.getJSONArray("songs")).put("setlists", normalized.getJSONArray("setlists"))
                .put("exportedAt", java.time.Instant.now().toString())
            for (setlist in SyncPayload.objects(merged.getJSONArray("setlists"))) {
                setlist.put("songIds", org.json.JSONArray(SyncPayload.objects(setlist.getJSONArray("songs")).map { SyncPayload.id(it) }))
            }
            withContext(Dispatchers.IO) { service.push(merged) }
            if (epoch != generation) return
            baseline = normalized
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
