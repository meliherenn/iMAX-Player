package com.imax.player.metadata

import com.imax.player.BuildConfig
import com.imax.player.core.common.Constants
import com.imax.player.core.common.StringUtils
import com.imax.player.core.database.MetadataCacheDao
import com.imax.player.core.database.MetadataCacheEntity
import com.imax.player.core.model.ContentType
import com.imax.player.core.network.TmdbApi
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
    val genre: String = "",
    val cast: String = "",
    val director: String = "",
    val runtime: Int = 0,
    val rating: Double = 0.0,
    val year: Int = 0
)

@Singleton
class MetadataProvider @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val cacheDao: MetadataCacheDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val apiKey: String = BuildConfig.TMDB_API_KEY

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
            val cached = cacheDao.find(normalizedTitle, year)
            if (cached != null && System.currentTimeMillis() - cached.cachedAt < 7 * 24 * 3600 * 1000L) {
                return cached.toResult()
            }

            val appLanguage = settingsDataStore.settings.first().appLanguage
            val languageTag = when (appLanguage.lowercase()) {
                "tr" -> "tr-TR"
                "en" -> "en-US"
                else -> Locale.getDefault().toLanguageTag()
            }

            val searchResponse = when (contentType) {
                ContentType.MOVIE -> tmdbApi.searchMovie(apiKey, cleanTitle, if (year > 0) year else null, 1, languageTag)
                ContentType.SERIES -> tmdbApi.searchTv(apiKey, cleanTitle, if (year > 0) year else null, 1, languageTag)
                ContentType.LIVE -> return null
            }

            val bestMatch = searchResponse.results.firstOrNull { result ->
                val resultTitle = result.title ?: result.name ?: ""
                val originalTitle = result.originalTitle ?: ""
                val resultYear = StringUtils.extractYear(result.releaseDate ?: result.firstAirDate)
                val isTitleMatch = StringUtils.fuzzyMatch(cleanTitle, resultTitle) || 
                                   StringUtils.fuzzyMatch(cleanTitle, originalTitle) ||
                                   StringUtils.fuzzyMatch(title, resultTitle)
                isTitleMatch && (year == 0 || resultYear == null || resultYear == year)
            } ?: searchResponse.results.firstOrNull() ?: return null

            val detail = when (contentType) {
                ContentType.MOVIE -> tmdbApi.getMovieDetails(bestMatch.id, apiKey, language = languageTag)
                ContentType.SERIES -> tmdbApi.getTvDetails(bestMatch.id, apiKey, language = languageTag)
                ContentType.LIVE -> return null
            }
            
            var finalOverview = detail.overview
            if (finalOverview.isBlank()) {
                val translations = detail.translations?.translations ?: emptyList()
                val enTranslation = translations.find { it.language.lowercase() == "en" }?.data
                if (!enTranslation?.overview.isNullOrBlank()) {
                    finalOverview = enTranslation!!.overview
                } else {
                    val anyTranslation = translations.find { !it.data?.overview.isNullOrBlank() }?.data
                    if (!anyTranslation?.overview.isNullOrBlank()) {
                        finalOverview = anyTranslation!!.overview
                    }
                }
            }

            val result = MetadataResult(
                tmdbId = detail.id,
                imdbId = detail.imdbId ?: detail.externalIds?.imdbId ?: "",
                posterUrl = detail.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" } ?: "",
                backdropUrl = detail.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" } ?: "",
                overview = finalOverview,
                genre = detail.genres.joinToString(", ") { it.name },
                cast = detail.credits?.cast?.take(10)?.joinToString(", ") { it.name } ?: "",
                director = detail.credits?.crew?.find { it.job == "Director" }?.name ?: "",
                runtime = detail.runtime ?: 0,
                rating = detail.voteAverage,
                year = StringUtils.extractYear(detail.releaseDate ?: detail.firstAirDate) ?: year
            )

            cacheDao.insert(
                MetadataCacheEntity(
                    title = normalizedTitle,
                    year = result.year,
                    tmdbId = result.tmdbId,
                    imdbId = result.imdbId,
                    posterUrl = result.posterUrl,
                    backdropUrl = result.backdropUrl,
                    overview = result.overview,
                    genre = result.genre,
                    cast = result.cast,
                    director = result.director,
                    runtime = result.runtime,
                    rating = result.rating,
                    contentType = contentType.name
                )
            )

            return result
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch metadata for: $title")
            return null
        }
    }

    private fun MetadataCacheEntity.toResult() = MetadataResult(
        tmdbId = tmdbId,
        imdbId = imdbId,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        overview = overview,
        genre = genre,
        cast = cast,
        director = director,
        runtime = runtime,
        rating = rating,
        year = year
    )
}
