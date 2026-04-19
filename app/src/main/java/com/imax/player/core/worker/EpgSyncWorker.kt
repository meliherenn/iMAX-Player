package com.imax.player.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.imax.player.core.database.EpgDao
import com.imax.player.core.database.PlaylistDao
import com.imax.player.data.parser.XmltvParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker that downloads and parses the EPG (XMLTV) file.
 *
 * - Runs once a day (PeriodicWorkRequest, 24h interval)
 * - Requires network
 * - On success: deletes old programs, inserts fresh data
 * - Input: EPG_URL (String)
 */
@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val epgDao: EpgDao,
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val epgUrl = inputData.getString(KEY_EPG_URL)
        if (epgUrl.isNullOrBlank()) {
            Timber.w("EpgSyncWorker: no EPG URL provided, skipping")
            return Result.success()
        }

        return try {
            Timber.d("EpgSyncWorker: fetching EPG from $epgUrl")
            val request = Request.Builder()
                .url(epgUrl)
                .header("User-Agent", "iMAX Player/Android")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("EpgSyncWorker: HTTP ${response.code}")
                return if (runAttemptCount < 2) Result.retry() else Result.failure()
            }

            val body = response.body ?: run {
                Timber.w("EpgSyncWorker: empty body")
                return Result.failure()
            }

            val programs = xmltvParser.parse(body.byteStream())
            if (programs.isEmpty()) {
                Timber.w("EpgSyncWorker: no programs parsed")
                return Result.success() // Not a failure — might be empty EPG
            }

            // Delete expired programs first
            val now = System.currentTimeMillis()
            epgDao.deleteOld(now - TimeUnit.HOURS.toMillis(2)) // Keep 2h of past programs

            // Insert in batches to avoid DB transaction timeouts
            programs.chunked(500).forEach { batch ->
                epgDao.insertAll(batch)
            }

            Timber.d("EpgSyncWorker: inserted ${programs.size} programs")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "EpgSyncWorker failed")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_EPG_URL = "epg_url"
        const val WORK_NAME_PREFIX = "epg_sync_"

        /**
         * Enqueue a daily EPG sync for the given [epgUrl].
         * Uses unique work name so only one sync runs per URL.
         */
        fun enqueue(context: Context, epgUrl: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(KEY_EPG_URL to epgUrl)
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$WORK_NAME_PREFIX${epgUrl.hashCode()}",
                ExistingPeriodicWorkPolicy.KEEP, // Don't reset existing schedule
                request
            )
            Timber.d("EpgSyncWorker: enqueued daily sync for $epgUrl")
        }

        /**
         * Run an immediate one-time EPG sync (e.g. on first playlist load).
         */
        fun runNow(context: Context, epgUrl: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<EpgSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_EPG_URL to epgUrl))
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Timber.d("EpgSyncWorker: one-time sync enqueued for $epgUrl")
        }

        fun cancel(context: Context, epgUrl: String) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("$WORK_NAME_PREFIX${epgUrl.hashCode()}")
        }
    }
}
