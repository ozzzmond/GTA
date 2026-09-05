package com.joel.gta.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class SetlistWithSongs(
    @Embedded
    val setlist: SetlistEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SetlistSongCrossRef::class,
            parentColumn = "setlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)
