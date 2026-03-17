package com.imax.player.data.repository

import com.imax.player.core.database.*
import com.imax.player.core.model.*
import com.imax.player.data.parser.XtreamClient
import com.imax.player.metadata.MetadataProvider
import com.imax.player.metadata.MetadataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val xtreamClient: XtreamClient,
    private val metadataProvider: MetadataProvider
) {
    // Channels
    fun getChannels(playlistId: Long): Flow<List<Channel>> =
        channelDao.getByPlaylist(playlistId).map { list -> list.map { it.toModel() } }

    fun getChannelsByGroup(playlistId: Long, group: String): Flow<List<Channel>> =
        channelDao.getByGroup(playlistId, group).map { list -> list.map { it.toModel() } }

    fun getChannelGroups(playlistId: Long): Flow<List<String>> =
        channelDao.getGroups(playlistId)

    fun getFavoriteChannels(playlistId: Long): Flow<List<Channel>> =
        channelDao.getFavorites(playlistId).map { list -> list.map { it.toModel() } }

    suspend fun getChannel(id: Long): Channel? = channelDao.getById(id)?.toModel()

    suspend fun toggleChannelFavorite(id: Long, favorite: Boolean) =
        channelDao.setFavorite(id, favorite)

    suspend fun updateChannelLastWatched(id: Long) =
        channelDao.updateLastWatched(id)

    // Movies
    fun getMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getByPlaylist(playlistId).map { list -> list.map { it.toModel() } }

    fun getMoviesByCategory(playlistId: Long, category: String): Flow<List<Movie>> =
        movieDao.getByCategory(playlistId, category).map { list -> list.map { it.toModel() } }

    fun getMovieCategories(playlistId: Long): Flow<List<String>> =
        movieDao.getCategories(playlistId)

    fun getFavoriteMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getFavorites(playlistId).map { list -> list.map { it.toModel() } }

    fun getRecentMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getRecentlyWatched(playlistId).map { list -> list.map { it.toModel() } }

    fun getContinueWatchingMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getContinueWatching(playlistId).map { list -> list.map { it.toModel() } }

    fun getSimilarMovies(playlistId: Long, category: String, excludeId: Long): Flow<List<Movie>> =
        movieDao.getSimilar(playlistId, category, excludeId).map { list -> list.map { it.toModel() } }

    suspend fun getMovie(id: Long): Movie? = movieDao.getById(id)?.toModel()

    suspend fun toggleMovieFavorite(id: Long, favorite: Boolean) =
        movieDao.setFavorite(id, favorite)

    suspend fun updateMovieProgress(id: Long, position: Long, total: Long) =
        movieDao.updateProgress(id, position, total)

    // Series
    fun getSeries(playlistId: Long): Flow<List<Series>> =
        seriesDao.getByPlaylist(playlistId).map { list -> list.map { it.toModel() } }

    fun getSeriesByCategory(playlistId: Long, category: String): Flow<List<Series>> =
        seriesDao.getByCategory(playlistId, category).map { list -> list.map { it.toModel() } }

    fun getSeriesCategories(playlistId: Long): Flow<List<String>> =
        seriesDao.getCategories(playlistId)

    fun getFavoriteSeries(playlistId: Long): Flow<List<Series>> =
        seriesDao.getFavorites(playlistId).map { list -> list.map { it.toModel() } }

    suspend fun getSeriesById(id: Long): Series? = seriesDao.getById(id)?.toModel()

    suspend fun toggleSeriesFavorite(id: Long, favorite: Boolean) =
        seriesDao.setFavorite(id, favorite)

    // Episodes
    fun getEpisodes(seriesId: Long): Flow<List<Episode>> =
        episodeDao.getBySeries(seriesId).map { list -> list.map { it.toModel() } }

    fun getEpisodesBySeason(seriesId: Long, season: Int): Flow<List<Episode>> =
        episodeDao.getBySeason(seriesId, season).map { list -> list.map { it.toModel() } }

    fun getSeasons(seriesId: Long): Flow<List<Int>> =
        episodeDao.getSeasons(seriesId)

    suspend fun getEpisode(id: Long): Episode? = episodeDao.getById(id)?.toModel()

    suspend fun updateEpisodeProgress(id: Long, position: Long, total: Long) =
        episodeDao.updateProgress(id, position, total)

    suspend fun getLastWatchedEpisode(seriesId: Long): Episode? =
        episodeDao.getLastWatched(seriesId)?.toModel()

    suspend fun getNextEpisode(seriesId: Long, season: Int, episode: Int): Episode? {
        val next = episodeDao.getEpisode(seriesId, season, episode + 1)
        if (next != null) return next.toModel()
        return episodeDao.getEpisode(seriesId, season + 1, 1)?.toModel()
    }

    /**
     * FIX: Fetch episodes from Xtream API if not already in DB.
     * Called from DetailViewModel when viewing a series detail.
     * Returns true if episodes were fetched and inserted.
     */
    suspend fun syncSeriesEpisodes(series: Series): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if we already have episodes for this series
            val existingCount = episodeDao.getBySeries(series.id).first().size
            if (existingCount > 0) {
                Timber.d("Episodes already cached for series ${series.id} (${series.name}), count=$existingCount")
                return@withContext false
            }

            // We need the playlist credentials to call the Xtream API
            val playlist = playlistDao.getById(series.playlistId)?.toModel()
            if (playlist == null || playlist.type != PlaylistType.XTREAM_CODES) {
                Timber.w("Cannot fetch episodes: playlist ${series.playlistId} not found or not Xtream")
                return@withContext false
            }

            if (series.seriesId == 0) {
                Timber.w("Cannot fetch episodes: series ${series.id} has no Xtream seriesId")
                return@withContext false
            }

            Timber.d("Fetching episodes from Xtream API for series ${series.name} (seriesId=${series.seriesId})")
            val episodes = xtreamClient.loadSeriesEpisodes(
                serverUrl = playlist.serverUrl,
                username = playlist.username,
                password = playlist.password,
                seriesId = series.seriesId,
                dbSeriesId = series.id
            )

            if (episodes.isNotEmpty()) {
                episodeDao.insertAll(episodes)
                Timber.d("Inserted ${episodes.size} episodes for series ${series.name}")
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync episodes for series ${series.name}")
            false
        }
    }

    /**
     * FIX: Fetch metadata from TMDB for a movie or series with missing details.
     * Returns the MetadataResult if successful, null otherwise.
     */
    suspend fun enrichMetadata(title: String, year: Int, contentType: ContentType): MetadataResult? {
        return withContext(Dispatchers.IO) {
            try {
                metadataProvider.fetchMetadata(title, year, contentType)
            } catch (e: Exception) {
                Timber.e(e, "Metadata enrichment failed for: $title")
                null
            }
        }
    }

    /**
     * Update a movie entity with enriched metadata from TMDB.
     */
    suspend fun updateMovieWithMetadata(movieId: Long, metadata: MetadataResult) {
        withContext(Dispatchers.IO) {
            val entity = movieDao.getById(movieId) ?: return@withContext
            val updated = entity.copy(
                plot = if (entity.plot.isBlank() && metadata.overview.isNotBlank()) metadata.overview else entity.plot,
                cast = if (entity.cast.isBlank() && metadata.cast.isNotBlank()) metadata.cast else entity.cast,
                director = if (entity.director.isBlank() && metadata.director.isNotBlank()) metadata.director else entity.director,
                genre = if (entity.genre.isBlank() && metadata.genre.isNotBlank()) metadata.genre else entity.genre,
                rating = if (entity.rating == 0.0 && metadata.rating > 0) metadata.rating else entity.rating,
                year = if (entity.year == 0 && metadata.year > 0) metadata.year else entity.year,
                posterUrl = if (entity.posterUrl.isBlank() && metadata.posterUrl.isNotBlank()) metadata.posterUrl else entity.posterUrl,
                backdropUrl = if (entity.backdropUrl.isBlank() && metadata.backdropUrl.isNotBlank()) metadata.backdropUrl else entity.backdropUrl,
                tmdbId = if (entity.tmdbId == 0 && metadata.tmdbId > 0) metadata.tmdbId else entity.tmdbId,
                imdbId = if (entity.imdbId.isBlank() && metadata.imdbId.isNotBlank()) metadata.imdbId else entity.imdbId,
                duration = if (entity.duration == 0 && metadata.runtime > 0) metadata.runtime else entity.duration
            )
            movieDao.insertAll(listOf(updated))
        }
    }

    /**
     * Update a series entity with enriched metadata from TMDB.
     */
    suspend fun updateSeriesWithMetadata(seriesId: Long, metadata: MetadataResult) {
        withContext(Dispatchers.IO) {
            val entity = seriesDao.getById(seriesId) ?: return@withContext
            val updated = entity.copy(
                plot = if (entity.plot.isBlank() && metadata.overview.isNotBlank()) metadata.overview else entity.plot,
                cast = if (entity.cast.isBlank() && metadata.cast.isNotBlank()) metadata.cast else entity.cast,
                director = if (entity.director.isBlank() && metadata.director.isNotBlank()) metadata.director else entity.director,
                genre = if (entity.genre.isBlank() && metadata.genre.isNotBlank()) metadata.genre else entity.genre,
                rating = if (entity.rating == 0.0 && metadata.rating > 0) metadata.rating else entity.rating,
                year = if (entity.year == 0 && metadata.year > 0) metadata.year else entity.year,
                posterUrl = if (entity.posterUrl.isBlank() && metadata.posterUrl.isNotBlank()) metadata.posterUrl else entity.posterUrl,
                backdropUrl = if (entity.backdropUrl.isBlank() && metadata.backdropUrl.isNotBlank()) metadata.backdropUrl else entity.backdropUrl,
                tmdbId = if (entity.tmdbId == 0 && metadata.tmdbId > 0) metadata.tmdbId else entity.tmdbId,
                imdbId = if (entity.imdbId.isBlank() && metadata.imdbId.isNotBlank()) metadata.imdbId else entity.imdbId
            )
            seriesDao.insertAll(listOf(updated))
        }
    }

    // Watch History
    fun getWatchHistory(): Flow<List<WatchHistoryItem>> =
        watchHistoryDao.getRecent().map { list -> list.map { it.toModel() } }

    fun getContinueWatching(): Flow<List<WatchHistoryItem>> =
        watchHistoryDao.getContinueWatching().map { list -> list.map { it.toModel() } }

    suspend fun addWatchHistory(item: WatchHistoryItem) {
        val existing = watchHistoryDao.getByContent(item.contentId, item.contentType.name)
        val entity = item.toEntity().copy(id = existing?.id ?: 0)
        watchHistoryDao.insert(entity)
    }

    // Favorites
    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    fun isFavorite(contentId: Long, type: ContentType): Flow<Boolean> =
        favoriteDao.isFavorite(contentId, type.name)

    suspend fun addFavorite(contentId: Long, type: ContentType, title: String, posterUrl: String, streamUrl: String) {
        favoriteDao.insert(FavoriteEntity(contentId = contentId, contentType = type.name, title = title, posterUrl = posterUrl, streamUrl = streamUrl))
    }

    suspend fun removeFavorite(contentId: Long, type: ContentType) {
        favoriteDao.delete(contentId, type.name)
    }

    // Search
    fun searchChannels(playlistId: Long, query: String): Flow<List<Channel>> =
        channelDao.search(playlistId, query).map { list -> list.map { it.toModel() } }

    fun searchMovies(playlistId: Long, query: String): Flow<List<Movie>> =
        movieDao.search(playlistId, query).map { list -> list.map { it.toModel() } }

    fun searchSeries(playlistId: Long, query: String): Flow<List<Series>> =
        seriesDao.search(playlistId, query).map { list -> list.map { it.toModel() } }
}
