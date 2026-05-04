package com.imax.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class CategoryOrderingTest {

    @Test
    fun `orderCategoryNames sorts names naturally`() {
        val categories = listOf(
            "Sports 10",
            "Movies",
            "sports 2",
            "News",
            "Sports 1"
        )

        val result = orderCategoryNames(categories, Locale.US)

        assertThat(result).containsExactly(
            "Movies",
            "News",
            "Sports 1",
            "sports 2",
            "Sports 10"
        ).inOrder()
    }

    @Test
    fun `orderCategoryNames removes blank and exact duplicate names`() {
        val result = orderCategoryNames(
            listOf("News", " ", "Movies", "News", ""),
            Locale.US
        )

        assertThat(result).containsExactly("Movies", "News").inOrder()
    }
}
