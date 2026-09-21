package com.example.ott.sott.models

data class CustomAsset(
    val id: String? = null,
    val name: String? = null,
    val images: List<AssetImage>? = null
)

data class CustomKalturaAsset(
    val id: String? = null,
    val name: String? = null,
    val images: List<AssetImage>? = null
)

data class AssetImage(
    val url: String? = null,
    val ratio: String? = null
)
