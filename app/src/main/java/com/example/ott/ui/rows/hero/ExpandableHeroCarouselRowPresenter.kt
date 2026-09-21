package com.example.ott.ui.rows.hero

import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemAlignmentFacet
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

/**
 * Self-contained ListRowPresenter tailored for the Expandable Hero Carousel.
 *
 * Encapsulates:
 * - Alignment & layout configuration so expanding 16:9 cards stay visible without clipping.
 * - Row-level focus tracking: pauses/stops trailer video when focus leaves the carousel rail.
 * - Automatic lifecycle teardown on unbind/recycle without fragment coupling.
 */
class ExpandableHeroCarouselRowPresenter(
    private val carouselFocusListener: CarouselFocusListener? = null
) : ListRowPresenter() {

    init {
        shadowEnabled = false
        selectEffectEnabled = false

        // Align by row header so rail title is never pushed offscreen when focused
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

        // Prevent cards from being clipped during 2:3 -> 16:9 expansion
        gridView.clipChildren = false
        gridView.clipToPadding = false

        // Both-edge alignment ensures focused expanding cards never cut off on either left or right boundary
        gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
        gridView.windowAlignmentOffset = (48 * density).toInt()
        gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        gridView.itemAlignmentOffsetPercent = 0f
        gridView.itemAlignmentOffset = 0

        var p = gridView.parent
        while (p is ViewGroup && p !is androidx.leanback.widget.VerticalGridView) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
        }

        // Detect when focus leaves the entire carousel row (e.g. DPAD Up/Down)
        gridView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                HeroCarouselCardPresenter.stopActiveVideo()
                carouselFocusListener?.onCarouselFocusChanged(false)
            } else {
                carouselFocusListener?.onCarouselFocusChanged(true)
            }
        }
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
    }
}
