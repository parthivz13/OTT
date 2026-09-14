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
    val badge: String? = null
)
