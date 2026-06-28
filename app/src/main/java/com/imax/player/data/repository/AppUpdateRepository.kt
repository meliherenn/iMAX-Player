package com.imax.player.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        if (!BuildConfig.SELF_HOSTED_UPDATES_ENABLED) {
            return@withContext AppUpdateCheckResult.Disabled
        }
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) return@withContext AppUpdateCheckResult.Disabled
        val parsedManifestUrl = manifestUrl.toHttpUrlOrNull()
            ?.takeIf { it.isHttps }
            ?: throw IllegalStateException("Update manifest URL must use HTTPS")

        val request = Request.Builder()
            .url(parsedManifestUrl)
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
                resolvedApkUrl = resolveApkUrl(parsedManifestUrl.toString(), manifest.apkUrl)
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

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("APK download failed: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("APK download returned an empty response")
                val totalBytes = body.contentLength()
                if (totalBytes > MAX_APK_BYTES) {
                    throw IllegalStateException("APK download is larger than the allowed limit")
                }
                var readBytes = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            readBytes += read
                            if (readBytes > MAX_APK_BYTES) {
                                throw IllegalStateException("APK download is larger than the allowed limit")
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            if (totalBytes > 0) {
                                onProgress(((readBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        }

        val actualSha256 = digest.digest().toHex()
        val expectedSha256 = update.sha256.normalizedSha256()
        if (expectedSha256.length != 64 || actualSha256 != expectedSha256) {
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
        return context.packageManager.canRequestPackageInstalls()
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
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolveApkUrl(manifestUrl: String, apkUrl: String): String {
        if (apkUrl.isBlank()) return ""
        return runCatching {
            manifestUrl.toHttpUrlOrNull()?.resolve(apkUrl)?.toString()
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
        const val MAX_APK_BYTES = 500L * 1024L * 1024L
    }
}
