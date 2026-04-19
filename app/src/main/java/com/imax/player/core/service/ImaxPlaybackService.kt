package com.imax.player.core.service

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.imax.player.core.player.ExoPlayerEngine
import com.imax.player.core.player.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Background playback service that exposes a [MediaSession] backed by the active
 * ExoPlayer instance.  This enables:
 *   – Lock-screen / notification media controls
 *   – Android Auto / Wear OS integration
 *   – Assistant "play/pause" commands
 *
 * The service is bound automatically by the OS when a [MediaController] is created.
 * It is started when the player becomes active and stopped when the app exits.
 *
 * Engine-aware: Listens to [PlayerManager.activeEngineName] and recreates the
 * MediaSession when the engine switches back to ExoPlayer.
 */
@AndroidEntryPoint
@UnstableApi
class ImaxPlaybackService : MediaSessionService(), androidx.lifecycle.LifecycleOwner {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    /** Delegate that provides lifecycle events for coroutine scoping. */
    private val lifecycleDelegate = LifecycleService()

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createSessionIfPossible()
        observeEngineChanges()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
        Timber.d("ImaxPlaybackService: destroyed")
    }

    override val lifecycle: androidx.lifecycle.Lifecycle
        get() = lifecycleDelegate.lifecycle

    // ─── Engine observation ──────────────────────────────────────────────────────

    /**
     * Observes engine name changes. When the active engine switches,
     * the MediaSession is rebuilt if an ExoPlayer instance is available,
     * or released if not.
     */
    private fun observeEngineChanges() {
        kotlinx.coroutines.MainScope().launch {
            var lastEngineName: String? = null
            playerManager.activeEngineName
                .collect { engineName ->
                    if (engineName != lastEngineName) {
                        lastEngineName = engineName
                        Timber.d("ImaxPlaybackService: engine changed to $engineName")
                        releaseSession()
                        createSessionIfPossible()
                    }
                }
        }
    }

    // ─── Session management ──────────────────────────────────────────────────────

    private fun createSessionIfPossible() {
        val exoPlayer = resolveExoPlayer()
        if (exoPlayer != null) {
            mediaSession = MediaSession.Builder(this, exoPlayer)
                .setId("imax_media_session")
                .build()
            Timber.d("ImaxPlaybackService: MediaSession created")
        } else {
            Timber.w("ImaxPlaybackService: ExoPlayer not available, session not created")
        }
    }

    private fun releaseSession() {
        mediaSession?.run {
            // Don't release the player — it's managed by PlayerManager
            release()
        }
        mediaSession = null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Attempts to retrieve the raw ExoPlayer instance from the active engine.
     * Returns null if the active engine is not ExoPlayer-based.
     */
    private fun resolveExoPlayer(): ExoPlayer? {
        return try {
            (playerManager.getEngine() as? ExoPlayerEngine)?.getExoPlayer()
        } catch (e: Exception) {
            Timber.e(e, "ImaxPlaybackService: failed to get ExoPlayer")
            null
        }
    }
}
