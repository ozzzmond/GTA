package com.joel.gta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "setlist_songs",
    primaryKeys = ["setlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = SetlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["setlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["setlistId"]),
        Index(value = ["songId"])
    ]
)
data class SetlistSongCrossRef(
    val setlistId: Long,
    val songId: Long,
    val position: Int = 0
)
