package com.imax.player.data.repository

import com.google.common.truth.Truth.assertThat
import com.imax.player.core.connected.CONNECTED_IMAX_PAYLOAD_SOURCE
import com.imax.player.core.connected.CONNECTED_IMAX_PAYLOAD_VERSION
import com.imax.player.core.connected.CONNECTED_SETUP_MAX_PLAYLIST_BYTES
import com.imax.player.core.connected.ConnectedPlaylistInfo
import com.imax.player.core.connected.ConnectedSetupPayload
import com.imax.player.core.model.PlaylistType
import org.junit.Test

class ConnectedSetupRepositoryTest {

    @Test
    fun `valid m3u payload maps to playlist draft and preserves settings`() {
        val draft = validateConnectedSetupPayload(
            expectedPairingCode = "A7K9Q2BC",
            payload = ConnectedSetupPayload(
                version = CONNECTED_IMAX_PAYLOAD_VERSION,
                source = CONNECTED_IMAX_PAYLOAD_SOURCE,
                pairingCode = "a7k9q2bc",
                playlist = ConnectedPlaylistInfo(
                    name = "  Ev Listem  ",
                    type = "m3u",
                    url = "https://provider.example/list.m3u",
                    epgUrl = "https://provider.example/epg.xml",
                    rememberOnStart = true,
                    epgAutoSync = false
                )
            )
        )

        assertThat(draft.playlist.name).isEqualTo("Ev Listem")
        assertThat(draft.playlist.type).isEqualTo(PlaylistType.M3U_URL)
        assertThat(draft.playlist.url).isEqualTo("https://provider.example/list.m3u")
        assertThat(draft.playlist.epgUrl).isEqualTo("https://provider.example/epg.xml")
        assertThat(draft.rememberOnStart).isTrue()
        assertThat(draft.epgAutoSync).isFalse()
    }

    @Test
    fun `xtream payload keeps password but trims username and server`() {
        val draft = validateConnectedSetupPayload(
            expectedPairingCode = "A7K9Q2BC",
            payload = ConnectedSetupPayload(
                version = CONNECTED_IMAX_PAYLOAD_VERSION,
                source = CONNECTED_IMAX_PAYLOAD_SOURCE,
                pairingCode = "A7K9Q2BC",
                playlist = ConnectedPlaylistInfo(
                    name = "Portal",
                    type = "xtream",
                    serverUrl = " https://portal.example.com/player_api.php ",
                    username = " user1 ",
                    password = " pass with spaces "
                )
            )
        )

        assertThat(draft.playlist.type).isEqualTo(PlaylistType.XTREAM_CODES)
        assertThat(draft.playlist.serverUrl).isEqualTo("https://portal.example.com/player_api.php")
        assertThat(draft.playlist.username).isEqualTo("user1")
        assertThat(draft.playlist.password).isEqualTo(" pass with spaces ")
    }

    @Test
    fun `mismatched pairing code is rejected`() {
        val error = kotlin.runCatching {
            validateConnectedSetupPayload(
                expectedPairingCode = "A7K9Q2BC",
                payload = validM3uPayload(pairingCode = "B7K9Q2BC")
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("Eşleştirme kodu")
    }

    @Test
    fun `unknown playlist type is rejected instead of falling back to url`() {
        val error = kotlin.runCatching {
            validateConnectedSetupPayload(
                expectedPairingCode = "A7K9Q2BC",
                payload = validM3uPayload(type = "unknown")
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("Desteklenmeyen playlist tipi")
    }

    @Test
    fun `file payload larger than limit is rejected before save`() {
        val error = kotlin.runCatching {
            validateConnectedSetupPayload(
                expectedPairingCode = "A7K9Q2BC",
                payload = ConnectedSetupPayload(
                    version = CONNECTED_IMAX_PAYLOAD_VERSION,
                    source = CONNECTED_IMAX_PAYLOAD_SOURCE,
                    pairingCode = "A7K9Q2BC",
                    playlist = ConnectedPlaylistInfo(
                        name = "Dosya",
                        type = "file",
                        fileName = "list.m3u",
                        fileSize = CONNECTED_SETUP_MAX_PLAYLIST_BYTES + 1,
                        fileContent = "#EXTM3U\n"
                    )
                )
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("5 MB")
    }

    @Test
    fun `pairing url keeps https bases and encodes query parameters`() {
        val url = buildConnectedPairingUrl(
            webBaseUrl = "https://setup.example.com/",
            apiBaseUrl = "https://setup-api.example.com/v1/",
            pairingCode = "a7k9q2bc"
        )

        assertThat(url).isEqualTo(
            "https://setup.example.com/?code=A7K9Q2BC&api=https%3A%2F%2Fsetup-api.example.com%2Fv1"
        )
    }

    @Test
    fun `non https connected base url is rejected`() {
        val error = kotlin.runCatching {
            buildConnectedPairingUrl(
                webBaseUrl = "http://setup.example.com",
                apiBaseUrl = "https://setup-api.example.com",
                pairingCode = "A7K9Q2BC"
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("HTTPS")
    }

    private fun validM3uPayload(
        pairingCode: String = "A7K9Q2BC",
        type: String = "m3u"
    ): ConnectedSetupPayload = ConnectedSetupPayload(
        version = CONNECTED_IMAX_PAYLOAD_VERSION,
        source = CONNECTED_IMAX_PAYLOAD_SOURCE,
        pairingCode = pairingCode,
        playlist = ConnectedPlaylistInfo(
            name = "Liste",
            type = type,
            url = "https://provider.example/list.m3u"
        )
    )
}
