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

/**
 * Renders a [HeroCarouselRow] as a Jio Hotstar-style spotlight banner: one full-bleed slide at a
 * time (no fanned/scaled stack), swapped instantly on D-pad left/right with a dot-strip position
 * indicator below - matching the real app's observed behavior (see presenter methods for the
 * exact boundary rules, verified against the live Jio Hotstar TV app).
 */
class HeroCarouselRowPresenter : RowPresenter() {

    init {
        // Leanback wraps a row's view in a WRAP_CONTENT RowContainerView whenever it has a header
        // presenter or the default select (dim-on-unselected) effect is active, which collapses
        // this row's match_parent width down to its content's intrinsic size. Real Jio Hotstar's
        // spotlight banner has no row header label or dim/border select effect (only the dot
        // strip indicates focus), so disabling both here is both the width fix and the correct
        // visual match.
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

        // Upper bound on how many upcoming slides peek past the current card's right edge - real
        // Jio Hotstar renders more than this in its underlying RecyclerView, but only ~2 are ever
        // actually visible before clipping off the row. [renderPeeks] hides any peek beyond the
        // slides a row actually has, so a short row never shows empty placeholder cards even
        // though this many views are always built up front.
        private const val PEEK_COUNT_MAX = 2

        // Each successive peek is a genuinely SMALLER card (not a same-size card just clipped),
        // sized as this fraction of the ACTIVE card's own width/height - measured directly off a
        // real Jio Hotstar screenshot: peek depth 1 is ~77%w / ~87%h, depth 2 is ~54%w / ~74%h.
        // Depth 3 continues the same step.
        private val PEEK_WIDTH_RATIO = floatArrayOf(0.77f, 0.54f, 0.40f)
        private val PEEK_HEIGHT_RATIO = floatArrayOf(0.87f, 0.74f, 0.62f)
        // How far each peek's start edge shifts right from the ACTIVE CARD'S OWN start edge, as
        // a constant dp step per depth (not a fraction of card width) - peek1 sits 230dp right of
        // the active card's left edge, peek2 230dp right of peek1's position, and so on.
        private const val PEEK_SHIFT_STEP_DP = 230f
        // Matches CrossfadeImagePair's own crossfade length, so a peek's card-level fade-in and
        // its backdrop image's crossfade (loaded in the same [renderPeeks] pass) finish together.
        private const val PEEK_FADE_DURATION_MS = 220L
    }

    /** One dynamically-built peek layer: the whole card that recedes further as [depth] grows.
     * [boundTitleId] tracks which title is currently shown so [renderPeeks] only fades the card
     * in when the title it's showing actually changes, not on every redundant re-render. */
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

    /** Flat colors used when a slide has no real backdrop art (offline sample data). */
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

    /** Returns true (consumes the key) if the move was applied; false lets the event propagate. */
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

    /** Builds up to [PEEK_COUNT_MAX] peek cards into [ViewHolder.peekContainer], nearest-to-
     * farthest, each a genuinely smaller card than the active one (see the ratio tables above,
     * measured off a real Jio Hotstar screenshot) rather than a full-size card merely clipped to
     * look smaller. Sizing depends on the active card's own width, which is only known once it
     * has been laid out to its final size - `doOnLayout` (unlike a one-shot ViewTreeObserver
     * listener removed after its first, possibly-premature firing) guarantees the callback runs
     * after a genuine layout pass with real measurements, so peek sizing can't be computed off a
     * transient 0/undersized pass. Built once per row view and reused across every subsequent
     * bind/slide change. */
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
        // hero_peek_container and hero_card share the same left edge (both match_parent, no
        // start margin), so the active card's own left edge is at marginStart=0. Each peek is a
        // genuinely smaller card (see the ratio tables above), shifted right from THAT edge and
        // vertically centered against the active card - measured off a real screenshot, a peek's
        // top and bottom insets from the active card's edges are equal, confirming
        // CENTER_VERTICAL, not top-alignment. peek1 sits 100dp right of the active card's own left
        // edge, peek2 100dp right of peek1's position, and so on. Z-index must mirror carousel
        // position - active card on top, peek1 below it, peek2 below peek1, and so on. FrameLayout
        // draws children in ascending child-index order (index 0 draws first/lowest, the highest
        // index draws last/on top), so each new peek is inserted at index 0 - pushing every peek
        // added before it (which must stay visually on top) up in z-order - rather than appended,
        // which would put the farthest, smallest peek on top instead.
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
        // Peeks are built asynchronously after the first layout pass, so re-apply whatever slide
        // is already bound - otherwise a peek built after bindSlide() already ran would stay
        // empty until the next navigation.
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

    /** Slivers of the upcoming slides peeking past the current card's right edge, matching real
     * Jio Hotstar's layered hero banner: every visible peek shows its title's actual (dimmed)
     * backdrop art, not just the nearest one - real content throughout the stack, like the real
     * app. Images load from Glide's memory cache instantly here in the common case, since
     * [preloadUpcoming] already fetched them while this same item was still a peek itself.
     *
     * This is also what makes the peek count dynamic despite [PEEK_COUNT_MAX] built-up-front
     * views: a peek past the last real slide (row has fewer titles left than [PEEK_COUNT_MAX])
     * gets no item back from [itemAt] and is hidden here instead of showing an empty card. */
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
            // A peek stepping into a new depth (or appearing for the first time) fades its whole
            // card in from transparent, matching the real app's stack-shift animation - not just
            // an instant visibility flip, and not just the backdrop image crossfading underneath.
            peek.card.animate().cancel()
            peek.card.alpha = 0f
            peek.card.visibility = View.VISIBLE
            peek.card.animate().alpha(1f).setDuration(PEEK_FADE_DURATION_MS).start()
        }
    }

    /** Loads a title's backdrop without ever flashing the flat fallback color mid-navigation:
     * [CrossfadeImagePair] keeps whatever was showing on screen until the new image is decoded,
     * then crossfades - matching the real app's behavior of never showing a bare loading state,
     * only ever a photo (old or new). The flat palette color is only ever used on a pair's very
     * first load, when there's no prior image to hold onto. */
    private fun loadBackdrop(images: CrossfadeImagePair, item: Title) {
        val backdropUrl = item.backdropUrl
        if (backdropUrl == null) {
            images.setColor(paletteColorFor(item))
        } else {
            images.load(backdropUrl)
        }
    }

    /** Warms Glide's cache for the slides just past what's currently visible (both directions),
     * so by the time the user D-pads there the image is already decoded and swaps in instantly
     * instead of showing the flat placeholder color while it loads. */
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
