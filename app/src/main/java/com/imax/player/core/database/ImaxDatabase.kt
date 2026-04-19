package com.imax.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        CategoryEntity::class,
        EpgProgramEntity::class,
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        MetadataCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ImaxDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun metadataCacheDao(): MetadataCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE metadata_cache ADD COLUMN language TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE metadata_cache ADD COLUMN tagline TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE metadata_cache ADD COLUMN trailerUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS index_metadata_cache_title_year")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_cache_title_year_language ON metadata_cache (title, year, language)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add stream health check column to channels (defaults to online)
                db.execSQL("ALTER TABLE channels ADD COLUMN isOnline INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
