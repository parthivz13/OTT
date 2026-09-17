package com.example.ott.ui.rows.stack

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.doOnLayout
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.RowPresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.ott.R
import com.example.ott.data.model.Title

// Jio Hotstar-style spotlight banner: one full-bleed slide at a time with a peeking card stack
// behind it, swapped on D-pad left/right with a dot-strip position indicator below.
class HeroCarouselRowPresenter : RowPresenter() {

    init {
        // No header/select-effect - Leanback would otherwise wrap this row in a WRAP_CONTENT
        // container and collapse its width.
        setHeaderPresenter(null)
        setSelectEffectEnabled(false)

    }

    override fun isUsingDefaultSelectEffect(): Boolean = false

    companion object {
        // Matches the real Jio Hotstar TV spotlight banner's observed rotation cadence.
        private const val AUTO_ROTATE_INTERVAL_MS = 15000L
        private const val DOT_SIZE_DP = 8
        private const val DOT_ACTIVE_WIDTH_DP = 22
        private const val DOT_SPACING_DP = 4

        // Max peeks built; only slides that actually exist show (see renderPeeks).
        private const val PEEK_COUNT_MAX = 2

        // Peek size/position ratios, measured off a real Jio Hotstar screenshot.
        private val PEEK_WIDTH_RATIO = floatArrayOf(0.77f, 0.54f, 0.40f)
        private val PEEK_HEIGHT_RATIO = floatArrayOf(0.87f, 0.74f, 0.62f)
        private const val PEEK_SHIFT_STEP_DP = 230f
        private const val PEEK_FADE_DURATION_MS = 220L
    }

    class PeekViewHolder(val card: CardView, val images: CrossfadeImagePair) {
        var boundTitleId: Int? = null
    }

    class ViewHolder(rootView: View) : RowPresenter.ViewHolder(rootView) {
        val card: CardView = rootView.findViewById(R.id.hero_card)
        val backdropContainer: FrameLayout = rootView.findViewById(R.id.hero_backdrop_container)
        val badge: TextView = rootView.findViewById(R.id.hero_badge)
        val title: TextView = rootView.findViewById(R.id.hero_title)
        val meta: TextView = rootView.findViewById(R.id.hero_meta)
        val overview: TextView = rootView.findViewById(R.id.hero_overview)
        val dotsContainer: LinearLayout = rootView.findViewById(R.id.hero_dots)
        val peekContainer: FrameLayout = rootView.findViewById(R.id.hero_peek_container)
        val peeks = mutableListOf<PeekViewHolder>()
        lateinit var backdrop: CrossfadeImagePair

        var adapter: ObjectAdapter? = null
        var selectedIndex: Int = 0
        val autoRotateHandler = Handler(Looper.getMainLooper())
        var autoRotateRunnable: Runnable? = null
    }

