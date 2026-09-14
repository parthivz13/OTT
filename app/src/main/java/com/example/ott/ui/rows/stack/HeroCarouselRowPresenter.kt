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

        // How many upcoming slides peek past the current card's right edge - real Jio Hotstar
        // renders more than this in its underlying RecyclerView, but only ~2 are ever actually
        // visible before clipping off the row; 3 gives a bit of extra depth. Minimum of 2 keeps
        // the layered-stack look recognizable - a single peek reads as a mistake, not a stack.
        private const val PEEK_COUNT = 3

        // Each successive peek is a genuinely SMALLER card (not a same-size card just clipped
        // more), sized as this fraction of the CURRENT card's own width/height, measured off the
        // live Jio Hotstar app: peek depth 1 is ~77%w / ~87.5%h, depth 2 is ~54%w / ~75%h.
        private val PEEK_WIDTH_RATIO = floatArrayOf(0.771f, 0.537f, 0.40f)
        private val PEEK_HEIGHT_RATIO = floatArrayOf(0.875f, 0.750f, 0.65f)
        // How far each peek's start edge shifts right, as a fraction of the current card's width
        // - increasing per depth so cards visibly stagger rather than stacking on the same edge.
        private val PEEK_START_SHIFT_RATIO = floatArrayOf(0.30f, 0.52f, 0.70f)
    }

    /** One dynamically-built peek layer: the whole card that recedes further as [depth] grows. */
    class PeekViewHolder(val card: CardView, val images: CrossfadeImagePair)

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

    /** Builds [PEEK_COUNT] peek cards into [ViewHolder.peekContainer], nearest-to-farthest, each
     * a genuinely smaller card than the last (see the ratio tables above, derived from real Jio
     * Hotstar measurements) rather than a full-size card merely clipped to look smaller. Sizing
     * depends on the current card's own width, which is only known once it has been laid out to
     * its final size - `doOnLayout` (unlike a one-shot ViewTreeObserver listener removed after
     * its first, possibly-premature firing) guarantees the callback runs after a genuine layout
     * pass with real measurements, so peek sizing can't be computed off a transient 0/undersized
     * pass. Built once per row view and reused across every subsequent bind/slide change. */
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
        for (i in 0 until PEEK_COUNT) {
            val card = CardView(context).apply {
                radius = 14f * density
                setCardBackgroundColor(context.getColor(R.color.banner_background))
                cardElevation = 0f
            }
            val peekWidth = (cardWidth * PEEK_WIDTH_RATIO[i]).toInt()
            val peekHeight = (cardHeight * PEEK_HEIGHT_RATIO[i]).toInt()
            val params = FrameLayout.LayoutParams(peekWidth, peekHeight, android.view.Gravity.CENTER_VERTICAL)
            params.marginStart = (cardWidth * PEEK_START_SHIFT_RATIO[i]).toInt()
            card.layoutParams = params

            val imageContainer = FrameLayout(context)
            card.addView(
                imageContainer,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            val images = CrossfadeImagePair(imageContainer)

            holder.peekContainer.addView(card)
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
     * [preloadUpcoming] already fetched them while this same item was still a peek itself. */
    private fun renderPeeks(holder: ViewHolder, adapter: ObjectAdapter, index: Int) {
        holder.peeks.forEachIndexed { i, peek ->
            val depth = i + 1
            val peekItem = itemAt(adapter, index + depth)
            peek.card.visibility = if (peekItem != null) View.VISIBLE else View.INVISIBLE
            if (peekItem == null) return@forEachIndexed
            loadBackdrop(peek.images, peekItem)
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
        val preloadRange = (index - 1)..(index + PEEK_COUNT + 1)
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
