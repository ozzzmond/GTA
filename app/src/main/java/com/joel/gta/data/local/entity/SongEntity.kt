package com.joel.gta.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastOpenedAt"]),
        Index(value = ["createdAt"])
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val capo: String? = null,
    val rawContent: String,
    val format: String = "TWO_LINE",
    val isFavorite: Boolean = false,
    val transposeOffset: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)
