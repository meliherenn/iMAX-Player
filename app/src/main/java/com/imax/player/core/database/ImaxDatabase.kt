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
    version = 2,
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
                // Add new columns to metadata_cache
                db.execSQL("ALTER TABLE metadata_cache ADD COLUMN language TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE metadata_cache ADD COLUMN tagline TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE metadata_cache ADD COLUMN trailerUrl TEXT NOT NULL DEFAULT ''")
                // Recreate index for locale-aware cache
                db.execSQL("DROP INDEX IF EXISTS index_metadata_cache_title_year")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_cache_title_year_language ON metadata_cache (title, year, language)")
            }
        }
    }
}
