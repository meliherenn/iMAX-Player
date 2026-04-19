package com.imax.player.core.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerEngine, MPVLib.EventObserver {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val engineName: String = "MPV (Donanım/Hardware)"

    private var surfaceView: SurfaceView? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var duration: Long = 0
    private var isPlaybackPaused = false

    // Configuration
    private var configuredBufferMs: Long = 30_000L
    private var configuredLatencyMode: String = "BALANCED"
    private var configuredPreferHw: Boolean = true

    override fun setPlaybackConfiguration(bufferDurationMs: Long, liveLatencyMode: String, preferHwDecoding: Boolean) {
        this.configuredBufferMs = bufferDurationMs
        this.configuredLatencyMode = liveLatencyMode
        this.configuredPreferHw = preferHwDecoding
    }

    override fun initialize() {
        if (isInitialized) return
        try {
            MPVLib.create(context)
            // Enforce hardware decoding
            if (configuredPreferHw) {
                MPVLib.setOptionString("hwdec", "mediacodec-copy")
            } else {
                MPVLib.setOptionString("hwdec", "no")
            }
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            // Optimize for IPTV latency
            MPVLib.setOptionString("profile", "fast")
            MPVLib.setOptionString("cache", "yes")
            
            MPVLib.init()
            MPVLib.addObserver(this)
            
            // Observe necessary properties
            MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("core-idle", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
            
            isInitialized = true
            Timber.tag("MpvPlayerEngine").d("MPVLib initialized successfully")
        } catch (e: Throwable) {
            Timber.e(e, "Error initializing MPVLib")
            _state.value = _state.value.copy(playbackState = PlaybackState.ERROR, errorMessage = e.message)
        }
    }

    override fun release() {
        if (!isInitialized) return
        try {
            stop()
            MPVLib.removeObserver(this)
            MPVLib.detachSurface()
            MPVLib.destroy()
            isInitialized = false
        } catch (e: Exception) {
            Timber.e(e, "Error releasing MPVLib")
        }
        _state.value = PlayerState(playbackState = PlaybackState.IDLE)
    }

    override fun play(url: String, startPosition: Long) {
        if (!isInitialized) initialize()
        
        _state.value = _state.value.copy(
            playbackState = PlaybackState.BUFFERING,
            isPlaying = false,
            errorMessage = null
        )
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (startPosition > 0) {
                    MPVLib.command(arrayOf("loadfile", url, "replace", "start=${startPosition / 1000}"))
                } else {
                    MPVLib.command(arrayOf("loadfile", url))
                }
                isPlaybackPaused = false
                MPVLib.setPropertyBoolean("pause", false)
            } catch (e: Exception) {
                Timber.e(e, "Error playing MPV file")
                _state.value = _state.value.copy(
                    playbackState = PlaybackState.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    override fun pause() {
        if (!isInitialized) return
        isPlaybackPaused = true
        MPVLib.setPropertyBoolean("pause", true)
        _state.value = _state.value.copy(isPlaying = false, playbackState = PlaybackState.PAUSED)
    }

    override fun resume() {
        if (!isInitialized) return
        isPlaybackPaused = false
        MPVLib.setPropertyBoolean("pause", false)
        _state.value = _state.value.copy(isPlaying = true, playbackState = PlaybackState.PLAYING)
    }

    override fun stop() {
        if (!isInitialized) return
        try {
            MPVLib.command(arrayOf("stop"))
        } catch(e: Exception){}
        _state.value = _state.value.copy(isPlaying = false, playbackState = PlaybackState.IDLE)
    }

    override fun seekTo(position: Long) {
        if (!isInitialized) return
        MPVLib.setPropertyDouble("time-pos", position / 1000.0)
    }

    override fun seekForward(ms: Long) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("seek", (ms / 1000).toString(), "relative"))
    }

    override fun seekBackward(ms: Long) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("seek", (-ms / 1000).toString(), "relative"))
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (!isInitialized) return
        MPVLib.setPropertyDouble("speed", speed.toDouble())
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun selectAudioTrack(index: Int) {
        if (!isInitialized) return
        try {
            MPVLib.setPropertyInt("aid", index)
        } catch(e: Exception){}
    }

    override fun selectSubtitleTrack(index: Int) {
        if (!isInitialized) return
        try {
            MPVLib.setPropertyInt("sid", index)
        } catch(e: Exception){}
    }

    override fun disableSubtitles() {
        if (!isInitialized) return
        try {
            MPVLib.setPropertyString("sid", "no")
        } catch(e: Exception){}
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        if (!isInitialized) return
        val keepaspect = when(mode) {
            AspectRatioMode.FIT, AspectRatioMode.ORIGINAL -> "yes"
            AspectRatioMode.STRETCH, AspectRatioMode.FILL -> "no"
            else -> "yes"
        }
        try {
            MPVLib.setPropertyString("keepaspect", keepaspect)
        } catch(e: Exception){}
        _state.value = _state.value.copy(aspectRatioMode = mode)
    }

    override fun getView(): Any? {
        if (surfaceView == null) {
            surfaceView = SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        if (isInitialized) {
                            MPVLib.attachSurface(holder.surface)
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        if (isInitialized) {
                            try {
                                MPVLib.detachSurface()
                            } catch(e: Exception){}
                        }
                    }
                })
            }
        }
        return surfaceView
    }

    // MPV Event Callbacks
    override fun eventProperty(property: String, value: Boolean) {
        mainHandler.post {
            when (property) {
                "pause" -> {
                    _state.value = _state.value.copy(
                        isPlaying = !value,
                        playbackState = if (value) PlaybackState.PAUSED else PlaybackState.PLAYING
                    )
                }
                "core-idle" -> {
                    if (value && !isPlaybackPaused) {
                        _state.value = _state.value.copy(playbackState = PlaybackState.BUFFERING)
                    } else if (!value && !isPlaybackPaused) {
                        _state.value = _state.value.copy(playbackState = PlaybackState.PLAYING)
                    }
                }
                "eof-reached" -> {
                    if (value) {
                        _state.value = _state.value.copy(playbackState = PlaybackState.ENDED)
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {}
    
    override fun eventProperty(property: String, value: Double) {
        mainHandler.post {
            when (property) {
                "time-pos" -> _state.value = _state.value.copy(currentPosition = (value * 1000).toLong())
                "duration" -> {
                    duration = (value * 1000).toLong()
                    _state.value = _state.value.copy(duration = duration)
                }
            }
        }
    }
    
    override fun eventProperty(property: String, value: String) {}
    
    override fun eventProperty(property: String) {}

    override fun event(eventId: Int) {
        mainHandler.post {
            when (eventId) {
                MPVLib.MPV_EVENT_START_FILE -> {
                    _state.value = _state.value.copy(playbackState = PlaybackState.BUFFERING)
                }
                MPVLib.MPV_EVENT_FILE_LOADED -> {
                    _state.value = _state.value.copy(playbackState = PlaybackState.PLAYING, isPlaying = true)
                }
                MPVLib.MPV_EVENT_END_FILE -> {
                    // Check if it's an error vs regular EOF
                }
            }
        }
    }
}
