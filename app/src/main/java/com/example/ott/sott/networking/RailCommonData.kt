package com.example.ott.sott.networking

import com.example.ott.sott.models.CustomAsset
import com.example.ott.sott.models.CustomKalturaAsset
import com.example.ott.types.Asset

data class ScreenWidget(
    var Id: String? = null,
    var name: String? = null,
    var type: String? = null,
    var contentImageType: String? = null,
    var autoRotate: Boolean? = true,
    var autoRotateDuration: Int? = 5
)

data class RailCommonData(
    var screenWidget: ScreenWidget? = null,
    var assets: List<Asset>? = null,
    var customAssets: List<CustomAsset>? = null,
    var customKalturaAssets: List<CustomKalturaAsset>? = null,
    var enveuAssets: List<com.example.ott.EnveuCategoryServices.Asset>? = null,
    var items: List<Any>? = null
)
