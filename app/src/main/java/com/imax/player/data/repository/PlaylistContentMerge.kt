package com.imax.player.data.repository

import com.imax.player.core.database.ChannelEntity
import com.imax.player.core.database.EpisodeEntity
import com.imax.player.core.database.MovieEntity
import com.imax.player.core.database.SeriesEntity
import com.imax.player.core.player.parsePlaybackSource
import java.util.Locale

/**
 * Stable natural keys and user-state preservation used when a playlist is refreshed.
 *
 * Content tables use auto-generated primary keys and the refresh path deletes then
 * re-inserts every row. Without preservation the re-inserted rows would receive brand
 * new ids, which (a) resets per-row user flags (isFavorite / watch progress) and
 * (b) orphans the [com.imax.player.core.database.FavoriteEntity] and
 * [com.imax.player.core.database.WatchHistoryEntity] rows that reference those ids by
 * `contentId`. These helpers carry the previous primary key and user-state forward so a
 * refresh keeps favorites and continue-watching intact.
 *
 * They are pure functions (no IO) so the merge behaviour can be unit tested directly.
 */

private fun String.normalizedKeyPart(): String = trim().lowercase(Locale.ROOT)

private fun normalizedUrlKey(url: String): String = parsePlaybackSource(url).url.trim()

internal fun ChannelEntity.stableContentKey(): String = when {
    streamId > 0 -> "stream:$streamId"
    streamUrl.isNotBlank() -> "url:${normalizedUrlKey(streamUrl)}"
    else -> "name:${name.normalizedKeyPart()}:${groupTitle.normalizedKeyPart()}"
}

internal fun MovieEntity.stableContentKey(): String = when {
    streamId > 0 -> "stream:$streamId"
    streamUrl.isNotBlank() -> "url:${normalizedUrlKey(streamUrl)}"
    else -> "name:${name.normalizedKeyPart()}:${categoryName.normalizedKeyPart()}"
}

internal fun SeriesEntity.stableContentKey(): String = when {
    seriesId > 0 -> "series:$seriesId"
    else -> "name:${name.normalizedKeyPart()}:${categoryName.normalizedKeyPart()}"
}

internal fun EpisodeEntity.stableContentKey(): String = when {
    streamUrl.isNotBlank() -> "url:${normalizedUrlKey(streamUrl)}"
    else -> "ep:$seriesId:s$seasonNumber:e$episodeNumber"
}

/** Carry the previous id + favorite/online/last-watched state onto refreshed channels. */
internal fun List<ChannelEntity>.withPreservedChannelState(
    existing: Map<String, ChannelEntity>
): List<ChannelEntity> = map { incoming ->
    val previous = existing[incoming.stableContentKey()] ?: return@map incoming
    incoming.copy(
        id = previous.id,
        isFavorite = previous.isFavorite,
        lastWatched = previous.lastWatched,
        isOnline = previous.isOnline
    )
}

/** Carry the previous id + favorite/progress state onto refreshed movies (also backfills addedAt). */
internal fun List<MovieEntity>.withPreservedMovieState(
    existing: Map<String, MovieEntity>,
    syncTime: Long
): List<MovieEntity> = map { incoming ->
    val previous = existing[incoming.stableContentKey()]
    if (previous == null) {
        incoming.copy(addedAt = incoming.addedAt.takeIf { it > 0L } ?: syncTime)
    } else {
        incoming.copy(
            id = previous.id,
            addedAt = previous.addedAt.takeIf { it > 0L } ?: syncTime,
            isFavorite = previous.isFavorite,
            lastPosition = previous.lastPosition,
            lastWatched = previous.lastWatched,
            totalDuration = previous.totalDuration
        )
    }
}

/** Carry the previous id + favorite/last-watched-episode state onto refreshed series. */
internal fun List<SeriesEntity>.withPreservedSeriesState(
    existing: Map<String, SeriesEntity>,
    syncTime: Long
): List<SeriesEntity> = map { incoming ->
    val previous = existing[incoming.stableContentKey()]
    if (previous == null) {
        incoming.copy(addedAt = incoming.addedAt.takeIf { it > 0L } ?: syncTime)
    } else {
        incoming.copy(
            id = previous.id,
            addedAt = previous.addedAt.takeIf { it > 0L } ?: syncTime,
            isFavorite = previous.isFavorite,
            lastWatchedEpisodeId = previous.lastWatchedEpisodeId
        )
    }
}

/** Carry the previous id + favorite/progress state onto refreshed episodes. */
internal fun List<EpisodeEntity>.withPreservedEpisodeState(
    existing: Map<String, EpisodeEntity>
): List<EpisodeEntity> = map { incoming ->
    val previous = existing[incoming.stableContentKey()] ?: return@map incoming
    incoming.copy(
        id = previous.id,
        isFavorite = previous.isFavorite,
        lastPosition = previous.lastPosition,
        lastWatched = previous.lastWatched,
        totalDuration = previous.totalDuration
    )
}
