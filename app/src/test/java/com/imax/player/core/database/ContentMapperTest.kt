package com.imax.player.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentMapperTest {

    @Test
    fun `movie mapper preserves latest ordering fields`() {
        val entity = MovieEntity(
            playlistId = 1L,
            name = "Synthetic Movie",
            streamUrl = "http://stream.example.com/movie/synthetic.mkv",
            addedAt = 1234L,
            sourceOrder = 7
        )

        val model = entity.toModel()
        val restored = model.toEntity()

        assertThat(model.addedAt).isEqualTo(1234L)
        assertThat(model.sourceOrder).isEqualTo(7)
        assertThat(restored.addedAt).isEqualTo(1234L)
        assertThat(restored.sourceOrder).isEqualTo(7)
    }

    @Test
    fun `series mapper preserves latest ordering fields`() {
        val entity = SeriesEntity(
            playlistId = 1L,
            name = "Synthetic Series",
            addedAt = 5678L,
            sourceOrder = 3
        )

        val model = entity.toModel()
        val restored = model.toEntity()

        assertThat(model.addedAt).isEqualTo(5678L)
        assertThat(model.sourceOrder).isEqualTo(3)
        assertThat(restored.addedAt).isEqualTo(5678L)
        assertThat(restored.sourceOrder).isEqualTo(3)
    }
}
