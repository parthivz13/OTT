package com.example.ott.data.model

data class Title(
    val id: Int,
    val name: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double,
    val mediaType: String,
    val year: String = "",
    val genre: String = "",
    val badge: String? = null,
    val contentRating: String = "U/A 16+",
    val durationOrSeasons: String = "",
    val audioLanguages: String = "English • Hindi",
    val qualityTag: String = "4K • Dolby Vision",
    val videoUrl: String? = null,
    val trailerUrl: String? = videoUrl,
    val durationSeconds: Int = 5400,
    val is4K: Boolean = true,
    val isHD: Boolean = true,
    val isAD: Boolean = false,
    val seasonEpisode: String? = null
)
