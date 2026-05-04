package com.imax.player.core.common

import java.text.Collator
import java.util.Locale

fun orderCategoryNames(
    categories: Iterable<String>,
    locale: Locale = Locale.getDefault()
): List<String> {
    return categories
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(categoryNameComparator(locale))
}

fun categoryNameComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }

    return Comparator { left, right ->
        val naturalResult = compareNatural(left.trim(), right.trim(), collator)
        if (naturalResult != 0) naturalResult else left.compareTo(right)
    }
}

private fun compareNatural(left: String, right: String, collator: Collator): Int {
    var leftIndex = 0
    var rightIndex = 0

    while (leftIndex < left.length && rightIndex < right.length) {
        val leftIsDigit = left[leftIndex].isDigit()
        val rightIsDigit = right[rightIndex].isDigit()
        val leftEnd = nextChunkEnd(left, leftIndex, leftIsDigit)
        val rightEnd = nextChunkEnd(right, rightIndex, rightIsDigit)

        val chunkResult = if (leftIsDigit && rightIsDigit) {
            compareNumberChunks(left, leftIndex, leftEnd, right, rightIndex, rightEnd)
        } else {
            collator.compare(
                left.substring(leftIndex, leftEnd),
                right.substring(rightIndex, rightEnd)
            )
        }

        if (chunkResult != 0) return chunkResult

        leftIndex = leftEnd
        rightIndex = rightEnd
    }

    return left.length.compareTo(right.length)
}

private fun nextChunkEnd(value: String, start: Int, isDigit: Boolean): Int {
    var end = start + 1
    while (end < value.length && value[end].isDigit() == isDigit) {
        end++
    }
    return end
}

private fun compareNumberChunks(
    left: String,
    leftStart: Int,
    leftEnd: Int,
    right: String,
    rightStart: Int,
    rightEnd: Int
): Int {
    val leftRaw = left.substring(leftStart, leftEnd)
    val rightRaw = right.substring(rightStart, rightEnd)
    val leftDigits = leftRaw.trimStart('0').ifEmpty { "0" }
    val rightDigits = rightRaw.trimStart('0').ifEmpty { "0" }

    val lengthResult = leftDigits.length.compareTo(rightDigits.length)
    if (lengthResult != 0) return lengthResult

    val digitResult = leftDigits.compareTo(rightDigits)
    if (digitResult != 0) return digitResult

    return leftRaw.length.compareTo(rightRaw.length)
}
