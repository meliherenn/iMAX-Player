package com.imax.player.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.imax.player.core.database.ChannelDao
import com.imax.player.core.database.PlaylistDao
import com.imax.player.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker for periodic playlist auto-refresh.
 *
 * - Runs every N hours (user-configurable: 6h/12h/24h)
 * - Requires network
 * - Silently refreshes active playlist in background
 * - Input: PLAYLIST_ID (Long), INTERVAL_HOURS (Int)
 */
@HiltWorker
class PlaylistRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)
        if (playlistId < 0) {
            Timber.w("PlaylistRefreshWorker: invalid playlistId, skipping")
            return Result.success()
        }

        return try {
            val playlist = playlistRepository.getPlaylistById(playlistId)
            if (playlist == null) {
                Timber.w("PlaylistRefreshWorker: playlist $playlistId not found")
                return Result.success()
            }

            Timber.d("PlaylistRefreshWorker: refreshing playlist '${playlist.name}' (id=$playlistId)")

            val countBefore = channelDao.countByPlaylist(playlistId)
            val result = playlistRepository.syncPlaylist(playlist)

            when (result) {
                is com.imax.player.core.common.Resource.Success -> {
                    val countAfter = channelDao.countByPlaylist(playlistId)
                    Timber.d("PlaylistRefreshWorker: success. channels before=$countBefore after=$countAfter")
                    Result.success(
                        workDataOf(
                            KEY_CHANNELS_BEFORE to countBefore,
                            KEY_CHANNELS_AFTER to countAfter,
                            KEY_PLAYLIST_NAME to playlist.name
                        )
                    )
                }
                is com.imax.player.core.common.Resource.Error -> {
                    Timber.w("PlaylistRefreshWorker: sync error: ${result.message}")
                    if (runAttemptCount < 2) Result.retry() else Result.failure()
                }
                else -> Result.failure()
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRefreshWorker failed")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_INTERVAL_HOURS = "interval_hours"
        const val KEY_CHANNELS_BEFORE = "channels_before"
        const val KEY_CHANNELS_AFTER = "channels_after"
        const val KEY_PLAYLIST_NAME = "playlist_name"
        const val WORK_NAME_PREFIX = "playlist_refresh_"

        /**
         * Schedule periodic refresh for [playlistId] with [intervalHours].
         * Valid intervals: 6, 12, 24
         */
        fun schedule(context: Context, playlistId: Long, intervalHours: Int = 24) {
            val safeInterval = intervalHours.coerceIn(6, 72)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(
                safeInterval.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_PLAYLIST_ID to playlistId,
                        KEY_INTERVAL_HOURS to safeInterval
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$WORK_NAME_PREFIX$playlistId",
                ExistingPeriodicWorkPolicy.UPDATE, // Update interval if changed
                request
            )
            Timber.d("PlaylistRefreshWorker: scheduled every ${safeInterval}h for playlist $playlistId")
        }

        /**
         * Run an immediate one-time refresh (e.g. user pulls to refresh).
         */
        fun runNow(context: Context, playlistId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PlaylistRefreshWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_PLAYLIST_ID to playlistId))
                .addTag("manual_refresh_$playlistId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Timber.d("PlaylistRefreshWorker: immediate refresh enqueued for playlist $playlistId")
        }

        fun cancel(context: Context, playlistId: Long) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("$WORK_NAME_PREFIX$playlistId")
        }
    }
}
