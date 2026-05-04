package com.imax.player.ui.live

import com.google.common.truth.Truth.assertThat
import com.imax.player.core.model.Channel
import org.junit.Test

class ChannelPriorityTest {

    @Test
    fun `prioritizeGroupsForMobile only promotes explicit Turkish groups`() {
        val groups = listOf("Astro", "TR Haber", "Travel", "Türkiye Spor", "Country", "News")

        val result = prioritizeGroupsForMobile(groups)

        assertThat(result).containsExactly(
            "TR Haber",
            "Türkiye Spor",
            "Astro",
            "Travel",
            "Country",
            "News"
        ).inOrder()
    }

    @Test
    fun `rankChannelsForMobile does not promote incidental tr text`() {
        val travel = channel(id = 1, name = "Travel Channel", group = "Travel")
        val turkish = channel(id = 2, name = "TR Haber", group = "News")
        val country = channel(id = 3, name = "Country Music", group = "Country")
        val trt = channel(id = 4, name = "TRT Haber", group = "News")

        val result = rankChannelsForMobile(listOf(travel, turkish, country, trt))

        assertThat(result).containsExactly(turkish, trt, travel, country).inOrder()
    }

    private fun channel(id: Long, name: String, group: String): Channel {
        return Channel(
            id = id,
            playlistId = 1L,
            name = name,
            groupTitle = group,
            streamUrl = "http://stream.example.com/$id"
        )
    }
}
