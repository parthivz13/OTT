package com.example.ott.sott.presenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.ott.R
import com.example.ott.data.model.Title
import com.example.ott.sott.models.CustomAsset
import com.example.ott.sott.networking.RailCommonData
import com.example.ott.types.Asset
import com.example.ott.types.StringValue
import com.example.ott.ui.rows.top10.RankNumberView

// -------------------------------------------------------------
// 1. Landscape Card Presenter (16:9)
// -------------------------------------------------------------
open class LandScapeCardPresenter(
    val railCommonData: RailCommonData? = null
) : Presenter() {

    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val root: View = view
        val surface: CardView = view.findViewById(R.id.card_surface)
        val image: ImageView = view.findViewById(R.id.card_image)
        val title: TextView = view.findViewById(R.id.card_title)
        val focusRing: View = view.findViewById(R.id.card_focus_ring)
        val skeletonBg: View = view.findViewById(R.id.skeleton_bg)
        val progress: ProgressBar = view.findViewById(R.id.card_progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_landscape_item, parent, false)
        val holder = ViewHolder(view)

        view.setOnFocusChangeListener { _, hasFocus ->
            holder.focusRing.animate().cancel()
            holder.focusRing.animate()
                .alpha(if (hasFocus) 1f else 0f)
                .setDuration(160L)
                .start()

            val elevation = if (hasFocus) 8f else 3f
            val density = parent.resources.displayMetrics.density
            holder.surface.cardElevation = elevation * density
        }

        return holder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as ViewHolder
        bindCard(holder, item)
    }

    protected open fun bindCard(holder: ViewHolder, item: Any?) {
        when (item) {
            is CustomAsset -> {
                // Dummy Skeleton state during initial loading
                holder.skeletonBg.visibility = View.VISIBLE
                holder.image.setImageDrawable(null)
                holder.title.text = ""
                holder.progress.visibility = View.GONE
            }
            is Asset -> {
                holder.skeletonBg.visibility = View.GONE
                holder.title.text = item.name.orEmpty()
                val imageUrl = item.images?.firstOrNull()?.url
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(holder.image)
                        .load(imageUrl)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.image)
                } else {
                    holder.image.setImageDrawable(null)
                }
                holder.progress.visibility = View.GONE
            }
            is Title -> {
                holder.skeletonBg.visibility = View.GONE
                holder.title.text = item.name
                Glide.with(holder.image)
                    .load(item.backdropUrl ?: item.posterUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.image)
                holder.progress.visibility = View.GONE
            }
            else -> {
                holder.skeletonBg.visibility = View.VISIBLE
                holder.title.text = ""
                holder.image.setImageDrawable(null)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as ViewHolder
        Glide.with(holder.image).clear(holder.image)
        holder.focusRing.alpha = 0f
    }
}

// -------------------------------------------------------------
// 2. Continue Watching Card Presenter (Landscape + Progress Bar)
// -------------------------------------------------------------
class ContinueWatchingPresenter(
    railCommonData: RailCommonData? = null
) : LandScapeCardPresenter(railCommonData) {

    override fun bindCard(holder: ViewHolder, item: Any?) {
        super.bindCard(holder, item)
        if (item is Asset || item is Title) {
            holder.progress.visibility = View.VISIBLE
            holder.progress.progress = 65
        } else {
            holder.progress.visibility = View.GONE
        }
    }
}

