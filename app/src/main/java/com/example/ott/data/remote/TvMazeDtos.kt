package com.example.ott.data.remote

import com.google.gson.annotations.SerializedName

data class ShowDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("premiered") val premiered: String?,
    @SerializedName("rating") val rating: RatingDto?,
    @SerializedName("image") val image: ImageDto?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("weight") val weight: Int?,
    @SerializedName("runtime") val runtime: Int?
)

data class RatingDto(
    @SerializedName("average") val average: Double?
)

data class ImageDto(
    @SerializedName("medium") val medium: String?,
    @SerializedName("original") val original: String?
)
