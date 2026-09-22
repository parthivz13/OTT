package com.example.ott.sott.models

enum class PredefinePlaylistType {
    CON_W,
    WATCHLIST,
    AT_BYW,
    BYSL
}

data class BaseCategory(
    var Id: String? = null,
    var name: String? = null,
    var displayOrder: Int? = 0,
    var railCardSize: String? = null,
    var top10Rails: Boolean? = false,
    var autoPlay: Boolean? = false,
    var autoPlayMode: String? = null,
    var autoRotate: Boolean? = true,
    var autoRotateDuration: Int? = 5,
    var transparentBgColor: String? = null,
    var progressBarColor: String? = null,
    var predefPlaylistType: String? = null,
    var brandingHeader: Boolean? = false,
    var railCardType: String? = null,
    var widgetImageorLogo: String? = null,
    var contentImageType: String? = null,
    var type: String? = null
)
