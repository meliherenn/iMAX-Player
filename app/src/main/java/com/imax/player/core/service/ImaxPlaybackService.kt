package com.imax.player.core.service

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.imax.player.core.player.ExoPlayerEngine
import com.imax.player.core.player.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
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
 */
@AndroidEntryPoint
@UnstableApi
class ImaxPlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
        Timber.d("ImaxPlaybackService: destroyed")
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
