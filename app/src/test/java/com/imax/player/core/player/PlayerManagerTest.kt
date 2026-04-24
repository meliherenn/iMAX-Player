package com.imax.player.core.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class PlayerManagerTest {

    @Test
    fun `playback profiles distinguish live and vod`() {
        assertThat(PlaybackProfile.entries).containsExactly(
            PlaybackProfile.LIVE,
            PlaybackProfile.VOD
        ).inOrder()
    }

    @Test
    fun `playback states are defined correctly`() {
        assertThat(PlaybackState.values()).hasLength(7)
        assertThat(PlaybackState.IDLE.name).isEqualTo("IDLE")
        assertThat(PlaybackState.PLAYING.name).isEqualTo("PLAYING")
        assertThat(PlaybackState.ERROR.name).isEqualTo("ERROR")
    }

    @Test
    fun `aspect ratio modes are available`() {
        assertThat(AspectRatioMode.values()).hasLength(8)
        assertThat(AspectRatioMode.FIT.name).isEqualTo("FIT")
    }

    @Test
    fun `initial player state starts unconfirmed`() {
        val state = PlayerState()

        assertThat(state.playbackState).isEqualTo(PlaybackState.IDLE)
        assertThat(state.isPlaying).isFalse()
        assertThat(state.currentPosition).isEqualTo(0L)
        assertThat(state.duration).isEqualTo(0L)
        assertThat(state.playbackSpeed).isEqualTo(1f)
        assertThat(state.audioTracks).isEmpty()
        assertThat(state.subtitleTracks).isEmpty()
        assertThat(state.isPlaybackConfirmed).isFalse()
        assertThat(state.isSurfaceReady).isFalse()
        assertThat(state.hasRenderedFirstFrame).isFalse()
    }

    @Test
    fun `player engine contract exposes single profile aware play method`() {
        val engine = object : PlayerEngine {
            override val state = MutableStateFlow(PlayerState())
            override val engineName: String = "TEST"

            override fun initialize() {}
            override fun release() {}
            override fun play(url: String, startPosition: Long, profile: PlaybackProfile) {}
            override fun pause() {}
            override fun resume() {}
            override fun stop() {}
            override fun seekTo(position: Long) {}
            override fun seekForward(ms: Long) {}
            override fun seekBackward(ms: Long) {}
            override fun setPlaybackSpeed(speed: Float) {}
            override fun selectAudioTrack(index: Int) {}
            override fun selectSubtitleTrack(index: Int) {}
            override fun disableSubtitles() {}
            override fun setAspectRatio(mode: AspectRatioMode) {}
        }

        engine.play("https://example.com/stream.ts", 0L, PlaybackProfile.LIVE)
        assertThat(engine.state.value.playbackState).isEqualTo(PlaybackState.IDLE)
    }
}
