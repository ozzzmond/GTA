package com.joel.gta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "setlists",
    indices = [
        androidx.room.Index(value = ["isDeleted"])
    ]
)
data class SetlistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "''")
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
