package com.example.ott.ui.rows.hero

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.animation.doOnEnd
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowView
import androidx.leanback.widget.Presenter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.ott.R
import com.example.ott.data.model.HeroCarouselConfig
import com.example.ott.data.model.Title

/**
 * Android TV OTT Hero Carousel Card Presenter.
 *
 * Implements:
 * - Fluid, hardware-accelerated D-pad Left/Right expand and collapse animation (260ms DecelerateInterpolator).
 * - Smooth parallel interpolation of width, height, topMargin, elevation, and crossfade between
 *   2:3 portrait poster and 16:9 landscape backdrop.
 * - Staggered slide-fade for title, metadata badges, description, and action buttons.
 * - Hardware-accelerated trailer autoplay via Media3 ExoPlayer with 600ms debounce on focus hold.
 * - Auto-slide synchronization with D-pad navigation and clean focus tracking.
 */
class HeroCarouselCardPresenter(
    private val config: HeroCarouselConfig = HeroCarouselConfig(),
    private val carouselFocusListener: CarouselFocusListener? = null,
    private val onItemClicked: ((Title) -> Unit)? = null
) : Presenter() {

    private val rowId: String
        get() = config.rowId

    companion object {
        private const val TAG = "HeroCarouselCard"
        private const val FOCUS_HOLD_BEFORE_AUTOPLAY_MS = 600L
        private const val EXPAND_COLLAPSE_DURATION_MS = 260L

        private var sharedPlayer: ExoPlayer? = null
        private var activeHolder: CardViewHolder? = null

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

    // ---------------------------------------------------------
    // VIEW HOLDER
    // ---------------------------------------------------------

    class CardViewHolder(val rootView: View) : Presenter.ViewHolder(rootView) {
        val cardContainer: CardView = rootView.findViewById(R.id.card_hero_container)
        val poster: ImageView = rootView.findViewById(R.id.iv_hero_carousel_poster)
        val backdrop: ImageView = rootView.findViewById(R.id.iv_hero_carousel_backdrop)
        val playerView: PlayerView = rootView.findViewById(R.id.player_view_hero_carousel)
        val title: TextView = rootView.findViewById(R.id.tv_hero_carousel_title)
        val description: TextView = rootView.findViewById(R.id.tv_hero_carousel_description)
        val trendingBadge: TextView = rootView.findViewById(R.id.tv_hero_carousel_trending)
        val muteIcon: ImageView = rootView.findViewById(R.id.iv_hero_carousel_mute)
        val basicDetailsLayout: View = rootView.findViewById(R.id.layout_hero_content)
        val metadataLayout: View = rootView.findViewById(R.id.layout_hero_metadata)
        val metadata: TextView = rootView.findViewById(R.id.tv_hero_metadata)
        val duration: TextView = rootView.findViewById(R.id.tv_hero_duration)
        val adBadge: TextView = rootView.findViewById(R.id.tv_hero_ad)
        val hdBadge: TextView = rootView.findViewById(R.id.tv_hero_hd)
        val fourKBadge: TextView = rootView.findViewById(R.id.tv_hero_4k)
        val parentalBadge: TextView = rootView.findViewById(R.id.tv_hero_parental)
        val rating: TextView = rootView.findViewById(R.id.tv_hero_rating)
        val bgShadow: View = rootView.findViewById(R.id.view_hero_bottom_shadow)
        val focusBorder: View = rootView.findViewById(R.id.view_hero_focus_border)
        val btnAddToWatchlist: Button = rootView.findViewById(R.id.btn_hero_carousel_watchlist)
        val btnWatchNow: Button = rootView.findViewById(R.id.btn_hero_carousel_watch)

        var trailerUrl: String? = null
        var boundTitle: Title? = null
        var anim: ValueAnimator? = null

        fun revertToPoster() {
            playerView.animate().cancel()
            playerView.player = null
            playerView.visibility = View.GONE
            playerView.alpha = 0f

            poster.visibility = View.VISIBLE
            backdrop.visibility = View.VISIBLE
            muteIcon.visibility = View.GONE
        }

        fun startAutoplayIfEligible() {
            val url = trailerUrl
            if (url.isNullOrEmpty()) return

            val player = getOrCreatePlayer(rootView.context)

            // Stop previous active card
            activeHolder?.let { oldHolder ->
                if (oldHolder !== this) {
                    oldHolder.revertToPoster()
                }
            }

            activeHolder = this
            playerView.player = player
            playerView.useController = false

            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(url))
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.playWhenReady = true

            playerView.visibility = View.VISIBLE
            playerView.alpha = 0f
            playerView.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction {
                    if (activeHolder === this) {
                        poster.visibility = View.INVISIBLE
                        backdrop.visibility = View.INVISIBLE
                        muteIcon.visibility = View.VISIBLE
                    }
                }
                .start()
        }
    }

    // ---------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hero_carousel_card, parent, false)

        view.tag = "HERO_CAROUSEL_CARD"
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        return CardViewHolder(view)
    }

    // ---------------------------------------------------------
    // REQUEST OPTIONS
    // ---------------------------------------------------------

    private val requestOptions by lazy {
        val cornerRadius = 12
        RequestOptions()
            .placeholder(R.drawable.simmer_background)
            .error(R.drawable.simmer_background)
            .centerCrop()
            .transform(RoundedCorners(cornerRadius))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
    }

    // ---------------------------------------------------------
    // BIND
    // ---------------------------------------------------------

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as? CardViewHolder ?: return
        val context = holder.rootView.context

        holder.anim?.cancel()
        holder.revertToPoster()
        holder.boundTitle = null
        holder.trailerUrl = null
        clearBasicDetails(holder)

        if (item is Title) {
            bindTitle(holder, context, item)
        } else {
            clearHolder(holder)
        }

        // Apply initial layout statically without animation
        animateCardExpansion(holder, hasFocus = holder.rootView.hasFocus(), animate = false)
        setupFocusListener(holder)
    }

    private fun bindTitle(holder: CardViewHolder, context: Context, title: Title) {
        holder.boundTitle = title
        holder.title.text = title.name

        // Trending badge
        if (!title.badge.isNullOrBlank()) {
            holder.trendingBadge.text = title.badge
            holder.trendingBadge.visibility = View.VISIBLE
        } else {
            holder.trendingBadge.visibility = View.GONE
        }

        // Description / Summary
        val descriptionText = if (!title.seasonEpisode.isNullOrEmpty()) {
            "${title.seasonEpisode} • ${title.overview}"
        } else {
            title.overview
        }
        if (descriptionText.isNotEmpty()) {
            holder.description.text = descriptionText
            holder.description.visibility = View.VISIBLE
        } else {
            holder.description.visibility = View.GONE
        }

        // Trailer URL
        holder.trailerUrl = title.trailerUrl ?: title.videoUrl

        // Basic Details
        bindBasicDetails(holder, title)

        // Pre-load dual images: 2:3 portrait poster & 16:9 backdrop for instant zero-lag crossfade
        loadCardImages(holder, title)

        // Click listeners
        holder.rootView.setOnClickListener {
            onItemClicked?.invoke(title)
        }
        holder.btnWatchNow.setOnClickListener {
            onItemClicked?.invoke(title)
        }
        holder.btnAddToWatchlist.setOnClickListener {
            android.widget.Toast.makeText(context, "Added ${title.name} to Watchlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindBasicDetails(holder: CardViewHolder, title: Title) {
        // Metadata: Year • Genre
        val metaParts = mutableListOf<String>()
        if (title.year.isNotEmpty()) metaParts.add(title.year)
        if (title.genre.isNotEmpty()) metaParts.add(title.genre)
        val metadataText = metaParts.joinToString(" • ")

        if (metadataText.isNotEmpty()) {
            holder.metadata.text = metadataText
            holder.metadata.visibility = View.VISIBLE
        } else {
            holder.metadata.visibility = View.GONE
        }

        // Duration
        val durationSec = title.durationSeconds
        if (durationSec > 0) {
            val hours = durationSec / 3600
            val minutes = (durationSec % 3600) / 60
            val durationText = when {
                hours > 0 && minutes > 0 -> "• ${hours}h ${minutes}m"
                hours > 0 -> "• ${hours}h"
                minutes > 0 -> "• ${minutes}m"
                else -> ""
            }
            if (durationText.isNotEmpty()) {
                holder.duration.text = durationText
                holder.duration.visibility = View.VISIBLE
            } else {
                holder.duration.visibility = View.GONE
            }
        } else if (title.durationOrSeasons.isNotEmpty()) {
            holder.duration.text = "• ${title.durationOrSeasons}"
            holder.duration.visibility = View.VISIBLE
        } else {
            holder.duration.visibility = View.GONE
        }

        // Badges: Quality
        holder.fourKBadge.visibility = if (title.is4K) View.VISIBLE else View.GONE
        holder.hdBadge.visibility = if (title.isHD && !title.is4K) View.VISIBLE else View.GONE
        holder.adBadge.visibility = if (title.isAD) View.VISIBLE else View.GONE

        // Parental rating
        if (title.contentRating.isNotEmpty()) {
            holder.parentalBadge.text = title.contentRating
            holder.parentalBadge.visibility = View.VISIBLE
        } else {
            holder.parentalBadge.visibility = View.GONE
        }

        // Star rating
        if (title.rating > 0.0) {
            holder.rating.text = "★ " + String.format("%.1f", title.rating)
            holder.rating.visibility = View.VISIBLE
        } else {
            holder.rating.visibility = View.GONE
        }
    }

    private fun loadCardImages(holder: CardViewHolder, title: Title) {
        val posterUrl = title.posterUrl ?: title.backdropUrl
        val backdropUrl = title.backdropUrl ?: title.posterUrl

        Glide.with(holder.poster)
            .load(posterUrl)
            .apply(requestOptions)
            .into(holder.poster)

        Glide.with(holder.backdrop)
            .load(backdropUrl)
            .apply(requestOptions)
            .into(holder.backdrop)
    }

    // ---------------------------------------------------------
    // FLUID EXPAND & COLLAPSE ANIMATION (D-PAD NAVIGATION)
    // ---------------------------------------------------------

    private fun animateCardExpansion(holder: CardViewHolder, hasFocus: Boolean, animate: Boolean = true) {
        val context = holder.rootView.context
        val density = context.resources.displayMetrics.density

        val unselectedW = context.resources.getDimensionPixelSize(R.dimen.carousel_unselected_width)
        val unselectedH = context.resources.getDimensionPixelSize(R.dimen.carousel_unselected_height)
        val unselectedTopMargin = context.resources.getDimensionPixelSize(R.dimen.hero_carousel_unfocused_top_margin)

        val selectedW = context.resources.getDimensionPixelSize(R.dimen.carousel_selected_width)
        val selectedH = context.resources.getDimensionPixelSize(R.dimen.carousel_selected_height)
        val selectedTopMargin = 0

        val targetW = if (hasFocus) selectedW else unselectedW
        val targetH = if (hasFocus) selectedH else unselectedH
        val targetMargin = if (hasFocus) selectedTopMargin else unselectedTopMargin
        val targetAlpha = if (hasFocus) 1f else 0f
        val targetElevation = if (hasFocus) 10f * density else 3f * density

        holder.anim?.cancel()

        if (!animate) {
            // Instant layout apply (used during initial binding)
            applyDimensionToContainer(holder, targetW, targetH, targetMargin)
            holder.backdrop.alpha = targetAlpha
            holder.poster.alpha = 1f - targetAlpha
            holder.basicDetailsLayout.alpha = targetAlpha
            holder.basicDetailsLayout.visibility = if (hasFocus) View.VISIBLE else View.GONE
            holder.basicDetailsLayout.translationY = 0f
            holder.bgShadow.alpha = targetAlpha
            holder.bgShadow.visibility = if (hasFocus) View.VISIBLE else View.GONE
            holder.focusBorder.alpha = targetAlpha
            holder.cardContainer.cardElevation = targetElevation
            return
        }

        // Animated fluid transition during D-pad navigation
        if (hasFocus) {
            holder.rootView.bringToFront()
            holder.basicDetailsLayout.visibility = View.VISIBLE
            holder.bgShadow.visibility = View.VISIBLE
        }

        val startW = holder.cardContainer.layoutParams.width.takeIf { it > 0 } ?: if (hasFocus) unselectedW else selectedW
        val startH = holder.cardContainer.layoutParams.height.takeIf { it > 0 } ?: if (hasFocus) unselectedH else selectedH
        val startMargin = (holder.cardContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: if (hasFocus) unselectedTopMargin else 0
        val startAlpha = holder.backdrop.alpha
        val startDetailsAlpha = holder.basicDetailsLayout.alpha

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EXPAND_COLLAPSE_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { va ->
                val f = va.animatedFraction

                val currentW = (startW + (targetW - startW) * f).toInt()
                val currentH = (startH + (targetH - startH) * f).toInt()
                val currentMargin = (startMargin + (targetMargin - startMargin) * f).toInt()

                applyDimensionToContainer(holder, currentW, currentH, currentMargin)

                // Ultra-smooth crossfade between 2:3 portrait poster and 16:9 landscape backdrop
                val currentBackdropAlpha = startAlpha + (targetAlpha - startAlpha) * f
                holder.backdrop.alpha = currentBackdropAlpha
                holder.poster.alpha = 1f - currentBackdropAlpha

                // Smooth slide & fade on metadata text, buttons, and shadow scrim
                val currentDetailsAlpha = startDetailsAlpha + (targetAlpha - startDetailsAlpha) * f
                holder.basicDetailsLayout.alpha = currentDetailsAlpha
                holder.bgShadow.alpha = currentDetailsAlpha
                holder.focusBorder.alpha = currentDetailsAlpha

                if (hasFocus) {
                    holder.basicDetailsLayout.translationY = 16f * density * (1f - f)
                } else {
                    holder.basicDetailsLayout.translationY = 16f * density * f
                }
            }
            doOnEnd {
                if (!hasFocus) {
                    holder.basicDetailsLayout.visibility = View.GONE
                    holder.bgShadow.visibility = View.GONE
                } else {
                    ensureCardInView(holder)
                }
                holder.anim = null
            }
        }

        holder.cardContainer.animate()
            .setDuration(EXPAND_COLLAPSE_DURATION_MS)
            .translationZ(if (hasFocus) 8f * density else 0f)
            .start()

        holder.anim = animator
        animator.start()
    }

    private fun ensureCardInView(holder: CardViewHolder) {
        val listRowView = findListRowView(holder.rootView) ?: return
        val gridView = listRowView.gridView
        val pos = gridView.getChildAdapterPosition(holder.rootView)
        if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION && holder.rootView.hasFocus()) {
            gridView.setSelectedPositionSmooth(pos)
        }
    }

    private fun applyDimensionToContainer(holder: CardViewHolder, width: Int, height: Int, topMargin: Int) {
        val lp = holder.cardContainer.layoutParams
        if (lp.width != width || lp.height != height || (lp is ViewGroup.MarginLayoutParams && lp.topMargin != topMargin)) {
            lp.width = width
            lp.height = height
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.topMargin = topMargin
            }
            holder.cardContainer.layoutParams = lp
        }
    }

    // ---------------------------------------------------------
    // FOCUS & AUTO-SLIDE
    // ---------------------------------------------------------

    private var isCarouselFocused = false

    private fun notifyCarouselFocus(hasFocus: Boolean) {
        if (isCarouselFocused == hasFocus) return
        isCarouselFocused = hasFocus
        carouselFocusListener?.onCarouselFocusChanged(hasFocus)
    }

    private fun isViewInsideCarousel(focusedView: View): Boolean {
        var currentView: View? = focusedView
        while (currentView != null) {
            if (currentView.tag == "HERO_CAROUSEL_CARD") return true
            currentView = currentView.parent as? View
        }
        return false
    }

    private fun setupFocusListener(holder: CardViewHolder) {
        registerAutoSlide(holder)

        holder.rootView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                notifyCarouselFocus(true)
                if (!HeroCarouselAutoSlideController.isProgrammaticChange(rowId)) {
                    HeroCarouselAutoSlideController.notifyUserInteraction(rowId)
                }

                // Smooth fluid expansion animation
                animateCardExpansion(holder, hasFocus = true, animate = true)

                // Ensure expanded card is scrolled into full view so right side is never cut off
                holder.rootView.post {
                    if (holder.rootView.hasFocus()) {
                        ensureCardInView(holder)
                    }
                }

                // Schedule video autoplay after 600ms hold on fully expanded card
                if (!holder.trailerUrl.isNullOrEmpty()) {
                    scheduleAutoplay(holder)
                }
            } else {
                // Smooth fluid collapse animation
                animateCardExpansion(holder, hasFocus = false, animate = true)

                cancelPendingAutoplay()

                if (activeHolder === holder) {
                    sharedPlayer?.apply {
                        playWhenReady = false
                        stop()
                        clearMediaItems()
                    }
                    holder.revertToPoster()
                    activeHolder = null
                }

                holder.rootView.post {
                    val focusedView = holder.rootView.rootView.findFocus()
                    val isStillInside = focusedView?.let { isViewInsideCarousel(it) } ?: false
                    if (!isStillInside) {
                        notifyCarouselFocus(false)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // AUTOPLAY
    // ---------------------------------------------------------

    private fun scheduleAutoplay(holder: CardViewHolder) {
        cancelPendingAutoplay()

        val runnable = Runnable {
            if (!holder.rootView.hasFocus()) return@Runnable
            if (holder.trailerUrl.isNullOrEmpty()) return@Runnable
            holder.startAutoplayIfEligible()
        }

        pendingAutoplayRunnable = runnable
        autoplayHandler.postDelayed(runnable, FOCUS_HOLD_BEFORE_AUTOPLAY_MS)
    }

    private fun cancelPendingAutoplay() {
        pendingAutoplayRunnable?.let { autoplayHandler.removeCallbacks(it) }
        pendingAutoplayRunnable = null
    }

    // ---------------------------------------------------------
    // AUTO-SLIDE REGISTRATION
    // ---------------------------------------------------------

    private fun findListRowView(view: View): ListRowView? {
        var current: View? = view
        while (current != null) {
            if (current is ListRowView) return current
            current = current.parent as? View
        }
        return null
    }

    private fun registerAutoSlide(holder: CardViewHolder, attempt: Int = 0) {
        val maxAttempts = 10
        val retryDelayMs = 150L

        holder.rootView.post {
            val listRowView = findListRowView(holder.rootView)
            if (listRowView == null) {
                if (attempt < maxAttempts) {
                    holder.rootView.postDelayed(
                        { registerAutoSlide(holder, attempt + 1) },
                        retryDelayMs
                    )
                }
                return@post
            }

            val gridView: HorizontalGridView = listRowView.gridView
            val itemCount = gridView.adapter?.itemCount ?: 0
            if (itemCount <= 1 || !config.autoRotateEnabled) {
                HeroCarouselAutoSlideController.unregisterRow(rowId)
                return@post
            }

            val intervalMs = (config.autoRotateDurationSec.takeIf { it > 0 } ?: 6) * 1000L

            HeroCarouselAutoSlideController.registerRow(
                rowId = rowId,
                gridView = gridView,
                itemCount = itemCount,
                autoRotateEnabled = true,
                intervalMs = intervalMs
            )
        }
    }

    // ---------------------------------------------------------
    // CLEAR
    // ---------------------------------------------------------

    private fun clearBasicDetails(holder: CardViewHolder) {
        holder.basicDetailsLayout.visibility = View.GONE
        holder.basicDetailsLayout.alpha = 0f
        holder.bgShadow.visibility = View.GONE
        holder.bgShadow.alpha = 0f
        holder.focusBorder.alpha = 0f
        holder.metadata.text = ""
        holder.metadata.visibility = View.GONE
        holder.duration.text = ""
        holder.duration.visibility = View.GONE
        holder.adBadge.visibility = View.GONE
        holder.hdBadge.visibility = View.GONE
        holder.fourKBadge.visibility = View.GONE
        holder.parentalBadge.text = ""
        holder.parentalBadge.visibility = View.GONE
        holder.rating.text = ""
        holder.rating.visibility = View.GONE
        holder.description.text = ""
        holder.description.visibility = View.GONE
    }

    private fun clearHolder(holder: CardViewHolder) {
        holder.title.text = ""
        holder.trendingBadge.visibility = View.GONE
        clearBasicDetails(holder)
        holder.trailerUrl = null
        holder.boundTitle = null

        Glide.with(holder.poster).clear(holder.poster)
        Glide.with(holder.backdrop).clear(holder.backdrop)
        holder.poster.setImageDrawable(null)
        holder.backdrop.setImageDrawable(null)
        holder.revertToPoster()
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as? CardViewHolder ?: return
        holder.anim?.cancel()
        if (activeHolder === holder) {
            stopActiveVideo()
        }
        cancelPendingAutoplay()
        try {
            Glide.with(holder.poster).clear(holder.poster)
            Glide.with(holder.backdrop).clear(holder.backdrop)
        } catch (_: Exception) {}
    }
}
