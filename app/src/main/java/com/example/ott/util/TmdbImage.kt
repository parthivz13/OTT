package com.example.ott.util

object TmdbImage {
    private const val BASE_URL = "https://image.tmdb.org/t/p/"

    fun posterUrl(path: String?, size: String = "w342"): String? = path?.let { "$BASE_URL$size$it" }

    fun backdropUrl(path: String?, size: String = "w780"): String? = path?.let { "$BASE_URL$size$it" }
}
