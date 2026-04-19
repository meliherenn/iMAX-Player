package com.imax.player.core.catchup

import com.imax.player.core.model.Channel
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a Catch-up TV stream URL for a given channel and time range.
 *
 * Xtream Codes catch-up URL format:
 *   {serverUrl}/timeshift/{username}/{password}/{duration}/{start}/{stream_id}.{ext}
 *
 * where:
 *   duration = minutes (e.g. 60)
 *   start    = yyyy-MM-dd:HH-mm  (UTC)
 *
 * If [Channel.catchupSource] is a direct template (e.g. contains {utc} or {duration}),
 * the template is interpolated instead.
 */
@Singleton
class CatchupUrlResolver @Inject constructor() {

    companion object {
        private const val CATCHUP_EXT = "ts"
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
    }

    /**
     * Build a time-shifted stream URL.
     *
     * @param channel       The live channel with catchupSource / streamUrl info
     * @param startTimeMs   Epoch ms of the desired start
     * @param durationMins  How many minutes to play back
     * @param serverUrl     Xtream Codes server base URL (e.g. http://host:port)
     * @param username      Xtream Codes username
     * @param password      Xtream Codes password
     *
     * @return Resolved URL string, or null if channel has no catchup support
     */
    fun resolve(
        channel: Channel,
        startTimeMs: Long,
        durationMins: Int,
        serverUrl: String = "",
        username: String = "",
        password: String = ""
    ): String? {
        val source = channel.catchupSource.trim()

        return when {
            // Direct template (Xtream-style or custom)
            source.isNotEmpty() && (source.contains("{utc}") || source.contains("{duration}")) -> {
                interpolateTemplate(source, startTimeMs, durationMins)
            }

            // Xtream Codes standard timeshift
            serverUrl.isNotEmpty() && username.isNotEmpty() && channel.streamId > 0 -> {
                buildXtreamTimeshiftUrl(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    streamId = channel.streamId,
                    startMs = startTimeMs,
                    durationMins = durationMins
                )
            }

            // No catchup info available
            else -> null
        }
    }

    private fun interpolateTemplate(template: String, startMs: Long, durationMins: Int): String {
        val utcSecs = startMs / 1000
        val utcEnd = utcSecs + durationMins * 60
        return template
            .replace("{utc}", utcSecs.toString())
            .replace("{utcend}", utcEnd.toString())
            .replace("{duration}", durationMins.toString())
            .replace("{start}", DATE_FMT.format(Date(startMs)))
    }

    private fun buildXtreamTimeshiftUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: Int,
        startMs: Long,
        durationMins: Int
    ): String {
        val start = DATE_FMT.format(Date(startMs))
        val base = serverUrl.trimEnd('/')
        return "$base/timeshift/$username/$password/$durationMins/$start/$streamId.$CATCHUP_EXT"
    }
}
