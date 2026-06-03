package com.imax.player.data.parser

import com.google.common.truth.Truth.assertThat
import com.imax.player.core.model.Channel
import org.junit.Test

class EpgChannelMatchingTest {

    @Test
    fun `lookup keys include epg id name and stream id variants`() {
        val channel = Channel(
            playlistId = 1L,
            streamId = 101,
            name = "TRT 1 HD",
            streamUrl = "http://example.test/live/101",
            epgChannelId = "trt.1"
        )

        val keys = epgLookupKeysForChannel(channel)

        assertThat(keys).containsAtLeast(
            "trt.1",
            "trt-1",
            "trt1",
            "TRT 1 HD",
            "trt-1-hd",
            "trt1hd",
            "101"
        )
    }

    @Test
    fun `channel id map points normalized aliases to canonical epg id`() {
        val channel = Channel(
            playlistId = 1L,
            streamId = 101,
            name = "TRT 1 HD",
            streamUrl = "http://example.test/live/101",
            epgChannelId = "trt.1"
        )

        val map = buildEpgChannelIdMap(listOf(channel))

        assertThat(map["trt-1"]).isEqualTo("trt.1")
        assertThat(map["trt1hd"]).isEqualTo("trt.1")
        assertThat(map["101"]).isEqualTo("trt.1")
    }

    @Test
    fun `resolve channel id can match XMLTV display name`() {
        val resolved = resolveEpgChannelId(
            xmlChannelId = "xml-random-id",
            displayNames = listOf("TRT 1 HD"),
            channelIdMap = mapOf("trt-1-hd" to "trt.1")
        )

        assertThat(resolved).isEqualTo("trt.1")
    }
}
