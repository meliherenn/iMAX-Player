package com.imax.player.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateModelsTest {

    @Test
    fun `returns no update when manifest version is not newer`() {
        val manifest = AppUpdateManifest(
            versionCode = 3,
            versionName = "1.0.3",
            apkUrl = "https://updates.example.com/app.apk"
        )

        val update = manifest.toAvailableUpdate(
            currentVersionCode = 3,
            resolvedApkUrl = manifest.apkUrl
        )

        assertThat(update).isNull()
    }

    @Test
    fun `marks update mandatory when min supported version is above current version`() {
        val manifest = AppUpdateManifest(
            versionCode = 5,
            versionName = "1.0.5",
            apkUrl = "https://updates.example.com/app.apk",
            minSupportedVersionCode = 4
        )

        val update = manifest.toAvailableUpdate(
            currentVersionCode = 3,
            resolvedApkUrl = manifest.apkUrl
        )

        assertThat(update?.isMandatory).isTrue()
    }
}
