package com.example.ott.ui.rows.top10

import androidx.leanback.widget.ItemAlignmentFacet
import androidx.leanback.widget.ListRowPresenter

class Top10RowPresenter : ListRowPresenter() {
    init {
        shadowEnabled = false
        selectEffectEnabled = false

        val rowHeaderFacet = ItemAlignmentFacet().apply {
            alignmentDefs = arrayOf(
                ItemAlignmentFacet.ItemAlignmentDef().apply {
                    setItemAlignmentViewId(androidx.leanback.R.id.row_header)
                    itemAlignmentOffset = 0
                    itemAlignmentOffsetPercent = 0f
                }
            )
        }
        setFacet(ItemAlignmentFacet::class.java, rowHeaderFacet)
    }
}
