package com.imax.player.ui.live

import com.imax.player.core.model.Channel
import java.text.Normalizer
import java.util.Locale

private val turkishSignalPattern = Regex(
    pattern = """(^|[^a-z0-9])(tr|trt|turkiye|turkish|turk|turkce)([^a-z0-9]|$)"""
)

private val turkishSignals = listOf(
    "trt",
    "turkiye",
    "turkish",
    "turk",
    "turkce"
)

fun rankChannelsForMobile(channels: List<Channel>): List<Channel> {
    if (channels.isEmpty()) return channels

    val prioritized = ArrayList<Channel>(channels.size)
    val remaining = ArrayList<Channel>(channels.size)

    channels.forEach { channel ->
        if (isTurkishMatch("${channel.groupTitle} ${channel.name}")) {
            prioritized += channel
        } else {
            remaining += channel
        }
    }

    return prioritized + remaining
}

fun prioritizeGroupsForMobile(groups: List<String>): List<String> {
    if (groups.isEmpty()) return groups

    val prioritized = ArrayList<String>(groups.size)
    val remaining = ArrayList<String>(groups.size)

    groups.forEach { group ->
        if (isTurkishMatch(group)) {
            prioritized += group
        } else {
            remaining += group
        }
    }

    return prioritized + remaining
}

private fun isTurkishMatch(value: String): Boolean {
    val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')

    return turkishSignalPattern.containsMatchIn(normalized) ||
        turkishSignals.any { signal -> normalized.contains(signal) }
}
