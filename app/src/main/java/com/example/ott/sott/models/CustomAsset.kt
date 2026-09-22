package com.example.ott.sott.models

data class CustomAsset(
    var Id: Any? = null,
    var id: String? = null,
    var name: String? = null,
    var images: List<AssetImage>? = null
)

data class CustomKalturaAsset(
    var Id: Any? = null,
    var id: String? = null,
    var name: String? = null,
    var images: List<AssetImage>? = null
)

data class AssetImage(
    var url: String? = null,
    var ratio: String? = null
)
