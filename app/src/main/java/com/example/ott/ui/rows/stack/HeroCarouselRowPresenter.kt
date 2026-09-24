package com.example.ott.ui.rows.stack

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.doOnLayout
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.RowPresenter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.ott.R
import com.example.ott.data.model.Title
import com.example.ott.sott.base.CarouselFocusListener
import com.example.ott.sott.models.CustomAsset
import com.example.ott.sott.models.CustomKalturaAsset
import com.example.ott.sott.networking.RailCommonData
import com.example.ott.sott.utils.AppCommonMethod
import com.example.ott.sott.utils.SharedPrefHelper
import com.example.ott.sott.utils.constants.AppConstants
import com.example.ott.types.Asset

class HeroCarouselRowPresenter(
    var railCommonData: RailCommonData? = null,
    private val carouselFocusListener: CarouselFocusListener? = null
) : RowPresenter() {

    init {
        setHeaderPresenter(null)
        setSelectEffectEnabled(false)
    }

    override fun isUsingDefaultSelectEffect(): Boolean = false

    companion object {
        private const val TAG = "DataChecker"
        private const val AUTO_ROTATE_INTERVAL_MS = 14000L
        private const val FOCUS_HOLD_BEFORE_AUTOPLAY_MS = 600L
        private const val TRAILER_CROSSFADE_MS = 250L

        private const val DOT_SIZE_DP = 6
        private const val DOT_ACTIVE_WIDTH_DP = 24
        private const val DOT_SPACING_DP = 6

        private const val SHIFT_DURATION_MS = 280L

        private const val PEEK_1_SCALE = 0.88f
        private const val PEEK_2_SCALE = 0.76f

        private const val PEEK_1_ALPHA = 0.85f
        private const val PEEK_2_ALPHA = 0.55f

        private var sharedPlayer: ExoPlayer? = null
        private var activeHolder: ViewHolder? = null
        private val autoplayHandler = Handler(Looper.getMainLooper())
        private var pendingAutoplayRunnable: Runnable? = null

        @Synchronized
        private fun getOrCreatePlayer(context: Context): ExoPlayer {
            if (sharedPlayer == null) {
                sharedPlayer = ExoPlayer.Builder(context.applicationContext)
                    .build()
                    .apply {
                        volume = 1f
                        repeatMode = Player.REPEAT_MODE_ONE
                    }
            }
            return sharedPlayer!!
        }

        fun stopActiveVideo() {
            pendingAutoplayRunnable?.let { autoplayHandler.removeCallbacks(it) }
            pendingAutoplayRunnable = null

            sharedPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                setVideoTextureView(null)
            }
            activeHolder?.revertToPoster()
            activeHolder = null
        }

        fun releasePlayer() {
            stopActiveVideo()
            sharedPlayer?.release()
            sharedPlayer = null
        }
    }

    class CardSlotView(
        val card: CardView,
        val images: CrossfadeImagePair,
        val textureView: TextureView
    ) {
        var boundTitleId: Any? = null
    }

    private data class Slot(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val scale: Float,
        val alpha: Float,
        val elevation: Float
    )

    class ViewHolder(rootView: View) : RowPresenter.ViewHolder(rootView) {
        val stack: FrameLayout = rootView.findViewById(R.id.hero_stack)
        val cardsContainer: FrameLayout = rootView.findViewById(R.id.hero_cards_container)
        val card: FrameLayout = rootView.findViewById(R.id.hero_card)
        val focusBorder: View = rootView.findViewById(R.id.hero_focus_border)
        val textBlock: View = rootView.findViewById(R.id.hero_text_block)
        val badge: TextView = rootView.findViewById(R.id.hero_badge)
        val title: TextView = rootView.findViewById(R.id.hero_title)
        val contentRating: TextView = rootView.findViewById(R.id.hero_content_rating)
        val meta: TextView = rootView.findViewById(R.id.hero_meta)
        val qualityBadge: TextView = rootView.findViewById(R.id.hero_quality_badge)
        val overview: TextView = rootView.findViewById(R.id.hero_overview)
        val dotsContainer: LinearLayout = rootView.findViewById(R.id.hero_dots)

        val cardViews = mutableListOf<CardSlotView>()
        var slotsInitialized: Boolean = false

        // Asset list: populated per-position via bindAsset(holder, position, item)
        val items = mutableListOf<Any>()

        // Tracks the currently observed adapter so we can unregister on unbind
        var boundAdapter: ObjectAdapter? = null
        var dataObserver: ObjectAdapter.DataObserver? = null

        var activeRailData: RailCommonData? = null
        var selectedIndex: Int = 0
        var isShifting: Boolean = false
        var pendingDelta: Int = 0
        val autoRotateHandler = Handler(Looper.getMainLooper())
        var autoRotateRunnable: Runnable? = null

        var activePlayingTextureView: TextureView? = null
        var isTrailerPlaying: Boolean = false
        var activeShiftAnimSet: AnimatorSet? = null

        fun revertToPoster() {
            cardViews.forEach { slotView ->
                slotView.textureView.animate().cancel()
                slotView.textureView.alpha = 0f
                slotView.textureView.visibility = View.GONE
            }
            activePlayingTextureView = null
            isTrailerPlaying = false
        }
    }

    private val fallbackPalette = intArrayOf(
        0xFF0F2027.toInt(),
        0xFF203A43.toInt(),
        0xFF2C5364.toInt(),
        0xFF1A1A2E.toInt(),
        0xFF16213E.toInt(),
        0xFF0F3460.toInt(),
        0xFF1B263B.toInt(),
        0xFF0D1B2A.toInt()
    )

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val rootView = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_hero_carousel, parent, false)
        val viewHolder = ViewHolder(rootView)

        setupSlots(viewHolder)
        viewHolder.stack.doOnLayout {
            setupSlots(viewHolder)
        }

        // Tag hero_card with live position provider so MainActivity can verify carousel position
        viewHolder.card.setTag(R.id.hero_card, { viewHolder.selectedIndex })

        // D-Pad Remote Navigation
        viewHolder.focusBorder.alpha = if (viewHolder.selectedIndex == 0) 1f else 0f
        viewHolder.card.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    tryAdvance(viewHolder, 1)
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (viewHolder.selectedIndex == 0) {
                        // Already at first position (pos 0): open side nav and focus selected item!
                        val success = com.example.ott.ui.browse.MainActivity.instance?.focusSelectedNavItem()
                            ?: (viewHolder.card.rootView.findViewById<com.example.ott.ui.navigation.SideNavView>(R.id.side_nav_view)?.focusSelectedNavItem() ?: false)
                        success
                    } else {
                        // Not at first position (pos > 0): step backwards to the first position!
                        tryAdvance(viewHolder, -1)
                        true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    viewHolder.onItemViewClickedListener?.onItemClicked(
                        null,
                        currentItem(viewHolder),
                        viewHolder,
                        viewHolder.row
                    )
                    true
                }
                else -> false
            }
        }

        // Focus Border & Auto-Rotation Sync
        viewHolder.card.setOnFocusChangeListener { _, hasFocus ->
            viewHolder.focusBorder.animate().cancel()
            val targetAlpha = if (hasFocus) 1f else 0f
            viewHolder.focusBorder.animate()
                .alpha(targetAlpha)
                .setDuration(160L)
                .start()

            carouselFocusListener?.onCarouselFocusChanged(hasFocus)

            if (hasFocus) {
                stopAutoRotate(viewHolder)
                scheduleTrailer(viewHolder)
            } else {
                stopTrailer(viewHolder)
                startAutoRotate(viewHolder)
            }

            if (hasFocus) {
                viewHolder.onItemViewSelectedListener?.onItemSelected(
                    null,
                    currentItem(viewHolder),
                    viewHolder,
                    viewHolder.row
                )
            }
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

    /**
     * Called by Leanback when this row is bound or its data changes.
     *
     * Leanback passes the entire ListRow here — NOT a single asset.
     * We iterate the row's ObjectAdapter to populate every slot, then register
     * a DataObserver so adapter.replace(i, realAsset) called by
     * ListFragment.updateRow live-refreshes individual slots without a full re-bind.
     */
    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        val holder = vh as ViewHolder

        // 1. Detach any previously observed adapter
        holder.boundAdapter?.let { old ->
            holder.dataObserver?.let { obs -> old.unregisterObserver(obs) }
        }
        holder.boundAdapter = null
        holder.dataObserver = null

        // 2. Reset carousel state (clean slate)
        stopTrailer(holder)
        stopAutoRotate(holder)
        holder.activeShiftAnimSet?.cancel()
        holder.isShifting = false
        holder.pendingDelta = 0
        holder.selectedIndex = 0
        holder.items.clear()
        holder.cardViews.forEach { it.boundTitleId = null }

        // 3. Get the adapter from the ListRow Leanback passes us
        val adapter: ObjectAdapter? = when (item) {
            is ListRow -> item.adapter
            else -> null
        }

        if (adapter == null) {
            // Fallback: single direct asset (e.g. RailCommonData metadata)
            if (item is RailCommonData) {
                railCommonData = item
                holder.activeRailData = item
            }
        } else {
            // 4. Populate all slots from the adapter immediately
            for (i in 0 until adapter.size()) {
                adapter.get(i)?.let { asset -> bindAsset(holder, i, asset) }
            }

            // 5. Register a DataObserver so adapter.replace(i, realAsset) from
            //    ListFragment.updateRow live-refreshes just that slot.
            val observer = object : ObjectAdapter.DataObserver() {
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    for (i in positionStart until positionStart + itemCount) {
                        adapter.get(i)?.let { asset -> bindAsset(holder, i, asset) }
                    }
                    // Re-render dots if count changed
                    renderDots(holder, holder.items.size, holder.selectedIndex)
                }

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    for (i in positionStart until positionStart + itemCount) {
                        adapter.get(i)?.let { asset -> bindAsset(holder, i, asset) }
                    }
                    renderDots(holder, holder.items.size, holder.selectedIndex)
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    // Trim items list and rebind visible slots
                    val excess = holder.items.size - adapter.size()
                    if (excess > 0) repeat(excess) { holder.items.removeLastOrNull() }
                    rebindVisibleSlots(holder)
                    renderDots(holder, holder.items.size, holder.selectedIndex)
                }
            }
            adapter.registerObserver(observer)
            holder.boundAdapter = adapter
            holder.dataObserver = observer
        }

        // 6. Initialise / refresh slot positions
        if (holder.stack.isLaidOut && holder.stack.width > 0) {
            setupSlots(holder)
            bindInitialState(holder)
        } else {
            holder.stack.doOnLayout {
                setupSlots(holder)
                bindInitialState(holder)
            }
        }

        // 7. Begin auto-rotation
        startAutoRotate(holder)
    }

    /** Re-draws slot 0, 1, 2 from holder.items after a structural change. */
    private fun rebindVisibleSlots(holder: ViewHolder) {
        listOf(0, 1, 2).forEach { slotOffset ->
            val idx = holder.selectedIndex + slotOffset
            val asset = holder.items.getOrNull(idx) ?: return@forEach
            bindAsset(holder, idx, asset)
        }
    }

    override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
        val holder = vh as ViewHolder
        // Unregister DataObserver to prevent leaks
        holder.boundAdapter?.let { adapter ->
            holder.dataObserver?.let { obs -> adapter.unregisterObserver(obs) }
        }
        holder.boundAdapter = null
        holder.dataObserver = null
        stopTrailer(holder)
        stopAutoRotate(holder)
        holder.activeShiftAnimSet?.cancel()
        holder.items.clear()
        super.onUnbindRowViewHolder(vh)
    }

    fun bindAsset(holder: ViewHolder, position: Int, item: Any?) {
        if (item == null) return
        while (holder.items.size <= position) {
            holder.items.add(item)
        }
        holder.items[position] = item

        if (!holder.slotsInitialized) return
        val activeIdx = holder.selectedIndex
        when (position) {
            activeIdx -> {
                if (holder.cardViews.isNotEmpty()) {
                    loadAsset(holder.cardViews[0], item, isFocused = true)
                    bindTextViews(holder, item)
                    scheduleTrailer(holder)
                }
            }
            activeIdx + 1 -> {
                if (holder.cardViews.size > 1) {
                    loadAsset(holder.cardViews[1], item, isFocused = false)
                    holder.cardViews[1].card.visibility = View.VISIBLE
                }
            }
            activeIdx + 2 -> {
                if (holder.cardViews.size > 2) {
                    loadAsset(holder.cardViews[2], item, isFocused = false)
                    holder.cardViews[2].card.visibility = View.VISIBLE
                }
            }
        }
        renderDots(holder, holder.items.size, activeIdx)
    }

    private fun setupSlots(holder: ViewHolder) {
        val density = holder.stack.context.resources.displayMetrics.density
        val totalW = if (holder.stack.width > 0) {
            holder.stack.width
        } else {
            val screenW = holder.stack.context.resources.displayMetrics.widthPixels
            screenW - ((64 + 12) * density).toInt()
        }
        val totalH = if (holder.stack.height > 0) {
            holder.stack.height
        } else {
            (390 * density).toInt()
        }

        val activeW = (totalW * 0.788f).toInt()
        val activeH = totalH

        val cardParams = holder.card.layoutParams as FrameLayout.LayoutParams
        cardParams.width = activeW
        cardParams.height = activeH
        cardParams.marginEnd = 0
        holder.card.layoutParams = cardParams

        if (holder.cardViews.isEmpty()) {
            val context = holder.cardsContainer.context
            for (i in 0 until 4) {
                val card = CardView(context).apply {
                    radius = 18f * density
                    setCardBackgroundColor(context.getColor(R.color.hotstar_card_surface))
                    cardElevation = 2f * density
                    pivotX = 0f
                    pivotY = 0f
                }

                val container = FrameLayout(context)
                card.addView(
                    container,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                val images = CrossfadeImagePair(container)

                val textureView = TextureView(context).apply {
                    alpha = 0f
                    visibility = View.GONE
                }
                card.addView(
                    textureView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )

                val vignette = View(context).apply {
                    setBackgroundResource(R.drawable.card_left_vignette_scrim)
                }
                card.addView(vignette, FrameLayout.LayoutParams((activeW * 0.60f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))

                val bottomScrim = View(context).apply {
                    setBackgroundResource(R.drawable.card_bottom_scrim)
                }
                card.addView(bottomScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activeH * 0.65f).toInt(), Gravity.BOTTOM))

                val lp = FrameLayout.LayoutParams(activeW, activeH)
                card.layoutParams = lp

                holder.cardsContainer.addView(card, 0)
                holder.cardViews.add(CardSlotView(card, images, textureView))
            }
        } else {
            for (slotView in holder.cardViews) {
                val lp = slotView.card.layoutParams as? FrameLayout.LayoutParams ?: continue
                if (lp.width != activeW || lp.height != activeH) {
                    lp.width = activeW
                    lp.height = activeH
                    slotView.card.layoutParams = lp
                }
            }
        }

        holder.slotsInitialized = true
    }

    private fun getSlots(holder: ViewHolder): List<Slot> {
        val density = holder.stack.context.resources.displayMetrics.density
        val totalW = if (holder.stack.width > 0) {
            holder.stack.width
        } else {
            val screenW = holder.stack.context.resources.displayMetrics.widthPixels
            screenW - ((64 + 12) * density).toInt()
        }
        val totalH = if (holder.stack.height > 0) {
            holder.stack.height
        } else {
            (390 * density).toInt()
        }

        val activeW = (totalW * 0.788f).toInt()
        val activeH = totalH

        val rightMargin = (36f * density).toInt()
        val remainingW = (totalW - activeW - rightMargin).coerceAtLeast(0)
        val peekVisible = (remainingW / 2).coerceAtLeast((70f * density).toInt())

        val peek1W = (activeW * PEEK_1_SCALE).toInt()
        val peek1H = (activeH * PEEK_1_SCALE).toInt()
        val peek1Left = activeW + peekVisible - peek1W
        val peek1Top = (activeH - peek1H) / 2

        val peek2W = (activeW * PEEK_2_SCALE).toInt()
        val peek2H = (activeH * PEEK_2_SCALE).toInt()
        val peek2Left = activeW + (2 * peekVisible) - peek2W
        val peek2Top = (activeH - peek2H) / 2

        val exitShift = (21f * density).toInt()
        val rightExitShift = (peekVisible * 0.6f).toInt()

        return listOf(
            Slot(-exitShift, 0, activeW, activeH, 0.96f, 0f, 4f * density),
            Slot(0, 0, activeW, activeH, 1.0f, 1.0f, 6f * density),
            Slot(peek1Left, peek1Top, peek1W, peek1H, PEEK_1_SCALE, PEEK_1_ALPHA, 4f * density),
            Slot(peek2Left, peek2Top, peek2W, peek2H, PEEK_2_SCALE, PEEK_2_ALPHA, 2f * density),
            Slot(peek2Left + rightExitShift, peek2Top, peek2W, peek2H, 0.68f, 0f, 1f * density)
        )
    }

    private fun applySlot(view: View, slot: Slot) {
        view.pivotX = 0f
        view.pivotY = 0f
        view.translationX = slot.left.toFloat()
        view.translationY = slot.top.toFloat()
        view.scaleX = slot.scale
        view.scaleY = slot.scale
        view.alpha = slot.alpha
        (view as? CardView)?.cardElevation = slot.elevation
    }

    private fun bindInitialState(holder: ViewHolder) {
        val totalCount = holder.items.size
        if (totalCount == 0) return
        if (holder.cardViews.size < 4) return
        val slots = getSlots(holder)

        val activeItem = itemAt(holder, holder.selectedIndex) ?: return

        val v0 = holder.cardViews[0]
        val v1 = holder.cardViews[1]
        val v2 = holder.cardViews[2]
        val vBuffer = holder.cardViews[3]

        applySlot(v0.card, slots[1])
        loadAsset(v0, activeItem, isFocused = true)
        v0.card.visibility = View.VISIBLE

        val item1 = itemAt(holder, holder.selectedIndex + 1)
        if (item1 != null) {
            applySlot(v1.card, slots[2])
            loadAsset(v1, item1, isFocused = false)
            v1.card.visibility = View.VISIBLE
        } else {
            v1.card.visibility = View.INVISIBLE
        }

        val item2 = itemAt(holder, holder.selectedIndex + 2)
        if (item2 != null) {
            applySlot(v2.card, slots[3])
            loadAsset(v2, item2, isFocused = false)
            v2.card.visibility = View.VISIBLE
        } else {
            v2.card.visibility = View.INVISIBLE
        }

        vBuffer.card.visibility = View.INVISIBLE

        vBuffer.card.bringToFront()
        v2.card.bringToFront()
        v1.card.bringToFront()
        v0.card.bringToFront()
        holder.card.bringToFront()

        bindTextViews(holder, activeItem)
        renderDots(holder, totalCount, holder.selectedIndex)
        preloadUpcoming(holder, holder.selectedIndex)
        scheduleTrailer(holder)
    }

    private fun tryAdvance(holder: ViewHolder, delta: Int): Boolean {
        val count = holder.items.size
        if (count <= 0) return false

        if (holder.isShifting) {
            val next = holder.selectedIndex + delta
            if (next in 0 until count) {
                holder.pendingDelta = delta
            }
            return true
        }

        val next = holder.selectedIndex + delta
        if (next >= count && delta > 0) {
            return true
        }
        if (next < 0) return false

        holder.selectedIndex = next
        stopTrailer(holder)
        playJioHotstarShift(holder, delta) {
            drainPendingAdvance(holder)
        }
        return true
    }

    private fun drainPendingAdvance(holder: ViewHolder) {
        val delta = holder.pendingDelta
        if (delta == 0) return
        holder.pendingDelta = 0
        tryAdvance(holder, delta)
    }

    private fun playJioHotstarShift(holder: ViewHolder, delta: Int, onEnd: () -> Unit) {
        val count = holder.items.size
        if (count <= 0) return
        holder.isShifting = true
        val slots = getSlots(holder)

        val v0 = holder.cardViews[0]
        val v1 = holder.cardViews[1]
        val v2 = holder.cardViews[2]
        val vBuffer = holder.cardViews[3]

        val nextIndex = holder.selectedIndex
        val animators = mutableListOf<Animator>()

        if (delta > 0) {
            val incomingItem = itemAt(holder, nextIndex + 2)
            if (incomingItem != null) {
                applySlot(vBuffer.card, slots[3])
                vBuffer.card.alpha = PEEK_2_ALPHA
                vBuffer.images.setColor(holder.stack.context.getColor(R.color.hotstar_card_surface))
                vBuffer.card.visibility = View.VISIBLE
            } else {
                vBuffer.card.visibility = View.INVISIBLE
            }

            v0.card.visibility = View.VISIBLE
            animators.add(animateBetweenSlots(v0.card, slots[1], slots[0]))

            v1.card.visibility = View.VISIBLE
            animators.add(animateBetweenSlots(v1.card, slots[2], slots[1]))

            val itemAtPeek1 = itemAt(holder, nextIndex + 1)
            if (itemAtPeek1 != null) {
                v2.card.visibility = View.VISIBLE
                animators.add(animateBetweenSlots(v2.card, slots[3], slots[2]))
            } else {
                v2.card.visibility = View.INVISIBLE
            }

            vBuffer.card.bringToFront()
            v2.card.bringToFront()
            v1.card.bringToFront()
            v0.card.bringToFront()
            holder.card.bringToFront()

            val animSet = AnimatorSet().apply {
                playTogether(animators)
                duration = SHIFT_DURATION_MS
                interpolator = DecelerateInterpolator(1.8f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        v0.card.visibility = View.INVISIBLE

                        holder.cardViews[0] = v1
                        holder.cardViews[1] = v2
                        holder.cardViews[2] = vBuffer
                        holder.cardViews[3] = v0

                        finishShiftSettle(holder, nextIndex, onEnd)
                    }
                })
            }
            holder.activeShiftAnimSet = animSet
            animSet.start()
        } else {
            val incomingItem = itemAt(holder, nextIndex)
            if (incomingItem != null) {
                applySlot(vBuffer.card, slots[0])
                loadAsset(vBuffer, incomingItem, isFocused = true)
                vBuffer.card.visibility = View.VISIBLE
                animators.add(animateBetweenSlots(vBuffer.card, slots[0], slots[1]))
            }

            v0.card.visibility = View.VISIBLE
            animators.add(animateBetweenSlots(v0.card, slots[1], slots[2]))

            val itemAtPeek2 = itemAt(holder, nextIndex + 2)
            if (itemAtPeek2 != null) {
                v1.card.visibility = View.VISIBLE
                animators.add(animateBetweenSlots(v1.card, slots[2], slots[3]))

                if (v2.card.visibility == View.VISIBLE) {
                    applySlot(v2.card, slots[3])
                    v2.card.alpha = PEEK_2_ALPHA
                    v2.card.visibility = View.VISIBLE
                }
            } else {
                v1.card.visibility = View.INVISIBLE
                if (v2.card.visibility == View.VISIBLE) {
                    animators.add(animateBetweenSlots(v2.card, slots[3], slots[4]))
                }
            }

            v2.card.bringToFront()
            v1.card.bringToFront()
            v0.card.bringToFront()
            if (incomingItem != null) {
                vBuffer.card.bringToFront()
            }
            holder.card.bringToFront()

            val animSet = AnimatorSet().apply {
                playTogether(animators)
                duration = SHIFT_DURATION_MS
                interpolator = DecelerateInterpolator(1.8f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        v2.card.visibility = View.INVISIBLE

                        holder.cardViews[0] = vBuffer
                        holder.cardViews[1] = v0
                        holder.cardViews[2] = v1
                        holder.cardViews[3] = v2

                        finishShiftSettle(holder, nextIndex, onEnd)
                    }
                })
            }
            holder.activeShiftAnimSet = animSet
            animSet.start()
        }
    }

    private fun finishShiftSettle(holder: ViewHolder, activeIndex: Int, onEnd: () -> Unit) {
        val totalCount = holder.items.size
        val v0 = holder.cardViews[0]
        val v1 = holder.cardViews[1]
        val v2 = holder.cardViews[2]
        val vBuffer = holder.cardViews[3]

        vBuffer.card.visibility = View.INVISIBLE
        val item1 = itemAt(holder, activeIndex + 1)
        if (item1 != null) {
            loadAsset(v1, item1, isFocused = false)
            v1.card.visibility = View.VISIBLE
        } else {
            v1.card.visibility = View.INVISIBLE
        }

        val item2 = itemAt(holder, activeIndex + 2)
        if (item2 != null) {
            loadAsset(v2, item2, isFocused = false)
            v2.card.visibility = View.VISIBLE
        } else {
            v2.card.visibility = View.INVISIBLE
        }

        vBuffer.card.bringToFront()
        v2.card.bringToFront()
        v1.card.bringToFront()
        v0.card.bringToFront()
        holder.card.bringToFront()

        val activeItem = itemAt(holder, activeIndex)
        if (activeItem != null) {
            bindTextViews(holder, activeItem)
        }

        renderDots(holder, totalCount, activeIndex)
        preloadUpcoming(holder, activeIndex)

        holder.isShifting = false
        scheduleTrailer(holder)
        onEnd()
    }

    private fun animateBetweenSlots(view: View, from: Slot, to: Slot): Animator {
        view.translationX = from.left.toFloat()
        view.translationY = from.top.toFloat()
        view.scaleX = from.scale
        view.scaleY = from.scale
        view.alpha = from.alpha

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(view, View.TRANSLATION_X, from.left.toFloat(), to.left.toFloat()),
            ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, from.top.toFloat(), to.top.toFloat()),
            ObjectAnimator.ofFloat(view, View.SCALE_X, from.scale, to.scale),
            ObjectAnimator.ofFloat(view, View.SCALE_Y, from.scale, to.scale),
            ObjectAnimator.ofFloat(view, View.ALPHA, from.alpha, to.alpha)
        )
        return set
    }

    data class ResolvedAsset(
        val id: Any,
        val name: String,
        val description: String,
        val imageUrl: String?,
        val trailerUrl: String?,
        val contentRating: String,
        val metaLine: String,
        val qualityTag: String,
        val badge: String?,
        val showBasicDetails: Boolean = true
    )

    private fun resolveAsset(context: Context, item: Any?, isFocused: Boolean = false): ResolvedAsset? {
        if (item == null) return null
        return when (item) {
            is CustomAsset -> bindCustomAsset(item)
            is CustomKalturaAsset -> bindCustomKalturaAsset(item)
            is com.example.ott.EnveuCategoryServices.Asset -> bindEnveuAsset(item)
            is Asset -> bindKalturaAsset(context, item, isFocused)
            is Title -> bindTitle(item)
            else -> {
                ResolvedAsset(
                    id = item.hashCode(),
                    name = item.toString(),
                    description = "",
                    imageUrl = null,
                    trailerUrl = null,
                    contentRating = "",
                    metaLine = "",
                    qualityTag = "",
                    badge = null,
                    showBasicDetails = true
                )
            }
        }
    }

    private fun bindCustomAsset(item: CustomAsset): ResolvedAsset {
        return ResolvedAsset(
            id = item.id ?: item.hashCode(),
            name = "",
            description = "",
            imageUrl = item.images?.firstOrNull()?.url,
            trailerUrl = null,
            contentRating = "",
            metaLine = "",
            qualityTag = "",
            badge = null,
            showBasicDetails = false
        )
    }

    private fun bindCustomKalturaAsset(item: CustomKalturaAsset): ResolvedAsset {
        return ResolvedAsset(
            id = item.id ?: item.hashCode(),
            name = item.name.orEmpty(),
            description = "",
            imageUrl = item.images?.firstOrNull()?.url,
            trailerUrl = null,
            contentRating = "",
            metaLine = "",
            qualityTag = "",
            badge = null,
            showBasicDetails = true
        )
    }

    private fun bindEnveuAsset(item: com.example.ott.EnveuCategoryServices.Asset): ResolvedAsset {
        return ResolvedAsset(
            id = item.id ?: item.hashCode(),
            name = item.name.orEmpty(),
            description = "",
            imageUrl = item.images?.firstOrNull()?.url,
            trailerUrl = null,
            contentRating = "",
            metaLine = "",
            qualityTag = "",
            badge = null,
            showBasicDetails = true
        )
    }

    private fun bindKalturaAsset(
        context: Context,
        asset: Asset,
        isFocused: Boolean = false
    ): ResolvedAsset {
        val seasonEpisode = AppCommonMethod.addSeasonAndEpisodeNo(asset)
        val descriptionText = if (!seasonEpisode.isNullOrEmpty()) {
            seasonEpisode
        } else {
            AppCommonMethod.getMetaByTag(asset, "LongSummary").orEmpty()
        }

        val initialTrailer = asset.mediaFiles?.firstOrNull {
            it.type?.equals("Preview", ignoreCase = true) == true
        }?.url

        val externalId = asset.externalId.orEmpty()
        val trailerFromPref = if (externalId.isNotEmpty()) {
            SharedPrefHelper.getInstance().getTrailerFromMap(context, externalId)?.toString()
        } else {
            null
        }
        val trailerUrl = if (!trailerFromPref.isNullOrEmpty()) trailerFromPref else initialTrailer

        val metadataText = AppCommonMethod.getMetas(asset)

        val duration = asset.mediaFiles?.firstOrNull()?.duration ?: 0L
        val hours = (duration / 3600).toInt()
        val minutes = ((duration % 3600) / 60).toInt()
        val durationText = when {
            hours > 0 && minutes > 0 -> "• ${hours}h ${minutes}m"
            hours > 0 -> "• ${hours}h"
            minutes > 0 -> "• ${minutes}m"
            else -> ""
        }

        val qualities = AppCommonMethod.getQualities(asset)
        val parentalRating = AppCommonMethod.getTagsFromAsset(asset, AppConstants.PARENTAL_RATING)

        val ratingData = AppCommonMethod.getMetaByTag(asset, AppConstants.star_rating)
        val ratingFormatted = if (!ratingData.isNullOrEmpty() && ratingData != "0") {
            ratingData.toDoubleOrNull()?.let { "★ " + String.format("%.1f", it) }.orEmpty()
        } else ""

        val metaParts = listOfNotNull(
            metadataText.takeIf { it.isNotBlank() },
            durationText.takeIf { it.isNotBlank() },
            ratingFormatted.takeIf { it.isNotBlank() }
        )

        val ratio = if (isFocused) AppConstants.RATIO_16X9_cover else AppConstants.RATIO_2X3
        val density = context.resources.displayMetrics.density
        val width = if (isFocused) (880 * density).toInt() else (260 * density).toInt()
        val height = if (isFocused) (390 * density).toInt() else (390 * density).toInt()

        val imageUrl = asset.images?.takeIf { it.isNotEmpty() }?.let {
            AppCommonMethod.getCardwiseImage(it, ratio, width, height)
        } ?: asset.images?.firstOrNull()?.url

        return ResolvedAsset(
            id = asset.id ?: asset.hashCode(),
            name = asset.name.orEmpty(),
            description = descriptionText,
            imageUrl = imageUrl,
            trailerUrl = trailerUrl,
            contentRating = parentalRating,
            metaLine = metaParts.joinToString("  •  "),
            qualityTag = qualities.joinToString(" • "),
            badge = null,
            showBasicDetails = true
        )
    }

    private fun bindTitle(title: Title): ResolvedAsset {
        return ResolvedAsset(
            id = title.id,
            name = title.name,
            description = if (!title.seasonEpisode.isNullOrEmpty()) title.seasonEpisode else title.overview,
            imageUrl = title.backdropUrl ?: title.posterUrl,
            trailerUrl = title.trailerUrl ?: title.videoUrl,
            contentRating = title.contentRating,
            metaLine = buildMetaLineForTitle(title),
            qualityTag = title.qualityTag,
            badge = title.badge,
            showBasicDetails = true
        )
    }

    private fun loadAsset(slotView: CardSlotView, item: Any?, isFocused: Boolean = false) {
        val asset = resolveAsset(slotView.card.context, item, isFocused)
        if (asset == null) {
            slotView.boundTitleId = null
            slotView.images.setColor(fallbackPalette[0])
            return
        }
        slotView.boundTitleId = asset.id
        val backdropUrl = asset.imageUrl
        if (backdropUrl == null) {
            slotView.images.setColor(paletteColorFor(asset.id))
        } else {
            slotView.images.load(backdropUrl, paletteColorFor(asset.id))
        }
    }

    private fun bindTextViews(holder: ViewHolder, item: Any?) {
        val asset = resolveAsset(holder.stack.context, item, true)
        if (asset == null || !asset.showBasicDetails) {
            holder.title.text = ""
            holder.overview.visibility = View.GONE
            holder.contentRating.visibility = View.GONE
            holder.meta.visibility = View.GONE
            holder.qualityBadge.visibility = View.GONE
            holder.badge.visibility = View.GONE
            return
        }

        holder.title.text = asset.name

        if (asset.description.isNotEmpty()) {
            holder.overview.text = asset.description
            holder.overview.visibility = View.VISIBLE
        } else {
            holder.overview.visibility = View.GONE
        }

        if (asset.contentRating.isNotEmpty()) {
            holder.contentRating.text = asset.contentRating
            holder.contentRating.visibility = View.VISIBLE
        } else {
            holder.contentRating.visibility = View.GONE
        }

        if (asset.metaLine.isNotEmpty()) {
            holder.meta.text = asset.metaLine
            holder.meta.visibility = View.VISIBLE
        } else {
            holder.meta.visibility = View.GONE
        }

        if (asset.qualityTag.isNotEmpty()) {
            holder.qualityBadge.text = asset.qualityTag
            holder.qualityBadge.visibility = View.VISIBLE
        } else {
            holder.qualityBadge.visibility = View.GONE
        }

        if (!asset.badge.isNullOrBlank()) {
            holder.badge.text = asset.badge
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }
    }

    private fun preloadUpcoming(holder: ViewHolder, index: Int) {
        val context = holder.stack.context
        val preloadRange = (index - 1)..(index + 4)
        for (i in preloadRange) {
            if (i == index) continue
            val item = itemAt(holder, i) ?: continue
            val url = resolveAsset(context, item, false)?.imageUrl ?: continue
            Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload()
        }
    }

    private fun itemAt(holder: ViewHolder, index: Int): Any? {
        return if (index in 0 until holder.items.size) holder.items[index] else null
    }

    private fun currentItem(holder: ViewHolder): Any? = itemAt(holder, holder.selectedIndex)

    private fun paletteColorFor(id: Any?): Int {
        val hash = id?.hashCode() ?: 0
        val index = Math.abs(hash) % fallbackPalette.size
        return fallbackPalette[index]
    }

    private fun formatDuration(durationSeconds: Int): String {
        if (durationSeconds <= 0) return ""
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> ""
        }
    }

    private fun buildMetaLineForTitle(title: Title): String {
        val durationText = formatDuration(title.durationSeconds)
        val parts = listOfNotNull(
            title.year.takeIf { it.isNotBlank() },
            title.genre.takeIf { it.isNotBlank() },
            durationText.takeIf { it.isNotBlank() } ?: title.durationOrSeasons.takeIf { it.isNotBlank() },
            if (title.rating > 0.0) "★ %.1f".format(title.rating) else null
        )
        return parts.joinToString("  •  ")
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
                dot.setBackgroundResource(if (i == activeIndex) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive)
                holder.dotsContainer.addView(dot)
            }
        }

        for (i in 0 until holder.dotsContainer.childCount) {
            val dot = holder.dotsContainer.getChildAt(i)
            val params = dot.layoutParams as LinearLayout.LayoutParams
            val targetWidth = if (i == activeIndex) activeWidth else dotSize

            if (params.width != targetWidth) {
                val startWidth = params.width
                ValueAnimator.ofInt(startWidth, targetWidth).apply {
                    duration = 200L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { anim ->
                        params.width = anim.animatedValue as Int
                        dot.layoutParams = params
                    }
                    start()
                }
            }

            dot.setBackgroundResource(if (i == activeIndex) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive)
        }
    }

    private fun startAutoRotate(holder: ViewHolder) {
        stopAutoRotate(holder)
        val activeData = holder.activeRailData ?: railCommonData
        val autoRotateEnabled = activeData?.screenWidget?.autoRotate ?: true
        if (!autoRotateEnabled) return

        val duration = activeData?.screenWidget?.autoRotateDuration?.let { if (it > 0) it else null }
            ?: (AUTO_ROTATE_INTERVAL_MS / 1000).toInt()
        val intervalMs = duration * 1000L

        val runnable = object : Runnable {
            override fun run() {
                val count = holder.items.size
                if (count > 1 && !holder.card.hasFocus() && !holder.isTrailerPlaying) {
                    val next = (holder.selectedIndex + 1) % count
                    holder.selectedIndex = next
                    playJioHotstarShift(holder, 1) {
                        drainPendingAdvance(holder)
                    }
                }
                holder.autoRotateHandler.postDelayed(this, intervalMs)
            }
        }
        holder.autoRotateRunnable = runnable
        holder.autoRotateHandler.postDelayed(runnable, intervalMs)
    }

    private fun stopAutoRotate(holder: ViewHolder) {
        holder.autoRotateRunnable?.let { holder.autoRotateHandler.removeCallbacks(it) }
        holder.autoRotateRunnable = null
    }

    // ========================================================================================
    // Hardware-Accelerated Trailer Video Playback (Media3 ExoPlayer + TextureView)
    // ========================================================================================

    private fun scheduleTrailer(holder: ViewHolder) {
        cancelPendingAutoplay()

        if (holder.isShifting || holder.cardViews.isEmpty()) return
        val currentItem = itemAt(holder, holder.selectedIndex) ?: return
        val trailerUrl = resolveAsset(holder.stack.context, currentItem, true)?.trailerUrl
        if (trailerUrl.isNullOrEmpty()) return

        val runnable = Runnable {
            if (!holder.card.hasFocus()) return@Runnable
            if (holder.isShifting || holder.cardViews.isEmpty()) return@Runnable
            startTrailerPlayback(holder, trailerUrl)
        }
        pendingAutoplayRunnable = runnable
        autoplayHandler.postDelayed(runnable, FOCUS_HOLD_BEFORE_AUTOPLAY_MS)
    }

    private fun cancelPendingAutoplay() {
        pendingAutoplayRunnable?.let { autoplayHandler.removeCallbacks(it) }
        pendingAutoplayRunnable = null
    }

    private fun startTrailerPlayback(holder: ViewHolder, trailerUrl: String) {
        if (holder.isShifting || holder.cardViews.isEmpty()) return
        val activeCardSlot = holder.cardViews[0]
        val textureView = activeCardSlot.textureView
        val context = holder.stack.context

        if (activeHolder !== holder) {
            activeHolder?.revertToPoster()
        }
        activeHolder = holder

        val player = getOrCreatePlayer(context)
        player.stop()
        player.clearMediaItems()
        player.setVideoTextureView(textureView)
        player.setMediaItem(MediaItem.fromUri(trailerUrl))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.playWhenReady = true

        holder.activePlayingTextureView = textureView
        holder.isTrailerPlaying = true

        textureView.visibility = View.VISIBLE
        textureView.alpha = 0f
        textureView.animate().cancel()
        textureView.animate()
            .alpha(1f)
            .setDuration(TRAILER_CROSSFADE_MS)
            .start()
    }

    private fun stopTrailer(holder: ViewHolder, resetAlpha: Boolean = true) {
        cancelPendingAutoplay()
        if (activeHolder === holder) {
            sharedPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                setVideoTextureView(null)
            }
            activeHolder = null
        }
        if (resetAlpha) {
            holder.revertToPoster()
        }
    }
}
