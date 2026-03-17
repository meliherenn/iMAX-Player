package com.imax.player.di

import android.content.Context
import androidx.room.Room
import com.imax.player.core.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ImaxDatabase {
        return Room.databaseBuilder(
            context,
            ImaxDatabase::class.java,
            "imax_player.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides fun providePlaylistDao(db: ImaxDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideChannelDao(db: ImaxDatabase): ChannelDao = db.channelDao()
    @Provides fun provideMovieDao(db: ImaxDatabase): MovieDao = db.movieDao()
    @Provides fun provideSeriesDao(db: ImaxDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideEpisodeDao(db: ImaxDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideCategoryDao(db: ImaxDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideEpgDao(db: ImaxDatabase): EpgDao = db.epgDao()
    @Provides fun provideWatchHistoryDao(db: ImaxDatabase): WatchHistoryDao = db.watchHistoryDao()
    @Provides fun provideFavoriteDao(db: ImaxDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideMetadataCacheDao(db: ImaxDatabase): MetadataCacheDao = db.metadataCacheDao()
}