    private val fallbackPalette = intArrayOf(
        0xFF1E3A5F.toInt(),
        0xFF6A1B9A.toInt(),
        0xFF00796B.toInt(),
        0xFFBF360C.toInt(),
        0xFFAD1457.toInt(),
        0xFF283593.toInt(),
        0xFF2E7D32.toInt(),
        0xFFF9A825.toInt()
    )

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val rootView = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_hero_carousel, parent, false)
        val viewHolder = ViewHolder(rootView)
        viewHolder.backdrop = CrossfadeImagePair(viewHolder.backdropContainer)
        buildPeekViews(viewHolder)

        viewHolder.card.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                // Both directions clamp at the adapter's bounds and consume the key either way,
                // so Left at the first slide never lets focus escape into the side nav.
                KeyEvent.KEYCODE_DPAD_RIGHT -> { tryAdvance(viewHolder, 1); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { tryAdvance(viewHolder, -1); true }
                else -> false
            }
        }

        viewHolder.card.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) stopAutoRotate(viewHolder) else startAutoRotate(viewHolder)
            viewHolder.onItemViewSelectedListener?.onItemSelected(
                null,
                currentItem(viewHolder),
                viewHolder,
                viewHolder.row
            )
        }

        viewHolder.card.setOnClickListener {
            viewHolder.onItemViewClickedListener?.onItemClicked(
                null,
                currentItem(viewHolder),
                viewHolder,
                viewHolder.row
            )
        }

        return viewHolder
    }

    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        val row = item as HeroCarouselRow
        val holder = vh as ViewHolder
        holder.adapter = row.adapter
        holder.selectedIndex = 0
        bindSlide(holder, 0)
        startAutoRotate(holder)
    }

    override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
        super.onUnbindRowViewHolder(vh)
        val holder = vh as ViewHolder
        stopAutoRotate(holder)
        holder.adapter = null
    }

    private fun tryAdvance(holder: ViewHolder, delta: Int): Boolean {
        val adapter = holder.adapter ?: return false
        val count = adapter.size()
        val next = holder.selectedIndex + delta
        if (next < 0 || next >= count) return false
        holder.selectedIndex = next
        bindSlide(holder, next)
        return true
    }

    private fun currentItem(holder: ViewHolder): Any? = holder.adapter?.get(holder.selectedIndex)

    // Peek sizing depends on the active card's own measured width, so this waits for a real
    // layout pass (doOnLayout survives re-layouts, unlike a one-shot ViewTreeObserver listener).
    private fun buildPeekViews(holder: ViewHolder) {
        holder.card.doOnLayout {
            val cardWidth = holder.card.width
            val cardHeight = holder.card.height
            if (cardWidth == 0 || cardHeight == 0) return@doOnLayout
            populatePeekViews(holder, cardWidth, cardHeight)
        }
    }

    private fun populatePeekViews(holder: ViewHolder, cardWidth: Int, cardHeight: Int) {
        if (holder.peeks.isNotEmpty()) return
        val context = holder.peekContainer.context
        val density = context.resources.displayMetrics.density
        // Each peek is inserted at index 0 (not appended) so earlier, closer peeks stay drawn on
        // top of farther ones - FrameLayout draws children lowest-index-first.
        val shiftStepPx = (PEEK_SHIFT_STEP_DP * density).toInt()
        for (i in 0 until PEEK_COUNT_MAX) {
            val depth = i + 1
            val card = CardView(context).apply {
                radius = 14f * density
                setCardBackgroundColor(context.getColor(R.color.banner_background))
                cardElevation = 0f
            }
            val peekWidth = (cardWidth * PEEK_WIDTH_RATIO[i]).toInt()
            val peekHeight = (cardHeight * PEEK_HEIGHT_RATIO[i]).toInt()
            val params = FrameLayout.LayoutParams(peekWidth, peekHeight, android.view.Gravity.CENTER_VERTICAL)
            params.marginStart = shiftStepPx * depth
            card.layoutParams = params

            val imageContainer = FrameLayout(context)
            card.addView(
                imageContainer,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            val images = CrossfadeImagePair(imageContainer)

            holder.peekContainer.addView(card, 0)
            holder.peeks.add(PeekViewHolder(card, images))
        }
        // Re-render whatever slide is already bound, since these views were built after that.
        val adapter = holder.adapter ?: return
        renderPeeks(holder, adapter, holder.selectedIndex)
    }

    private fun bindSlide(holder: ViewHolder, index: Int) {
        val adapter = holder.adapter ?: return
        if (index !in 0 until adapter.size()) return
        val item = adapter.get(index) as Title

        holder.title.text = item.name
        holder.meta.text = buildMetaLine(item)
        holder.overview.text = item.overview

        if (item.badge != null) {
            holder.badge.text = item.badge
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        loadBackdrop(holder.backdrop, item)
        renderDots(holder, adapter.size(), index)
        renderPeeks(holder, adapter, index)
        preloadUpcoming(holder, adapter, index)
    }

    private fun renderPeeks(holder: ViewHolder, adapter: ObjectAdapter, index: Int) {
        holder.peeks.forEachIndexed { i, peek ->
            val depth = i + 1
            val peekItem = itemAt(adapter, index + depth)
            if (peekItem == null) {
                peek.card.animate().cancel()
                peek.card.visibility = View.INVISIBLE
                peek.boundTitleId = null
                return@forEachIndexed
            }
            val isNewTitle = peek.boundTitleId != peekItem.id
            peek.boundTitleId = peekItem.id
            loadBackdrop(peek.images, peekItem)
            if (!isNewTitle && peek.card.visibility == View.VISIBLE) return@forEachIndexed
            peek.card.animate().cancel()
            peek.card.alpha = 0f
            peek.card.visibility = View.VISIBLE
            peek.card.animate().alpha(1f).setDuration(PEEK_FADE_DURATION_MS).start()
        }
    }

    private fun loadBackdrop(images: CrossfadeImagePair, item: Title) {
        val backdropUrl = item.backdropUrl
        if (backdropUrl == null) {
            images.setColor(paletteColorFor(item))
        } else {
            images.load(backdropUrl)
        }
    }

    private fun preloadUpcoming(holder: ViewHolder, adapter: ObjectAdapter, index: Int) {
        val context = holder.peekContainer.context
        val preloadRange = (index - 1)..(index + PEEK_COUNT_MAX + 1)
        for (i in preloadRange) {
            if (i == index) continue
            val url = itemAt(adapter, i)?.backdropUrl ?: continue
            Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload()
        }
    }

    private fun itemAt(adapter: ObjectAdapter, index: Int): Title? =
        if (index in 0 until adapter.size()) adapter.get(index) as Title else null

    private fun paletteColorFor(item: Title): Int = fallbackPalette[item.id % fallbackPalette.size]

    private fun buildMetaLine(title: Title): String {
        val typeLabel = if (title.mediaType == "tv") "TV Show" else "Movie"
        val parts = listOfNotNull(
            title.year.takeIf { it.isNotBlank() },
            title.genre.takeIf { it.isNotBlank() } ?: typeLabel,
            "★ %.1f".format(title.rating)
        )
        return parts.joinToString(" • ")
    }

    private fun renderDots(holder: ViewHolder, count: Int, activeIndex: Int) {
        val context = holder.dotsContainer.context
        val density = context.resources.displayMetrics.density
        val dotSize = (DOT_SIZE_DP * density).toInt()
        val activeWidth = (DOT_ACTIVE_WIDTH_DP * density).toInt()
        val spacing = (DOT_SPACING_DP * density).toInt()

        if (holder.dotsContainer.childCount != count) {
            holder.dotsContainer.removeAllViews()
            repeat(count) { i ->
                val dot = View(context)
                val params = LinearLayout.LayoutParams(dotSize, dotSize)
                if (i != 0) params.marginStart = spacing
                dot.layoutParams = params
                holder.dotsContainer.addView(dot)
            }
        }

        for (i in 0 until holder.dotsContainer.childCount) {
            val dot = holder.dotsContainer.getChildAt(i)
            val params = dot.layoutParams as LinearLayout.LayoutParams
            if (i == activeIndex) {
                params.width = activeWidth
                dot.setBackgroundResource(R.drawable.hero_dot_active)
            } else {
                params.width = dotSize
                dot.setBackgroundResource(R.drawable.hero_dot_inactive)
            }
            dot.layoutParams = params
        }
    }

    private fun startAutoRotate(holder: ViewHolder) {
        stopAutoRotate(holder)
        val runnable = object : Runnable {
            override fun run() {
                val adapter = holder.adapter
                if (adapter != null && adapter.size() > 1) {
                    val next = (holder.selectedIndex + 1) % adapter.size()
                    holder.selectedIndex = next
                    bindSlide(holder, next)
                }
                holder.autoRotateHandler.postDelayed(this, AUTO_ROTATE_INTERVAL_MS)
            }
        }
        holder.autoRotateRunnable = runnable
        holder.autoRotateHandler.postDelayed(runnable, AUTO_ROTATE_INTERVAL_MS)
    }

    private fun stopAutoRotate(holder: ViewHolder) {
        holder.autoRotateRunnable?.let { holder.autoRotateHandler.removeCallbacks(it) }
        holder.autoRotateRunnable = null
    }
}
