package com.joel.gta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.joel.gta.data.local.dao.ChordDao
import com.joel.gta.data.local.dao.SearchHistoryDao
import com.joel.gta.data.local.dao.SetlistDao
import com.joel.gta.data.local.dao.SongDao
import com.joel.gta.data.local.entity.ChordVoicingEntity
import com.joel.gta.data.local.entity.SearchHistoryEntity
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        SetlistEntity::class,
        SetlistSongCrossRef::class,
        ChordVoicingEntity::class,
        SearchHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class GtaDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun chordDao(): ChordDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: GtaDatabase? = null

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_isDeleted ON songs(isDeleted)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chord_voicings (
                        chord TEXT NOT NULL PRIMARY KEY,
                        baseFret INTEGER NOT NULL DEFAULT 1,
                        frets TEXT NOT NULL,
                        fingers TEXT NOT NULL DEFAULT '',
                        barres TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chord_voicings_chord ON chord_voicings(chord)")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isImported INTEGER NOT NULL DEFAULT 0,
                        importedSongId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")
            }
        }

        fun getDatabase(context: Context): GtaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GtaDatabase::class.java,
                    "gta_database.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