// -------------------------------------------------------------
// 3. Top 10 Card Presenter (Giant 3D Rank Number + Poster)
// -------------------------------------------------------------
open class TopTenCardPresenter(
    val railCommonData: RailCommonData? = null
) : Presenter() {

    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val root: View = view
        val rankNumber: RankNumberView = view.findViewById(R.id.top10_rank_number)
        val surface: CardView = view.findViewById(R.id.top10_card_surface)
        val image: ImageView = view.findViewById(R.id.top10_image)
        val title: TextView = view.findViewById(R.id.top10_title)
        val focusRing: View = view.findViewById(R.id.top10_focus_ring)
        val badge: TextView = view.findViewById(R.id.top10_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_top10_item, parent, false)
        val holder = ViewHolder(view)

        view.setOnFocusChangeListener { _, hasFocus ->
            holder.rankNumber.isCardFocused = hasFocus
            holder.focusRing.animate().cancel()
            holder.focusRing.animate()
                .alpha(if (hasFocus) 1f else 0f)
                .setDuration(160L)
                .start()

            val elevation = if (hasFocus) 8f else 3f
            val density = parent.resources.displayMetrics.density
            holder.surface.cardElevation = elevation * density
        }

        return holder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as ViewHolder
        when (item) {
            is CustomAsset -> {
                // Dummy state: Show rank from item.Id, but surface is skeleton
                val rankInt = (item.Id as? Int)?.plus(1) ?: 1
                holder.rankNumber.rankText = rankInt.toString()
                holder.title.text = ""
                holder.badge.visibility = View.GONE
                holder.image.setImageDrawable(null)
            }
            is Asset -> {
                val pos = (item.metas["PositionForRail"] as? StringValue)?.value
                    ?: (item.metas["PositionForRail"] as? String)
                    ?: "1"
                holder.rankNumber.rankText = pos
                holder.title.text = item.name.orEmpty()
                holder.badge.visibility = View.VISIBLE
                val imageUrl = item.images?.firstOrNull()?.url
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(holder.image)
                        .load(imageUrl)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.image)
                } else {
                    holder.image.setImageDrawable(null)
                }
            }
            is Title -> {
                holder.rankNumber.rankText = "1"
                holder.title.text = item.name
                holder.badge.visibility = View.VISIBLE
                Glide.with(holder.image)
                    .load(item.posterUrl ?: item.backdropUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.image)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as ViewHolder
        Glide.with(holder.image).clear(holder.image)
        holder.focusRing.alpha = 0f
    }
}

class TopTenPortraitPresenter(railCommonData: RailCommonData? = null) : TopTenCardPresenter(railCommonData)
class TopTenPortraitNineSixteenPresenter(railCommonData: RailCommonData? = null) : TopTenCardPresenter(railCommonData)

// -------------------------------------------------------------
// 4. Portrait Card Presenter (2:3 or 9:16)
// -------------------------------------------------------------
open class ItemPresenter(
    val railCommonData: RailCommonData? = null
) : Presenter() {

    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val root: View = view
        val surface: CardView = view.findViewById(R.id.card_portrait_surface)
        val image: ImageView = view.findViewById(R.id.card_portrait_image)
        val title: TextView = view.findViewById(R.id.card_portrait_title)
        val focusRing: View = view.findViewById(R.id.card_portrait_focus_ring)
        val skeletonBg: View = view.findViewById(R.id.skeleton_portrait_bg)
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_portrait_item, parent, false)
        val holder = ViewHolder(view)

        view.setOnFocusChangeListener { _, hasFocus ->
            holder.focusRing.animate().cancel()
            holder.focusRing.animate()
                .alpha(if (hasFocus) 1f else 0f)
                .setDuration(160L)
                .start()

            val elevation = if (hasFocus) 8f else 3f
            val density = parent.resources.displayMetrics.density
            holder.surface.cardElevation = elevation * density
        }

        return holder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as ViewHolder
        when (item) {
            is CustomAsset -> {
                holder.skeletonBg.visibility = View.VISIBLE
                holder.image.setImageDrawable(null)
                holder.title.text = ""
            }
            is Asset -> {
                holder.skeletonBg.visibility = View.GONE
                holder.title.text = item.name.orEmpty()
                val imageUrl = item.images?.firstOrNull()?.url
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(holder.image)
                        .load(imageUrl)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.image)
                } else {
                    holder.image.setImageDrawable(null)
                }
            }
            is Title -> {
                holder.skeletonBg.visibility = View.GONE
                holder.title.text = item.name
                Glide.with(holder.image)
                    .load(item.posterUrl ?: item.backdropUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.image)
            }
            else -> {
                holder.skeletonBg.visibility = View.VISIBLE
                holder.title.text = ""
                holder.image.setImageDrawable(null)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as ViewHolder
        Glide.with(holder.image).clear(holder.image)
        holder.focusRing.alpha = 0f
    }
}

class NineSixteenCardPresenter(railCommonData: RailCommonData? = null) : ItemPresenter(railCommonData)
class SquareMediumPresenter(railCommonData: RailCommonData? = null) : ItemPresenter(railCommonData)
class CircleCardPresenter(railCommonData: RailCommonData? = null) : ItemPresenter(railCommonData)
class CircleTransparentCardPresenter(railCommonData: RailCommonData? = null, val bgColor: String = "") : ItemPresenter(railCommonData)
class LandScapeAutoPlayCardPresenter(railCommonData: RailCommonData? = null) : LandScapeCardPresenter(railCommonData)
class HeroCardPresenter(railCommonData: RailCommonData? = null) : LandScapeCardPresenter(railCommonData)
