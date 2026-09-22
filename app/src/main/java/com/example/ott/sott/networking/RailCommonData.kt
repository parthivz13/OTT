package com.example.ott.sott.networking

import com.example.ott.sott.models.BaseCategory
import com.example.ott.sott.models.CustomAsset
import com.example.ott.sott.models.CustomKalturaAsset
import com.example.ott.sott.utils.enums.RailTypes
import com.example.ott.types.Asset

typealias ScreenWidget = BaseCategory

data class RailCommonData(
    var railType: RailTypes = RailTypes.HORIZONTAL_LDS_LANDSCAPE,
    var screenWidget: BaseCategory? = null,
    var assets: ArrayList<Asset> = ArrayList(),
    var customAssets: List<CustomAsset>? = null,
    var customKalturaAssets: List<CustomKalturaAsset>? = null,
    var enveuAssets: List<com.example.ott.EnveuCategoryServices.Asset>? = null,
    var items: List<Any>? = null
)
