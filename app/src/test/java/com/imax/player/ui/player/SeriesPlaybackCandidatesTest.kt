package com.imax.player.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeriesPlaybackCandidatesTest {

    @Test
    fun `extensionless xtream series url gets bounded fallbacks`() {
        assertThat(
            seriesPlaybackCandidates(
                "https://provider.example/series/user/pass/12345?token=synthetic"
            )
        ).containsExactly(
            "https://provider.example/series/user/pass/12345?token=synthetic",
            "https://provider.example/series/user/pass/12345.mkv?token=synthetic",
            "https://provider.example/series/user/pass/12345.mp4?token=synthetic",
            "https://provider.example/series/user/pass/12345.ts?token=synthetic"
        ).inOrder()
    }

    @Test
    fun `series url with extension is not rewritten`() {
        val url = "https://provider.example/series/user/pass/12345.mkv"

        assertThat(seriesPlaybackCandidates(url)).containsExactly(url)
    }

    @Test
    fun `non xtream url is not rewritten`() {
        val url = "https://cdn.example/synthetic/episode"

        assertThat(seriesPlaybackCandidates(url)).containsExactly(url)
    }
}
