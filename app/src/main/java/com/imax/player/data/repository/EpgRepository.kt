package com.imax.player.data.repository

import android.content.Context
import com.imax.player.core.database.EpgDao
import com.imax.player.core.database.EpgProgramEntity
import com.imax.player.core.worker.EpgSyncWorker
import com.imax.player.data.parser.EpgProgram
import com.imax.player.data.parser.XmltvParser
import com.imax.player.data.parser.toUiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val epgDao: EpgDao,
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {

    /**
     * Returns a live flow of current and upcoming programs for [channelId].
     * Automatically refreshed when DB changes.
     */
    fun getProgramsForChannel(channelId: String): Flow<List<EpgProgram>> =
        epgDao.getPrograms(channelId, System.currentTimeMillis())
            .map { list -> list.map { it.toUiModel() } }

    /**
     * Returns the currently-airing program for [channelId], or null.
     */
    suspend fun getCurrentProgram(channelId: String): EpgProgram? =
        epgDao.getCurrentProgram(channelId, System.currentTimeMillis())?.toUiModel()

    /**
     * Returns the next program after [nowMs] for [channelId], or null.
     */
    suspend fun getNextProgram(channelId: String, nowMs: Long = System.currentTimeMillis()): EpgProgram? =
        withContext(Dispatchers.IO) {
            epgDao.getPrograms(channelId, nowMs).first()
                .firstOrNull { it.startTime > nowMs }
                ?.toUiModel()
        }

    /**
     * Fetch and parse XMLTV from [url], insert into DB.
     * Called manually or from [EpgSyncWorker].
     */
    suspend fun fetchAndSave(url: String, channelIdMap: Map<String, String>? = null): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("EpgRepository: fetching $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "iMAX Player/Android")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val programs = xmltvParser.parse(body.byteStream(), channelIdMap)
                if (programs.isNotEmpty()) {
                    epgDao.deleteOld(System.currentTimeMillis() - 7_200_000L) // 2h past
                    programs.chunked(500).forEach { epgDao.insertAll(it) }
                }
                Timber.d("EpgRepository: saved ${programs.size} programs")
                Result.success(programs.size)
            } catch (e: Exception) {
                Timber.e(e, "EpgRepository fetch failed")
                Result.failure(e)
            }
        }

    /**
     * Schedule daily background EPG sync via WorkManager.
     */
    fun scheduleDailySync(epgUrl: String) {
        EpgSyncWorker.enqueue(context, epgUrl)
    }

    /**
     * Trigger an immediate EPG refresh.
     */
    fun syncNow(epgUrl: String) {
        EpgSyncWorker.runNow(context, epgUrl)
    }

    /**
     * Delete all EPG data older than 2 hours.
     */
    suspend fun pruneOld() {
        epgDao.deleteOld(System.currentTimeMillis() - 7_200_000L)
    }

    /**
     * Returns a map of channelId → current program for a list of channels.
     * Efficient batch lookup for EPG grid screens.
     */
    suspend fun getCurrentProgramsForChannels(
        channelIds: List<String>
    ): Map<String, EpgProgram> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, EpgProgram>()
        channelIds.forEach { channelId ->
            epgDao.getCurrentProgram(channelId, now)?.let { entity ->
                result[channelId] = entity.toUiModel()
            }
        }
        result
    }

    /**
     * M3U/Xtream channel id'lerini XMLTV channel id'leriyle eşleştiren map oluşturur.
     * Önce tam eşleşme, sonra normalize edilmiş fuzzy eşleşme dener.
     */
    suspend fun buildChannelIdMap(channels: List<com.imax.player.core.model.Channel>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        channels.forEach { channel ->
            val epgId = channel.epgChannelId
            if (epgId.isNotBlank()) {
                map[epgId] = epgId
                // Normalize: küçük harf, boşluk → tire
                val normalized = epgId.lowercase().replace(" ", "-").replace("_", "-")
                map[normalized] = epgId
            }
        }
        return map
    }
}

private fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
    val result = mutableMapOf<K, V>()
    for (item in this) {
        transform(item)?.let { (k, v) -> result[k] = v }
    }
    return result
}
