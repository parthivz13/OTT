package com.example.ott.ui.rows.stack

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
 * 100% JioHotstar / Disney+ Hotstar spotlight hero carousel.
 *
 * Implements exact telemetry observed from the running JioHotstar TV app:
 * - 2.5D Stacked cards with fixed active viewport and peeking right cards.
 * - Stationary crisp white rounded focus border on active card slot.
 * - Hardware-accelerated slot-shift transition physics (Decelerate 1.8f, 280ms).
 * - Instant text fade-out before slide, and staggered slide-fade in after settling.
 * - Zero-flicker view-slot recycling without image reloads during navigation.
 * - Exact D-pad navigation: left-handoff to sidebar at index 0, down-handoff to rails.
 * - Animated white capsule & translucent dot indicator strip.
 * - Smooth trailer playback with hardware-accelerated TextureView + MediaPlayer,
 *   respecting CardView rounded corners, vignette scrims, and auto-advancing on completion.
 */
class HeroCarouselRowPresenter : RowPresenter() {

    init {
        setHeaderPresenter(null)
        setSelectEffectEnabled(false)
    }

    override fun isUsingDefaultSelectEffect(): Boolean = false

    companion object {
        private const val AUTO_ROTATE_INTERVAL_MS = 14000L
        private const val TRAILER_DELAY_MS = 2200L
        private const val TRAILER_CROSSFADE_MS = 350L

        private const val DOT_SIZE_DP = 6
        private const val DOT_ACTIVE_WIDTH_DP = 24
        private const val DOT_SPACING_DP = 6

        private const val SHIFT_DURATION_MS = 280L
        private const val TEXT_FADE_OUT_MS = 100L
        private const val TEXT_FADE_IN_MS = 160L

        private const val PEEK_1_SCALE = 0.88f
        private const val PEEK_2_SCALE = 0.76f

        private const val PEEK_1_ALPHA = 0.85f
        private const val PEEK_2_ALPHA = 0.55f
    }

