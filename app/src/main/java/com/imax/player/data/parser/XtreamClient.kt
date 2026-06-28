package com.imax.player.data.parser

import com.imax.player.core.common.Constants
import com.imax.player.core.common.normalizeRemoteArtworkUrl
import com.imax.player.core.common.rethrowIfCancellation
import com.imax.player.core.database.*
import com.imax.player.core.model.Channel
import com.imax.player.core.model.ContentType
import com.imax.player.core.network.XtreamApi
import com.imax.player.core.network.dto.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamClient @Inject constructor(
    private val api: XtreamApi
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun buildApiUrl(serverUrl: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base${Constants.XTREAM_API_PATH}"
    }

    private fun buildStreamUrl(
        serverUrl: String,
        pathSegment: String,
        username: String,
        password: String,
        streamId: String,
        extension: String? = null
    ): String {
        val base = serverUrl.trimEnd('/')
        val normalizedExtension = extension
            ?.trim()
            ?.trimStart('.')
            ?.takeIf { it.isNotBlank() }
        val normalizedStreamId = streamId.trim()

        if (normalizedStreamId.isHttpStreamUrl()) {
            return normalizedStreamId
        }

        return if (
            normalizedExtension != null &&
            !normalizedStreamId.endsWith(".$normalizedExtension", ignoreCase = true)
        ) {
            "$base/$pathSegment/$username/$password/$normalizedStreamId.$normalizedExtension"
        } else {
            "$base/$pathSegment/$username/$password/$normalizedStreamId"
        }
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String): Result<XtreamAuthResponse> {
        return try {
            val url = buildApiUrl(serverUrl)
            val response = api.authenticate(url, username, password)
            if (response.userInfo?.status == "Active") {
                Result.success(response)
            } else {
                Result.failure(Exception("Account not active: ${response.userInfo?.status}"))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("Xtream authentication", e)
            Result.failure(e)
        }
    }

    suspend fun loadContent(
        serverUrl: String,
        username: String,
        password: String,
        playlistId: Long
    ): XtreamContentResult {
        val url = buildApiUrl(serverUrl)
        val channels = mutableListOf<ChannelEntity>()
        val movies = mutableListOf<MovieEntity>()
        val series = mutableListOf<SeriesEntity>()
        val categories = mutableListOf<CategoryEntity>()

        try {
            val liveCategories = api.getLiveCategories(url, username, password)
            categories.addAll(liveCategories.map {
                CategoryEntity(
                    playlistId = playlistId,
                    categoryId = it.categoryId.toIntOrNull() ?: 0,
                    name = it.categoryName,
                    contentType = ContentType.LIVE.name,
                    parentId = it.parentId
                )
            })

            val liveStreams = api.getLiveStreams(url, username, password)
            channels.addAll(liveStreams.mapIndexed { index, stream ->
                val categoryName = liveCategories.find { it.categoryId == stream.categoryId }?.categoryName ?: ""
                ChannelEntity(
                    playlistId = playlistId,
                    streamId = stream.streamId,
                    name = stream.name,
                    logoUrl = normalizeRemoteArtworkUrl(serverUrl, stream.streamIcon),
                    groupTitle = categoryName,
                    streamUrl = buildStreamUrl(serverUrl, "live", username, password, stream.streamId.toString(), "m3u8"),
                    epgChannelId = stream.epgChannelId ?: "",
                    catchupSource = if (stream.tvArchive > 0) "xtream" else "",
                    sortOrder = index
                )
            })
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("Live stream loading", e)
        }

        try {
            val vodCategories = api.getVodCategories(url, username, password)
            categories.addAll(vodCategories.map {
                CategoryEntity(
                    playlistId = playlistId,
                    categoryId = it.categoryId.toIntOrNull() ?: 0,
                    name = it.categoryName,
                    contentType = ContentType.MOVIE.name,
                    parentId = it.parentId
                )
            })

            val vodStreams = api.getVodStreams(url, username, password)
            movies.addAll(vodStreams.mapIndexed { index, stream ->
                val categoryName = vodCategories.find { it.categoryId == stream.categoryId }?.categoryName ?: ""
                MovieEntity(
                    playlistId = playlistId,
                    streamId = stream.streamId,
                    name = stream.name,
                    posterUrl = normalizeRemoteArtworkUrl(serverUrl, stream.streamIcon),
                    streamUrl = buildStreamUrl(
                        serverUrl = serverUrl,
                        pathSegment = "movie",
                        username = username,
                        password = password,
                        streamId = stream.streamId.toString(),
                        extension = stream.containerExtension
                    ),
                    genre = stream.genre,
                    plot = stream.plot,
                    cast = stream.cast,
                    director = stream.director,
                    releaseDate = stream.releaseDate,
                    year = stream.year.toIntOrNull() ?: 0,
                    rating = stream.rating.toDoubleOrNull() ?: (stream.rating5based * 2),
                    containerExtension = stream.containerExtension,
                    categoryId = stream.categoryId.toIntOrNull() ?: 0,
                    categoryName = categoryName,
                    tmdbId = stream.tmdbId?.toIntOrNull() ?: 0,
                    duration = stream.episodeRunTime.toIntOrNull() ?: 0,
                    sourceOrder = index
                )
            })
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("VOD loading", e)
        }

        try {
            val seriesCategories = api.getSeriesCategories(url, username, password)
            categories.addAll(seriesCategories.map {
                CategoryEntity(
                    playlistId = playlistId,
                    categoryId = it.categoryId.toIntOrNull() ?: 0,
                    name = it.categoryName,
                    contentType = ContentType.SERIES.name,
                    parentId = it.parentId
                )
            })

            val seriesStreams = api.getSeriesStreams(url, username, password)
            series.addAll(seriesStreams.mapIndexed { index, stream ->
                val categoryName = seriesCategories.find { it.categoryId == stream.categoryId }?.categoryName ?: ""
                SeriesEntity(
                    playlistId = playlistId,
                    seriesId = stream.seriesId,
                    name = stream.name,
                    posterUrl = normalizeRemoteArtworkUrl(serverUrl, stream.cover),
                    backdropUrl = normalizeRemoteArtworkUrl(serverUrl, stream.backdropPath?.firstOrNull().orEmpty()),
                    genre = stream.genre,
                    plot = stream.plot,
                    cast = stream.cast,
                    director = stream.director,
                    releaseDate = stream.releaseDate,
                    year = stream.year.toIntOrNull() ?: 0,
                    rating = stream.rating.toDoubleOrNull() ?: (stream.rating5based * 2),
                    categoryId = stream.categoryId.toIntOrNull() ?: 0,
                    categoryName = categoryName,
                    tmdbId = stream.tmdbId?.toIntOrNull() ?: 0,
                    sourceOrder = index
                )
            })
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("Series loading", e)
        }

        return XtreamContentResult(
            channels = channels,
            movies = movies,
            series = series,
            categories = categories
        )
    }

    suspend fun loadSeriesEpisodes(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: Int,
        dbSeriesId: Long
    ): List<EpisodeEntity> {
        return try {
            val url = buildApiUrl(serverUrl)
            val info = api.getSeriesInfo(url, username, password, seriesId = seriesId)
            val episodes = mutableListOf<EpisodeEntity>()
            parseSeriesEpisodes(info.episodes).forEach { (seasonKey, episodeList) ->
                val seasonNum = seasonKey.toIntOrNull()
                    ?: Regex("""\d+""").find(seasonKey)?.value?.toIntOrNull()
                    ?: 1
                episodeList.forEachIndexed { index, episode ->
                    val streamId = episode.id.ifBlank { episode.episodeNum.toString() }
                    val episodeNumber = episode.episodeNum.takeIf { it > 0 } ?: (index + 1)
                    val streamUrl = episode.directSource
                        .trim()
                        .takeIf { it.isHttpStreamUrl() }
                        ?: buildStreamUrl(
                            serverUrl = serverUrl,
                            pathSegment = "series",
                            username = username,
                            password = password,
                            streamId = streamId,
                            extension = episode.containerExtension
                        )
                    episodes.add(
                        EpisodeEntity(
                            seriesId = dbSeriesId,
                            seasonNumber = seasonNum,
                            episodeNumber = episodeNumber,
                            name = episode.title.ifBlank { "Episode $episodeNumber" },
                            plot = episode.info?.plot ?: "",
                            posterUrl = episode.info?.movieImage ?: "",
                            streamUrl = streamUrl,
                            duration = episode.info?.durationSecs ?: 0,
                            rating = episode.info?.rating ?: 0.0,
                            containerExtension = episode.containerExtension
                        )
                    )
                }
            }
            episodes
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("Series episode loading", e)
            emptyList()
        }
    }

    suspend fun loadVodMetadata(
        serverUrl: String,
        username: String,
        password: String,
        vodId: Int
    ): XtreamVodMetadata? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || vodId <= 0) return null
        return try {
            parseXtreamVodMetadata(
                element = api.getVodInfo(buildApiUrl(serverUrl), username, password, vodId = vodId),
                serverUrl = serverUrl
            )
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            logRequestFailure("VOD metadata loading for stream $vodId", error)
            null
        }
    }

    suspend fun loadLiveEpgPrograms(
        serverUrl: String,
        username: String,
        password: String,
        channels: List<Channel>
    ): List<EpgProgramEntity> {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            return emptyList()
        }

        val url = buildApiUrl(serverUrl)
        val programs = mutableListOf<EpgProgramEntity>()
        channels
            .asSequence()
            .filter { it.streamId > 0 }
            .distinctBy { it.streamId }
            .forEach { channel ->
                programs += loadLiveEpgProgramsForChannel(url, username, password, channel)
            }
        return programs
    }

    private suspend fun loadLiveEpgProgramsForChannel(
        url: String,
        username: String,
        password: String,
        channel: Channel
    ): List<EpgProgramEntity> {
        val shortEpg = runCatching {
            api.getLiveEpg(
                url = url,
                username = username,
                password = password,
                action = "get_short_epg",
                streamId = channel.streamId,
                limit = 6
            )
        }
            .map { response -> parseXtreamEpgPrograms(response, channel) }
            .getOrElse { error ->
                error.rethrowIfCancellation()
                logRequestFailure("Short EPG loading for stream ${channel.streamId}", error)
                emptyList()
            }

        if (shortEpg.isNotEmpty()) return shortEpg

        return runCatching {
            api.getLiveEpg(
                url = url,
                username = username,
                password = password,
                action = "get_simple_data_table",
                streamId = channel.streamId
            )
        }
            .map { response -> parseXtreamEpgPrograms(response, channel) }
            .getOrElse { error ->
                error.rethrowIfCancellation()
                logRequestFailure("Simple EPG loading for stream ${channel.streamId}", error)
                emptyList()
            }
    }

    private fun parseSeriesEpisodes(element: JsonElement?): List<Pair<String, List<XtreamEpisode>>> {
        return when (element) {
            is JsonObject -> element.mapNotNull { (seasonKey, seasonValue) ->
                val episodes = decodeEpisodeCollection(seasonValue)
                if (episodes.isEmpty()) null else seasonKey to episodes
            }
            is JsonArray -> {
                val episodes = decodeEpisodeCollection(element)
                if (episodes.isEmpty()) emptyList() else listOf("1" to episodes)
            }
            else -> emptyList()
        }
    }

    private fun decodeEpisodeCollection(element: JsonElement): List<XtreamEpisode> {
        return when (element) {
            is JsonArray -> element.mapNotNull(::decodeEpisode)
            is JsonObject -> {
                decodeEpisode(element)?.let { listOf(it) }
                    ?: element.values.mapNotNull(::decodeEpisode)
            }
            else -> emptyList()
        }
    }

    private fun decodeEpisode(element: JsonElement): XtreamEpisode? {
        val decodedEpisode = runCatching {
            json.decodeFromJsonElement<XtreamEpisode>(element)
        }.getOrNull()
        val flexibleEpisode = (element as? JsonObject)?.toFlexibleXtreamEpisode()
        val episode = when {
            decodedEpisode == null -> flexibleEpisode
            flexibleEpisode == null -> decodedEpisode
            else -> decodedEpisode.withFallback(flexibleEpisode)
        }

        return episode?.takeIf {
            it.id.isNotBlank() ||
                it.episodeNum > 0 ||
                it.directSource.isNotBlank()
        }
    }

    private fun XtreamEpisode.withFallback(fallback: XtreamEpisode): XtreamEpisode = copy(
        id = id.ifBlank { fallback.id },
        episodeNum = episodeNum.takeIf { it > 0 } ?: fallback.episodeNum,
        title = title.ifBlank { fallback.title },
        containerExtension = containerExtension.ifBlank { fallback.containerExtension },
        directSource = directSource.ifBlank { fallback.directSource },
        info = info ?: fallback.info
    )

    private fun JsonObject.toFlexibleXtreamEpisode(): XtreamEpisode {
        val infoObject = this["info"] as? JsonObject
        return XtreamEpisode(
            id = textValue("id", "stream_id"),
            episodeNum = intValue("episode_num", "episode", "episode_number"),
            title = textValue("title", "name"),
            containerExtension = textValue("container_extension", "extension"),
            directSource = textValue("direct_source", "stream_url"),
            info = infoObject?.let {
                XtreamEpisodeInfo(
                    plot = it.textValue("plot", "description"),
                    durationSecs = it.intValue("duration_secs", "duration_seconds"),
                    duration = it.textValue("duration"),
                    movieImage = it.textValue("movie_image", "cover", "poster"),
                    rating = it.doubleValue("rating")
                )
            }
        )
    }

    private fun JsonObject.textValue(vararg keys: String): String {
        keys.forEach { key ->
            val value = runCatching { (this[key] as? JsonPrimitive)?.content }
                .getOrNull()
                ?.trim()
            if (!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
                return value
            }
        }
        return ""
    }

    private fun JsonObject.intValue(vararg keys: String): Int =
        textValue(*keys).toIntOrNull() ?: 0

    private fun JsonObject.doubleValue(vararg keys: String): Double =
        textValue(*keys).toDoubleOrNull() ?: 0.0

    private fun String.isHttpStreamUrl(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

    private fun logRequestFailure(operation: String, error: Throwable) {
        // Retrofit exceptions may retain a request URL containing Xtream credentials.
        Timber.w("%s failed: %s", operation, error.javaClass.simpleName)
    }
}

