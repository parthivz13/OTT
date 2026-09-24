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
import com.example.ott.data.model.Title
import com.example.ott.sott.base.CarouselFocusListener
import com.example.ott.sott.models.CustomAsset
import com.example.ott.sott.models.CustomKalturaAsset
import com.example.ott.sott.networking.RailCommonData
import com.example.ott.sott.utils.AppCommonMethod
import com.example.ott.sott.utils.LogUtils
import com.example.ott.sott.utils.SharedPrefHelper
import com.example.ott.sott.utils.constants.AppConstants
import com.example.ott.types.Asset

class HeroCarouselCardPresenter(
    var railCommonData: RailCommonData = RailCommonData(),
    private val carouselFocusListener: CarouselFocusListener? = null,
    private val onItemClicked: ((Any) -> Unit)? = null,
    /**
     * When true the presenter behaves as a hero carousel:
     * the last focused card stays expanded even after focus leaves the row.
     * When false (default) the card collapses as soon as it loses focus.
     */
    val keepExpandedWhenUnfocused: Boolean = false
) : Presenter() {

    private val rowId: String
        get() = railCommonData.screenWidget?.Id ?: "hero_carousel_default"

    companion object {
        private const val FOCUS_HOLD_BEFORE_AUTOPLAY_MS = 600L
        private const val EXPAND_COLLAPSE_DURATION_MS = 250L

        private var sharedPlayer: ExoPlayer? = null
        private var activeHolder: CardViewHolder? = null
        private val autoplayHandler = Handler(Looper.getMainLooper())
        private var pendingAutoplayRunnable: Runnable? = null

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

        /**
         * The card that was last focused and is kept expanded when
         * keepExpandedWhenUnfocused = true and focus leaves the row.
         */
        var lastExpandedHolder: CardViewHolder? = null
            private set

        /**
         * Collapse and clear the pinned expanded card.
         * Call this when the row loses focus or is unbound.
         */
        fun collapseLastExpanded() {
            lastExpandedHolder?.let { holder ->
                holder.anim?.cancel()
                holder.anim = null

                // Stop any video playing on the pinned card
                if (activeHolder === holder) {
                    stopActiveVideo()
                }

                // Animate collapse back to poster/small size
                val context = holder.rootView.context
                val unselectedW = context.resources.getDimensionPixelSize(R.dimen.carousel_unselected_width)
                val unselectedH = context.resources.getDimensionPixelSize(R.dimen.carousel_unselected_height)
                val unselectedTopMargin = context.resources.getDimensionPixelSize(R.dimen.hero_carousel_unfocused_top_margin)
                holder.basicDetailsLayout.visibility = View.GONE
                holder.bgShadow.visibility = View.GONE
                holder.backdrop.alpha = 0f
                holder.poster.alpha = 1f
                holder.focusBorder.alpha = 0f
                holder.focusBorder.visibility = View.GONE
                holder.focusBorder.elevation = 0f
                holder.focusBorder.translationZ = 0f
                val lp = holder.cardContainer.layoutParams
                lp.width = unselectedW
                lp.height = unselectedH
                if (lp is ViewGroup.MarginLayoutParams) lp.topMargin = unselectedTopMargin
                holder.cardContainer.layoutParams = lp
                holder.cardContainer.requestLayout()
                holder.rootView.requestLayout()
            }
            lastExpandedHolder = null
        }

        /** Called by the card itself to register or clear the pin. */
        internal fun pinExpanded(holder: CardViewHolder?) {
            lastExpandedHolder = holder
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

    class CardViewHolder(val rootView: View) : ViewHolder(rootView) {
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
        var boundItem: Any? = null
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
        holder.boundItem = item
        holder.trailerUrl = null
        clearBasicDetails(holder)

        when (item) {
            is CustomAsset -> bindCustomAsset(holder, item)
            is CustomKalturaAsset -> bindCustomKalturaAsset(holder, context, item)
            is com.example.ott.EnveuCategoryServices.Asset -> bindEnveuAsset(holder, item)
            is Asset -> bindKalturaAsset(holder, context, item)
            is Title -> bindTitle(holder, context, item)
            else -> clearHolder(holder)
        }

        // Apply initial layout statically without animation
        animateCardExpansion(holder, hasFocus = holder.rootView.hasFocus(), animate = false)
        setupFocusListener(holder)
    }

    private fun bindCustomAsset(holder: CardViewHolder, item: CustomAsset) {
        holder.title.text = ""
        holder.description.visibility = View.GONE
        holder.trendingBadge.visibility = View.GONE
        holder.basicDetailsLayout.visibility = View.GONE
        holder.trailerUrl = null

        holder.poster.setImageResource(R.drawable.simmer_background)
        holder.backdrop.setImageResource(R.drawable.simmer_background)
    }

    private fun bindEnveuAsset(holder: CardViewHolder, item: com.example.ott.EnveuCategoryServices.Asset) {
        holder.title.text = item.name.orEmpty()
        holder.description.visibility = View.GONE
        holder.trendingBadge.visibility = View.GONE
        holder.basicDetailsLayout.visibility = View.VISIBLE
        holder.trailerUrl = null

        val imageUrl = item.images?.firstOrNull()?.url
        Glide.with(holder.poster).load(imageUrl).apply(requestOptions).into(holder.poster)
        Glide.with(holder.backdrop).load(imageUrl).apply(requestOptions).into(holder.backdrop)
    }

    private fun bindCustomKalturaAsset(holder: CardViewHolder, context: Context, item: CustomKalturaAsset) {
        holder.title.text = item.name.orEmpty()
        holder.description.visibility = View.GONE
        holder.trendingBadge.visibility = View.GONE
        holder.basicDetailsLayout.visibility = View.VISIBLE
        holder.trailerUrl = null

        val imageUrl = item.images?.firstOrNull()?.url
        Glide.with(holder.poster).load(imageUrl).apply(requestOptions).into(holder.poster)
        Glide.with(holder.backdrop).load(imageUrl).apply(requestOptions).into(holder.backdrop)
    }

    private fun bindKalturaAsset(holder: CardViewHolder, context: Context, asset: Asset) {
        holder.title.text = asset.name.orEmpty()
        holder.trendingBadge.visibility = View.GONE

        val seasonEpisode = AppCommonMethod.addSeasonAndEpisodeNo(asset)
        val descriptionText = if (!seasonEpisode.isNullOrEmpty()) {
            seasonEpisode
        } else {
            AppCommonMethod.getMetaByTag(asset, "LongSummary")
        }

        if (!descriptionText.isNullOrEmpty()) {
            holder.description.text = descriptionText
            holder.description.visibility = View.VISIBLE
        } else {
            holder.description.visibility = View.GONE
        }

        // Trailer
        val initialTrailer = asset.mediaFiles?.firstOrNull {
            it.type?.equals("Preview", ignoreCase = true) == true
        }?.url
        val externalId = asset.externalId.orEmpty()
        val trailerFromPref = if (externalId.isNotEmpty()) {
            SharedPrefHelper.getInstance().getTrailerFromMap(context, externalId)?.toString()
        } else null
        holder.trailerUrl = trailerFromPref ?: initialTrailer

        bindBasicDetails(holder, asset)

        val unselectedW = context.resources.getDimensionPixelSize(R.dimen.carousel_unselected_width)
        val unselectedH = context.resources.getDimensionPixelSize(R.dimen.carousel_unselected_height)
        val selectedW = context.resources.getDimensionPixelSize(R.dimen.carousel_selected_width)
        val selectedH = context.resources.getDimensionPixelSize(R.dimen.carousel_selected_height)

        val posterUrl = asset.images?.takeIf { it.isNotEmpty() }?.let {
            AppCommonMethod.getCardwiseImage(it, AppConstants.RATIO_2X3, unselectedW, unselectedH)
        } ?: asset.images?.firstOrNull()?.url

        val backdropUrl = asset.images?.takeIf { it.isNotEmpty() }?.let {
            AppCommonMethod.getCardwiseImage(it, AppConstants.RATIO_16X9_cover, selectedW, selectedH)
        } ?: asset.images?.firstOrNull()?.url ?: posterUrl

        Glide.with(holder.poster).load(posterUrl).apply(requestOptions).into(holder.poster)
        Glide.with(holder.backdrop).load(backdropUrl).apply(requestOptions).into(holder.backdrop)

        holder.rootView.setOnClickListener { onItemClicked?.invoke(asset) }
        holder.btnWatchNow.setOnClickListener { onItemClicked?.invoke(asset) }
        holder.btnAddToWatchlist.setOnClickListener {
            android.widget.Toast.makeText(context, "Added ${asset.name} to Watchlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindBasicDetails(holder: CardViewHolder, asset: Asset) {
        val metadataText = AppCommonMethod.getMetas(asset)
        if (metadataText.isNotEmpty()) {
            holder.metadata.text = metadataText
            holder.metadata.visibility = View.VISIBLE
        } else {
            holder.metadata.visibility = View.GONE
        }

        val duration = asset.mediaFiles?.firstOrNull()?.duration ?: 0
        if (duration > 0) {
            val hours = (duration / 3600).toInt()
            val minutes = ((duration % 3600) / 60).toInt()
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
        } else {
            holder.duration.visibility = View.GONE
        }

        val qualities = AppCommonMethod.getQualities(asset)
        holder.adBadge.visibility = if ("AD" in qualities) View.VISIBLE else View.GONE
        holder.hdBadge.visibility = if ("HD" in qualities) View.VISIBLE else View.GONE
        holder.fourKBadge.visibility = if ("4K" in qualities) View.VISIBLE else View.GONE

        val parentalRating = AppCommonMethod.getTagsFromAsset(asset, AppConstants.PARENTAL_RATING)
        if (parentalRating.isNotEmpty()) {
            holder.parentalBadge.text = parentalRating
            holder.parentalBadge.visibility = View.VISIBLE
        } else {
            holder.parentalBadge.visibility = View.GONE
        }

        val ratingData = AppCommonMethod.getMetaByTag(asset, AppConstants.star_rating)
        if (!ratingData.isNullOrEmpty() && ratingData != "0") {
            ratingData.toDoubleOrNull()?.let {
                holder.rating.text = "★ " + String.format("%.1f", it)
                holder.rating.visibility = View.VISIBLE
            } ?: run { holder.rating.visibility = View.GONE }
        } else {
            holder.rating.visibility = View.GONE
        }
    }

    private fun bindTitle(holder: CardViewHolder, context: Context, title: Title) {
        holder.title.text = title.name
        holder.description.text = title.overview
        holder.description.visibility = if (title.overview.isNotEmpty()) View.VISIBLE else View.GONE
        holder.trendingBadge.visibility = if (!title.badge.isNullOrBlank()) {
            holder.trendingBadge.text = title.badge
            View.VISIBLE
        } else View.GONE

        holder.trailerUrl = title.trailerUrl ?: title.videoUrl

        // Metadata
        val metaParts = listOfNotNull(title.year.takeIf { it.isNotEmpty() }, title.genre.takeIf { it.isNotEmpty() })
        holder.metadata.text = metaParts.joinToString(" • ")
        holder.metadata.visibility = if (metaParts.isNotEmpty()) View.VISIBLE else View.GONE

        // Rating
        if (title.rating > 0.0) {
            holder.rating.text = "★ " + String.format("%.1f", title.rating)
            holder.rating.visibility = View.VISIBLE
        } else {
            holder.rating.visibility = View.GONE
        }

        val posterUrl = title.posterUrl ?: title.backdropUrl
        val backdropUrl = title.backdropUrl ?: title.posterUrl

        Glide.with(holder.poster).load(posterUrl).apply(requestOptions).into(holder.poster)
        Glide.with(holder.backdrop).load(backdropUrl).apply(requestOptions).into(holder.backdrop)

        holder.rootView.setOnClickListener { onItemClicked?.invoke(title) }
        holder.btnWatchNow.setOnClickListener { onItemClicked?.invoke(title) }
        holder.btnAddToWatchlist.setOnClickListener {
            android.widget.Toast.makeText(context, "Added ${title.name} to Watchlist", android.widget.Toast.LENGTH_SHORT).show()
        }
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
            applyDimensionToContainer(holder, targetW, targetH, targetMargin)
            holder.backdrop.alpha = targetAlpha
            holder.poster.alpha = 1f - targetAlpha
            holder.basicDetailsLayout.alpha = targetAlpha
            holder.basicDetailsLayout.visibility = if (hasFocus) View.VISIBLE else View.GONE
            holder.basicDetailsLayout.translationY = 0f
            holder.bgShadow.alpha = targetAlpha
            holder.bgShadow.visibility = if (hasFocus) View.VISIBLE else View.GONE
            holder.focusBorder.elevation = if (hasFocus) 14f * density else 0f
            holder.focusBorder.translationZ = if (hasFocus) 4f * density else 0f
            holder.focusBorder.alpha = if (hasFocus) 1f else 0f
            holder.focusBorder.visibility = if (hasFocus) View.VISIBLE else View.GONE
            holder.cardContainer.cardElevation = targetElevation
            return
        }

        if (hasFocus) {
            holder.rootView.bringToFront()
            holder.basicDetailsLayout.visibility = View.VISIBLE
            holder.bgShadow.visibility = View.VISIBLE
            holder.focusBorder.bringToFront()
            holder.focusBorder.elevation = 14f * density
            holder.focusBorder.translationZ = 4f * density
            holder.focusBorder.alpha = 1f
            holder.focusBorder.visibility = View.VISIBLE
        } else {
            holder.focusBorder.alpha = 0f
            holder.focusBorder.visibility = View.GONE
            holder.focusBorder.elevation = 0f
            holder.focusBorder.translationZ = 0f
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
            if (gridView.selectedPosition != pos) {
                gridView.setSelectedPositionSmooth(pos)
            }
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
            holder.cardContainer.requestLayout()
            holder.rootView.requestLayout()
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

                // If another card was pinned expanded in this row, collapse it first
                if (lastExpandedHolder !== null && lastExpandedHolder !== holder) {
                    collapseLastExpanded()
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

                // After focus settles, decide whether to collapse or keep expanded
                holder.rootView.post {
                    if (holder.rootView.hasFocus()) return@post

                    val focusedView = holder.rootView.rootView.findFocus()
                    val isStillInside = focusedView?.let { isViewInsideCarousel(it) } ?: false

                    if (keepExpandedWhenUnfocused && !isStillInside) {
                        // Focus left the entire row — pin this card as the visible expanded state
                        pinExpanded(holder)
                        // Don't collapse the card visually
                    } else {
                        // Either within row (another card getting focus) or collapse mode
                        if (lastExpandedHolder === holder) pinExpanded(null)
                        animateCardExpansion(holder, hasFocus = false, animate = true)
                    }

                    if (!isStillInside) {
                        notifyCarouselFocus(false)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // AUTO-SLIDE CONTROLLER REGISTRATION
    // ---------------------------------------------------------

    private fun registerAutoSlide(holder: CardViewHolder, attempt: Int = 0) {
        val maxAttempts = 10
        val retryDelayMs = 150L

        holder.rootView.post {
            val listRowView = findListRowView(holder.rootView)
            if (listRowView == null) {
                if (attempt < maxAttempts) {
                    holder.rootView.postDelayed({ registerAutoSlide(holder, attempt + 1) }, retryDelayMs)
                }
                return@post
            }

            val gridView: HorizontalGridView = listRowView.gridView
            val itemCount = gridView.adapter?.itemCount ?: 0
            if (itemCount <= 1) {
                HeroCarouselAutoSlideController.unregisterRow(rowId)
                return@post
            }

            val autoRotateEnabled = keepExpandedWhenUnfocused && railCommonData.screenWidget?.autoRotate == true
            if (!autoRotateEnabled) {
                HeroCarouselAutoSlideController.unregisterRow(rowId)
                return@post
            }

            val duration = railCommonData.screenWidget?.autoRotateDuration?.takeIf { it > 0 } ?: 5
            val intervalMs = duration * 1000L

            HeroCarouselAutoSlideController.registerRow(
                rowId = rowId,
                gridView = gridView,
                itemCount = itemCount,
                autoRotateEnabled = true,
                intervalMs = intervalMs
            )
        }
    }

    private fun findListRowView(view: View): ListRowView? {
        var current: View? = view
        while (current != null) {
            if (current is ListRowView) return current
            current = current.parent as? View
        }
        return null
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

    private fun clearBasicDetails(holder: CardViewHolder) {
        holder.basicDetailsLayout.visibility = View.GONE
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
        holder.boundItem = null

        Glide.with(holder.poster).clear(holder.poster)
        Glide.with(holder.backdrop).clear(holder.backdrop)
        holder.poster.setImageDrawable(null)
        holder.backdrop.setImageDrawable(null)
        holder.revertToPoster()
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as? CardViewHolder ?: return
        if (activeHolder === holder) {
            stopActiveVideo()
        }
        // If this was the pinned expanded card, clear the pin and collapse it
        if (lastExpandedHolder === holder) {
            collapseLastExpanded()
        }
        cancelPendingAutoplay()
        holder.anim?.cancel()
        holder.anim = null
        try {
            Glide.with(holder.poster).clear(holder.poster)
            Glide.with(holder.backdrop).clear(holder.backdrop)
        } catch (_: Exception) {}
    }
}
