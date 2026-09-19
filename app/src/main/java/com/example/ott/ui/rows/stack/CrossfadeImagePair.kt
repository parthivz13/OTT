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

    // What's currently showing (or in flight) - re-requesting the same source while a shift
    // animation runs would otherwise reset the visible ImageView to alpha 0 and blank the card
    // for a frame while Glide redecodes an image it already fetched moments ago.
    private var currentUrl: String? = null
    private var currentColor: Int? = null

    init {
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container.addView(back, params)
        container.addView(front, params)
    }

    fun load(url: String, placeholderColor: Int) {
        if (url == currentUrl) return
        currentUrl = url
        currentColor = null

        val incoming = if (showingFront) back else front
        val outgoing = if (showingFront) front else back
        showingFront = !showingFront

        incoming.animate().cancel()
        // A fresh (never-shown-before) source can take a while over the network - show a neutral
        // placeholder immediately instead of leaving the incoming view blank/black until it
        // resolves, matching the outgoing view's alpha=1 look so the crossfade doesn't dip to 0.
        val hasExistingContent = outgoing.drawable != null
        incoming.setImageDrawable(ColorDrawable(placeholderColor))
        outgoing.animate().cancel()

        if (!hasExistingContent) {
            incoming.alpha = 1f
            outgoing.alpha = 0f
        }

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
                ): Boolean {
                    incoming.alpha = 1f
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (hasExistingContent) {
                        crossfade(incoming, outgoing)
                    } else {
                        incoming.alpha = 1f
                        outgoing.alpha = 0f
                    }
                    return false
                }
            })
            .into(incoming)
    }

    fun setColor(color: Int) {
        if (color == currentColor) return
        currentColor = color
        currentUrl = null

        val incoming = if (showingFront) back else front
        val outgoing = if (showingFront) front else back
        showingFront = !showingFront

        incoming.animate().cancel()
        outgoing.animate().cancel()
        Glide.with(incoming).clear(incoming)
        val hasExistingContent = outgoing.drawable != null
        incoming.setImageDrawable(ColorDrawable(color))
        if (hasExistingContent) {
            crossfade(incoming, outgoing)
        } else {
            incoming.alpha = 1f
            outgoing.alpha = 0f
        }
    }

    private fun crossfade(incoming: View, outgoing: View) {
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE
        incoming.animate().alpha(1f).setDuration(durationMs).start()
        outgoing.animate().alpha(0f).setDuration(durationMs).start()
    }
}
