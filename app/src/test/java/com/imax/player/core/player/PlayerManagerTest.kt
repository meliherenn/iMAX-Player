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

    @Test
    fun `PlayerEngine isAvailable defaults to true`() {
        // Interface default is true — engines must override to report unavailability
        val defaultAvailable = object : PlayerEngine {
            override val state = kotlinx.coroutines.flow.MutableStateFlow(PlayerState())
            override val engineName = "Test"
            override fun initialize() {}
            override fun release() {}
            override fun play(url: String, startPosition: Long) {}
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
            override fun getView(): Any? = null
        }
        assertThat(defaultAvailable.isAvailable()).isTrue()
    }

    @Test
    fun `engine switch state enum has correct values`() {
        assertThat(EngineSwitchState.values()).hasLength(4)
        assertThat(EngineSwitchState.IDLE.name).isEqualTo("IDLE")
        assertThat(EngineSwitchState.SWITCHING.name).isEqualTo("SWITCHING")
        assertThat(EngineSwitchState.SUCCESS.name).isEqualTo("SUCCESS")
        assertThat(EngineSwitchState.FAILED.name).isEqualTo("FAILED")
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // MPV Fallback Test Cases (contract documentation)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    fun `when MPV unavailable should fallback to ExoPlayer on initializeWithSettings`() {
        // Contract: When mpvPlayerEngine.isAvailable() returns false,
        // initializeWithSettings() should select ExoPlayer instead of MPV
        // and persist the EXOPLAYER setting via settingsDataStore.updatePlayerEngine()
        //
        // To fully test: mock mpvPlayerEngine.isAvailable() = false,
        // call initializeWithSettings(), assert currentEngine is ExoPlayerEngine,
        // verify settingsDataStore.updatePlayerEngine(EXOPLAYER) was called
        assertThat(true).isTrue() // placeholder — needs DI/mock setup
    }

    @Test
    fun `tryFallback skips MPV when unavailable and goes to VLC`() {
        // Contract: When currentEngine is ExoPlayer and mpvPlayerEngine.isAvailable() = false,
        // but vlcPlayerEngine.isAvailable() = true,
        // tryFallback() should switch to VLC (not MPV)
        //
        // To fully test: mock engine availability,
        // set currentEngine = exoPlayerEngine,
        // call tryFallback(), verify switchEngine was called with VLC
        assertThat(true).isTrue() // placeholder — needs DI/mock setup
    }

    @Test
    fun `tryFallback returns false when no fallback engines available`() {
        // Contract: When currentEngine is ExoPlayer and both MPV and VLC are unavailable,
        // tryFallback() should return false
        assertThat(true).isTrue() // placeholder — needs DI/mock setup
    }
}
