package com.imax.player.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistEpgUrlTest {

    @Test
    fun `m3u get php url derives xtream xmltv url when header epg is missing`() {
        val epgUrl = resolveM3uEpgUrl(
            epgUrl = "",
            playlistUrl = "https://provider.example/get.php?username=user1&password=pass1&type=m3u_plus&output=ts"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/xmltv.php?username=user1&password=pass1")
    }

    @Test
    fun `explicit relative m3u epg url resolves against playlist url`() {
        val epgUrl = resolveM3uEpgUrl(
            epgUrl = "epg/xmltv.xml.gz",
            playlistUrl = "https://provider.example/lists/playlist.m3u"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/lists/epg/xmltv.xml.gz")
    }

    @Test
    fun `stream live url derives xtream xmltv url`() {
        val epgUrl = buildXtreamEpgUrlFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/xmltv.php?username=user1&password=pass1")
    }

    @Test
    fun `m3u epg urls include header playlist and stream candidates`() {
        val epgUrls = resolveM3uEpgUrls(
            epgUrls = listOf("epg/header.xml.gz"),
            playlistUrl = "https://provider.example/get.php?username=user1&password=pass1&type=m3u_plus",
            streamUrls = listOf("https://backup.example/live/user2/pass2/12345.m3u8")
        )

        assertThat(epgUrls).containsExactly(
            "https://provider.example/epg/header.xml.gz",
            "https://provider.example/xmltv.php?username=user1&password=pass1",
            "https://backup.example/xmltv.php?username=user2&password=pass2"
        ).inOrder()
    }
}
