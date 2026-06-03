package com.imax.player.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.imax.player.BuildConfig
import com.imax.player.core.update.AppUpdateCheckResult
import com.imax.player.core.update.AppUpdateInfo
import com.imax.player.core.update.AppUpdateManifest
import com.imax.player.core.update.toAvailableUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) return@withContext AppUpdateCheckResult.Disabled

        val request = Request.Builder()
            .url(manifestUrl)
            .header("Cache-Control", "no-cache")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Update check failed: HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val manifest = json.decodeFromString<AppUpdateManifest>(body)
            val update = manifest.toAvailableUpdate(
                currentVersionCode = BuildConfig.VERSION_CODE,
                resolvedApkUrl = resolveApkUrl(manifestUrl, manifest.apkUrl)
            )

            if (update == null) AppUpdateCheckResult.NotAvailable else AppUpdateCheckResult.Available(update)
        }
    }

    suspend fun downloadApk(
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(update.apkUrl).build()
        val outputDir = File(context.cacheDir, "shared/updates").apply { mkdirs() }
        val outputFile = File(outputDir, "imax-player-${update.versionCode}.apk")
        val tempFile = File(outputDir, "${outputFile.name}.download")
        val digest = MessageDigest.getInstance("SHA-256")

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("APK download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("APK download returned an empty response")
            val totalBytes = body.contentLength()
            var readBytes = 0L

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        readBytes += read
                        if (totalBytes > 0) {
                            onProgress(((readBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }

        val actualSha256 = digest.digest().toHex()
        val expectedSha256 = update.sha256.normalizedSha256()
        if (expectedSha256.isNotBlank() && actualSha256 != expectedSha256) {
            tempFile.delete()
            throw IllegalStateException("Downloaded APK checksum did not match")
        }

        if (outputFile.exists()) outputFile.delete()
        if (!tempFile.renameTo(outputFile)) {
            throw IllegalStateException("Downloaded APK could not be saved")
        }

        onProgress(100)
        outputFile
    }

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun createUnknownAppSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun resolveApkUrl(manifestUrl: String, apkUrl: String): String {
        if (apkUrl.isBlank()) return ""
        return runCatching {
            manifestUrl.toHttpUrl().resolve(apkUrl)?.toString()
        }.getOrNull() ?: apkUrl
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun String.normalizedSha256(): String =
        trim()
            .replace(":", "")
            .lowercase(Locale.ROOT)

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
