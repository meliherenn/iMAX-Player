package com.imax.player.data.parser

import com.imax.player.core.common.Constants
import com.imax.player.core.database.*
import com.imax.player.core.model.ContentType
import com.imax.player.core.network.XtreamApi
import com.imax.player.core.network.dto.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamClient @Inject constructor(
    private val api: XtreamApi
) {
    private fun buildApiUrl(serverUrl: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base${Constants.XTREAM_API_PATH}"
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
            Timber.e(e, "Xtream authentication failed")
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
                    logoUrl = stream.streamIcon,
                    groupTitle = categoryName,
                    streamUrl = "$serverUrl/live/$username/$password/${stream.streamId}.m3u8",
                    epgChannelId = stream.epgChannelId ?: "",
                    sortOrder = index
                )
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to load live streams")
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
            movies.addAll(vodStreams.map { stream ->
                val categoryName = vodCategories.find { it.categoryId == stream.categoryId }?.categoryName ?: ""
                MovieEntity(
                    playlistId = playlistId,
                    streamId = stream.streamId,
                    name = stream.name,
                    posterUrl = stream.streamIcon,
                    streamUrl = "$serverUrl/movie/$username/$password/${stream.streamId}.${stream.containerExtension}",
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
                    duration = stream.episodeRunTime.toIntOrNull() ?: 0
                )
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to load VOD streams")
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
            series.addAll(seriesStreams.map { stream ->
                val categoryName = seriesCategories.find { it.categoryId == stream.categoryId }?.categoryName ?: ""
                SeriesEntity(
                    playlistId = playlistId,
                    seriesId = stream.seriesId,
                    name = stream.name,
                    posterUrl = stream.cover,
                    backdropUrl = stream.backdropPath?.firstOrNull() ?: "",
                    genre = stream.genre,
                    plot = stream.plot,
                    cast = stream.cast,
                    director = stream.director,
                    releaseDate = stream.releaseDate,
                    year = stream.year.toIntOrNull() ?: 0,
                    rating = stream.rating.toDoubleOrNull() ?: (stream.rating5based * 2),
                    categoryId = stream.categoryId.toIntOrNull() ?: 0,
                    categoryName = categoryName,
                    tmdbId = stream.tmdbId?.toIntOrNull() ?: 0
                )
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to load series")
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
            info.episodes?.forEach { (seasonKey, episodeList) ->
                val seasonNum = seasonKey.toIntOrNull() ?: 0
                episodeList.forEach { episode ->
                    episodes.add(
                        EpisodeEntity(
                            seriesId = dbSeriesId,
                            seasonNumber = seasonNum,
                            episodeNumber = episode.episodeNum,
                            name = episode.title,
                            plot = episode.info?.plot ?: "",
                            posterUrl = episode.info?.movieImage ?: "",
                            streamUrl = "$serverUrl/series/$username/$password/${episode.id}.${episode.containerExtension}",
                            duration = episode.info?.durationSecs ?: 0,
                            rating = episode.info?.rating ?: 0.0,
                            containerExtension = episode.containerExtension
                        )
                    )
                }
            }
            episodes
        } catch (e: Exception) {
            Timber.e(e, "Failed to load series episodes")
            emptyList()
        }
    }
}

data class XtreamContentResult(
    val channels: List<ChannelEntity>,
    val movies: List<MovieEntity>,
    val series: List<SeriesEntity>,
    val categories: List<CategoryEntity>
)
