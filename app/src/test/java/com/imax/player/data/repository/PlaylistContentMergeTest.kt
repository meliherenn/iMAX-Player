package com.imax.player.data.repository

import com.google.common.truth.Truth.assertThat
import com.imax.player.core.database.ChannelEntity
import com.imax.player.core.database.EpisodeEntity
import com.imax.player.core.database.MovieEntity
import com.imax.player.core.database.SeriesEntity
import org.junit.Test

/**
 * Verifies that a playlist refresh (delete + re-insert) preserves the primary key and
 * per-row user state for content matched by a stable natural key. This is the regression
 * guard for K-1: without it, favorites and continue-watching break on every refresh.
 */
class PlaylistContentMergeTest {

    // ─── Stable keys ────────────────────────────────────────────────────────────

    @Test
    fun `channel stable key prefers streamId then url then name`() {
        assertThat(channel(streamId = 7, streamUrl = "http://a/x").stableContentKey())
            .isEqualTo("stream:7")
        assertThat(channel(streamId = 0, streamUrl = "http://a/x").stableContentKey())
            .isEqualTo("url:http://a/x")
        assertThat(channel(streamId = 0, streamUrl = "", name = "BBC", group = "UK").stableContentKey())
            .isEqualTo("name:bbc:uk")
    }

    @Test
    fun `channel url key ignores playback header suffix`() {
        // parsePlaybackSource strips pipe-suffixed headers, so the same stream with and
        // without a header resolves to the same key.
        val withHeader = channel(streamId = 0, streamUrl = "http://a/x|User-Agent=Foo")
        val without = channel(streamId = 0, streamUrl = "http://a/x")
        assertThat(withHeader.stableContentKey()).isEqualTo(without.stableContentKey())
    }

    // ─── Channels ───────────────────────────────────────────────────────────────

    @Test
    fun `refreshed channel keeps previous id favorite and online state`() {
        val previous = channel(id = 42, streamId = 7, isFavorite = true, lastWatched = 999L)
            .copy(isOnline = false)
        val incoming = channel(id = 0, streamId = 7, isFavorite = false, lastWatched = 0L)

        val merged = listOf(incoming).withPreservedChannelState(
            mapOf(previous.stableContentKey() to previous)
        ).single()

        assertThat(merged.id).isEqualTo(42)
        assertThat(merged.isFavorite).isTrue()
        assertThat(merged.lastWatched).isEqualTo(999L)
        assertThat(merged.isOnline).isFalse()
        // Non-user fields come from the incoming (fresh) row.
        assertThat(merged.streamUrl).isEqualTo(incoming.streamUrl)
    }

    @Test
    fun `new channel without a match keeps fresh id`() {
        val incoming = channel(id = 0, streamId = 9)
        val merged = listOf(incoming).withPreservedChannelState(emptyMap()).single()
        assertThat(merged.id).isEqualTo(0L)
    }

    // ─── Movies ─────────────────────────────────────────────────────────────────

    @Test
    fun `refreshed movie keeps id favorite progress and original addedAt`() {
        val previous = movie(id = 5, streamId = 3, isFavorite = true)
            .copy(lastPosition = 1234L, totalDuration = 5000L, lastWatched = 88L, addedAt = 100L)
        val incoming = movie(id = 0, streamId = 3)

        val merged = listOf(incoming).withPreservedMovieState(
            mapOf(previous.stableContentKey() to previous), syncTime = 777L
        ).single()

        assertThat(merged.id).isEqualTo(5)
        assertThat(merged.isFavorite).isTrue()
        assertThat(merged.lastPosition).isEqualTo(1234L)
        assertThat(merged.totalDuration).isEqualTo(5000L)
        assertThat(merged.lastWatched).isEqualTo(88L)
        assertThat(merged.addedAt).isEqualTo(100L)
    }

    @Test
    fun `new movie gets syncTime as addedAt`() {
        val merged = listOf(movie(id = 0, streamId = 3))
            .withPreservedMovieState(emptyMap(), syncTime = 777L).single()
        assertThat(merged.id).isEqualTo(0L)
        assertThat(merged.addedAt).isEqualTo(777L)
    }

    // ─── Series ─────────────────────────────────────────────────────────────────

    @Test
    fun `refreshed series keeps id favorite and last watched episode`() {
        val previous = series(id = 11, seriesId = 2)
            .copy(isFavorite = true, lastWatchedEpisodeId = 314L, addedAt = 50L)
        val incoming = series(id = 0, seriesId = 2)

        val merged = listOf(incoming).withPreservedSeriesState(
            mapOf(previous.stableContentKey() to previous), syncTime = 600L
        ).single()

        assertThat(merged.id).isEqualTo(11)
        assertThat(merged.isFavorite).isTrue()
        assertThat(merged.lastWatchedEpisodeId).isEqualTo(314L)
        assertThat(merged.addedAt).isEqualTo(50L)
    }

    // ─── Episodes ─────────────────────────────────────────────────────────────────

    @Test
    fun `refreshed episode keeps id and progress by stream url`() {
        val previous = episode(id = 20, streamUrl = "http://a/ep1")
            .copy(lastPosition = 42L, totalDuration = 100L, lastWatched = 7L, isFavorite = true)
        val incoming = episode(id = 0, streamUrl = "http://a/ep1")

        val merged = listOf(incoming).withPreservedEpisodeState(
            mapOf(previous.stableContentKey() to previous)
        ).single()

        assertThat(merged.id).isEqualTo(20)
        assertThat(merged.lastPosition).isEqualTo(42L)
        assertThat(merged.totalDuration).isEqualTo(100L)
        assertThat(merged.lastWatched).isEqualTo(7L)
        assertThat(merged.isFavorite).isTrue()
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────

    private fun channel(
        id: Long = 0,
        streamId: Int = 0,
        streamUrl: String = "http://host/live/1",
        name: String = "Channel",
        group: String = "Group",
        isFavorite: Boolean = false,
        lastWatched: Long = 0L
    ) = ChannelEntity(
        id = id,
        playlistId = 1,
        streamId = streamId,
        name = name,
        groupTitle = group,
        streamUrl = streamUrl,
        isFavorite = isFavorite,
        lastWatched = lastWatched
    )

    private fun movie(
        id: Long = 0,
        streamId: Int = 0,
        streamUrl: String = "http://host/movie/1",
        name: String = "Movie",
        isFavorite: Boolean = false
    ) = MovieEntity(
        id = id,
        playlistId = 1,
        streamId = streamId,
        name = name,
        streamUrl = streamUrl,
        isFavorite = isFavorite
    )

    private fun series(
        id: Long = 0,
        seriesId: Int = 0,
        name: String = "Series"
    ) = SeriesEntity(
        id = id,
        playlistId = 1,
        seriesId = seriesId,
        name = name
    )

    private fun episode(
        id: Long = 0,
        streamUrl: String = "http://host/series/1"
    ) = EpisodeEntity(
        id = id,
        seriesId = 1,
        seasonNumber = 1,
        episodeNumber = 1,
        name = "Episode",
        streamUrl = streamUrl
    )
}
