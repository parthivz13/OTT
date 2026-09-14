package com.example.ott.ui.rows.stack

import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row

/**
 * Distinct [Row] type purely so [androidx.leanback.widget.ClassPresenterSelector] can pick
 * [HeroCarouselRowPresenter] for it instead of the standard [androidx.leanback.widget.ListRowPresenter].
 */
class HeroCarouselRow(headerItem: HeaderItem?, val adapter: ObjectAdapter) : Row(headerItem)
