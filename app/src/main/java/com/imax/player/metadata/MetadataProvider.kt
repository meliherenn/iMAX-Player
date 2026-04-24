package com.imax.player.metadata

import com.imax.player.BuildConfig
import com.imax.player.core.common.Constants
import com.imax.player.core.common.StringUtils
import com.imax.player.core.database.MetadataCacheDao
import com.imax.player.core.database.MetadataCacheEntity
import com.imax.player.core.model.ContentType
import com.imax.player.core.network.TmdbApi
import com.imax.player.core.network.dto.TmdbDetailResponse
import com.imax.player.core.network.dto.TmdbSearchResult
import com.imax.player.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
        private const val TURKISH_METADATA_LANGUAGE = "tr-TR"
        private const val ENGLISH_FALLBACK_LANGUAGE = "en-US"
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

        return if (isFreshUsableCache(cached)) {
            cached.toResult()
        } else {
            null
        }
    }

    suspend fun fetchMetadata(
        title: String,
        year: Int = 0,
        contentType: ContentType = ContentType.MOVIE,
        tmdbId: Int = 0
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

            // Check locale-aware cache. Skip partial caches so blank detail pages can be repaired.
            val cached = if (tmdbId > 0) {
                cacheDao.findByTmdbId(tmdbId, languageTag)
            } else {
                cacheDao.find(normalizedTitle, effectiveYear, languageTag)
            }
            if (cached != null && isFreshUsableCache(cached)) {
                Timber.d("Cache hit for '$normalizedTitle' (lang=$languageTag)")
                return cached.toResult()
            }

            if (tmdbId > 0) {
                val localizedDetail = fetchDetailByTmdbId(tmdbId, contentType, languageTag) ?: return null
                val fallbackDetail = fetchFallbackDetailIfNeeded(localizedDetail, contentType, tmdbId, languageTag)
                val result = buildResult(
                    detail = localizedDetail,
                    fallbackDetail = fallbackDetail,
                    bestMatch = null,
                    effectiveYear = effectiveYear,
                    confidence = 100.0,
                    languageTag = languageTag
                )
                cacheResult(normalizedTitle, result, languageTag, contentType)
                return result.takeIf { it.hasUsableDetails() }
            }

            val searchResults = searchMetadataCandidates(cleanTitle, title, effectiveYear, contentType, languageTag)

            if (searchResults.isEmpty()) {
                Timber.d("No TMDB results for '$cleanTitle'")
                return null
            }

            // Smart scoring to establish best match with confidence
            val scoredResults = searchResults.distinctBy { it.id }.map { result ->
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
            val fallbackDetail = fetchFallbackDetailIfNeeded(detail, contentType, bestMatch.id, languageTag)

            val result = buildResult(
                detail = detail,
                fallbackDetail = fallbackDetail,
                bestMatch = bestMatch,
                effectiveYear = effectiveYear,
                confidence = bestScore,
                languageTag = languageTag
            )

            cacheResult(normalizedTitle, result, languageTag, contentType)

            return result.takeIf { it.hasUsableDetails() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch metadata for: $title")
            return null
        }
    }

    private suspend fun currentLanguageTag(): String {
        settingsDataStore.settings.first()
        return TURKISH_METADATA_LANGUAGE
    }

    private suspend fun fetchDetailByTmdbId(
        tmdbId: Int,
        contentType: ContentType,
        languageTag: String
    ): TmdbDetailResponse? {
        return when (contentType) {
            ContentType.MOVIE -> tmdbApi.getMovieDetails(tmdbId, apiKey, language = languageTag)
            ContentType.SERIES -> tmdbApi.getTvDetails(tmdbId, apiKey, language = languageTag)
            ContentType.LIVE -> null
        }
    }

    private suspend fun fetchFallbackDetailIfNeeded(
        localizedDetail: TmdbDetailResponse,
        contentType: ContentType,
        tmdbId: Int,
        languageTag: String
    ): TmdbDetailResponse? {
        if (languageTag.equals(ENGLISH_FALLBACK_LANGUAGE, ignoreCase = true)) return null
        if (!localizedDetail.needsFallbackDetail()) return null

        return runCatching {
            fetchDetailByTmdbId(tmdbId, contentType, ENGLISH_FALLBACK_LANGUAGE)
        }.onFailure {
            Timber.w(it, "Failed to fetch English fallback metadata for TMDB id=$tmdbId")
        }.getOrNull()
    }

    private suspend fun searchMetadataCandidates(
        cleanTitle: String,
        originalTitle: String,
        effectiveYear: Int,
        contentType: ContentType,
        languageTag: String
    ): List<TmdbSearchResult> {
        val queries = buildList {
            add(cleanTitle)
            val extractedTitle = StringUtils.extractYearFromTitle(originalTitle).first
            add(StringUtils.cleanTitleForSearch(extractedTitle))
            add(originalTitle)
        }.map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        val languages = listOf(languageTag, ENGLISH_FALLBACK_LANGUAGE).distinct()

        return queries.flatMap { query ->
            val year = effectiveYear.takeIf { it > 0 }
            languages.flatMap { language ->
                when (contentType) {
                    ContentType.MOVIE -> tmdbApi.searchMovie(apiKey, query, year, 1, language).results
                    ContentType.SERIES -> tmdbApi.searchTv(apiKey, query, year, 1, language).results
                    ContentType.LIVE -> emptyList()
                }
            }
        }
    }

    private fun buildResult(
        detail: TmdbDetailResponse,
        fallbackDetail: TmdbDetailResponse?,
        bestMatch: TmdbSearchResult?,
        effectiveYear: Int,
        confidence: Double,
        languageTag: String
    ): MetadataResult {
        val translations = detail.translations?.translations ?: fallbackDetail?.translations?.translations ?: emptyList()
        val translated = resolveTranslation(translations, languageTag, detail.originalLanguage)
        val finalOverview = translated?.overview?.takeIf { it.isNotBlank() }
            ?: detail.overview.ifBlank { fallbackDetail?.overview.orEmpty() }
        val finalTagline = translated?.tagline?.takeIf { it.isNotBlank() }
            ?: detail.tagline.orEmpty().ifBlank { fallbackDetail?.tagline.orEmpty() }

        val director = detail.credits?.crew?.find { it.job == "Director" }?.name
            ?: fallbackDetail?.credits?.crew?.find { it.job == "Director" }?.name
            ?: detail.createdBy?.firstOrNull()?.name
            ?: fallbackDetail?.createdBy?.firstOrNull()?.name
            ?: ""

        val runtime = detail.runtime
            ?: fallbackDetail?.runtime
            ?: detail.episodeRunTime?.firstOrNull()
            ?: fallbackDetail?.episodeRunTime?.firstOrNull()
            ?: 0

        val videoResults = detail.videos?.results.orEmpty().ifEmpty { fallbackDetail?.videos?.results.orEmpty() }
        val trailerUrl = videoResults
            .filter { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
            .maxByOrNull { if (it.official) 1 else 0 }
            ?.let { "https://www.youtube.com/watch?v=${it.key}" }
            ?: videoResults
                .firstOrNull { it.site.equals("YouTube", ignoreCase = true) }
                ?.let { "https://www.youtube.com/watch?v=${it.key}" }
            ?: ""

        val genres = detail.genres.ifEmpty { fallbackDetail?.genres.orEmpty() }
        val cast = detail.credits?.cast.orEmpty().ifEmpty { fallbackDetail?.credits?.cast.orEmpty() }

        return MetadataResult(
            tmdbId = detail.id.takeIf { it > 0 } ?: fallbackDetail?.id ?: 0,
            imdbId = detail.imdbId ?: detail.externalIds?.imdbId ?: fallbackDetail?.imdbId ?: fallbackDetail?.externalIds?.imdbId ?: "",
            posterUrl = detail.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                ?: fallbackDetail?.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                ?: bestMatch?.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                ?: "",
            backdropUrl = detail.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                ?: fallbackDetail?.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                ?: bestMatch?.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                ?: "",
            overview = finalOverview,
            tagline = finalTagline,
            genre = genres.joinToString(", ") { it.name },
            cast = cast.take(15).joinToString(", ") { it.name },
            director = director,
            runtime = runtime,
            rating = when {
                detail.voteAverage > 0 -> detail.voteAverage
                fallbackDetail?.voteAverage != null && fallbackDetail.voteAverage > 0 -> fallbackDetail.voteAverage
                else -> bestMatch?.voteAverage ?: 0.0
            },
            year = StringUtils.extractYear(detail.releaseDate ?: detail.firstAirDate)
                ?: StringUtils.extractYear(fallbackDetail?.releaseDate ?: fallbackDetail?.firstAirDate)
                ?: StringUtils.extractYear(bestMatch?.releaseDate ?: bestMatch?.firstAirDate)
                ?: effectiveYear,
            trailerUrl = trailerUrl,
            confidence = confidence
        )
    }

    private suspend fun cacheResult(
        normalizedTitle: String,
        result: MetadataResult,
        languageTag: String,
        contentType: ContentType
    ) {
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
     * 2. Original Turkish language data, if the content itself is Turkish
     * 3. English fallback, so detail pages are not left blank when Turkish metadata is unavailable.
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

        // 2. Try original language only when it is Turkish.
        if (originalLanguage.equals("tr", ignoreCase = true)) {
            val original = translations.find {
                it.language.equals("tr", ignoreCase = true) && !it.data?.overview.isNullOrBlank()
            }?.data
            if (original != null) return original
        }

        // 3. English fallback. This is preferable to a blank detail screen.
        return translations.find {
            it.language.equals("en", ignoreCase = true) && !it.data?.overview.isNullOrBlank()
        }?.data
    }

    private fun isFreshUsableCache(cached: MetadataCacheEntity): Boolean {
        return System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MS && cached.toResult().hasUsableDetails()
    }

    private fun TmdbDetailResponse.needsFallbackDetail(): Boolean {
        return overview.isBlank() ||
            genres.isEmpty() ||
            credits?.cast.isNullOrEmpty() ||
            (posterPath.isNullOrBlank() && backdropPath.isNullOrBlank())
    }

    private fun MetadataResult.hasUsableDetails(): Boolean {
        return overview.isNotBlank() ||
            genre.isNotBlank() ||
            cast.isNotBlank() ||
            director.isNotBlank() ||
            posterUrl.isNotBlank() ||
            backdropUrl.isNotBlank() ||
            rating > 0.0 ||
            year > 0
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
