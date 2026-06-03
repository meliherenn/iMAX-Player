package com.imax.player.data.parser

import com.imax.player.core.model.Channel
import java.text.Normalizer
import java.util.Locale

private val combiningMarksRegex = Regex("\\p{Mn}+")
private val separatorRegex = Regex("[^a-z0-9]+")
private val repeatedDashRegex = Regex("-+")
private val repeatedUnderscoreRegex = Regex("_+")
private val leadingRegionTokens = setOf("tr", "turkey", "turkiye")
private val trailingRegionTokens = setOf("tr", "turkey", "turkiye")
private val trailingDescriptorTokens = setOf(
    "raw",
    "hd",
    "fhd",
    "fullhd",
    "uhd",
    "sd",
    "hevc",
    "h264",
    "h265",
    "x264",
    "x265",
    "vip",
    "backup",
    "yedek"
)

fun buildEpgChannelIdMap(channels: List<Channel>): Map<String, String> {
    val map = linkedMapOf<String, String>()
    channels.forEach { channel ->
        val canonicalId = canonicalEpgChannelId(channel)
        if (canonicalId.isBlank()) return@forEach

        epgLookupKeysForChannel(channel).forEach { key ->
            if (key !in map) {
                map[key] = canonicalId
            }
        }
    }
    return map
}

fun epgLookupKeysForChannel(channel: Channel): List<String> = buildList {
    addEpgLookupKeys(channel.epgChannelId)
    addEpgLookupKeys(channel.name)
    if (channel.streamId > 0) {
        addEpgLookupKeys(channel.streamId.toString())
    }
}.distinct()

fun canonicalEpgChannelId(channel: Channel): String =
    channel.epgChannelId.trim()
        .ifBlank { channel.name.trim() }
        .ifBlank { channel.streamId.takeIf { it > 0 }?.toString().orEmpty() }

internal fun resolveEpgChannelId(
    xmlChannelId: String,
    displayNames: List<String>,
    channelIdMap: Map<String, String>?
): String {
    if (channelIdMap.isNullOrEmpty()) return xmlChannelId

    val candidates = buildList {
        addEpgLookupKeys(xmlChannelId)
        displayNames.forEach(::addEpgLookupKeys)
    }

    candidates.forEach { candidate ->
        channelIdMap[candidate]?.let { return it }
    }
    return xmlChannelId
}

internal fun epgLookupKeys(value: String): List<String> = buildList {
    addEpgLookupKeys(value)
}.distinct()

private fun MutableList<String>.addEpgLookupKeys(value: String) {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return

    addUnique(trimmed)
    addUnique(trimmed.lowercase(Locale.ROOT))

    val normalized = normalizeForEpgLookup(trimmed)
    if (normalized.isBlank()) return

    addUnique(normalized)
    addUnique(normalized.replace(separatorRegex, "-").replace(repeatedDashRegex, "-").trim('-'))
    addUnique(normalized.replace(separatorRegex, "_").replace(repeatedUnderscoreRegex, "_").trim('_'))
    addUnique(normalized.filter { it in 'a'..'z' || it in '0'..'9' })

    val tokens = normalized.split(separatorRegex)
        .filter(String::isNotBlank)
    addTokenLookupVariants(tokens)
}

private fun MutableList<String>.addTokenLookupVariants(tokens: List<String>) {
    if (tokens.isEmpty()) return

    val variants = linkedSetOf<List<String>>()
    variants += tokens

    val withoutLeadingRegion = tokens.dropWhile { it in leadingRegionTokens }
    if (withoutLeadingRegion.isNotEmpty()) {
        variants += withoutLeadingRegion
    }

    variants.toList().forEach { variant ->
        val withoutTrailingDescriptors = variant.dropLastWhile { it in trailingDescriptorTokens }
        if (withoutTrailingDescriptors.isNotEmpty()) {
            variants += withoutTrailingDescriptors
        }

        val withoutTrailingRegion = variant.dropLastWhile { it in trailingRegionTokens }
        if (withoutTrailingRegion.isNotEmpty()) {
            variants += withoutTrailingRegion
        }

        val compacted = withoutTrailingDescriptors.dropLastWhile { it in trailingRegionTokens }
        if (compacted.isNotEmpty()) {
            variants += compacted
        }
    }

    variants.forEach { variant ->
        addUnique(variant.joinToString("-"))
        addUnique(variant.joinToString("_"))
        addUnique(variant.joinToString(""))
    }
}

private fun MutableList<String>.addUnique(value: String) {
    if (value.isNotBlank() && value !in this) {
        add(value)
    }
}

private fun normalizeForEpgLookup(value: String): String {
    val turkishSafe = value
        .replace('ı', 'i')
        .replace('İ', 'I')
        .replace('ğ', 'g')
        .replace('Ğ', 'G')
        .replace('ü', 'u')
        .replace('Ü', 'U')
        .replace('ş', 's')
        .replace('Ş', 'S')
        .replace('ö', 'o')
        .replace('Ö', 'O')
        .replace('ç', 'c')
        .replace('Ç', 'C')

    return Normalizer.normalize(turkishSafe, Normalizer.Form.NFD)
        .replace(combiningMarksRegex, "")
        .lowercase(Locale.ROOT)
}
