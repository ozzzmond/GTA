package com.joel.gta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.joel.gta.data.local.dao.SetlistDao
import com.joel.gta.data.local.dao.SongDao
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        SetlistEntity::class,
        SetlistSongCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GtaDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao

    companion object {
        @Volatile
        private var INSTANCE: GtaDatabase? = null

        fun getDatabase(context: Context): GtaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GtaDatabase::class.java,
                    "gta_database.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
