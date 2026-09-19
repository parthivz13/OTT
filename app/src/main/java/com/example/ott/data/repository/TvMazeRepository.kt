package com.example.ott.data.repository

import androidx.core.text.HtmlCompat
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
            .mapIndexed { index, dto -> dto.toDomain(index) }
    }

    private fun ShowDto.toDomain(index: Int): Title {
        val cleanSummary = if (!summary.isNullOrBlank()) {
            HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            ""
        }
        val computedRating = rating?.average ?: 7.5
        val badgeText = when {
            index == 0 -> "🔥 TRENDING #1"
            index in 1..2 -> "HOTSTAR SPECIAL"
            index in 3..4 -> "NEW EPISODE"
            computedRating >= 8.5 -> "POPULAR"
            else -> null
        }
        val contentRating = when {
            genres?.contains("Crime") == true || genres?.contains("Horror") == true -> "A 18+"
            genres?.contains("Drama") == true || genres?.contains("Action") == true -> "U/A 16+"
            else -> "U/A 13+"
        }
        val quality = if (index % 2 == 0) "4K • Dolby Vision" else "4K • Dolby Atmos"

        return Title(
            id = id,
            name = name.orEmpty(),
            overview = cleanSummary,
            posterUrl = image?.original ?: image?.medium,
            backdropUrl = image?.original ?: image?.medium,
            rating = computedRating,
            mediaType = "tv",
            year = premiered?.take(4).orEmpty(),
            genre = genres?.firstOrNull() ?: "Drama",
            badge = badgeText,
            contentRating = contentRating,
            durationOrSeasons = "Series",
            audioLanguages = "English • Hindi",
            qualityTag = quality
        )
    }
}
