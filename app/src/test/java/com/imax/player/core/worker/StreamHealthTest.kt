package com.imax.player.core.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the conservative health policy (K-3): a working stream must never be classified
 * as DEAD just because a probe returned an ambiguous or transient status.
 */
class StreamHealthTest {

    @Test
    fun `2xx and 3xx are alive`() {
        listOf(200, 204, 206, 301, 302, 307, 399).forEach { code ->
            assertThat(classifyStreamHealth(code)).isEqualTo(StreamHealth.ALIVE)
        }
    }

    @Test
    fun `auth method and rate-limit responses prove the stream exists`() {
        listOf(401, 403, 405, 429).forEach { code ->
            assertThat(classifyStreamHealth(code)).isEqualTo(StreamHealth.ALIVE)
        }
    }

    @Test
    fun `only not-found and gone are dead`() {
        assertThat(classifyStreamHealth(404)).isEqualTo(StreamHealth.DEAD)
        assertThat(classifyStreamHealth(410)).isEqualTo(StreamHealth.DEAD)
    }

    @Test
    fun `server errors and other 4xx are unknown and do not flip state`() {
        listOf(400, 408, 409, 451, 500, 502, 503, 504).forEach { code ->
            assertThat(classifyStreamHealth(code)).isEqualTo(StreamHealth.UNKNOWN)
        }
    }
}
