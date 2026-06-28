package com.imax.player.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.Locale

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
    if (!resolvedApkUrl.isSecureHttpsUrl()) return null
    val normalizedSha256 = sha256.normalizedSha256OrNull() ?: return null

    return AppUpdateInfo(
        versionCode = versionCode,
        versionName = versionName.ifBlank { versionCode.toString() },
        apkUrl = resolvedApkUrl,
        sha256 = normalizedSha256,
        isMandatory = mandatory || minSupportedVersionCode > currentVersionCode,
        releaseNotes = releaseNotes
    )
}

private fun String.isSecureHttpsUrl(): Boolean = runCatching {
    val uri = URI(trim())
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo.isNullOrBlank()
}.getOrDefault(false)

private fun String.normalizedSha256OrNull(): String? {
    val normalized = trim().replace(":", "").lowercase(Locale.ROOT)
    return normalized.takeIf { value ->
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

sealed interface AppUpdateCheckResult {
    data class Available(val update: AppUpdateInfo) : AppUpdateCheckResult
    data object NotAvailable : AppUpdateCheckResult
    data object Disabled : AppUpdateCheckResult
}
