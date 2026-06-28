package com.imax.player.data.parser

import com.google.common.truth.Truth.assertThat
import com.imax.player.core.network.XtreamApi
import com.imax.player.core.network.dto.XtreamSeriesInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class XtreamClientTest {
    private val api = mockk<XtreamApi>()
    private val client = XtreamClient(api)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `series episodes accept mixed provider value types`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "Season 2": [
                    {
                      "id": 12345,
                      "episode_num": "3",
                      "title": "Synthetic Episode",
                      "container_extension": "mkv",
                      "info": {
                        "duration_secs": "1800",
                        "rating": "8.4"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes).hasSize(1)
        assertThat(episodes.single().seasonNumber).isEqualTo(2)
        assertThat(episodes.single().episodeNumber).isEqualTo(3)
        assertThat(episodes.single().duration).isEqualTo(1800)
        assertThat(episodes.single().rating).isWithin(0.001).of(8.4)
        assertThat(episodes.single().streamUrl)
            .isEqualTo("https://provider.example/series/user/pass/12345.mkv")
    }

    @Test
    fun `series episodes survive malformed optional info`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "1": [
                    {
                      "id": "67890",
                      "episode_num": 1,
                      "title": "Synthetic Episode",
                      "container_extension": "mp4",
                      "info": []
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes).hasSize(1)
        assertThat(episodes.single().name).isEqualTo("Synthetic Episode")
        assertThat(episodes.single().streamUrl)
            .isEqualTo("https://provider.example/series/user/pass/67890.mp4")
    }

    @Test
    fun `series episodes prefer provider direct source and avoid duplicate extension`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "1": [
                    {
                      "id": "12345.mkv",
                      "episode_num": 1,
                      "container_extension": "mkv"
                    },
                    {
                      "id": "67890",
                      "episode_num": 2,
                      "container_extension": "mp4",
                      "direct_source": "https://cdn.example/synthetic/episode.mp4",
                      "info": ""
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes.map { it.streamUrl }).containsExactly(
            "https://provider.example/series/user/pass/12345.mkv",
            "https://cdn.example/synthetic/episode.mp4"
        ).inOrder()
    }

    @Test
    fun `series episodes accept alternate stream id and extension keys`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "3": [
                    {
                      "stream_id": "24680",
                      "episode_num": 4,
                      "title": "Synthetic Episode",
                      "extension": "ts"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes).hasSize(1)
        assertThat(episodes.single().streamUrl)
            .isEqualTo("https://provider.example/series/user/pass/24680.ts")
    }
}
