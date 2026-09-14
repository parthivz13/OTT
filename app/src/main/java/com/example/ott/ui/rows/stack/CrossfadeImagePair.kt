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

/**
 * Two stacked ImageViews inside [container], loaded independently and crossfaded via
 * [View.animate] alpha rather than Glide's Drawable-level crossfade. Reusing a live Drawable
 * Glide handed to one request as the placeholder for the next (e.g. `imageView.drawable`) is a
 * known Glide anti-pattern: the underlying Bitmap can be recycled back into Glide's pool by a
 * newer request while a View still holds a reference to it, crashing with "Canvas: trying to use
 * a recycled bitmap" under rapid navigation. Keeping every load on its own View sidesteps that
 * entirely - there's no Drawable reused across requests, so there's nothing to recycle out from
 * under a still-visible frame.
 */
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

    /** Loads [url] into whichever view is currently hidden, then crossfades it to the front. */
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

    /** Shows a flat color instantly (no crossfade) - used when a title has no backdrop art. */
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
