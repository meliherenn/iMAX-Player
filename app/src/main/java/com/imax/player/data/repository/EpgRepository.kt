package com.imax.player.data.repository

import android.content.Context
import com.imax.player.core.common.SensitiveLog
import com.imax.player.core.database.EpgDao
import com.imax.player.core.database.EpgProgramEntity
import com.imax.player.core.model.Channel
import com.imax.player.core.worker.EpgSyncWorker
import com.imax.player.data.parser.EpgProgram
import com.imax.player.data.parser.XmltvParser
import com.imax.player.data.parser.asXmltvInputStream
import com.imax.player.data.parser.buildEpgChannelIdMap
import com.imax.player.data.parser.epgLookupKeysForChannel
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
    private companion object {
        private const val MAX_SQL_BIND_ARGS = 800
    }

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

    suspend fun getCurrentProgram(channel: Channel): EpgProgram? =
        withContext(Dispatchers.IO) {
            val candidates = epgLookupKeysForChannel(channel)
            queryCurrentProgramForCandidates(candidates, System.currentTimeMillis())?.toUiModel()
        }

    /**
     * Returns the next program after [nowMs] for [channelId], or null.
     */
    suspend fun getNextProgram(channelId: String, nowMs: Long = System.currentTimeMillis()): EpgProgram? =
        withContext(Dispatchers.IO) {
            epgDao.getPrograms(channelId, nowMs).first()
                .firstOrNull { it.startTime > nowMs }
                ?.toUiModel()
        }

    suspend fun getNextProgram(channel: Channel, nowMs: Long = System.currentTimeMillis()): EpgProgram? =
        withContext(Dispatchers.IO) {
            val candidates = epgLookupKeysForChannel(channel)
            queryUpcomingProgramsForCandidates(candidates, nowMs)
                .filter { it.startTime > nowMs }
                .firstProgramForCandidates(candidates)
                ?.toUiModel()
        }

    /**
     * Fetch and parse XMLTV from [url], insert into DB.
     * Called manually or from [EpgSyncWorker].
     */
    suspend fun fetchAndSave(url: String, channelIdMap: Map<String, String>? = null): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("EpgRepository: fetching %s", SensitiveLog.redactUrl(url))
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "iMAX Player/Android")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val programs = xmltvParser.parse(
                    body.byteStream().asXmltvInputStream(url, body.contentType()?.toString()),
                    channelIdMap
                )
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

    suspend fun savePrograms(programs: List<EpgProgramEntity>): Int =
        withContext(Dispatchers.IO) {
            if (programs.isEmpty()) return@withContext 0
            epgDao.deleteOld(System.currentTimeMillis() - 7_200_000L)
            programs.chunked(500).forEach { epgDao.insertAll(it) }
            Timber.d("EpgRepository: saved ${programs.size} Xtream EPG programs")
            programs.size
        }

    /**
     * Schedule daily background EPG sync via WorkManager.
     */
    fun scheduleDailySync(epgUrl: String) {
        EpgSyncWorker.enqueue(context, epgUrl)
    }

    fun cancelScheduledSync(epgUrl: String) {
        EpgSyncWorker.cancel(context, epgUrl)
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
     * Returns a map of app channel id to the currently-airing program.
     * The lookup accepts tvg-id, XMLTV id variants, channel names, and stream ids.
     */
    suspend fun getCurrentProgramsForChannels(
        channels: List<Channel>
    ): Map<Long, EpgProgram> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val candidatesByChannelId = channels
            .distinctBy(Channel::id)
            .associate { channel -> channel.id to epgLookupKeysForChannel(channel) }
            .filterValues { it.isNotEmpty() }

        if (candidatesByChannelId.isEmpty()) {
            return@withContext emptyMap()
        }

        val programsByLookupId = candidatesByChannelId.values
            .flatten()
            .distinct()
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids -> epgDao.getCurrentProgramsForIds(ids, now) }
            .groupBy(EpgProgramEntity::channelId)

        candidatesByChannelId.mapNotNull { (channelId, candidates) ->
            val program = candidates
                .firstNotNullOfOrNull { candidate -> programsByLookupId[candidate]?.firstOrNull() }
                ?.toUiModel()
            program?.let { channelId to it }
        }.toMap()
    }

    /**
     * M3U/Xtream channel id'lerini XMLTV channel id'leriyle eşleştiren map oluşturur.
     * Önce tam eşleşme, sonra normalize edilmiş fuzzy eşleşme dener.
     */
    suspend fun buildChannelIdMap(channels: List<Channel>): Map<String, String> {
        return buildEpgChannelIdMap(channels)
    }

    private suspend fun queryCurrentProgramForCandidates(
        candidates: List<String>,
        nowMs: Long
    ): EpgProgramEntity? {
        if (candidates.isEmpty()) return null
        return candidates
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids -> epgDao.getCurrentProgramsForIds(ids, nowMs) }
            .firstProgramForCandidates(candidates)
    }

    private suspend fun queryUpcomingProgramsForCandidates(
        candidates: List<String>,
        nowMs: Long
    ): List<EpgProgramEntity> {
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids -> epgDao.getUpcomingProgramsForIds(ids, nowMs) }
    }
}

private fun List<EpgProgramEntity>.firstProgramForCandidates(
    candidates: List<String>
): EpgProgramEntity? {
    if (isEmpty()) return null

    val programsByLookupId = groupBy(EpgProgramEntity::channelId)
    candidates.forEach { candidate ->
        programsByLookupId[candidate]?.firstOrNull()?.let { return it }
    }
    return null
}
