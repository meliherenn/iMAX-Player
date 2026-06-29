package com.imax.player.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.imax.player.core.common.SensitiveLog
import com.imax.player.core.player.parsePlaybackSource
import com.imax.player.core.common.rethrowIfCancellation
import com.imax.player.core.database.ChannelDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Health classification for a single stream probe.
 *
 * Only [ALIVE] and [DEAD] are acted upon; [UNKNOWN] (transient network errors, timeouts,
 * ambiguous 4xx/5xx) intentionally leaves the channel's stored `isOnline` state untouched,
 * so a flaky probe never hides a working channel.
 */
internal enum class StreamHealth { ALIVE, DEAD, UNKNOWN }

/**
 * Map an HTTP status code to a [StreamHealth] verdict. Conservative by design:
 * a stream is only marked dead on an unambiguous "gone" status. Auth/method/rate-limit
 * responses prove the stream exists, and 5xx / other 4xx are treated as unknown because
 * IPTV edges routinely return them transiently.
 */
internal fun classifyStreamHealth(code: Int): StreamHealth = when {
    code in 200..399 -> StreamHealth.ALIVE
    code == 401 || code == 403 || code == 405 || code == 429 -> StreamHealth.ALIVE
    code == 404 || code == 410 -> StreamHealth.DEAD
    else -> StreamHealth.UNKNOWN
}

/**
 * Checks stream health for the channels in a playlist.
 *
 * - Runs once after playlist sync (currently not wired; see [runForPlaylist]).
 * - Marks unreachable streams as [com.imax.player.core.database.ChannelEntity.isOnline] = false,
 *   and revives recovered streams, but only on a definitive verdict (see [classifyStreamHealth]).
 * - Uses a ranged GET (many IPTV servers reject HEAD) with a short timeout.
 * - Channels are probed in small concurrent batches with an inter-batch delay, capped at
 *   [MAX_CHANNELS_PER_RUN] to bound runtime/battery on very large playlists.
 */
@HiltWorker
class StreamHealthCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, params) {

    // Short-timeout client for health checks.
    private val pingClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)
        if (playlistId < 0) return Result.success()

        return try {
            val totalChannels = channelDao.countByPlaylist(playlistId)
            Timber.d("StreamHealthCheckWorker: checking up to %d of %d channels for playlist %d", MAX_CHANNELS_PER_RUN, totalChannels, playlistId)

            var deadCount = 0
            var aliveCount = 0
            var unknownCount = 0
            var checkedChannels = 0
            var offset = 0
            var capped = false

            while (offset < totalChannels && !capped) {
                val page = channelDao.getByPlaylistPaged(playlistId, PAGE_SIZE, offset)
                if (page.isEmpty()) break

                for (batch in page.chunked(BATCH_SIZE)) {
                    val results = coroutineScope {
                        batch.map { channel ->
                            async { channel to pingStream(channel.streamUrl) }
                        }.map { it.await() }
                    }

                    for ((channel, health) in results) {
                        when (health) {
                            StreamHealth.ALIVE -> {
                                aliveCount++
                                if (!channel.isOnline) channelDao.setStreamOnline(channel.id, isOnline = true)
                            }
                            StreamHealth.DEAD -> {
                                deadCount++
                                if (channel.isOnline) channelDao.setStreamOnline(channel.id, isOnline = false)
                            }
                            // UNKNOWN: leave the stored isOnline state untouched.
                            StreamHealth.UNKNOWN -> unknownCount++
                        }
                    }

                    checkedChannels += batch.size
                    if (checkedChannels >= MAX_CHANNELS_PER_RUN) {
                        capped = true
                        break
                    }
                    // Small delay between batches to avoid rate limiting.
                    delay(BATCH_DELAY_MS)
                }

                offset += PAGE_SIZE
            }

            if (capped && checkedChannels < totalChannels) {
                Timber.w(
                    "StreamHealthCheckWorker: reached per-run cap; checked %d of %d channels for playlist %d",
                    checkedChannels, totalChannels, playlistId
                )
            }
            Timber.d("StreamHealthCheckWorker: done. alive=%d dead=%d unknown=%d", aliveCount, deadCount, unknownCount)
            Result.success(
                workDataOf(
                    KEY_ALIVE_COUNT to aliveCount,
                    KEY_DEAD_COUNT to deadCount,
                    KEY_UNKNOWN_COUNT to unknownCount
                )
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "StreamHealthCheckWorker failed")
            Result.failure()
        }
    }

    private fun pingStream(url: String): StreamHealth {
        if (url.isBlank()) return StreamHealth.UNKNOWN
        val source = parsePlaybackSource(url)
        return try {
            val requestBuilder = Request.Builder()
                .url(source.url)
                // Ranged GET: HEAD is widely rejected by IPTV servers; ask for one byte and
                // close immediately so we never download the stream body.
                .header("Range", "bytes=0-1")
                .header("User-Agent", source.userAgent)
                .get()
            source.requestProperties.forEach(requestBuilder::header)
            val request = requestBuilder.build()
            val code = pingClient.newCall(request).execute().use { it.code }
            classifyStreamHealth(code)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            // Transient/network failure — do NOT mark a working channel offline.
            Timber.v("pingStream unknown: %s - %s", SensitiveLog.redactUrl(source.url), e.javaClass.simpleName)
            StreamHealth.UNKNOWN
        }
    }

    companion object {
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_ALIVE_COUNT = "alive_count"
        const val KEY_DEAD_COUNT = "dead_count"
        const val KEY_UNKNOWN_COUNT = "unknown_count"
        const val DEAD_STREAM_SENTINEL = -1
        private const val WORK_TAG = "stream_health_check"
        private const val PAGE_SIZE = 100
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 200L

        /** Upper bound on channels probed per run to keep runtime/battery within WorkManager limits. */
        const val MAX_CHANNELS_PER_RUN = 2000

        fun runForPlaylist(context: Context, playlistId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
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