data class XtreamContentResult(
    val channels: List<ChannelEntity>,
    val movies: List<MovieEntity>,
    val series: List<SeriesEntity>,
    val categories: List<CategoryEntity>
)

data class XtreamVodMetadata(
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val duration: Int = 0,
    val rating: Double = 0.0,
    val tmdbId: Int = 0
)

internal fun parseXtreamVodMetadata(element: JsonElement, serverUrl: String): XtreamVodMetadata? {
    val root = element as? JsonObject ?: return null
    val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
    val movieData = root["movie_data"] as? JsonObject ?: JsonObject(emptyMap())

    fun JsonObject.firstText(vararg keys: String): String {
        keys.forEach { key ->
            val candidate = when (val value = this[key]) {
                is JsonPrimitive -> value.content.trim()
                is JsonArray -> value.firstOrNull()?.let { (it as? JsonPrimitive)?.content?.trim() }.orEmpty()
                else -> ""
            }
            if (candidate.isNotBlank() && !candidate.equals("null", ignoreCase = true)) return candidate
        }
        return ""
    }

    fun value(vararg keys: String): String = info.firstText(*keys)
        .ifBlank { movieData.firstText(*keys) }
        .ifBlank { root.firstText(*keys) }

    val metadata = XtreamVodMetadata(
        posterUrl = normalizeRemoteArtworkUrl(
            serverUrl,
            value("movie_image", "cover_big", "cover", "stream_icon", "poster", "poster_path")
        ),
        backdropUrl = normalizeRemoteArtworkUrl(
            serverUrl,
            value("backdrop_path", "backdrop", "background")
        ),
        plot = value("plot", "description"),
        cast = value("cast", "actors"),
        director = value("director"),
        genre = value("genre"),
        releaseDate = value("releasedate", "release_date", "date"),
        duration = value("duration_secs", "duration_seconds").toLongOrNull()
            ?.let { seconds -> (seconds / 60L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            ?: parseDurationMinutes(value("duration", "episode_run_time")),
        rating = value("rating", "rating_5based").toDoubleOrNull() ?: 0.0,
        tmdbId = value("tmdb_id", "tmdb").toIntOrNull() ?: 0
    )
    return metadata.takeIf {
        it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank() || it.plot.isNotBlank() || it.tmdbId > 0
    }
}

private fun parseDurationMinutes(value: String): Int {
    val parts = value.split(':').mapNotNull(String::toIntOrNull)
    return when (parts.size) {
        3 -> parts[0] * 60 + parts[1]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
}
