package com.example.ott.EnveuCategoryServices

data class Asset(
    val id: String? = null,
    val name: String? = null,
    val images: List<AssetImage>? = null
)

data class AssetImage(
    val url: String? = null
)
