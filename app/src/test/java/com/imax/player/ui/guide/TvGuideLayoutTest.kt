package com.imax.player.ui.guide

import com.google.common.truth.Truth.assertThat
import com.imax.player.data.parser.EpgProgram
import org.junit.Test

class TvGuideLayoutTest {

    @Test
    fun `guide window starts one slot before rounded current time`() {
        val now = 10 * 60 * 60 * 1000L + 17 * 60 * 1000L

        assertThat(guideWindowStart(now)).isEqualTo(9 * 60 * 60 * 1000L + 30 * 60 * 1000L)
    }

    @Test
    fun `program layout clips items to visible window`() {
        val program = EpgProgram(
            channelId = "synthetic",
            title = "Program",
            startTime = 30 * 60_000L,
            endTime = 150 * 60_000L
        )

        val layout = guideProgramLayout(
            program = program,
            windowStart = 60 * 60_000L,
            windowEnd = 120 * 60_000L
        )

        assertThat(layout?.offsetMinutes).isEqualTo(0f)
        assertThat(layout?.durationMinutes).isEqualTo(60f)
    }

    @Test
    fun `program outside window has no layout`() {
        val program = EpgProgram("synthetic", "Program", startTime = 0L, endTime = 1_000L)

        assertThat(guideProgramLayout(program, 2_000L, 3_000L)).isNull()
    }
}
