package com.example.ott.sott.presenter

import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import com.example.ott.sott.networking.RailCommonData

interface OnItemInteractionListener {
    fun onItemSelected1(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row,
        position: Int,
        rowPosition: Int,
        railCommonData: RailCommonData,
        rowCount: Int
    )

    fun onItemClicked1(
        vh: Presenter.ViewHolder?,
        item: Any?,
        rowVH: RowPresenter.ViewHolder?,
        row: Row?,
        position: Int,
        rowPosition: Int,
        railCommonData: RailCommonData
    )
}
