package com.example.ott.ui.rows.hero

import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import com.example.ott.data.model.HeroCarouselConfig

/**
 * Dedicated Expandable Hero Carousel Row representation for Leanback.
 */
class ExpandableHeroCarouselRow(
    headerItem: HeaderItem,
    adapter: ObjectAdapter,
    val config: HeroCarouselConfig = HeroCarouselConfig()
) : ListRow(headerItem, adapter)
