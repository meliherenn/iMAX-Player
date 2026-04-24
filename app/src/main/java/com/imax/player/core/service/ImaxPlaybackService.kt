package com.imax.player.core.service

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.imax.player.core.player.ExoPlayerEngine
import com.imax.player.core.player.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class ImaxPlaybackService : MediaSessionService(), androidx.lifecycle.LifecycleOwner {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var exoPlayerEngine: ExoPlayerEngine

    private var mediaSession: MediaSession? = null
    private var currentEngineName: String? = null

    private val lifecycleDelegate = LifecycleService()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        observeEngineChanges()
        observeExoPlayerInstances()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        releaseSession()
        serviceScope.cancel()
        super.onDestroy()
        Timber.d("ImaxPlaybackService: destroyed")
    }

    override val lifecycle: androidx.lifecycle.Lifecycle
        get() = lifecycleDelegate.lifecycle

    private fun observeEngineChanges() {
        serviceScope.launch {
            playerManager.activeEngineName.collect { engineName ->
                currentEngineName = engineName
                if (engineName == exoPlayerEngine.engineName) {
                    syncSession(exoPlayerEngine.getExoPlayer())
                } else {
                    releaseSession()
                }
            }
        }
    }

    private fun observeExoPlayerInstances() {
        serviceScope.launch {
            exoPlayerEngine.playerInstance.collect { exoPlayer ->
                if (currentEngineName == exoPlayerEngine.engineName) {
                    syncSession(exoPlayer)
                }
            }
        }
    }

    private fun syncSession(exoPlayer: ExoPlayer?) {
        if (exoPlayer == null) {
            releaseSession()
            return
        }

        if (mediaSession?.player !== exoPlayer) {
            releaseSession()
            mediaSession = MediaSession.Builder(this, exoPlayer)
                .setId("imax_media_session")
                .build()
            Timber.d("ImaxPlaybackService: MediaSession created for ExoPlayer")
        }
    }

    private fun releaseSession() {
        mediaSession?.run {
            release()
        }
        mediaSession = null
    }
}
