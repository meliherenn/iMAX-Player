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
        episodeDao.deleteByPlaylist(playlist.id)
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
            episodeDao.deleteByPlaylist(playlist.id)
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
        if (result.series.isEmpty()) return

        seriesDao.insertAll(result.series)

        val playlistId = result.series.firstOrNull()?.playlistId
            ?: result.channels.firstOrNull()?.playlistId
            ?: result.movies.firstOrNull()?.playlistId
            ?: return

        val storedSeries = seriesDao.getByPlaylist(playlistId).first().associateBy { it.name }
        val episodeEntities = buildM3uEpisodes(result.seriesEpisodes, storedSeries)

        if (episodeEntities.isNotEmpty()) {
            episodeDao.insertAll(episodeEntities)

            val countsBySeriesId = episodeEntities.groupBy { it.seriesId }
            val updatedSeries = storedSeries.values.mapNotNull { series ->
                val episodes = countsBySeriesId[series.id] ?: return@mapNotNull null
                val seasonCount = episodes.map { it.seasonNumber }.distinct().count()
                series.copy(
                    seasonCount = seasonCount,
                    episodeCount = episodes.size
                )
            }

            if (updatedSeries.isNotEmpty()) {
                seriesDao.insertAll(updatedSeries)
            }
        }
    }

    private fun buildM3uEpisodes(
        seriesEpisodes: Map<String, List<com.imax.player.data.parser.M3uEntry>>,
        storedSeries: Map<String, SeriesEntity>
    ): List<EpisodeEntity> {
        return buildList {
            seriesEpisodes.forEach { (seriesName, entries) ->
                val seriesEntity = storedSeries[seriesName] ?: return@forEach

                entries.forEachIndexed { index, entry ->
                    val parsedEpisode = parseEpisode(entry.name, seriesName, index)
                    add(
                        EpisodeEntity(
                            seriesId = seriesEntity.id,
                            seasonNumber = parsedEpisode.seasonNumber,
                            episodeNumber = parsedEpisode.episodeNumber,
                            name = parsedEpisode.displayName,
                            posterUrl = entry.logoUrl,
                            streamUrl = entry.url
                        )
                    )
                }
            }
        }
    }

    private fun parseEpisode(
        rawTitle: String,
        seriesName: String,
        index: Int
    ): ParsedEpisode {
        val normalizedTitle = rawTitle.trim()
        val patterns = listOf(
            Regex("""(?i)\bS(\d{1,2})\s*E(\d{1,3})\b"""),
            Regex("""(?i)\b(\d{1,2})x(\d{1,3})\b"""),
            Regex("""(?i)\bseason\s*(\d{1,2})\D+episode\s*(\d{1,3})\b""")
        )

        patterns.forEach { pattern ->
            val match = pattern.find(normalizedTitle) ?: return@forEach
            val seasonNumber = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            val episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull() ?: (index + 1)
            val cleanedTitle = normalizedTitle
                .replace(seriesName, "", ignoreCase = true)
                .replace(pattern, "")
                .replace("""^[\s\-_:|]+|[\s\-_:|]+$""".toRegex(), "")
                .ifBlank { "Episode $episodeNumber" }

            return ParsedEpisode(
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                displayName = cleanedTitle
            )
        }

        return ParsedEpisode(
            seasonNumber = 1,
            episodeNumber = index + 1,
            displayName = normalizedTitle
                .replace(seriesName, "", ignoreCase = true)
                .replace("""^[\s\-_:|]+|[\s\-_:|]+$""".toRegex(), "")
                .ifBlank { "Episode ${index + 1}" }
        )
    }

    private data class ParsedEpisode(
        val seasonNumber: Int,
        val episodeNumber: Int,
        val displayName: String
    )
}
