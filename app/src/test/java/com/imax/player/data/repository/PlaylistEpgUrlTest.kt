package com.imax.player.data.repository

import com.google.common.truth.Truth.assertThat
import com.imax.player.core.model.Channel
import com.imax.player.core.model.Playlist
import com.imax.player.core.model.PlaylistType
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
    fun `stream headers do not block xtream xmltv discovery`() {
        val epgUrl = buildXtreamEpgUrlFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts|User-Agent=Example%20TV"
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

    @Test
    fun `m3u get php url derives xtream credentials`() {
        val credentials = buildXtreamCredentialsFromM3uPlaylistUrl(
            "https://provider.example/get.php?username=user1&password=pass1&type=m3u_plus&output=ts"
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `stream live url derives xtream credentials`() {
        val credentials = buildXtreamCredentialsFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts"
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `stream headers do not block xtream credential discovery`() {
        val credentials = buildXtreamCredentialsFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts|Referer=https%3A%2F%2Fportal.example%2F"
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `xtream playlist keeps explicit credentials`() {
        val credentials = resolveXtreamCredentials(
            playlist = Playlist(
                id = 1L,
                name = "Test",
                type = PlaylistType.XTREAM_CODES,
                serverUrl = "https://provider.example/player_api.php",
                username = "user1",
                password = "pass1"
            ),
            streamUrls = emptyList()
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `turkish public epg urls include requested fallback sources`() {
        assertThat(TURKISH_PUBLIC_EPG_URLS).containsExactly(
            "https://www.open-epg.com/files/turkey1.xml",
            "https://www.open-epg.com/files/turkey2.xml",
            "https://www.open-epg.com/files/turkey3.xml",
            "https://www.open-epg.com/files/turkey4.xml",
            "https://www.open-epg.com/files/turkey5.xml",
            "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz",
            "https://epgshare01.online/epgshare01/epg_ripper_TR3.xml.gz"
        ).inOrder()
    }

    @Test
    fun `turkish public epg fallback is enabled for tr channel lists`() {
        val channels = listOf(
            Channel(
                playlistId = 1L,
                name = "TR • TRT 1 FHD",
                groupTitle = "TR Ulusal",
                streamUrl = "https://example.test/1"
            ),
            Channel(
                playlistId = 1L,
                name = "TR • Show HD",
                groupTitle = "TR Ulusal",
                streamUrl = "https://example.test/2"
            )
        )

        assertThat(shouldUseTurkishPublicEpgFallback(channels)).isTrue()
    }

    @Test
    fun `turkish public epg fallback is not enabled for unrelated channel lists`() {
        val channels = listOf(
            Channel(
                playlistId = 1L,
                name = "BBC News",
                groupTitle = "UK News",
                streamUrl = "https://example.test/1"
            ),
            Channel(
                playlistId = 1L,
                name = "CNN International",
                groupTitle = "News",
                streamUrl = "https://example.test/2"
            )
        )

        assertThat(shouldUseTurkishPublicEpgFallback(channels)).isFalse()
    }
}
