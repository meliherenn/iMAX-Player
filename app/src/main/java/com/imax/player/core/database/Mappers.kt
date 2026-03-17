package com.imax.player.core.database

import com.imax.player.core.model.*

fun PlaylistEntity.toModel() = Playlist(
    id = id, name = name, type = PlaylistType.valueOf(type),
    url = url, serverUrl = serverUrl, username = username, password = password,
    filePath = filePath, lastUpdated = lastUpdated, lastUsed = lastUsed,
    isActive = isActive, channelCount = channelCount, movieCount = movieCount, seriesCount = seriesCount
)

fun Playlist.toEntity() = PlaylistEntity(
    id = id, name = name, type = type.name,
    url = url, serverUrl = serverUrl, username = username, password = password,
    filePath = filePath, lastUpdated = lastUpdated, lastUsed = lastUsed,
    isActive = isActive, channelCount = channelCount, movieCount = movieCount, seriesCount = seriesCount
)

fun ChannelEntity.toModel() = Channel(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    logoUrl = logoUrl, groupTitle = groupTitle, streamUrl = streamUrl,
    epgChannelId = epgChannelId, catchupSource = catchupSource,
    isFavorite = isFavorite, lastWatched = lastWatched, sortOrder = sortOrder
)

fun Channel.toEntity() = ChannelEntity(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    logoUrl = logoUrl, groupTitle = groupTitle, streamUrl = streamUrl,
    epgChannelId = epgChannelId, catchupSource = catchupSource,
    isFavorite = isFavorite, lastWatched = lastWatched, sortOrder = sortOrder
)

fun MovieEntity.toModel() = Movie(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, streamUrl = streamUrl,
    genre = genre, plot = plot, cast = cast, director = director,
    releaseDate = releaseDate, year = year, duration = duration, rating = rating,
    imdbId = imdbId, tmdbId = tmdbId, containerExtension = containerExtension,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration
)

fun Movie.toEntity() = MovieEntity(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, streamUrl = streamUrl,
    genre = genre, plot = plot, cast = cast, director = director,
    releaseDate = releaseDate, year = year, duration = duration, rating = rating,
    imdbId = imdbId, tmdbId = tmdbId, containerExtension = containerExtension,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration
)

fun SeriesEntity.toModel() = Series(
    id = id, playlistId = playlistId, seriesId = seriesId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, genre = genre,
    plot = plot, cast = cast, director = director, releaseDate = releaseDate,
    year = year, rating = rating, imdbId = imdbId, tmdbId = tmdbId,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastWatchedEpisodeId = lastWatchedEpisodeId,
    seasonCount = seasonCount, episodeCount = episodeCount
)

fun Series.toEntity() = SeriesEntity(
    id = id, playlistId = playlistId, seriesId = seriesId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, genre = genre,
    plot = plot, cast = cast, director = director, releaseDate = releaseDate,
    year = year, rating = rating, imdbId = imdbId, tmdbId = tmdbId,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastWatchedEpisodeId = lastWatchedEpisodeId,
    seasonCount = seasonCount, episodeCount = episodeCount
)

fun EpisodeEntity.toModel() = Episode(
    id = id, seriesId = seriesId, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, name = name, plot = plot,
    posterUrl = posterUrl, streamUrl = streamUrl, duration = duration,
    rating = rating, containerExtension = containerExtension,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration
)

fun Episode.toEntity() = EpisodeEntity(
    id = id, seriesId = seriesId, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, name = name, plot = plot,
    posterUrl = posterUrl, streamUrl = streamUrl, duration = duration,
    rating = rating, containerExtension = containerExtension,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration
)

fun CategoryEntity.toModel() = Category(
    id = id, playlistId = playlistId, categoryId = categoryId,
    name = name, contentType = ContentType.valueOf(contentType),
    parentId = parentId, itemCount = itemCount
)

fun Category.toEntity() = CategoryEntity(
    id = id, playlistId = playlistId, categoryId = categoryId,
    name = name, contentType = contentType.name,
    parentId = parentId, itemCount = itemCount
)

fun EpgProgramEntity.toModel() = EpgProgram(
    id = id, channelId = channelId, title = title,
    description = description, startTime = startTime, endTime = endTime,
    posterUrl = posterUrl, genre = genre
)

fun WatchHistoryEntity.toModel() = WatchHistoryItem(
    id = id, contentId = contentId, contentType = ContentType.valueOf(contentType),
    title = title, posterUrl = posterUrl, streamUrl = streamUrl,
    position = position, totalDuration = totalDuration, progress = progress,
    timestamp = timestamp, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, seriesName = seriesName
)

fun WatchHistoryItem.toEntity() = WatchHistoryEntity(
    id = id, contentId = contentId, contentType = contentType.name,
    title = title, posterUrl = posterUrl, streamUrl = streamUrl,
    position = position, totalDuration = totalDuration, progress = progress,
    timestamp = timestamp, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, seriesName = seriesName
)
