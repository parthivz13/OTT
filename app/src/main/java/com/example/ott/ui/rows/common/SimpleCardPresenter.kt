package com.example.ott.ui.rows.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.example.ott.R
import com.example.ott.data.model.Title

/** Plain poster card for standard (non-overlapping) rails - image + title below. On focus, shows
 * only a white ring around the poster; no scaling, so nothing overlaps neighboring rows. */
class SimpleCardPresenter : Presenter() {

    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val focusRing: View = view.findViewById(R.id.simple_card_focus_ring)
        val image: ImageView = view.findViewById(R.id.simple_card_image)
        val title: TextView = view.findViewById(R.id.simple_card_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_simple_item, parent, false)
        val holder = ViewHolder(view)
        view.setOnFocusChangeListener { _, hasFocus ->
            holder.focusRing.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
        }
        return holder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as ViewHolder
        val title = item as Title
        holder.title.text = title.name
        Glide.with(holder.image)
            .load(title.posterUrl)
            .centerCrop()
            .placeholder(R.drawable.poster_placeholder)
            .into(holder.image)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as ViewHolder
        Glide.with(holder.image).clear(holder.image)
    }
}
