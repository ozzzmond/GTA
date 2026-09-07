package com.joel.gta.data.sync

import org.json.JSONObject

sealed class SyncMessage {

    /**
     * Broadcast by the Host when a song is opened or changed.
     */
    data class SongSync(
        val title: String,
        val artist: String? = null,
        val rawContent: String,
        val key: String? = null,
        val capo: String? = null,
        val songId: Long? = null,
        val setlistIndex: Int? = null
    ) : SyncMessage()

    /**
     * Broadcast by the Host when navigating songs in Setlist Mode
     * (via swipe gesture, next/prev buttons, or Bluetooth pedal footswitch).
     */
    data class SongChange(
        val title: String = "",
        val artist: String? = null,
        val queueType: String = "LIBRARY", // "SETLIST" or "LIBRARY"
        val queueIndex: Int = 0,
        val transpose: Int = 0,
        val scrollProgress: Float = 0f,
        val songId: Long? = null,
        val setlistIndex: Int = 0,
        val rawContent: String = "",
        val key: String? = null,
        val capo: String? = null
    ) : SyncMessage()

    /**
     * Broadcast by the Host when the stage viewer is scrolled.
     * [scrollFraction] ranges from 0.0f (top) to 1.0f (bottom).
     */
    data class ScrollSync(
        val scrollFraction: Float
    ) : SyncMessage()

    /**
     * Broadcast by the Host when the key transpose offset is changed.
     */
    data class TransposeSync(
        val transposeOffset: Int
    ) : SyncMessage()

    /**
     * Broadcast by the Host when the stage metronome tempo is adjusted or started/stopped.
     */
    data class TempoSync(
        val bpm: Int,
        val isPlaying: Boolean,
        val beatsPerMeasure: Int = 4
    ) : SyncMessage()

    /**
     * Sent by a client device upon connecting to identify itself.
     */
    data class ClientJoin(
        val clientName: String
    ) : SyncMessage()

    /**
     * Sent by the Band Leader to push an entire setlist with complete song data
     * directly to all connected Band Members.
     */
    data class SetlistSongItem(
        val title: String,
        val artist: String? = null,
        val key: String? = null,
        val capo: String? = null,
        val bpm: String? = null,
        val format: String = "CHORD_PRO",
        val rawContent: String = ""
    )

    data class SetlistSync(
        val setlistName: String,
        val songs: List<SetlistSongItem>
    ) : SyncMessage()

    /**
     * Keepalive heartbeat to maintain socket liveness.
     */
    object Heartbeat : SyncMessage()

