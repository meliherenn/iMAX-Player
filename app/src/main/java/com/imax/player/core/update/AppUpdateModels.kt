package com.imax.player.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateManifest(
    @SerialName("versionCode") val versionCode: Int = 0,
    @SerialName("versionName") val versionName: String = "",
    @SerialName("apkUrl") val apkUrl: String = "",
    @SerialName("sha256") val sha256: String = "",
    @SerialName("mandatory") val mandatory: Boolean = false,
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Int = 0,
    @SerialName("releaseNotes") val releaseNotes: String = ""
)

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val isMandatory: Boolean,
    val releaseNotes: String
)

fun AppUpdateManifest.toAvailableUpdate(
    currentVersionCode: Int,
    resolvedApkUrl: String
): AppUpdateInfo? {
    if (versionCode <= currentVersionCode) return null
    if (resolvedApkUrl.isBlank()) return null

    return AppUpdateInfo(
        versionCode = versionCode,
        versionName = versionName.ifBlank { versionCode.toString() },
        apkUrl = resolvedApkUrl,
        sha256 = sha256,
        isMandatory = mandatory || minSupportedVersionCode > currentVersionCode,
        releaseNotes = releaseNotes
    )
}

sealed interface AppUpdateCheckResult {
    data class Available(val update: AppUpdateInfo) : AppUpdateCheckResult
    data object NotAvailable : AppUpdateCheckResult
    data object Disabled : AppUpdateCheckResult
}
