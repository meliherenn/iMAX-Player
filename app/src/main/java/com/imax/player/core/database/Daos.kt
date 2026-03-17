package com.imax.player.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY lastUsed DESC")
    fun getAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun getActiveFlow(): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE playlists SET isActive = 1, lastUsed = :time WHERE id = :id")
    suspend fun activate(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET channelCount = :channels, movieCount = :movies, seriesCount = :series, lastUpdated = :time WHERE id = :id")
    suspend fun updateCounts(id: Long, channels: Int, movies: Int, series: Int, time: Long = System.currentTimeMillis())
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder, name")
    fun getByPlaylist(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group ORDER BY sortOrder, name")
    fun getByGroup(playlistId: Long, group: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE playlistId = :playlistId ORDER BY groupTitle")
    fun getGroups(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun getFavorites(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(playlistId: Long, query: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE channels SET lastWatched = :time WHERE id = :id")
    suspend fun updateLastWatched(id: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY name")
    fun getByPlaylist(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name")
    fun getByCategory(playlistId: Long, category: String): Flow<List<MovieEntity>>

    @Query("SELECT DISTINCT categoryName FROM movies WHERE playlistId = :playlistId AND categoryName != '' ORDER BY categoryName")
    fun getCategories(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun getFavorites(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY lastWatched DESC LIMIT 20")
    fun getRecentlyWatched(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(playlistId: Long, query: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getById(id: Long): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("UPDATE movies SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE movies SET lastPosition = :position, totalDuration = :total, lastWatched = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, position: Long, total: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND lastPosition > 0 ORDER BY lastWatched DESC LIMIT 20")
    fun getContinueWatching(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryName = :category AND id != :excludeId ORDER BY rating DESC LIMIT 10")
    fun getSimilar(playlistId: Long, category: String, excludeId: Long): Flow<List<MovieEntity>>
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY name")
    fun getByPlaylist(playlistId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name")
    fun getByCategory(playlistId: Long, category: String): Flow<List<SeriesEntity>>

    @Query("SELECT DISTINCT categoryName FROM series WHERE playlistId = :playlistId AND categoryName != '' ORDER BY categoryName")
    fun getCategories(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun getFavorites(playlistId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(playlistId: Long, query: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: Long): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<SeriesEntity>)

    @Query("UPDATE series SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    fun getBySeries(seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :season ORDER BY episodeNumber")
    fun getBySeason(seriesId: Long, season: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT DISTINCT seasonNumber FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber")
    fun getSeasons(seriesId: Long): Flow<List<Int>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET lastPosition = :position, totalDuration = :total, lastWatched = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, position: Long, total: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteBySeries(seriesId: Long)

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :season AND episodeNumber = :episode LIMIT 1")
    suspend fun getEpisode(seriesId: Long, season: Int, episode: Int): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND lastPosition > 0 ORDER BY lastWatched DESC LIMIT 1")
    suspend fun getLastWatched(seriesId: Long): EpisodeEntity?
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND contentType = :type ORDER BY name")
    fun getByType(playlistId: Long, type: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime > :now ORDER BY startTime LIMIT 10")
    fun getPrograms(channelId: String, now: Long = System.currentTimeMillis()): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, now: Long = System.currentTimeMillis()): EpgProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE endTime < :time")
    suspend fun deleteOld(time: Long = System.currentTimeMillis())
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentByType(type: String, limit: Int = 50): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE progress < 0.95 AND progress > 0.02 ORDER BY timestamp DESC LIMIT :limit")
    fun getContinueWatching(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentId = :contentId AND contentType = :type LIMIT 1")
    suspend fun getByContent(contentId: Long, type: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchHistoryEntity): Long

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE contentType = :type ORDER BY timestamp DESC")
    fun getByType(type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId AND contentType = :type)")
    fun isFavorite(contentId: Long, type: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId AND contentType = :type")
    suspend fun delete(contentId: Long, type: String)
}

@Dao
interface MetadataCacheDao {
    @Query("SELECT * FROM metadata_cache WHERE title = :title AND (year = :year OR year = 0 OR :year = 0) LIMIT 1")
    suspend fun find(title: String, year: Int = 0): MetadataCacheEntity?

    @Query("SELECT * FROM metadata_cache WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun findByTmdbId(tmdbId: Int): MetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE cachedAt < :before")
    suspend fun deleteOld(before: Long)
}
