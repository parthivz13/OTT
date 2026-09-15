package com.example.ott.ui.rows.stack

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

// Two stacked ImageViews, crossfaded via alpha instead of reusing a Drawable across Glide
// requests - avoids "Canvas: trying to use a recycled bitmap" under rapid navigation.
class CrossfadeImagePair(container: FrameLayout, private val durationMs: Long = 220L) {

    private val front = ImageView(container.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val back = ImageView(container.context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
    }
    private var showingFront = true

    init {
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container.addView(back, params)
        container.addView(front, params)
    }

    fun load(url: String) {
        val incoming = if (showingFront) back else front
        val outgoing = if (showingFront) front else back
        showingFront = !showingFront

        incoming.animate().cancel()
        outgoing.animate().cancel()

        Glide.with(incoming)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    crossfade(incoming, outgoing)
                    return false
                }
            })
            .into(incoming)
    }

    fun setColor(color: Int) {
        val incoming = if (showingFront) back else front
        val outgoing = if (showingFront) front else back
        showingFront = !showingFront

        incoming.animate().cancel()
        outgoing.animate().cancel()
        Glide.with(incoming).clear(incoming)
        incoming.setImageDrawable(ColorDrawable(color))
        crossfade(incoming, outgoing)
    }

    private fun crossfade(incoming: View, outgoing: View) {
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE
        incoming.animate().alpha(1f).setDuration(durationMs).start()
        outgoing.animate().alpha(0f).setDuration(durationMs).start()
    }
}
