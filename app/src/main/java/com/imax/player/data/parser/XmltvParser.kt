package com.imax.player.data.parser

import android.util.Xml
import com.imax.player.core.database.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XMLTV format parser using Android's XmlPullParser.
 *
 * Parses <programme> elements from standard XMLTV XML.
 * All times are normalized to UTC milliseconds.
 *
 * XMLTV time format: "20240419120000 +0300"
 */
@Singleton
class XmltvParser @Inject constructor() {

    // XMLTV standard time format: YYYYMMDDHHmmss +ZZZZ
    private val xmltvFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    // Also try without timezone
    private val xmltvFormatterNoTz = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * Parse XMLTV stream and return a list of [EpgProgramEntity].
     * Runs on IO dispatcher.
     *
     * @param inputStream The XMLTV XML input
     * @param channelIdMap Optional mapping from XMLTV channel id to internal channel id
     *                     If null, uses the XMLTV channel-id directly.
     */
    suspend fun parse(
        inputStream: InputStream,
        channelIdMap: Map<String, String>? = null
    ): List<EpgProgramEntity> {
        return withContext(Dispatchers.IO) {
            val result: List<EpgProgramEntity> = try {
                parseInternal(inputStream, channelIdMap)
            } catch (e: Exception) {
                Timber.e(e, "XMLTV parse failed")
                emptyList()
            }
            try { inputStream.close() } catch (_: Exception) {}
            result
        }
    }

    private fun parseInternal(
        inputStream: InputStream,
        channelIdMap: Map<String, String>?
    ): List<EpgProgramEntity> {
        val programs = mutableListOf<EpgProgramEntity>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentProgram: ProgramBuilder? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            val channelXmlId = parser.getAttributeValue(null, "channel") ?: ""
                            val startStr = parser.getAttributeValue(null, "start") ?: ""
                            val stopStr = parser.getAttributeValue(null, "stop") ?: ""

                            val resolvedChannelId = if (channelIdMap != null) {
                                channelIdMap[channelXmlId] ?: channelXmlId
                            } else {
                                channelXmlId
                            }

                            val startMs = parseXmltvTime(startStr)
                            val stopMs = parseXmltvTime(stopStr)

                            if (resolvedChannelId.isNotBlank() && startMs > 0 && stopMs > 0) {
                                currentProgram = ProgramBuilder(
                                    channelId = resolvedChannelId,
                                    startTime = startMs,
                                    endTime = stopMs
                                )
                            }
                        }
                        "title" -> {
                            currentProgram?.let {
                                val lang = parser.getAttributeValue(null, "lang") ?: ""
                                // Prefer title without lang or with tr/en lang
                                if (it.title.isBlank() || lang.startsWith("tr") || lang.startsWith("en")) {
                                    it.pendingTextTag = "title"
                                    it.pendingTextLang = lang
                                }
                            }
                        }
                        "desc" -> {
                            currentProgram?.let {
                                val lang = parser.getAttributeValue(null, "lang") ?: ""
                                if (it.description.isBlank() || lang.startsWith("tr") || lang.startsWith("en")) {
                                    it.pendingTextTag = "desc"
                                    it.pendingTextLang = lang
                                }
                            }
                        }
                        "category" -> {
                            currentProgram?.let {
                                it.pendingTextTag = "category"
                            }
                        }
                        "icon" -> {
                            currentProgram?.let {
                                it.iconUrl = parser.getAttributeValue(null, "src") ?: ""
                            }
                        }
                        "sub-title" -> {
                            currentProgram?.let {
                                it.pendingTextTag = "sub-title"
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    currentProgram?.let { prog ->
                        when (prog.pendingTextTag) {
                            "title" -> if (text.isNotBlank()) {
                                // Only overwrite if blank or preferred lang
                                if (prog.title.isBlank() || prog.pendingTextLang.startsWith("tr") || prog.pendingTextLang.startsWith("en")) {
                                    prog.title = text
                                }
                            }
                            "desc" -> if (text.isNotBlank() && prog.description.isBlank()) {
                                prog.description = text
                            }
                            "category" -> if (text.isNotBlank() && prog.genre.isBlank()) {
                                prog.genre = text
                            }
                            "sub-title" -> if (text.isNotBlank() && prog.subTitle.isBlank()) {
                                prog.subTitle = text
                            }
                        }
                        prog.pendingTextTag = null
                        prog.pendingTextLang = ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme") {
                        currentProgram?.let { prog ->
                            if (prog.isValid()) {
                                programs.add(prog.build())
                            }
                        }
                        currentProgram = null
                    }
                }
            }
            try {
                eventType = parser.next()
            } catch (e: XmlPullParserException) {
                Timber.w(e, "XMLTV parse exception, skipping")
                break
            }
        }

        Timber.d("XMLTV parsed ${programs.size} programs")
        return programs
    }

    /**
     * Parse XMLTV time string to UTC milliseconds.
     * Format: "20240419120000 +0300" or "20240419120000"
     */
    private fun parseXmltvTime(timeStr: String): Long {
        val cleaned = timeStr.trim()
        if (cleaned.isBlank()) return -1L

        return try {
            // Standard format with timezone: "20240419120000 +0300"
            val zdt = ZonedDateTime.parse(cleaned, xmltvFormatter)
            zdt.toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                // Try alternative format (inline timezone): "20240419120000+0300"
                val alt = if (cleaned.length > 14) {
                    val date = cleaned.substring(0, 14)
                    val tz = cleaned.substring(14).trim()
                    "$date $tz"
                } else cleaned
                val zdt = ZonedDateTime.parse(alt, xmltvFormatter)
                zdt.toInstant().toEpochMilli()
            } catch (e2: DateTimeParseException) {
                try {
                    // Fallback: assume UTC
                    val localDt = java.time.LocalDateTime.parse(
                        cleaned.take(14),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    )
                    localDt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                } catch (e3: Exception) {
                    Timber.w("Cannot parse XMLTV time: $cleaned")
                    -1L
                }
            }
        }
    }

    // ─── Builder helpers ────────────────────────────────────────────────────────

    private class ProgramBuilder(
        val channelId: String,
        val startTime: Long,
        val endTime: Long
    ) {
        var title: String = ""
        var description: String = ""
        var genre: String = ""
        var iconUrl: String = ""
        var subTitle: String = ""
        var pendingTextTag: String? = null
        var pendingTextLang: String = ""

        fun isValid() = title.isNotBlank() && startTime > 0 && endTime > startTime

        fun build() = EpgProgramEntity(
            channelId = channelId,
            title = buildFullTitle(),
            description = description,
            startTime = startTime,
            endTime = endTime,
            posterUrl = iconUrl,
            genre = genre
        )

        private fun buildFullTitle(): String {
            return if (subTitle.isNotBlank()) "$title: $subTitle" else title
        }
    }
}

/**
 * Data class for EPG program used in the UI (not the Room entity).
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val posterUrl: String = "",
    val genre: String = ""
) {
    val durationMs: Long get() = endTime - startTime
    val isCurrentlyAiring: Boolean get() {
        val now = System.currentTimeMillis()
        return startTime <= now && endTime > now
    }
    val progressFraction: Float get() {
        if (!isCurrentlyAiring) return 0f
        val now = System.currentTimeMillis()
        return ((now - startTime).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}

fun EpgProgramEntity.toUiModel() = EpgProgram(
    channelId = channelId,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    posterUrl = posterUrl,
    genre = genre
)
