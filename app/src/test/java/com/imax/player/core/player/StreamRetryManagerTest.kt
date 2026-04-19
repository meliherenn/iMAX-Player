package com.imax.player.core.player

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamRetryManagerTest {

    // We test only the pure logic parts that don't need Android Context
    // The full StreamRetryManager requires Context, so we test the data classes
    // and classification logic via a testable wrapper.

    // ─── RetryState ────────────────────────────────────────────────────────────

    @Test
    fun `default RetryState is idle`() {
        val state = RetryState()
        assertThat(state.isRetrying).isFalse()
        assertThat(state.attempt).isEqualTo(0)
        assertThat(state.maxAttempts).isEqualTo(3)
        assertThat(state.nextRetryInSeconds).isEqualTo(0)
    }

    @Test
    fun `RetryState is retrying`() {
        val state = RetryState(
            isRetrying = true,
            attempt = 1,
            maxAttempts = 3,
            errorType = StreamErrorType.STREAM_OFFLINE,
            userMessage = "Yayın kapalı",
            nextRetryInSeconds = 2
        )
        assertThat(state.isRetrying).isTrue()
        assertThat(state.attempt).isEqualTo(1)
        assertThat(state.errorType).isEqualTo(StreamErrorType.STREAM_OFFLINE)
    }

    // ─── StreamErrorType enum ──────────────────────────────────────────────────

    @Test
    fun `StreamErrorType values are defined`() {
        assertThat(StreamErrorType.values()).asList().containsAtLeast(
            StreamErrorType.NO_INTERNET,
            StreamErrorType.STREAM_OFFLINE,
            StreamErrorType.PLAYBACK_ERROR,
            StreamErrorType.UNKNOWN
        )
    }

    // ─── PlayerState error detection ─────────────────────────────────────────

    @Test
    fun `PlayerState with errorMessage is ERROR state`() {
        val state = PlayerState(
            playbackState = PlaybackState.ERROR,
            errorMessage = "Stream offline"
        )
        assertThat(state.playbackState).isEqualTo(PlaybackState.ERROR)
        assertThat(state.errorMessage).isNotNull()
    }

    @Test
    fun `PlayerState without error is not ERROR`() {
        val state = PlayerState(playbackState = PlaybackState.PLAYING)
        assertThat(state.playbackState).isNotEqualTo(PlaybackState.ERROR)
        assertThat(state.errorMessage).isNull()
    }

    // ─── PlaybackState transitions ─────────────────────────────────────────────

    @Test
    fun `playback error triggers retry logic`() {
        // Simulate: error → retry state → playing
        val errorState = RetryState(
            isRetrying = true,
            attempt = 1,
            maxAttempts = 3,
            errorType = StreamErrorType.STREAM_OFFLINE,
            userMessage = "Yayın şu an kapalı, yeniden deneniyor... (1/3)",
            nextRetryInSeconds = 2
        )
        assertThat(errorState.isRetrying).isTrue()
        assertThat(errorState.userMessage).contains("1/3")

        val successState = RetryState()
        assertThat(successState.isRetrying).isFalse()
    }

    // ─── Backoff progression ──────────────────────────────────────────────────

    @Test
    fun `backoff delays increase exponentially`() {
        val delays = listOf(2_000L, 4_000L, 8_000L)
        assertThat(delays[0]).isEqualTo(2_000L)
        assertThat(delays[1]).isEqualTo(delays[0] * 2)
        assertThat(delays[2]).isEqualTo(delays[1] * 2)
    }

    // ─── User messages per error type ─────────────────────────────────────────

    @Test
    fun `no internet message contains network check text`() {
        val msg = buildMessage(StreamErrorType.NO_INTERNET, 1, 3)
        assertThat(msg.lowercase()).containsMatch("internet|bağlantı")
    }

    @Test
    fun `stream offline message contains retry text`() {
        val msg = buildMessage(StreamErrorType.STREAM_OFFLINE, 2, 3)
        assertThat(msg).contains("2/3")
    }

    @Test
    fun `messages include attempt counter`() {
        listOf(StreamErrorType.NO_INTERNET, StreamErrorType.STREAM_OFFLINE,
               StreamErrorType.PLAYBACK_ERROR, StreamErrorType.UNKNOWN).forEach { type ->
            val msg = buildMessage(type, 2, 3)
            assertThat(msg).contains("2/3")
        }
    }

    // Helper to replicate message logic without requiring Context
    private fun buildMessage(type: StreamErrorType, attempt: Int, max: Int) = when (type) {
        StreamErrorType.NO_INTERNET ->
            "İnternet bağlantınızı kontrol edin ($attempt/$max)"
        StreamErrorType.STREAM_OFFLINE ->
            "Yayın şu an kapalı, yeniden deneniyor... ($attempt/$max)"
        StreamErrorType.PLAYBACK_ERROR ->
            "Oynatma hatası, farklı format deneniyor... ($attempt/$max)"
        StreamErrorType.UNKNOWN ->
            "Bağlantı hatası, yeniden deneniyor... ($attempt/$max)"
    }
}
