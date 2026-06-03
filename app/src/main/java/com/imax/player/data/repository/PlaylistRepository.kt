package com.imax.player.data.repository

import androidx.room.withTransaction
import com.imax.player.core.common.Resource
import com.imax.player.core.database.*
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.model.*
import com.imax.player.data.parser.M3uParser
import com.imax.player.data.parser.XtreamClient
import com.imax.player.data.parser.XtreamContentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import timber.log.Timber
import java.util.Locale
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
    private val database: ImaxDatabase,
    private val m3uParser: M3uParser,
    private val xtreamClient: XtreamClient,
    private val epgRepository: EpgRepository,
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
        database.withTransaction {
            replacePlaylistContent(playlist.id)
            playlistDao.delete(playlist.toEntity())
        }
    }

    suspend fun activatePlaylist(id: Long) {
        playlistDao.deactivateAll()
        playlistDao.activate(id)
        settingsDataStore.updateLastPlaylistId(id)
    }

    suspend fun hasSyncedContent(playlistId: Long): Boolean =
        channelDao.countByPlaylist(playlistId) > 0 ||
            movieDao.countByPlaylist(playlistId) > 0 ||
            seriesDao.countByPlaylist(playlistId) > 0

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
            val normalizedPlaylist = playlist.normalizedForSync()
            val content = when (normalizedPlaylist.type) {
                PlaylistType.M3U_URL -> PlaylistSyncContent.M3u(loadM3uUrl(normalizedPlaylist))
                PlaylistType.M3U_FILE -> PlaylistSyncContent.M3u(loadM3uFile(normalizedPlaylist))
                PlaylistType.XTREAM_CODES -> PlaylistSyncContent.Xtream(loadXtream(normalizedPlaylist))
            }
            content.requireNonEmpty()
            val syncTime = System.currentTimeMillis()

            database.withTransaction {
                val existingContent = loadExistingContentState(playlist.id)
                replacePlaylistContent(playlist.id)

                when (content) {
                    is PlaylistSyncContent.M3u -> saveParseResult(content.result, existingContent, syncTime)
                    is PlaylistSyncContent.Xtream -> saveXtreamResult(content.result, existingContent, syncTime)
                }

                val channelCount = channelDao.countByPlaylist(playlist.id)
                val movieCount = movieDao.countByPlaylist(playlist.id)
                val seriesCount = seriesDao.countByPlaylist(playlist.id)
                playlistDao.updateCounts(playlist.id, channelCount, movieCount, seriesCount)
            }

            syncDiscoveredEpg(normalizedPlaylist, content)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Sync failed for playlist ${playlist.id}")
            Resource.Error(e.message ?: "Sync failed", e)
        }
    }

    suspend fun ensureEpgSynced(playlist: Playlist): Boolean = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.settings.first()
        if (settings.epgUrl.isNotBlank() && settings.epgLastSync > 0L) {
            return@withContext false
        }

        runCatching {
            val normalizedPlaylist = playlist.normalizedForSync()
            val content = when (normalizedPlaylist.type) {
                PlaylistType.M3U_URL -> PlaylistSyncContent.M3u(loadM3uUrl(normalizedPlaylist))
                PlaylistType.M3U_FILE -> PlaylistSyncContent.M3u(loadM3uFile(normalizedPlaylist))
                PlaylistType.XTREAM_CODES -> {
                    val channels = channelDao.getByPlaylistSnapshot(normalizedPlaylist.id)
                    PlaylistSyncContent.Xtream(
                        XtreamContentResult(
                            channels = channels,
                            movies = emptyList(),
                            series = emptyList(),
                            categories = emptyList()
                        )
                    )
                }
            }

            syncDiscoveredEpg(normalizedPlaylist, content)
        }.getOrElse { error ->
            Timber.w(error, "EPG auto discovery failed for playlist ${playlist.id}")
            false
        }
    }

    private suspend fun loadM3uUrl(playlist: Playlist): com.imax.player.data.parser.M3uParseResult {
        val request = Request.Builder().url(playlist.url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Playlist request failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("Empty response")
            return m3uParser.parse(body.byteStream(), playlist.id)
        }
    }

    private suspend fun loadM3uFile(playlist: Playlist): com.imax.player.data.parser.M3uParseResult {
        val file = java.io.File(playlist.filePath)
        if (!file.exists()) throw Exception("File not found: ${playlist.filePath}")
        return file.inputStream().use { input ->
            m3uParser.parse(input, playlist.id)
        }
    }

    private suspend fun loadXtream(playlist: Playlist): XtreamContentResult =
        xtreamClient.loadContent(
            playlist.serverUrl, playlist.username, playlist.password, playlist.id
        )

    private suspend fun replacePlaylistContent(playlistId: Long) {
        channelDao.deleteByPlaylist(playlistId)
        movieDao.deleteByPlaylist(playlistId)
        episodeDao.deleteByPlaylist(playlistId)
        seriesDao.deleteByPlaylist(playlistId)
        categoryDao.deleteByPlaylist(playlistId)
    }

    private suspend fun saveXtreamResult(
        result: XtreamContentResult,
        existingContent: ExistingContentState,
        syncTime: Long
    ) {
        insertChunked(result.channels) { channelDao.insertAll(it) }
        insertChunked(result.movies.withMovieAddedAt(existingContent.movieAddedAtByKey, syncTime)) { movieDao.insertAll(it) }
        insertChunked(result.series.withSeriesAddedAt(existingContent.seriesAddedAtByKey, syncTime)) { seriesDao.insertAll(it) }
        insertChunked(result.categories) { categoryDao.insertAll(it) }
    }

    private suspend fun syncDiscoveredEpg(
        playlist: Playlist,
        content: PlaylistSyncContent
    ): Boolean {
        val discoveredEpgUrl = when (content) {
            is PlaylistSyncContent.M3u -> resolveM3uEpgUrl(content.result.epgUrl, playlist.url)
            is PlaylistSyncContent.Xtream -> buildXtreamEpgUrl(playlist)
        }

        if (discoveredEpgUrl.isBlank()) {
            Timber.d("No EPG URL discovered for playlist ${playlist.id}")
            return false
        }

        val currentSettings = settingsDataStore.settings.first()
        if (currentSettings.epgUrl != discoveredEpgUrl) {
            settingsDataStore.updateEpgUrl(discoveredEpgUrl)
        }

        val channels = when (content) {
            is PlaylistSyncContent.M3u -> content.result.channels.map { it.toModel() }
            is PlaylistSyncContent.Xtream -> content.result.channels.map { it.toModel() }
        }

        if (channels.isEmpty()) return false

        val channelIdMap = epgRepository.buildChannelIdMap(channels)
        val result = epgRepository.fetchAndSave(discoveredEpgUrl, channelIdMap)
        val savedProgramCount = result.getOrNull() ?: 0
        val error = result.exceptionOrNull()
        if (error != null) {
            Timber.w(error, "Auto EPG sync failed for playlist ${playlist.id}")
            return false
        }

        if (savedProgramCount <= 0) {
            Timber.w("Auto EPG sync parsed no programs for playlist ${playlist.id}")
            return false
        }

        settingsDataStore.updateEpgLastSync(System.currentTimeMillis())
        if (currentSettings.epgAutoSync) {
            epgRepository.scheduleDailySync(discoveredEpgUrl)
        }
        Timber.d("Auto EPG sync completed for playlist ${playlist.id}: $savedProgramCount programs")
        return true
    }

    private suspend fun saveParseResult(
        result: com.imax.player.data.parser.M3uParseResult,
        existingContent: ExistingContentState,
        syncTime: Long
    ) {
        insertChunked(result.channels) { channelDao.insertAll(it) }
        insertChunked(result.movies.withMovieAddedAt(existingContent.movieAddedAtByKey, syncTime)) { movieDao.insertAll(it) }

        val series = result.series.withSeriesAddedAt(existingContent.seriesAddedAtByKey, syncTime)
        if (series.isEmpty()) return

        insertChunked(series) { seriesDao.insertAll(it) }

        val playlistId = result.series.firstOrNull()?.playlistId
            ?: result.channels.firstOrNull()?.playlistId
            ?: result.movies.firstOrNull()?.playlistId
            ?: return

        val storedSeries = seriesDao.getByPlaylistSnapshot(playlistId).associateBy { it.name }
        val episodeEntities = buildM3uEpisodes(result.seriesEpisodes, storedSeries)

        if (episodeEntities.isNotEmpty()) {
            insertChunked(episodeEntities) { episodeDao.insertAll(it) }

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
                insertChunked(updatedSeries) { seriesDao.insertAll(it) }
            }
        }
    }

    private suspend fun loadExistingContentState(playlistId: Long): ExistingContentState {
        val movies = movieDao.getByPlaylistSnapshot(playlistId)
        val series = seriesDao.getByPlaylistSnapshot(playlistId)

        return ExistingContentState(
            movieAddedAtByKey = movies.associate { it.stableContentKey() to it.addedAt },
            seriesAddedAtByKey = series.associate { it.stableContentKey() to it.addedAt }
        )
    }

    private fun List<MovieEntity>.withMovieAddedAt(
        existingAddedAtByKey: Map<String, Long>,
        syncTime: Long
    ): List<MovieEntity> = map { movie ->
        movie.copy(addedAt = existingAddedAtByKey[movie.stableContentKey()] ?: syncTime)
    }

    private fun List<SeriesEntity>.withSeriesAddedAt(
        existingAddedAtByKey: Map<String, Long>,
        syncTime: Long
    ): List<SeriesEntity> = map { series ->
        series.copy(addedAt = existingAddedAtByKey[series.stableContentKey()] ?: syncTime)
    }

    private suspend fun <T> insertChunked(items: List<T>, insert: suspend (List<T>) -> Unit) {
        items.chunked(INSERT_CHUNK_SIZE).forEach { chunk ->
            insert(chunk)
        }
    }

    private fun MovieEntity.stableContentKey(): String {
        return when {
            streamId > 0 -> "stream:$streamId"
            streamUrl.isNotBlank() -> "url:${streamUrl.trim()}"
            else -> "name:${name.normalizedKeyPart()}:${categoryName.normalizedKeyPart()}"
        }
    }

    private fun SeriesEntity.stableContentKey(): String {
        return when {
            seriesId > 0 -> "series:$seriesId"
            else -> "name:${name.normalizedKeyPart()}:${categoryName.normalizedKeyPart()}"
        }
    }

    private fun String.normalizedKeyPart(): String =
        trim().lowercase(Locale.ROOT)

    private data class ExistingContentState(
        val movieAddedAtByKey: Map<String, Long>,
        val seriesAddedAtByKey: Map<String, Long>
    )

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

    private companion object {
        const val INSERT_CHUNK_SIZE = 500
    }
}

