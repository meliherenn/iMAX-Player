package com.imax.player.ui.live

import com.imax.player.core.model.Channel
import java.util.Locale

private val trSignals = listOf(
    "tr",
    "turkiye",
    "türkiye",
    "turkish",
    "turk",
    "türk"
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
    val normalized = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')
        .replace('İ', 'i')

    return trSignals.any { signal -> normalized.contains(signal) }
}
