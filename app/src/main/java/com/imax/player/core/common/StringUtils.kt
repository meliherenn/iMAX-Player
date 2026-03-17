package com.imax.player.core.common

import java.text.Normalizer
import java.util.Locale

object StringUtils {
    fun normalizeTitle(title: String): String {
        return Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .trim()
            .lowercase(Locale.ENGLISH)
            .replace(Regex("\\s+"), " ")
    }

    fun cleanTitleForSearch(title: String): String {
        var clean = title
        // Remove quality tags
        clean = clean.replace(Regex("(?i)\\b(1080p|720p|4k|8k|uhd|fhd|hd|x264|x265|hevc|web-dl|webrip|hdrip|bluray|brrip)\\b"), "")
        // Remove S01E01 style tags
        clean = clean.replace(Regex("(?i)\\bS\\d{2}E\\d{2}\\b"), "")
        // Remove year in parentheses or brackets if trailing
        clean = clean.replace(Regex("\\(\\d{4}\\)|\\[\\d{4}\\]"), "")
        // Remove extra info like (TURKCE DUBLAJ), [EN], etc.
        clean = clean.replace(Regex("(?i)(\\([^)]*dublaj[^)]*\\))|(\\([^)]*altyaz\\w*[^)]*\\))"), "")
        // Remove multiple spaces and trim
        return clean.trim().replace(Regex("\\s+"), " ")
    }

    fun fuzzyMatch(query: String, target: String, threshold: Double = 0.7): Boolean {
        val normalizedQuery = normalizeTitle(query)
        val normalizedTarget = normalizeTitle(target)
        if (normalizedQuery == normalizedTarget) return true
        if (normalizedTarget.contains(normalizedQuery) || normalizedQuery.contains(normalizedTarget)) return true
        val similarity = jaroWinklerSimilarity(normalizedQuery, normalizedTarget)
        return similarity >= threshold
    }

    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchDistance = (maxOf(s1.length, s2.length) / 2) - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches) / 3.0

        var prefix = 0
        for (i in 0 until minOf(4, minOf(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return jaro + prefix * 0.1 * (1 - jaro)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun extractYear(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("(19|20)\\d{2}").find(text)
        return match?.value?.toIntOrNull()
    }
}
