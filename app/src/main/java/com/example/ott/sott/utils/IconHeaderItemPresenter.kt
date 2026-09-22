package com.example.ott.sott.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowHeaderPresenter
import com.bumptech.glide.Glide
import com.example.ott.R

class IconHeaderItem(
    id: Long,
    name: String,
    val iconUrl: String? = null
) : HeaderItem(id, name)

class IconHeaderItemPresenter : RowHeaderPresenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val root = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_header, parent, false)
        return ViewHolder(root)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as? ViewHolder ?: return
        val headerItem = item as? HeaderItem ?: return

        holder.titleView.text = headerItem.name

        if (headerItem is IconHeaderItem && !headerItem.iconUrl.isNullOrEmpty()) {
            holder.iconView.visibility = View.VISIBLE
            Glide.with(holder.iconView)
                .load(headerItem.iconUrl)
                .into(holder.iconView)
        } else {
            holder.iconView.visibility = View.GONE
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as? ViewHolder ?: return
        holder.titleView.text = null
        holder.iconView.setImageDrawable(null)
    }

    class ViewHolder(view: View) : RowHeaderPresenter.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.header_title)
        val iconView: ImageView = view.findViewById(R.id.header_icon)
    }
}
