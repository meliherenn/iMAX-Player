package com.imax.player.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackDiagnosticsReportTest {

    @Test
    fun `report contains useful state without request secrets`() {
        val report = buildPlaybackDiagnosticsReport(
            appVersion = "1.0.15",
            device = "Synthetic TV",
            androidSdk = 35,
            contentType = "LIVE",
            diagnostics = PlaybackDiagnostics(
                engineName = "VLC",
                streamProtocol = "https",
                streamHost = "0123456789ab",
                requestHeaderNames = setOf("User-Agent", "Referer"),
                recovery = PlaybackRecoveryState(
                    automaticFallbackUsed = true,
                    lastErrorCategory = "decoder"
                )
            ),
            state = PlayerState(
                playbackState = PlaybackState.PLAYING,
                currentPosition = 1_000L,
                bufferedPosition = 5_000L,
                isPlaybackConfirmed = true
            )
        )

        assertThat(report).contains("engine=VLC")
        assertThat(report).contains("bufferAheadMs=4000")
        assertThat(report).contains("requestHeaders=Referer,User-Agent")
        assertThat(report).doesNotContain("password")
        assertThat(report).doesNotContain("https://")
    }
}
