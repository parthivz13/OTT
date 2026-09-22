package com.example.ott.sott.models

import androidx.leanback.widget.ArrayObjectAdapter
import com.example.ott.sott.networking.RailCommonData

data class NavigationInfoModel(
    var assetsAdapter: ArrayObjectAdapter,
    var position: Int,
    var baseCategory: BaseCategory,
    var railCommonData: RailCommonData
)