    companion object {
        private const val TYPE_SONG = "SONG"
        private const val TYPE_SONG_CHANGE = "SONG_CHANGE"
        private const val TYPE_SCROLL = "SCROLL"
        private const val TYPE_TRANSPOSE = "TRANSPOSE"
        private const val TYPE_TEMPO = "TEMPO"
        private const val TYPE_JOIN = "JOIN"
        private const val TYPE_PING = "PING"
        const val TYPE_SETLIST_SYNC = "SETLIST_SYNC"

        /**
         * Serializes a SyncMessage into a single-line JSON string suitable for line-based streaming.
         */
        fun serialize(message: SyncMessage): String {
            val json = JSONObject()
            when (message) {
                is SongSync -> {
                    json.put("type", TYPE_SONG)
                    json.put("title", message.title)
                    json.put("artist", message.artist ?: "")
                    json.put("content", message.rawContent)
                    json.put("key", message.key ?: "")
                    json.put("capo", message.capo ?: "")
                    if (message.songId != null) json.put("id", message.songId)
                    if (message.setlistIndex != null) json.put("setlistIndex", message.setlistIndex)
                }
                is SongChange -> {
                    val effectiveIndex = if (message.queueIndex != 0) message.queueIndex else message.setlistIndex
                    json.put("type", TYPE_SONG_CHANGE)
                    json.put("title", message.title)
                    json.put("artist", message.artist ?: "")
                    json.put("queueType", message.queueType)
                    json.put("queueIndex", effectiveIndex)
                    json.put("setlistIndex", effectiveIndex)
                    json.put("transpose", message.transpose)
                    json.put("scrollProgress", message.scrollProgress.toDouble())
                    if (message.songId != null) {
                        json.put("songId", message.songId)
                        json.put("id", message.songId)
                    }
                    if (message.rawContent.isNotBlank()) {
                        json.put("content", message.rawContent)
                        json.put("rawContent", message.rawContent)
                    }
                    if (!message.key.isNullOrBlank()) json.put("key", message.key)
                    if (!message.capo.isNullOrBlank()) json.put("capo", message.capo)
                }
                is ScrollSync -> {
                    json.put("type", TYPE_SCROLL)
                    json.put("scroll", message.scrollFraction.toDouble())
                }
                is TransposeSync -> {
                    json.put("type", TYPE_TRANSPOSE)
                    json.put("transposeOffset", message.transposeOffset)
                    json.put("offset", message.transposeOffset)
                }
                is TempoSync -> {
                    json.put("type", TYPE_TEMPO)
                    json.put("bpm", message.bpm)
                    json.put("playing", message.isPlaying)
                    json.put("beats", message.beatsPerMeasure)
                }
                is ClientJoin -> {
                    json.put("type", TYPE_JOIN)
                    json.put("name", message.clientName)
                }
                is SetlistSync -> {
                    json.put("type", TYPE_SETLIST_SYNC)
                    json.put("setlistName", message.setlistName)
                    val arr = org.json.JSONArray()
                    for (s in message.songs) {
                        val sObj = JSONObject().apply {
                            put("title", s.title)
                            put("artist", s.artist ?: "")
                            put("key", s.key ?: "")
                            put("capo", s.capo ?: "")
                            put("bpm", s.bpm ?: "")
                            put("format", s.format)
                            put("rawContent", s.rawContent)
                        }
                        arr.put(sObj)
                    }
                    json.put("songs", arr)
                }
                is Heartbeat -> {
                    json.put("type", TYPE_PING)
                }
            }
            return json.toString()
        }

        /**
         * Deserializes a single-line JSON string into a typed SyncMessage.
         */
        fun deserialize(line: String): SyncMessage? {
            return try {
                val json = JSONObject(line.trim())
                when (json.optString("type")) {
                    TYPE_SONG -> SongSync(
                        title = json.getString("title"),
                        artist = json.optString("artist").takeIf { it.isNotBlank() },
                        rawContent = json.getString("content"),
                        key = json.optString("key").takeIf { it.isNotBlank() },
                        capo = json.optString("capo").takeIf { it.isNotBlank() },
                        songId = if (json.has("id")) json.getLong("id") else if (json.has("songId")) json.getLong("songId") else null,
                        setlistIndex = if (json.has("setlistIndex")) json.getInt("setlistIndex") else null
                    )
                    TYPE_SONG_CHANGE -> {
                        val songId = when {
                            json.has("songId") -> json.getLong("songId")
                            json.has("id") -> json.getLong("id")
                            else -> null
                        }
                        val content = when {
                            json.has("content") -> json.getString("content")
                            json.has("rawContent") -> json.getString("rawContent")
                            else -> ""
                        }
                        val queueType = if (json.has("queueType")) {
                            json.getString("queueType").uppercase()
                        } else if (json.has("setlistIndex")) {
                            "SETLIST"
                        } else {
                            "LIBRARY"
                        }
                        val queueIndex = when {
                            json.has("queueIndex") -> json.getInt("queueIndex")
                            json.has("setlistIndex") -> json.getInt("setlistIndex")
                            else -> 0
                        }
                        val transpose = when {
                            json.has("transpose") -> json.getInt("transpose")
                            json.has("transposeOffset") -> json.getInt("transposeOffset")
                            json.has("offset") -> json.getInt("offset")
                            else -> 0
                        }
                        val scrollProgress = when {
                            json.has("scrollProgress") -> json.getDouble("scrollProgress").toFloat()
                            json.has("scroll") -> json.getDouble("scroll").toFloat()
                            else -> 0f
                        }
                        SongChange(
                            title = json.optString("title", ""),
                            artist = json.optString("artist").takeIf { it.isNotBlank() },
                            queueType = queueType,
                            queueIndex = queueIndex,
                            transpose = transpose,
                            scrollProgress = scrollProgress,
                            songId = songId,
                            setlistIndex = queueIndex,
                            rawContent = content,
                            key = json.optString("key").takeIf { it.isNotBlank() },
                            capo = json.optString("capo").takeIf { it.isNotBlank() }
                        )
                    }
                    TYPE_SCROLL -> ScrollSync(
                        scrollFraction = json.getDouble("scroll").toFloat().coerceIn(0f, 1f)
                    )
                    TYPE_TRANSPOSE -> TransposeSync(
                        transposeOffset = if (json.has("transposeOffset")) json.getInt("transposeOffset") else json.optInt("offset", 0)
                    )
                    TYPE_TEMPO -> TempoSync(
                        bpm = json.getInt("bpm"),
                        isPlaying = json.getBoolean("playing"),
                        beatsPerMeasure = json.optInt("beats", 4)
                    )
                    TYPE_JOIN -> ClientJoin(
                        clientName = json.optString("name", "Band Member")
                    )
                    TYPE_PING -> Heartbeat
                    TYPE_SETLIST_SYNC -> {
                        val setlistName = json.optString("setlistName", "Band Setlist").ifBlank { "Band Setlist" }
                        val arr = json.optJSONArray("songs") ?: org.json.JSONArray()
                        val songList = mutableListOf<SetlistSongItem>()
                        for (i in 0 until arr.length()) {
                            val sObj = arr.getJSONObject(i)
                            val title = sObj.optString("title", "Untitled Song").ifBlank { "Untitled Song" }
                            val artist = sObj.optString("artist").takeIf { it.isNotBlank() }
                            val key = sObj.optString("key").takeIf { it.isNotBlank() }
                            val capo = sObj.optString("capo").takeIf { it.isNotBlank() }
                            val bpm = sObj.optString("bpm").takeIf { it.isNotBlank() }
                            val format = sObj.optString("format", "CHORD_PRO")
                            val rawContent = when {
                                sObj.has("rawContent") -> sObj.getString("rawContent")
                                sObj.has("content") -> sObj.getString("content")
                                sObj.has("chordsContent") -> sObj.getString("chordsContent")
                                else -> ""
                            }
                            songList.add(
                                SetlistSongItem(
                                    title = title,
                                    artist = artist,
                                    key = key,
                                    capo = capo,
                                    bpm = bpm,
                                    format = format,
                                    rawContent = rawContent
                                )
                            )
                        }
                        SetlistSync(setlistName = setlistName, songs = songList)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
