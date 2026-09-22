package com.example.ott.types

data class Asset(
    var id: String? = null,
    var name: String? = null,
    var externalId: String? = null,
    var images: List<AssetImage>? = null,
    var mediaFiles: List<MediaFile>? = null,
    var tags: MutableMap<String, Any?> = mutableMapOf(),
    var metas: MutableMap<String, Any?> = mutableMapOf()
)

data class StringValue(
    var value: String? = null
)

data class AssetImage(
    var url: String? = null,
    var ratio: String? = null,
    var width: Int? = null,
    var height: Int? = null
)

data class MediaFile(
    var type: String? = null,
    var url: String? = null,
    var duration: Long = 0L
)
