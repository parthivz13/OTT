package com.example.ott.types

data class Asset(
    val id: String? = null,
    val name: String? = null,
    val externalId: String? = null,
    val images: List<AssetImage>? = null,
    val mediaFiles: List<MediaFile>? = null,
    val tags: Map<String, Any>? = null,
    val metas: Map<String, Any>? = null
)

data class AssetImage(
    val url: String? = null,
    val ratio: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class MediaFile(
    val type: String? = null,
    val url: String? = null,
    val duration: Long = 0L
)
