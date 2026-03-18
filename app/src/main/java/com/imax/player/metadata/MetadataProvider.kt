package com.imax.player.metadata

import com.imax.player.BuildConfig
import com.imax.player.core.common.Constants
import com.imax.player.core.common.StringUtils
import com.imax.player.core.database.MetadataCacheDao
import com.imax.player.core.database.MetadataCacheEntity
import com.imax.player.core.model.ContentType
import com.imax.player.core.network.TmdbApi
import com.imax.player.core.network.dto.TmdbSearchResult
import com.imax.player.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class MetadataResult(
    val tmdbId: Int = 0,
    val imdbId: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val overview: String = "",
    val tagline: String = "",
    val genre: String = "",
    val cast: String = "",
    val director: String = "",
    val runtime: Int = 0,
    val rating: Double = 0.0,
    val year: Int = 0,
    val trailerUrl: String = "",
    val confidence: Double = 0.0
)

@Singleton
class MetadataProvider @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val cacheDao: MetadataCacheDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val apiKey: String = BuildConfig.TMDB_API_KEY

    // Minimum confidence to accept a TMDB match
    companion object {
        private const val MIN_CONFIDENCE_SCORE = 35.0
        private const val SHORT_TITLE_THRESHOLD = 3
        private const val SHORT_TITLE_MIN_CONFIDENCE = 55.0
        private const val CACHE_TTL_MS = 7 * 24 * 3600 * 1000L
    }

    suspend fun getCachedMetadata(
        title: String,
        year: Int = 0,
        contentType: ContentType = ContentType.MOVIE
    ): MetadataResult? {
        if (contentType == ContentType.LIVE) return null

        val normalizedTitle = StringUtils.normalizeTitle(title)
        val languageTag = currentLanguageTag()
        val cached = cacheDao.find(normalizedTitle, year, languageTag) ?: return null

        return if (System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MS) {
            cached.toResult()
        } else {
            null
        }
    }

    suspend fun fetchMetadata(
        title: String,
        year: Int = 0,
        contentType: ContentType = ContentType.MOVIE
    ): MetadataResult? {
        if (apiKey.isBlank()) {
            Timber.w("TMDB API key not configured")
            return null
        }

        try {
            val cleanTitle = StringUtils.cleanTitleForSearch(title)
            val normalizedTitle = StringUtils.normalizeTitle(title)
            
            // Extract year from title if not provided
            val (_, titleYear) = StringUtils.extractYearFromTitle(title)
            val effectiveYear = if (year > 0) year else (titleYear ?: 0)

            val languageTag = currentLanguageTag()

            // Check locale-aware cache
            val cached = cacheDao.find(normalizedTitle, effectiveYear, languageTag)
            if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MS) {
                Timber.d("Cache hit for '$normalizedTitle' (lang=$languageTag)")
                return cached.toResult()
            }

            val searchResponse = when (contentType) {
                ContentType.MOVIE -> tmdbApi.searchMovie(apiKey, cleanTitle, if (effectiveYear > 0) effectiveYear else null, 1, languageTag)
                ContentType.SERIES -> tmdbApi.searchTv(apiKey, cleanTitle, if (effectiveYear > 0) effectiveYear else null, 1, languageTag)
                ContentType.LIVE -> return null
            }

            if (searchResponse.results.isEmpty()) {
                Timber.d("No TMDB results for '$cleanTitle'")
                return null
            }

            // Smart scoring to establish best match with confidence
            val scoredResults = searchResponse.results.map { result ->
                val score = calculateMatchScore(cleanTitle, title, effectiveYear, result, contentType)
                Pair(result, score)
            }.sortedByDescending { it.second }

            val bestPair = scoredResults.firstOrNull() ?: return null
            val bestMatch = bestPair.first
            val bestScore = bestPair.second

            // Confidence threshold — NEVER accept low-confidence matches
            val isShortTitle = cleanTitle.trim().length <= SHORT_TITLE_THRESHOLD
            val requiredConfidence = if (isShortTitle) SHORT_TITLE_MIN_CONFIDENCE else MIN_CONFIDENCE_SCORE

            if (bestScore < requiredConfidence) {
                Timber.w("Best match for '$cleanTitle' scored $bestScore (threshold=$requiredConfidence) — rejecting to prevent wrong metadata")
                return null
            }

            val bestTitleSim = getBestTitleSimilarity(cleanTitle, title, bestMatch)
            if (bestTitleSim < 0.45) {
                Timber.w("Title similarity too low ($bestTitleSim) for '$cleanTitle' vs '${bestMatch.title ?: bestMatch.name}' — rejecting")
                return null
            }

            Timber.d("TMDB match: '${bestMatch.title ?: bestMatch.name}' score=$bestScore sim=$bestTitleSim for query='$cleanTitle'")

            // Fetch full details
            val detail = when (contentType) {
                ContentType.MOVIE -> tmdbApi.getMovieDetails(bestMatch.id, apiKey, language = languageTag)
                ContentType.SERIES -> tmdbApi.getTvDetails(bestMatch.id, apiKey, language = languageTag)
                ContentType.LIVE -> return null
            }

            // ─── Overview resolution with locale-aware fallback ───
            var finalOverview = detail.overview
            var finalTagline = detail.tagline ?: ""

            if (finalOverview.isBlank()) {
                val translations = detail.translations?.translations ?: emptyList()
                val resolved = resolveTranslation(translations, languageTag, detail.originalLanguage)
                if (resolved != null) {
                    if (resolved.overview.isNotBlank()) finalOverview = resolved.overview
                    if (finalTagline.isBlank() && resolved.tagline.isNotBlank()) finalTagline = resolved.tagline
                }
            }

            // ─── Director / Creator resolution ───
            val director = detail.credits?.crew?.find { it.job == "Director" }?.name
                ?: detail.createdBy?.firstOrNull()?.name
                ?: ""

            // ─── Runtime resolution (movie vs TV) ───
            val runtime = detail.runtime
                ?: detail.episodeRunTime?.firstOrNull()
                ?: 0

            // ─── Trailer URL ───
            val trailerUrl = detail.videos?.results
                ?.filter { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
                ?.maxByOrNull { if (it.official) 1 else 0 }
                ?.let { "https://www.youtube.com/watch?v=${it.key}" }
                ?: detail.videos?.results
                    ?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }
                    ?.let { "https://www.youtube.com/watch?v=${it.key}" }
                ?: ""

            val result = MetadataResult(
                tmdbId = detail.id,
                imdbId = detail.imdbId ?: detail.externalIds?.imdbId ?: "",
                posterUrl = detail.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                    ?: bestMatch.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                    ?: "",
                backdropUrl = detail.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                    ?: bestMatch.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                    ?: "",
                overview = finalOverview,
                tagline = finalTagline,
                genre = detail.genres.joinToString(", ") { it.name },
                cast = detail.credits?.cast?.take(15)?.joinToString(", ") { it.name } ?: "",
                director = director,
                runtime = runtime,
                rating = if (detail.voteAverage > 0) detail.voteAverage else bestMatch.voteAverage,
                year = StringUtils.extractYear(detail.releaseDate ?: detail.firstAirDate)
                    ?: StringUtils.extractYear(bestMatch.releaseDate ?: bestMatch.firstAirDate)
                    ?: effectiveYear,
                trailerUrl = trailerUrl,
                confidence = bestScore
            )

            // Cache with locale awareness
            cacheDao.insert(
                MetadataCacheEntity(
                    title = normalizedTitle,
                    year = result.year,
                    language = languageTag,
                    tmdbId = result.tmdbId,
                    imdbId = result.imdbId,
                    posterUrl = result.posterUrl,
                    backdropUrl = result.backdropUrl,
                    overview = result.overview,
                    tagline = result.tagline,
                    genre = result.genre,
                    cast = result.cast,
                    director = result.director,
                    runtime = result.runtime,
                    rating = result.rating,
                    trailerUrl = result.trailerUrl,
                    contentType = contentType.name
                )
            )

            return result
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch metadata for: $title")
            return null
        }
    }

    private suspend fun currentLanguageTag(): String {
        val appLanguage = settingsDataStore.settings.first().appLanguage
        return when (appLanguage.lowercase()) {
            "tr" -> "tr-TR"
            "en" -> "en-US"
            else -> Locale.getDefault().toLanguageTag()
        }
    }

    /**
     * Calculate a multi-signal match score for a TMDB search result.
     * Returns a score from 0-100 where higher is better.
     */
    private fun calculateMatchScore(
        cleanTitle: String,
        originalTitle: String,
        year: Int,
        result: TmdbSearchResult,
        expectedType: ContentType
    ): Double {
        var score = 0.0
        val resultTitle = result.title ?: result.name ?: ""
        val resultOriginalTitle = result.originalTitle ?: result.originalName ?: ""
        val resultYear = StringUtils.extractYear(result.releaseDate ?: result.firstAirDate)

        // ─── 1. Title similarity (max 50 points) ───
        val bestTitleSim = getBestTitleSimilarity(cleanTitle, originalTitle, result)
        score += bestTitleSim * 50

        // ─── 2. Year match (max 25 points, min -15) ───
        if (year > 0 && resultYear != null) {
            val yearDiff = kotlin.math.abs(year - resultYear)
            score += when {
                yearDiff == 0 -> 25.0
                yearDiff == 1 -> 15.0
                yearDiff == 2 -> 5.0
                yearDiff <= 5 -> -5.0
                else -> -15.0
            }
        } else if (year > 0 && resultYear == null) {
            score -= 3.0 // Small penalty if we have year but result doesn't
        }

        // ─── 3. Popularity as minor tiebreaker (max 5 points) ───
        // Use log scale to prevent runaway scores
        val popBonus = minOf(5.0, kotlin.math.ln(1.0 + (result.popularity / 10.0)))
        score += popBonus

        // ─── 4. Original language compatibility (max 5 points, min -10) ───
        val resultLang = result.originalLanguage ?: ""
        if (resultLang.isNotBlank()) {
            // Penalty for unexpected languages when title is clearly Latin-script
            val titleIsLatin = cleanTitle.all { it.isLetterOrDigit() || it.isWhitespace() || it in "-':!,." }
            val langIsUnexpected = resultLang in listOf("ko", "ja", "zh", "ar", "hi", "th")
            if (titleIsLatin && langIsUnexpected && bestTitleSim < 0.90) {
                score -= 10.0
            }
            // Small bonus for Turkish or English content when matching Latin titles
            if (resultLang in listOf("tr", "en", "de", "fr", "es", "it", "pt", "hr", "nl", "sv", "no", "da")) {
                score += 3.0
            }
        }

        // ─── 5. Vote average signal (max 3 points) ───
        if (result.voteAverage > 0) {
            score += minOf(3.0, result.voteAverage * 0.3)
        }

        // ─── 6. Short title extra caution ───
        val normalizedClean = StringUtils.normalizeTitle(cleanTitle)
        if (normalizedClean.length <= SHORT_TITLE_THRESHOLD) {
            // For very short titles, require near-exact match
            val normalizedResult = StringUtils.normalizeTitle(resultTitle)
            val normalizedOriginal = StringUtils.normalizeTitle(resultOriginalTitle)
            if (normalizedClean != normalizedResult && normalizedClean != normalizedOriginal) {
                score -= 15.0 // Heavy penalty for non-exact short title matches
            }
        }

        return score
    }

    /**
     * Get the best title similarity across all available title variants.
     */
    private fun getBestTitleSimilarity(
        cleanTitle: String,
        originalTitle: String,
        result: TmdbSearchResult
    ): Double {
        val resultTitle = result.title ?: result.name ?: ""
        val resultOriginalTitle = result.originalTitle ?: result.originalName ?: ""

        val similarities = listOf(
            StringUtils.fuzzyMatchScore(cleanTitle, resultTitle),
            StringUtils.fuzzyMatchScore(cleanTitle, resultOriginalTitle),
            StringUtils.fuzzyMatchScore(originalTitle, resultTitle),
            StringUtils.fuzzyMatchScore(originalTitle, resultOriginalTitle)
        )

        return similarities.max()
    }

    /**
     * Resolve the best translation following priority:
     * 1. Requested locale (e.g. Turkish)
     * 2. English
     * 3. Original language of the content
     * 4. Any available non-blank translation
     */
    private fun resolveTranslation(
        translations: List<com.imax.player.core.network.dto.TmdbTranslation>,
        requestedLanguageTag: String,
        originalLanguage: String?
    ): com.imax.player.core.network.dto.TmdbTranslationData? {
        if (translations.isEmpty()) return null

        val requestedLang = requestedLanguageTag.split("-").firstOrNull()?.lowercase() ?: ""

        // 1. Try requested language (e.g. "tr")
        val requested = translations.find { 
            it.language.lowercase() == requestedLang && !it.data?.overview.isNullOrBlank() 
        }?.data
        if (requested != null) return requested

        // 2. Try English
        val english = translations.find { 
            it.language.lowercase() == "en" && !it.data?.overview.isNullOrBlank() 
        }?.data
        if (english != null) return english

        // 3. Try original language of the content
        if (!originalLanguage.isNullOrBlank()) {
            val original = translations.find { 
                it.language.lowercase() == originalLanguage.lowercase() && !it.data?.overview.isNullOrBlank() 
            }?.data
            if (original != null) return original
        }

        // 4. Any available non-blank translation (last resort)
        return translations.find { !it.data?.overview.isNullOrBlank() }?.data
    }

    private fun MetadataCacheEntity.toResult() = MetadataResult(
        tmdbId = tmdbId,
        imdbId = imdbId,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        overview = overview,
        tagline = tagline,
        genre = genre,
        cast = cast,
        director = director,
        runtime = runtime,
        rating = rating,
        year = year,
        trailerUrl = trailerUrl,
        confidence = 100.0 // Cached results were already validated
    )
}
