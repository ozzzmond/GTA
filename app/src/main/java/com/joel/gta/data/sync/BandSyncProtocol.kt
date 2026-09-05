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
        val songId: Long? = null,
        val setlistIndex: Int = 0,
        val title: String = "",
        val artist: String? = null,
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
     * Keepalive heartbeat to maintain socket liveness.
     */
    object Heartbeat : SyncMessage()

    companion object {
        private const val TYPE_SONG = "SONG"
        private const val TYPE_SONG_CHANGE = "SONG_CHANGE"
        private const val TYPE_SCROLL = "SCROLL"
        private const val TYPE_TEMPO = "TEMPO"
        private const val TYPE_JOIN = "JOIN"
        private const val TYPE_PING = "PING"

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
                    json.put("type", TYPE_SONG_CHANGE)
                    if (message.songId != null) {
                        json.put("songId", message.songId)
                        json.put("id", message.songId)
                    }
                    json.put("setlistIndex", message.setlistIndex)
                    json.put("title", message.title)
                    if (!message.artist.isNullOrBlank()) json.put("artist", message.artist)
                    json.put("content", message.rawContent)
                    json.put("rawContent", message.rawContent)
                    if (!message.key.isNullOrBlank()) json.put("key", message.key)
                    if (!message.capo.isNullOrBlank()) json.put("capo", message.capo)
                }
                is ScrollSync -> {
                    json.put("type", TYPE_SCROLL)
                    json.put("scroll", message.scrollFraction.toDouble())
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
                        SongChange(
                            songId = songId,
                            setlistIndex = json.optInt("setlistIndex", 0),
                            title = json.optString("title", ""),
                            artist = json.optString("artist").takeIf { it.isNotBlank() },
                            rawContent = content,
                            key = json.optString("key").takeIf { it.isNotBlank() },
                            capo = json.optString("capo").takeIf { it.isNotBlank() }
                        )
                    }
                    TYPE_SCROLL -> ScrollSync(
                        scrollFraction = json.getDouble("scroll").toFloat().coerceIn(0f, 1f)
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
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
