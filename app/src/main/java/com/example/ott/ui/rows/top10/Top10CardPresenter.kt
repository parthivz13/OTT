package com.example.ott.ui.rows.top10

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.example.ott.R

class Top10CardPresenter : Presenter() {

    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val root: View = view
        val rankNumber: RankNumberView = view.findViewById(R.id.top10_rank_number)
        val cardSurface: CardView = view.findViewById(R.id.top10_card_surface)
        val image: ImageView = view.findViewById(R.id.top10_image)
        val title: TextView = view.findViewById(R.id.top10_title)
        val focusRing: View = view.findViewById(R.id.top10_focus_ring)
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_top10_item, parent, false)
        val holder = ViewHolder(view)
        val density = parent.resources.displayMetrics.density

        view.setOnFocusChangeListener { _, hasFocus ->
            holder.rankNumber.isCardFocused = hasFocus
            holder.root.animate().cancel()
            holder.focusRing.animate().cancel()

            if (hasFocus) {
                holder.root.bringToFront()
                (holder.root.parent as? ViewGroup)?.invalidate()

                holder.cardSurface.cardElevation = 10f * density
                holder.focusRing.elevation = 14f * density
                holder.focusRing.translationZ = 4f * density
                holder.focusRing.alpha = 1f
                holder.focusRing.visibility = View.VISIBLE
                holder.focusRing.bringToFront()
            } else {
                holder.cardSurface.cardElevation = 3f * density
                holder.focusRing.elevation = 0f
                holder.focusRing.translationZ = 0f
                holder.focusRing.alpha = 0f
                holder.focusRing.visibility = View.INVISIBLE
            }
        }

        return holder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as ViewHolder
        val top10Item = item as? Top10Item ?: return

        holder.rankNumber.rankText = top10Item.rank.toString()
        holder.title.text = top10Item.title.name

        Glide.with(holder.image)
            .load(top10Item.title.posterUrl)
            .centerCrop()
            .placeholder(R.drawable.poster_placeholder)
            .into(holder.image)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as ViewHolder
        Glide.with(holder.image).clear(holder.image)
        holder.root.scaleX = 1.0f
        holder.root.scaleY = 1.0f
        holder.focusRing.alpha = 0f
    }
}
