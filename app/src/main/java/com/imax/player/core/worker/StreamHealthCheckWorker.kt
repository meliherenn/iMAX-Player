package com.imax.player.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.imax.player.core.database.ChannelDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Checks stream health for all channels in a playlist using HTTP HEAD requests.
 *
 * - Runs once after playlist sync
 * - Marks unreachable streams as [ChannelEntity.isOnline] = false in the DB
 *   (persisted via MIGRATION_2_3 which adds the `isOnline` column)
 * - Uses a shorter OkHttpClient timeout (5s) for fast pings
 * - Channels checked in batches of 10 with 200ms inter-batch delay
 */
@HiltWorker
class StreamHealthCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, params) {

    // Short-timeout client for health checks
    private val pingClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)
        if (playlistId < 0) return Result.success()

        return try {
            val channels = channelDao.getByPlaylist(playlistId).first()
            Timber.d("StreamHealthCheckWorker: checking ${channels.size} channels for playlist $playlistId")

            var deadCount = 0
            var aliveCount = 0

            // Check in batches to avoid overwhelming the network
            channels.chunked(10).forEach { batch ->
                batch.forEach { channel ->
                    val isAlive = pingStream(channel.streamUrl)
                    if (!isAlive) {
                        deadCount++
                        channelDao.setStreamOnline(channel.id, isOnline = false)
                    } else {
                        aliveCount++
                        if (!channel.isOnline) {
                            channelDao.setStreamOnline(channel.id, isOnline = true)
                        }
                    }
                }
                // Small delay between batches to avoid rate limiting
                kotlinx.coroutines.delay(200L)
            }

            Timber.d("StreamHealthCheckWorker: done. alive=$aliveCount dead=$deadCount")
            Result.success(
                workDataOf(
                    KEY_ALIVE_COUNT to aliveCount,
                    KEY_DEAD_COUNT to deadCount
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "StreamHealthCheckWorker failed")
            Result.failure()
        }
    }

    private fun pingStream(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "iMAX Player/Android")
                .build()
            val response = pingClient.newCall(request).execute()
            val code = response.code
            response.close()
            // 2xx and 3xx are OK; 4xx auth errors we treat as alive (stream exists)
            code < 500
        } catch (e: Exception) {
            Timber.v("pingStream dead: $url — ${e.message}")
            false
        }
    }

    companion object {
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_ALIVE_COUNT = "alive_count"
        const val KEY_DEAD_COUNT = "dead_count"
        const val DEAD_STREAM_SENTINEL = -1
        private const val WORK_TAG = "stream_health_check"

        fun runForPlaylist(context: Context, playlistId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<StreamHealthCheckWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_PLAYLIST_ID to playlistId))
                .addTag(WORK_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Timber.d("StreamHealthCheckWorker: enqueued for playlist $playlistId")
        }
    }
}
