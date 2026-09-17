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

        // Slot-shift carousel motion: each of the 3 fixed slots (active, peek1, peek2) briefly
        // animates its transform to *look like* it occupies the neighboring slot, then snaps back
        // to identity and rebinds content - the underlying slot geometry (position/size/margins)
        // never actually changes, so this can never regress the peek-stack layout.
        private const val SHIFT_DURATION_MS = 260L
        private const val EXIT_SHIFT_EXTRA_DP = 120f
    }

    class PeekViewHolder(val card: CardView, val images: CrossfadeImagePair) {
        var boundTitleId: Int? = null
    }

    private class SlotGeometry(val left: Int, val top: Int, val width: Int, val height: Int)

    class ViewHolder(rootView: View) : RowPresenter.ViewHolder(rootView) {
        val card: CardView = rootView.findViewById(R.id.hero_card)
        val backdropContainer: FrameLayout = rootView.findViewById(R.id.hero_backdrop_container)
        val textBlock: View = rootView.findViewById(R.id.hero_text_block)
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
        var textBound: Boolean = false
        var isShifting: Boolean = false
        var pendingDelta: Int = 0
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
        holder.textBound = false
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
        // A shift is already animating - queue this key press (latest one wins) instead of
        // dropping it, so a burst of rapid presses still lands on the final requested slide.
        if (holder.isShifting) {
            val next = holder.selectedIndex + delta
            if (next < 0 || next >= adapter.size()) return false
            holder.pendingDelta = delta
            return true
        }
        val count = adapter.size()
        val next = holder.selectedIndex + delta
        if (next < 0 || next >= count) return false
        holder.selectedIndex = next
        if (holder.peeks.size == PEEK_COUNT_MAX) {
            playShiftAnimation(holder, delta) {
                bindSlide(holder, next)
                drainPendingAdvance(holder)
            }
        } else {
            bindSlide(holder, next)
        }
        return true
    }

    private fun drainPendingAdvance(holder: ViewHolder) {
        val delta = holder.pendingDelta
        if (delta == 0) return
        holder.pendingDelta = 0
        tryAdvance(holder, delta)
    }

    // Slides every slot's content one position toward the incoming direction: on RIGHT, active
    // content moves toward peek1's slot while fading out (it's leaving), peek1's content moves
    // toward peek2's slot, and peek2 fades out to make room for the new content bindSlide loads
    // once slots snap back. LEFT mirrors this toward the opposite edge. Slot geometry itself never
    // moves - only translationX/scale transforms, reset to identity before rebinding.
    private fun playShiftAnimation(holder: ViewHolder, delta: Int, onEnd: () -> Unit) {
        holder.isShifting = true
        val density = holder.card.context.resources.displayMetrics.density
        val activeGeom = SlotGeometry(0, 0, holder.card.width, holder.card.height)
        val peek1 = holder.peeks[0]
        val peek2 = holder.peeks[1]
        val peek1Geom = SlotGeometry(
            (peek1.card.layoutParams as FrameLayout.LayoutParams).marginStart,
            peek1.card.top, peek1.card.width, peek1.card.height
        )
        val peek2Geom = SlotGeometry(
            (peek2.card.layoutParams as FrameLayout.LayoutParams).marginStart,
            peek2.card.top, peek2.card.width, peek2.card.height
        )
        val exitShiftPx = (EXIT_SHIFT_EXTRA_DP * density).toInt()

        // RIGHT (delta > 0): active exits left and fades, peek1 moves left into the active slot,
        // peek2 moves left into peek1's slot - the vacated peek2 slot gets the new (4th) title
        // once slots snap back. LEFT (delta < 0): mirror image - active moves right into peek1's
        // slot and fades, peek1 moves right into peek2's slot, peek2 exits right and fades, making
        // room for the previous title to land in the active slot once slots snap back.
        val animators: List<android.animation.Animator> = if (delta > 0) {
            listOf(
                transformTo(peek1.card, peek1Geom, activeGeom, fadeOut = false),
                transformTo(peek2.card, peek2Geom, peek1Geom, fadeOut = false),
                fadeOutTranslate(holder.card, -exitShiftPx)
            )
        } else {
            listOf(
                transformTo(holder.card, activeGeom, peek1Geom, fadeOut = true),
                transformTo(peek1.card, peek1Geom, peek2Geom, fadeOut = false),
                fadeOutInPlace(peek2.card, exitShiftPx)
            )
        }

        android.animation.AnimatorSet().apply {
            playTogether(animators)
            duration = SHIFT_DURATION_MS
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    resetTransform(holder.card)
                    resetTransform(peek1.card)
                    resetTransform(peek2.card)
                    holder.isShifting = false
                    onEnd()
                }
            })
            start()
        }
    }

    private fun transformTo(view: View, from: SlotGeometry, to: SlotGeometry, fadeOut: Boolean): android.animation.Animator {
        val dx = (to.left + to.width / 2f) - (from.left + from.width / 2f)
        val scaleX = to.width / from.width.toFloat()
        val scaleY = to.height / from.height.toFloat()
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        val set = android.animation.AnimatorSet()
        val move = android.animation.ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, dx)
        val sx = android.animation.ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, scaleX)
        val sy = android.animation.ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, scaleY)
        val parts = mutableListOf<android.animation.Animator>(move, sx, sy)
        if (fadeOut) {
            parts += android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        }
        set.playTogether(parts)
        return set
    }

    private fun fadeOutInPlace(view: View, exitShiftPx: Int): android.animation.Animator {
        view.translationX = 0f
        val set = android.animation.AnimatorSet()
        set.playTogether(
            android.animation.ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, exitShiftPx.toFloat()),
            android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f)
        )
        return set
    }

    private fun fadeOutTranslate(view: View, dx: Int): android.animation.Animator {
        view.translationX = 0f
        val set = android.animation.AnimatorSet()
        set.playTogether(
            android.animation.ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, dx.toFloat()),
            android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        )
        return set
    }

    private fun resetTransform(view: View) {
        view.animate().cancel()
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
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

        applyTextForSlide(holder, item)

        loadBackdrop(holder.backdrop, item)
        renderDots(holder, adapter.size(), index)
        renderPeeks(holder, adapter, index)
        preloadUpcoming(holder, adapter, index)
    }

    // Crossfades the title/meta/overview block the same way the backdrop does, instead of
    // snapping the text instantly - swap happens at alpha 0, in between the two fades.
    private fun applyTextForSlide(holder: ViewHolder, item: Title) {
        holder.textBlock.animate().cancel()
        if (!holder.textBound) {
            holder.textBound = true
            bindTextViews(holder, item)
            return
        }
        holder.textBlock.animate()
            .alpha(0f)
            .setDuration(PEEK_FADE_DURATION_MS)
            .withEndAction {
                bindTextViews(holder, item)
                holder.textBlock.animate().alpha(1f).setDuration(PEEK_FADE_DURATION_MS).start()
            }
            .start()
    }

    private fun bindTextViews(holder: ViewHolder, item: Title) {
        holder.title.text = item.name
        holder.meta.text = buildMetaLine(item)
        holder.overview.text = item.overview

        if (item.badge != null) {
            holder.badge.text = item.badge
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }
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
            images.load(backdropUrl, paletteColorFor(item))
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
