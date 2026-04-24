package com.imax.player.core.common

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Builds an optimized Coil [ImageLoader] with:
 * - 100 MB disk cache
 * - 64 MB memory cache
 * - crossfade(300ms)
 * - Respectful cache policy
 */
fun buildImaxImageLoader(context: Context, okHttpClient: OkHttpClient): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25) // 25% of available memory
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
        }
        .okHttpClient {
            okHttpClient.newBuilder()
                .apply {
                    interceptors().removeAll { it is HttpLoggingInterceptor }
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(300)
        .respectCacheHeaders(false) // Many IPTV image servers send no-cache headers
        .build()
}
