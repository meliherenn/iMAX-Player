package com.imax.player.data.repository

import com.imax.player.core.common.Resource
import com.imax.player.core.database.*
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.model.*
import com.imax.player.data.parser.M3uParser
import com.imax.player.data.parser.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryDao: CategoryDao,
    private val m3uParser: M3uParser,
    private val xtreamClient: XtreamClient,
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) {
    fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAll().map { list -> list.map { it.toModel() } }

    fun getActivePlaylist(): Flow<Playlist?> =
        playlistDao.getActiveFlow().map { it?.toModel() }

    suspend fun getPlaylistById(id: Long): Playlist? =
        playlistDao.getById(id)?.toModel()

    suspend fun savePlaylist(playlist: Playlist): Long {
        val entity = playlist.toEntity()
        return playlistDao.insert(entity)
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.update(playlist.toEntity())
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        channelDao.deleteByPlaylist(playlist.id)
        movieDao.deleteByPlaylist(playlist.id)
        seriesDao.deleteByPlaylist(playlist.id)
        categoryDao.deleteByPlaylist(playlist.id)
        playlistDao.delete(playlist.toEntity())
    }

    suspend fun activatePlaylist(id: Long) {
        playlistDao.deactivateAll()
        playlistDao.activate(id)
        settingsDataStore.updateLastPlaylistId(id)
    }

    suspend fun testConnection(playlist: Playlist): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (playlist.type) {
                PlaylistType.M3U_URL -> {
                    val request = Request.Builder().url(playlist.url).head().build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) Result.success("Connection successful")
                    else Result.failure(Exception("HTTP ${response.code}"))
                }
                PlaylistType.XTREAM_CODES -> {
                    val result = xtreamClient.authenticate(playlist.serverUrl, playlist.username, playlist.password)
                    result.map { "Connection successful - ${it.userInfo?.status}" }
                }
                PlaylistType.M3U_FILE -> Result.success("Local file ready")
            }
        } catch (e: Exception) {
            Timber.e(e, "Connection test failed")
            Result.failure(e)
        }
    }

    suspend fun syncPlaylist(playlist: Playlist): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            channelDao.deleteByPlaylist(playlist.id)
            movieDao.deleteByPlaylist(playlist.id)
            seriesDao.deleteByPlaylist(playlist.id)
            categoryDao.deleteByPlaylist(playlist.id)

            when (playlist.type) {
                PlaylistType.M3U_URL -> syncM3uUrl(playlist)
                PlaylistType.M3U_FILE -> syncM3uFile(playlist)
                PlaylistType.XTREAM_CODES -> syncXtream(playlist)
            }

            val channelCount = channelDao.countByPlaylist(playlist.id)
            val movieCount = movieDao.countByPlaylist(playlist.id)
            val seriesCount = seriesDao.countByPlaylist(playlist.id)
            playlistDao.updateCounts(playlist.id, channelCount, movieCount, seriesCount)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Sync failed for playlist ${playlist.id}")
            Resource.Error(e.message ?: "Sync failed", e)
        }
    }

    private suspend fun syncM3uUrl(playlist: Playlist) {
        val request = Request.Builder().url(playlist.url).build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body ?: throw Exception("Empty response")
        val result = m3uParser.parse(body.byteStream(), playlist.id)
        saveParseResult(result)
    }

    private suspend fun syncM3uFile(playlist: Playlist) {
        val file = java.io.File(playlist.filePath)
        if (!file.exists()) throw Exception("File not found: ${playlist.filePath}")
        val result = m3uParser.parse(file.inputStream(), playlist.id)
        saveParseResult(result)
    }

    private suspend fun syncXtream(playlist: Playlist) {
        val result = xtreamClient.loadContent(
            playlist.serverUrl, playlist.username, playlist.password, playlist.id
        )
        channelDao.insertAll(result.channels)
        movieDao.insertAll(result.movies)
        seriesDao.insertAll(result.series)
        categoryDao.insertAll(result.categories)
    }

    private suspend fun saveParseResult(result: com.imax.player.data.parser.M3uParseResult) {
        channelDao.insertAll(result.channels)
        movieDao.insertAll(result.movies)
        seriesDao.insertAll(result.series)
    }
}
