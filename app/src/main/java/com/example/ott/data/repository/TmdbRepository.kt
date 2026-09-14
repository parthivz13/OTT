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
            .map { it.toDomain() }
    }

    private fun TitleDto.toDomain(): Title {
        val type = mediaType ?: "movie"
        return Title(
            id = id,
            name = name ?: title ?: "",
            overview = overview.orEmpty(),
            posterUrl = TmdbImage.posterUrl(posterPath),
            backdropUrl = TmdbImage.backdropUrl(backdropPath),
            rating = voteAverage ?: 0.0,
            mediaType = type,
            year = (releaseDate ?: firstAirDate)?.take(4).orEmpty(),
            genre = if (type == "tv") "TV Show" else "Movie"
        )
    }
}
