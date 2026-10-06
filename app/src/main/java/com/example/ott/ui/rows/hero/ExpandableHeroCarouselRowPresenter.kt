package com.example.ott.ui.rows.hero
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemAlignmentFacet
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter
class ExpandableHeroCarouselRowPresenter(
    private val carouselFocusListener: CarouselFocusListener? = null,
    val keepExpandedWhenUnfocused: Boolean = false
) : ListRowPresenter() {
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
    override fun initializeRowViewHolder(holder: RowPresenter.ViewHolder) {
        super.initializeRowViewHolder(holder)
        val listRowHolder = holder as? ViewHolder ?: return
        val gridView: HorizontalGridView = listRowHolder.gridView
        val density = gridView.resources.displayMetrics.density
        gridView.clipChildren = false
        gridView.clipToPadding = false
        gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
        gridView.windowAlignmentOffset = (17 * density).toInt()
        gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        gridView.itemAlignmentOffsetPercent = 0f
        gridView.itemAlignmentOffset = 0
        listRowHolder.view.setPadding(0, listRowHolder.view.paddingTop, listRowHolder.view.paddingRight, listRowHolder.view.paddingBottom)
        gridView.setPadding(0, gridView.paddingTop, gridView.paddingRight, gridView.paddingBottom)
        var p = gridView.parent
        while (p is ViewGroup && p !is androidx.leanback.widget.VerticalGridView) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
        }
        gridView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        (listRowHolder.view as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        gridView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                carouselFocusListener?.onCarouselFocusChanged(true)
                val pos = gridView.selectedPosition
                val childHolder = gridView.findViewHolderForAdapterPosition(pos)
                if (childHolder != null) {
                    if (!childHolder.itemView.hasFocus()) {
                        childHolder.itemView.requestFocus()
                    }
                } else {
                    gridView.post {
                        val vh = gridView.findViewHolderForAdapterPosition(gridView.selectedPosition)
                        if (vh != null && !vh.itemView.hasFocus()) {
                            vh.itemView.requestFocus()
                        }
                    }
                }
            }
        }
        gridView.setOnChildViewHolderSelectedListener(object : androidx.leanback.widget.OnChildViewHolderSelectedListener() {
            override fun onChildViewHolderSelected(
                parent: androidx.recyclerview.widget.RecyclerView,
                child: androidx.recyclerview.widget.RecyclerView.ViewHolder?,
                position: Int,
                subposition: Int
            ) {
                if (gridView.hasFocus() && child != null && !child.itemView.hasFocus()) {
                    child.itemView.requestFocus()
                }
            }
        })
    }
    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(holder, item)
    }
    override fun onUnbindRowViewHolder(holder: RowPresenter.ViewHolder) {
        super.onUnbindRowViewHolder(holder)
        val row = holder.row as? ExpandableHeroCarouselRow
        row?.let {
            HeroCarouselAutoSlideController.unregisterRow(it.config.rowId)
        }
        HeroCarouselCardPresenter.stopActiveVideo()
        HeroCarouselCardPresenter.collapseLastExpanded()
    }
}