private fun resolveM3uEpgUrl(epgUrl: String, playlistUrl: String): String {
    val trimmed = epgUrl.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }

    return playlistUrl
        .toHttpUrlOrNull()
        ?.resolve(trimmed)
        ?.toString()
        ?: trimmed
}

private fun buildXtreamEpgUrl(playlist: Playlist): String {
    if (playlist.serverUrl.isBlank() || playlist.username.isBlank() || playlist.password.isBlank()) {
        return ""
    }

    val base = playlist.serverUrl.trimEnd('/')
    return base.toHttpUrlOrNull()
        ?.newBuilder()
        ?.addPathSegment("xmltv.php")
        ?.addQueryParameter("username", playlist.username)
        ?.addQueryParameter("password", playlist.password)
        ?.build()
        ?.toString()
        ?: "$base/xmltv.php?username=${playlist.username}&password=${playlist.password}"
}

private sealed interface PlaylistSyncContent {
    data class M3u(val result: com.imax.player.data.parser.M3uParseResult) : PlaylistSyncContent
    data class Xtream(val result: XtreamContentResult) : PlaylistSyncContent
}

private fun PlaylistSyncContent.requireNonEmpty() {
    val hasContent = when (this) {
        is PlaylistSyncContent.M3u ->
            result.channels.isNotEmpty() || result.movies.isNotEmpty() || result.series.isNotEmpty()
        is PlaylistSyncContent.Xtream ->
            result.channels.isNotEmpty() || result.movies.isNotEmpty() || result.series.isNotEmpty()
    }
    if (!hasContent) {
        throw IllegalStateException("Playlist sync returned no playable content")
    }
}

private fun Playlist.normalizedForSync(): Playlist {
    return when (type) {
        PlaylistType.M3U_URL -> copy(url = normalizeHttpUrl(url))
        PlaylistType.XTREAM_CODES -> copy(serverUrl = normalizeXtreamServerUrl(serverUrl))
        PlaylistType.M3U_FILE -> this
    }
}

private fun normalizeHttpUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return trimmed
    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

private fun normalizeXtreamServerUrl(value: String): String {
    val normalized = normalizeHttpUrl(value).trimEnd('/')
    return if (normalized.endsWith("/player_api.php", ignoreCase = true)) {
        normalized.dropLast("/player_api.php".length)
    } else {
        normalized
    }
}
