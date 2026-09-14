package com.example.ott.data.repository

import com.example.ott.data.model.Title
import com.example.ott.data.remote.ShowDto
import com.example.ott.data.remote.TvMazeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvMazeRepository(private val api: TvMazeApi) {

    suspend fun getShows(): List<Title> = withContext(Dispatchers.IO) {
        api.getShows(page = 0)
            .filter { !it.image?.medium.isNullOrBlank() }
            .sortedByDescending { it.weight ?: 0 }
            .map { it.toDomain() }
    }

    private fun ShowDto.toDomain(): Title = Title(
        id = id,
        name = name.orEmpty(),
        overview = summary.orEmpty().replace(Regex("<[^>]*>"), "").trim(),
        posterUrl = image?.original ?: image?.medium,
        backdropUrl = image?.original ?: image?.medium,
        rating = rating?.average ?: 0.0,
        mediaType = "tv",
        year = premiered?.take(4).orEmpty(),
        genre = genres?.firstOrNull() ?: "Other"
    )
}
