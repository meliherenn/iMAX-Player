package com.imax.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringUtilsTest {

    @Test
    fun `normalizeTitle removes special characters and normalizes whitespace`() {
        assertThat(StringUtils.normalizeTitle("The Matrix (1999)")).isEqualTo("the matrix 1999")
        assertThat(StringUtils.normalizeTitle("  Hello  World  ")).isEqualTo("hello world")
        assertThat(StringUtils.normalizeTitle("Café")).isEqualTo("cafe")
    }

    @Test
    fun `fuzzyMatch returns true for exact matches`() {
        assertThat(StringUtils.fuzzyMatch("The Matrix", "The Matrix")).isTrue()
    }

    @Test
    fun `fuzzyMatch returns true for close matches`() {
        assertThat(StringUtils.fuzzyMatch("The Matrix", "the matrix")).isTrue()
        assertThat(StringUtils.fuzzyMatch("Matrix", "The Matrix")).isTrue()
    }

    @Test
    fun `fuzzyMatch returns false for unrelated strings`() {
        assertThat(StringUtils.fuzzyMatch("The Matrix", "Inception")).isFalse()
    }

    @Test
    fun `formatDuration formats correctly`() {
        assertThat(StringUtils.formatDuration(0)).isEqualTo("00:00")
        assertThat(StringUtils.formatDuration(61000)).isEqualTo("01:01")
        assertThat(StringUtils.formatDuration(3661000)).isEqualTo("1:01:01")
    }

    @Test
    fun `formatDurationMinutes formats correctly`() {
        assertThat(StringUtils.formatDurationMinutes(90)).isEqualTo("1h 30m")
        assertThat(StringUtils.formatDurationMinutes(45)).isEqualTo("45m")
    }

    @Test
    fun `extractYear extracts year from string`() {
        assertThat(StringUtils.extractYear("2023-01-15")).isEqualTo(2023)
        assertThat(StringUtils.extractYear("Released in 1999")).isEqualTo(1999)
        assertThat(StringUtils.extractYear("No year here")).isNull()
        assertThat(StringUtils.extractYear(null)).isNull()
    }
}
