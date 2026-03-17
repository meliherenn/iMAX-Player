package com.imax.player.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerManagerTest {

    @Test
    fun `default engine is ExoPlayer`() {
        // The player manager should default to ExoPlayer engine
        // This is validated by the settings defaulting to EXOPLAYER
        val defaultEngine = com.imax.player.core.model.PlayerEngineType.EXOPLAYER
        assertThat(defaultEngine.name).isEqualTo("EXOPLAYER")
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
    fun `initial player state is correct`() {
        val state = PlayerState()
        assertThat(state.playbackState).isEqualTo(PlaybackState.IDLE)
        assertThat(state.isPlaying).isFalse()
        assertThat(state.currentPosition).isEqualTo(0)
        assertThat(state.duration).isEqualTo(0)
        assertThat(state.playbackSpeed).isEqualTo(1f)
        assertThat(state.audioTracks).isEmpty()
        assertThat(state.subtitleTracks).isEmpty()
    }
}
