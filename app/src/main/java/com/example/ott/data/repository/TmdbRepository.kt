package com.example.ott.data.repository

import com.example.ott.data.model.Title
import com.example.ott.data.remote.TitleDto
import com.example.ott.data.remote.TmdbApi
import com.example.ott.util.TmdbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmdbRepository(private val api: TmdbApi) {

    suspend fun getTrendingTitles(): List<Title> = withContext(Dispatchers.IO) {
        api.getTrendingAllWeek().results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .mapIndexed { index, dto -> dto.toDomain(index) }
    }

    private fun TitleDto.toDomain(index: Int): Title {
        val type = mediaType ?: "movie"
        val vote = voteAverage ?: 7.5
        val badge = when {
            index == 0 -> "🔥 TRENDING #1"
            index in 1..2 -> "HOTSTAR SPECIAL"
            index in 3..4 -> "NEW RELEASE"
            vote >= 8.0 -> "TOP RATED"
            else -> null
        }
        return Title(
            id = id,
            name = name ?: title ?: "",
            overview = overview.orEmpty(),
            posterUrl = TmdbImage.posterUrl(posterPath),
            backdropUrl = TmdbImage.backdropUrl(backdropPath),
            rating = vote,
            mediaType = type,
            year = (releaseDate ?: firstAirDate)?.take(4).orEmpty(),
            genre = if (type == "tv") "TV Show" else "Movie",
            badge = badge,
            contentRating = if (vote > 8.0) "U/A 16+" else "U/A 13+",
            durationOrSeasons = if (type == "tv") "Series" else "Movie",
            audioLanguages = "English • Hindi",
            qualityTag = "4K • Dolby Atmos"
        )
    }
}
