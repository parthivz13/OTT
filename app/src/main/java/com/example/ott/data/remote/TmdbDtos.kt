package com.example.ott.data.remote

import com.google.gson.annotations.SerializedName

data class TrendingResponseDto(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<TitleDto>
)

data class TitleDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?
)