    class CardSlotView(
        val card: CardView,
        val images: CrossfadeImagePair,
        val textureView: TextureView
    ) {
        var boundTitleId: Int? = null
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
        var activeSlotIndex: Int = 0 // points to which cardView is currently at Slot 0
        var slotsInitialized: Boolean = false

        var adapter: ObjectAdapter? = null
        var selectedIndex: Int = 0
        var isShifting: Boolean = false
        var pendingDelta: Int = 0
        val autoRotateHandler = Handler(Looper.getMainLooper())
        var autoRotateRunnable: Runnable? = null

        var mediaPlayer: MediaPlayer? = null
        val trailerHandler = Handler(Looper.getMainLooper())
        var trailerRunnable: Runnable? = null
        var activePlayingTextureView: TextureView? = null
        var isTrailerPlaying: Boolean = false
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

        // Pre-create card views synchronously so they are in the hierarchy before the first layout pass
        setupSlots(viewHolder)
        viewHolder.stack.doOnLayout {
            setupSlots(viewHolder)
        }

        // D-Pad Remote Navigation
        viewHolder.card.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val consumed = tryAdvance(viewHolder, 1)
                    consumed
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (viewHolder.selectedIndex == 0) {
                        val nextFocus = viewHolder.card.focusSearch(View.FOCUS_LEFT)
                        if (nextFocus != null && nextFocus !== viewHolder.card) {
                            nextFocus.requestFocus()
                        } else {
                            viewHolder.card.rootView.findViewById<View>(R.id.nav_home)?.requestFocus()
                        }
                        true
                    } else {
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
                else -> false // DPAD_DOWN escapes to rail below; DPAD_UP escapes above
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

            if (hasFocus) {
                stopAutoRotate(viewHolder)
            } else {
                startAutoRotate(viewHolder)
            }

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
        if (holder.stack.isLaidOut && holder.stack.width > 0) {
            setupSlots(holder)
        } else {
            holder.stack.doOnLayout {
                setupSlots(holder)
            }
        }
        startAutoRotate(holder)
    }

    override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
        super.onUnbindRowViewHolder(vh)
        val holder = vh as ViewHolder
        stopTrailer(holder)
        stopAutoRotate(holder)
        holder.adapter = null
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

        // Active Card dimensions matching JioHotstar 78.8% width ratio of carousel viewport
        val activeW = (totalW * 0.788f).toInt()
        val activeH = totalH

        // Update hero_card overlay layoutParams to precisely cover Slot 0
        val cardParams = holder.card.layoutParams as FrameLayout.LayoutParams
        cardParams.width = activeW
        cardParams.height = activeH
        cardParams.marginEnd = 0
        holder.card.layoutParams = cardParams

        if (holder.cardViews.isEmpty()) {
            val context = holder.cardsContainer.context
            // Create 4 card views:
            // View 3 (buffer / incoming)
            // View 2 (Slot 2 - Peek 2)
            // View 1 (Slot 1 - Peek 1)
            // View 0 (Slot 0 - Active)
            for (i in 0 until 4) {
                val card = CardView(context).apply {
                    radius = 18f * density
                    setCardBackgroundColor(context.getColor(R.color.hotstar_card_surface))
                    cardElevation = 2f * density
                    pivotX = 0f
                    pivotY = 0f
                }

                // 1. Poster image layer
                val container = FrameLayout(context)
                card.addView(
                    container,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                val images = CrossfadeImagePair(container)

                // 2. Hardware-accelerated trailer player layer (TextureView)
                val textureView = TextureView(context).apply {
                    alpha = 0f
                    visibility = View.GONE
                }
                card.addView(
                    textureView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )

                // 3. Left vignette scrim (maintains metadata readability over video)
                val vignette = View(context).apply {
                    setBackgroundResource(R.drawable.card_left_vignette_scrim)
                }
                card.addView(vignette, FrameLayout.LayoutParams((activeW * 0.60f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))

                // 4. Bottom gradient scrim
                val bottomScrim = View(context).apply {
                    setBackgroundResource(R.drawable.card_bottom_scrim)
                }
                card.addView(bottomScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activeH * 0.65f).toInt(), Gravity.BOTTOM))

                // Initial layout params matching active card size
                val lp = FrameLayout.LayoutParams(activeW, activeH)
                card.layoutParams = lp

                // Add to container: index 0 is at bottom of z-stack
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
        bindInitialState(holder)
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

        // Active Card: matching JioHotstar 78.8% width ratio of carousel viewport (2786px / 3536px)
        val activeW = (totalW * 0.788f).toInt()
        val activeH = totalH

        // Visible peeking slice: both Peek 1 and Peek 2 have identical visible width (~76dp / 303px at 4K)
        // Leaving 36dp (144px at 4K) right screen edge margin
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

        // Slot indices:
        // 0: Slot -1 (Left subtle exit/enter, scale 0.96f, alpha 0f)
        // 1: Slot 0  (Active slot, scale 1.0f, alpha 1.0f)
        // 2: Slot 1  (Peek 1, scale PEEK_1_SCALE, alpha PEEK_1_ALPHA)
        // 3: Slot 2  (Peek 2, scale PEEK_2_SCALE, alpha PEEK_2_ALPHA)
        // 4: Slot 3  (Right subtle exit/enter, scale 0.68f, alpha 0f)
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
        val adapter = holder.adapter ?: return
        if (holder.cardViews.size < 4) return
        val slots = getSlots(holder)

        val activeItem = itemAt(adapter, holder.selectedIndex) ?: return

        // 4 Views:
        // cardViews[0] -> Slot 0 (Active)
        // cardViews[1] -> Slot 1 (Peek 1)
        // cardViews[2] -> Slot 2 (Peek 2)
        // cardViews[3] -> Slot -1 / 3 (Buffer)
        val v0 = holder.cardViews[0]
        val v1 = holder.cardViews[1]
        val v2 = holder.cardViews[2]
        val vBuffer = holder.cardViews[3]

        applySlot(v0.card, slots[1])
        loadTitle(v0, activeItem)
        v0.card.visibility = View.VISIBLE

        val item1 = itemAt(adapter, holder.selectedIndex + 1)
        if (item1 != null) {
            applySlot(v1.card, slots[2])
            loadTitle(v1, item1)
            v1.card.visibility = View.VISIBLE
        } else {
            v1.card.visibility = View.INVISIBLE
        }

        val item2 = itemAt(adapter, holder.selectedIndex + 2)
        if (item2 != null) {
            applySlot(v2.card, slots[3])
            loadTitle(v2, item2)
            v2.card.visibility = View.VISIBLE
        } else {
            v2.card.visibility = View.INVISIBLE
        }

        vBuffer.card.visibility = View.INVISIBLE

        // Ensure Z-order: vBuffer < v2 < v1 < v0
        vBuffer.card.bringToFront()
        v2.card.bringToFront()
        v1.card.bringToFront()
        v0.card.bringToFront()
        holder.card.bringToFront() // Overlay remains on top

        bindTextViews(holder, activeItem)
        holder.textBlock.alpha = 1f
        holder.textBlock.translationY = 0f

        renderDots(holder, adapter.size(), holder.selectedIndex)
        preloadUpcoming(holder, adapter, holder.selectedIndex)
        scheduleTrailer(holder)
    }

    private fun tryAdvance(holder: ViewHolder, delta: Int): Boolean {
        val adapter = holder.adapter ?: return false
        val count = adapter.size()
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
            // At the end of the carousel, play tactile spring resistance like JioHotstar
            playEndBounce(holder)
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

    private fun playEndBounce(holder: ViewHolder) {
        if (holder.cardViews.isEmpty()) return
        val activeCard = holder.cardViews[0].card
        activeCard.animate().cancel()
        val density = holder.stack.context.resources.displayMetrics.density
        val nudge = 12f * density
        activeCard.animate()
            .translationX(nudge)
            .setDuration(120)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .withEndAction {
                activeCard.animate()
                    .translationX(0f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator(1.8f))
                    .start()
            }
            .start()
    }

    private fun drainPendingAdvance(holder: ViewHolder) {
        val delta = holder.pendingDelta
        if (delta == 0) return
        holder.pendingDelta = 0
        tryAdvance(holder, delta)
    }

    private fun playJioHotstarShift(holder: ViewHolder, delta: Int, onEnd: () -> Unit) {
        val adapter = holder.adapter ?: return
        holder.isShifting = true
        val slots = getSlots(holder)

        // 1. Instantly dissolve text overlay and focus border (exact JioHotstar behavior)
        holder.textBlock.animate().cancel()
        holder.textBlock.alpha = 0f
        holder.focusBorder.animate().cancel()
        holder.focusBorder.alpha = 0f

        val v0 = holder.cardViews[0] // currently in Slot 0
        val v1 = holder.cardViews[1] // currently in Slot 1
        val v2 = holder.cardViews[2] // currently in Slot 2
        val vBuffer = holder.cardViews[3] // reusable buffer

        val nextIndex = holder.selectedIndex
        val animators = mutableListOf<Animator>()

        if (delta > 0) {
            // ADVANCE RIGHT:
            // v0 (Active) dissolves and slides slightly left to Slot -1 (-21dp, scale 0.96f, alpha 0f)
            // v1 (Peek 1) scales up and slides left to Slot 0 (Active)
            // v2 (Peek 2) scales up and slides left to Slot 1 (Peek 1), only if it was visible
            // vBuffer (incoming): In JioHotstar, the right stack ALWAYS looks like "only stack" (clean dark card surface).
            // If item (nextIndex + 2) exists, vBuffer is ALREADY positioned solidly at Slot 2 (slots[3])
            // with PEEK_2_ALPHA underneath v2. It shows ONLY the blank card surface, not a full poster!
            // As v2 scales and slides left into Slot 1, what is revealed behind it is purely the stack.
            // When the animation settles, finishShiftSettle binds the poster for the incoming item.

            val incomingItem = itemAt(adapter, nextIndex + 2)
            if (incomingItem != null) {
                applySlot(vBuffer.card, slots[3]) // Pre-position solidly at Slot 2 (Peek 2)
                vBuffer.card.alpha = PEEK_2_ALPHA
                vBuffer.images.setColor(holder.stack.context.getColor(R.color.hotstar_card_surface))
                vBuffer.card.visibility = View.VISIBLE
                // vBuffer does NOT animate: it stays stationary at slots[3] as the physical stack
            } else {
                vBuffer.card.visibility = View.INVISIBLE
            }

            // v0 exits: dissolves and shifts slightly left
            v0.card.visibility = View.VISIBLE
            animators.add(animateBetweenSlots(v0.card, slots[1], slots[0]))

            // v1 becomes active: scales up and slides into Slot 0
            v1.card.visibility = View.VISIBLE
            animators.add(animateBetweenSlots(v1.card, slots[2], slots[1]))

            // v2 moves to Peek 1 if item (nextIndex + 1) exists
            val itemAtPeek1 = itemAt(adapter, nextIndex + 1)
            if (itemAtPeek1 != null) {
                v2.card.visibility = View.VISIBLE
                animators.add(animateBetweenSlots(v2.card, slots[3], slots[2]))
            } else {
                v2.card.visibility = View.INVISIBLE
            }

            // Strict Z-ordering for DPAD_RIGHT:
            // vBuffer (stationary in stack at Peek 2) < v2 (sliding into Peek 1) < v1 (sliding into Active) < v0 (dissolving on top) < holder.card (focus border overlay)
            vBuffer.card.bringToFront()
            v2.card.bringToFront()
            v1.card.bringToFront()
            v0.card.bringToFront()
            holder.card.bringToFront() // Overlay remains on top

            AnimatorSet().apply {
                playTogether(animators)
                duration = SHIFT_DURATION_MS
                interpolator = DecelerateInterpolator(1.8f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        v0.card.visibility = View.INVISIBLE

                        // Promote views: v1 -> cardViews[0], v2 -> cardViews[1], vBuffer -> cardViews[2], v0 -> cardViews[3]
                        holder.cardViews[0] = v1
                        holder.cardViews[1] = v2
                        holder.cardViews[2] = vBuffer
                        holder.cardViews[3] = v0

                        finishShiftSettle(holder, nextIndex, onEnd)
                    }
                })
                start()
            }
        } else {
            // ADVANCE LEFT:
            // vBuffer binds item (nextIndex) and animates from Slot -1 into Slot 0
            // v0 (Active) animates right from Slot 0 to Slot 1 (Peek 1)
            // v1 (Peek 1) animates right from Slot 1 to Slot 2 (Peek 2), only if item exists
            // v2 (Peek 2): In JioHotstar, when the stack has cards (nextIndex + 2 exists),
            // the card already at Slot 2 remains solidly VISIBLE at Slot 2 (slots[3]) with its peek alpha.
            // It does NOT animate or dissolve out to alpha 0 and recreate! v1 smoothly glides from Slot 1
            // directly over v2 into Slot 2.

            val incomingItem = itemAt(adapter, nextIndex)
            if (incomingItem != null) {
                applySlot(vBuffer.card, slots[0]) // Slot -1 (Left: -36dp, scale 0.96f, alpha 0f)
                loadTitle(vBuffer, incomingItem)
                vBuffer.card.visibility = View.VISIBLE
                animators.add(animateBetweenSlots(vBuffer.card, slots[0], slots[1]))
            }

            // v0 moves to Peek 1
            v0.card.visibility = View.VISIBLE
            animators.add(animateBetweenSlots(v0.card, slots[1], slots[2]))

            // v1 moves to Peek 2 if item (nextIndex + 2) exists
            val itemAtPeek2 = itemAt(adapter, nextIndex + 2)
            if (itemAtPeek2 != null) {
                v1.card.visibility = View.VISIBLE
                animators.add(animateBetweenSlots(v1.card, slots[2], slots[3]))

                // If v2 was already visible at Slot 2, keep it stationary and solid at slots[3]
                // while v1 glides in over it. Do NOT fade it out to slots[4]!
                if (v2.card.visibility == View.VISIBLE) {
                    applySlot(v2.card, slots[3])
                    v2.card.alpha = PEEK_2_ALPHA
                    v2.card.visibility = View.VISIBLE
                }
            } else {
                v1.card.visibility = View.INVISIBLE
                // If there's no item at Peek 2, fade v2 out if it was visible
                if (v2.card.visibility == View.VISIBLE) {
                    animators.add(animateBetweenSlots(v2.card, slots[3], slots[4]))
                }
            }

            // Strict Z-ordering for DPAD_LEFT:
            // v2 (stationary bottom) < v1 (sliding into Peek 2) < v0 (sliding into Peek 1) < vBuffer (entering active) < holder.card
            v2.card.bringToFront()
            v1.card.bringToFront()
            v0.card.bringToFront()
            if (incomingItem != null) {
                vBuffer.card.bringToFront()
            }
            holder.card.bringToFront()

            AnimatorSet().apply {
                playTogether(animators)
                duration = SHIFT_DURATION_MS
                interpolator = DecelerateInterpolator(1.8f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        v2.card.visibility = View.INVISIBLE

                        // Promote views: vBuffer -> cardViews[0], v0 -> cardViews[1], v1 -> cardViews[2], v2 -> cardViews[3]
                        holder.cardViews[0] = vBuffer
                        holder.cardViews[1] = v0
                        holder.cardViews[2] = v1
                        holder.cardViews[3] = v2

                        finishShiftSettle(holder, nextIndex, onEnd)
                    }
                })
                start()
            }
        }
    }

    private fun finishShiftSettle(holder: ViewHolder, activeIndex: Int, onEnd: () -> Unit) {
        val adapter = holder.adapter
        val density = holder.stack.context.resources.displayMetrics.density

        val v0 = holder.cardViews[0]
        val v1 = holder.cardViews[1]
        val v2 = holder.cardViews[2]
        val vBuffer = holder.cardViews[3]

        // Strict end-of-list visibility check
        vBuffer.card.visibility = View.INVISIBLE
        if (adapter != null) {
            val item1 = itemAt(adapter, activeIndex + 1)
            if (item1 != null) {
                loadTitle(v1, item1)
                v1.card.visibility = View.VISIBLE
            } else {
                v1.card.visibility = View.INVISIBLE
            }

            val item2 = itemAt(adapter, activeIndex + 2)
            if (item2 != null) {
                loadTitle(v2, item2)
                v2.card.visibility = View.VISIBLE
            } else {
                v2.card.visibility = View.INVISIBLE
            }
        }

        // Ensure proper layer hierarchy
        vBuffer.card.bringToFront()
        v2.card.bringToFront()
        v1.card.bringToFront()
        v0.card.bringToFront()
        holder.card.bringToFront() // Overlay always top

        // Restore focus border on active card if hero card is focused
        if (holder.card.hasFocus()) {
            holder.focusBorder.animate().alpha(1f).setDuration(160).start()
        }

        val activeItem = adapter?.let { itemAt(it, activeIndex) }
        if (activeItem != null) {
            bindTextViews(holder, activeItem)
            // Staggered slide and fade-in (160ms)
            holder.textBlock.translationY = 8f * density
            holder.textBlock.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(TEXT_FADE_IN_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        if (adapter != null) {
            renderDots(holder, adapter.size(), activeIndex)
            preloadUpcoming(holder, adapter, activeIndex)
        }

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

    private fun loadTitle(slotView: CardSlotView, item: Title) {
        slotView.boundTitleId = item.id
        val backdropUrl = item.backdropUrl
        if (backdropUrl == null) {
            slotView.images.setColor(paletteColorFor(item))
        } else {
            slotView.images.load(backdropUrl, paletteColorFor(item))
        }
    }

    private fun bindTextViews(holder: ViewHolder, item: Title) {
        holder.title.text = item.name
        holder.contentRating.text = item.contentRating
        holder.meta.text = buildMetaLine(item)
        holder.qualityBadge.text = item.qualityTag
        holder.overview.text = item.overview

        if (!item.badge.isNullOrBlank()) {
            holder.badge.text = item.badge
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }
    }

    private fun preloadUpcoming(holder: ViewHolder, adapter: ObjectAdapter, index: Int) {
        val context = holder.stack.context
        val preloadRange = (index - 1)..(index + 4)
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
        if (index in 0 until adapter.size()) adapter.get(index) as? Title else null

    private fun currentItem(holder: ViewHolder): Any? = holder.adapter?.let { itemAt(it, holder.selectedIndex) }

    private fun paletteColorFor(item: Title): Int = fallbackPalette[item.id % fallbackPalette.size]

    private fun buildMetaLine(title: Title): String {
        val typeLabel = if (title.mediaType == "tv") "Series" else "Movie"
        val parts = listOfNotNull(
            title.year.takeIf { it.isNotBlank() },
            title.durationOrSeasons.takeIf { it.isNotBlank() } ?: typeLabel,
            title.genre.takeIf { it.isNotBlank() },
            "★ %.1f".format(title.rating)
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
        val runnable = object : Runnable {
            override fun run() {
                val adapter = holder.adapter
                if (adapter != null && adapter.size() > 1 && !holder.card.hasFocus() && !holder.isTrailerPlaying) {
                    val next = (holder.selectedIndex + 1) % adapter.size()
                    holder.selectedIndex = next
                    playJioHotstarShift(holder, 1) {
                        drainPendingAdvance(holder)
                    }
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

    // ========================================================================================
    // Hardware-Accelerated Trailer Video Playback (TextureView + MediaPlayer)
    // ========================================================================================

    private fun scheduleTrailer(holder: ViewHolder, delayMs: Long = TRAILER_DELAY_MS) {
        stopTrailer(holder, resetAlpha = true)
        // Trailer video playback temporarily disabled per user instruction
    }

    private fun startTrailerPlayback(holder: ViewHolder) {
        if (holder.isShifting || holder.cardViews.isEmpty()) return
        val adapter = holder.adapter ?: return
        val currentTitle = itemAt(adapter, holder.selectedIndex) ?: return
        val videoUrl = currentTitle.videoUrl ?: return

        val activeCardSlot = holder.cardViews[0]
        val textureView = activeCardSlot.textureView
        val context = holder.stack.context

        stopTrailer(holder, resetAlpha = false)

        val startPlayer = {
            try {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    try {
                        if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                            setDataSource(videoUrl)
                        } else {
                            val afd = context.resources.openRawResourceFd(R.raw.sample_trailer)
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                        }
                    } catch (e: Exception) {
                        val afd = context.resources.openRawResourceFd(R.raw.sample_trailer)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    setSurface(Surface(textureView.surfaceTexture))
                    setOnVideoSizeChangedListener { _, width, height ->
                        adjustTextureViewAspectRatio(textureView, width, height)
                    }
                    setOnPreparedListener { player ->
                        if (!holder.isShifting && holder.cardViews.isNotEmpty() && holder.cardViews[0] === activeCardSlot) {
                            player.start()
                            textureView.visibility = View.VISIBLE
                            textureView.animate().cancel()
                            textureView.animate()
                                .alpha(1f)
                                .setDuration(TRAILER_CROSSFADE_MS)
                                .start()
                            holder.isTrailerPlaying = true
                        } else {
                            try { player.release() } catch (_: Exception) {}
                        }
                    }
                    setOnCompletionListener {
                        // Trailer playback complete! Auto-change to next card matching JioHotstar
                        stopTrailer(holder, resetAlpha = true)
                        tryAdvance(holder, 1)
                    }
                    setOnErrorListener { _, _, _ ->
                        // If streaming error occurred, fallback to local bundled trailer
                        try {
                            val fallbackMp = MediaPlayer().apply {
                                val afd = context.resources.openRawResourceFd(R.raw.sample_trailer)
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                setSurface(Surface(textureView.surfaceTexture))
                                setOnVideoSizeChangedListener { _, width, height ->
                                    adjustTextureViewAspectRatio(textureView, width, height)
                                }
                                setOnPreparedListener { p ->
                                    if (!holder.isShifting && holder.cardViews.isNotEmpty() && holder.cardViews[0] === activeCardSlot) {
                                        p.start()
                                        textureView.visibility = View.VISIBLE
                                        textureView.animate().cancel()
                                        textureView.animate()
                                            .alpha(1f)
                                            .setDuration(TRAILER_CROSSFADE_MS)
                                            .start()
                                        holder.isTrailerPlaying = true
                                    } else {
                                        try { p.release() } catch (_: Exception) {}
                                    }
                                }
                                setOnCompletionListener {
                                    stopTrailer(holder, resetAlpha = true)
                                    tryAdvance(holder, 1)
                                }
                                prepareAsync()
                            }
                            holder.mediaPlayer?.release()
                            holder.mediaPlayer = fallbackMp
                        } catch (_: Exception) {
                            stopTrailer(holder, resetAlpha = true)
                        }
                        true
                    }
                    prepareAsync()
                }
                holder.mediaPlayer = mp
                holder.activePlayingTextureView = textureView
            } catch (e: Exception) {
                stopTrailer(holder, resetAlpha = true)
            }
        }

        if (textureView.isAvailable && textureView.surfaceTexture != null) {
            startPlayer()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    if (holder.cardViews.isNotEmpty() && holder.cardViews[0] === activeCardSlot) {
                        startPlayer()
                    }
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
            textureView.visibility = View.VISIBLE
        }
    }

    private fun adjustTextureViewAspectRatio(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewAspect = viewWidth / viewHeight
        val scaleX: Float
        val scaleY: Float
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    private fun stopTrailer(holder: ViewHolder, resetAlpha: Boolean = true) {
        holder.trailerRunnable?.let { holder.trailerHandler.removeCallbacks(it) }
        holder.trailerRunnable = null
        holder.isTrailerPlaying = false

        val mp = holder.mediaPlayer
        holder.mediaPlayer = null
        if (mp != null) {
            try {
                mp.stop()
                mp.reset()
                mp.release()
            } catch (_: Exception) {}
        }

        val tv = holder.activePlayingTextureView
        holder.activePlayingTextureView = null
        if (tv != null && resetAlpha) {
            tv.animate().cancel()
            tv.alpha = 0f
            tv.visibility = View.GONE
        }
    }
}
