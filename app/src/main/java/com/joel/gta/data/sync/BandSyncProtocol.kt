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
        val songId: Long? = null
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
                        songId = if (json.has("id")) json.getLong("id") else null
                    )
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
