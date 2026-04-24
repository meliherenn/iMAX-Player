package com.imax.player.data.parser

import com.imax.player.core.database.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import timber.log.Timber
import java.io.InputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.SAXParserFactory

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
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            disableFeature("http://xml.org/sax/features/external-general-entities")
            disableFeature("http://xml.org/sax/features/external-parameter-entities")
            disableFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd")
        }

        var currentProgram: ProgramBuilder? = null
        var currentTag: String? = null
        var currentLang: String = ""
        val textBuffer = StringBuilder()

        factory.newSAXParser().parse(inputStream, object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String,
                attributes: Attributes
            ) {
                when (qName) {
                    "programme" -> {
                        val channelXmlId = attributes.getValue("channel").orEmpty()
                        val startMs = parseXmltvTime(attributes.getValue("start").orEmpty())
                        val stopMs = parseXmltvTime(attributes.getValue("stop").orEmpty())
                        val resolvedChannelId = channelIdMap?.get(channelXmlId) ?: channelXmlId

                        currentProgram = if (
                            resolvedChannelId.isNotBlank() &&
                            startMs > 0 &&
                            stopMs > startMs
                        ) {
                            ProgramBuilder(
                                channelId = resolvedChannelId,
                                startTime = startMs,
                                endTime = stopMs
                            )
                        } else {
                            null
                        }
                    }

                    "title", "desc", "category", "sub-title" -> {
                        currentTag = qName
                        currentLang = attributes.getValue("lang").orEmpty()
                        textBuffer.setLength(0)
                    }

                    "icon" -> currentProgram?.iconUrl = attributes.getValue("src").orEmpty()
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (currentTag != null) {
                    textBuffer.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val text = textBuffer.toString().trim()
                when (qName) {
                    "title" -> currentProgram?.consumeTitle(text, currentLang)
                    "desc" -> currentProgram?.consumeDescription(text, currentLang)
                    "category" -> currentProgram?.consumeCategory(text)
                    "sub-title" -> currentProgram?.consumeSubTitle(text)
                    "programme" -> {
                        currentProgram?.takeIf(ProgramBuilder::isValid)?.let { programs.add(it.build()) }
                        currentProgram = null
                    }
                }

                if (currentTag == qName) {
                    currentTag = null
                    currentLang = ""
                    textBuffer.setLength(0)
                }
            }
        })

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
            OffsetDateTime.parse(normalizeXmltvTime(cleaned), xmltvFormatter)
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(cleaned.take(14), xmltvFormatterNoTz)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            } catch (e2: DateTimeParseException) {
                Timber.w("Cannot parse XMLTV time: $cleaned")
                -1L
            }
        }
    }

    private fun normalizeXmltvTime(timeStr: String): String {
        if (timeStr.length <= 14) return timeStr
        val date = timeStr.take(14)
        val timezone = timeStr.drop(14).trim()
        return "$date $timezone"
    }

    private fun SAXParserFactory.disableFeature(feature: String) {
        runCatching { setFeature(feature, false) }
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

        fun consumeTitle(text: String, lang: String) {
            if (text.isBlank()) return
            if (title.isBlank() || lang.startsWith("tr") || lang.startsWith("en")) {
                title = text
            }
        }

        fun consumeDescription(text: String, lang: String) {
            if (text.isBlank()) return
            if (description.isBlank() || lang.startsWith("tr") || lang.startsWith("en")) {
                description = text
            }
        }

        fun consumeCategory(text: String) {
            if (text.isNotBlank() && genre.isBlank()) {
                genre = text
            }
        }

        fun consumeSubTitle(text: String) {
            if (text.isNotBlank() && subTitle.isBlank()) {
                subTitle = text
            }
        }

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
