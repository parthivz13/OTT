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
class CrossfadeImagePair(container: FrameLayout, private val durationMs: Long = 220L) {
    private val front = ImageView(container.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val back = ImageView(container.context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
    }
    private var showingFront = true
    private var currentUrl: String? = null
    private var currentColor: Int? = null
    init {
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container.addView(back, params)
        container.addView(front, params)
    }
    fun load(url: String, placeholderColor: Int, onBitmapReady: ((android.graphics.Bitmap) -> Unit)? = null) {
        if (url == currentUrl) return
        currentUrl = url
        currentColor = null
        val incoming = if (showingFront) back else front
        val outgoing = if (showingFront) front else back
        showingFront = !showingFront
        incoming.animate().cancel()
        val hasExistingContent = outgoing.drawable != null
        incoming.setImageDrawable(ColorDrawable(placeholderColor))
        outgoing.animate().cancel()
        if (!hasExistingContent) {
            incoming.alpha = 1f
            outgoing.alpha = 0f
        }
        Glide.with(incoming.context.applicationContext)
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
                    if (resource is android.graphics.drawable.BitmapDrawable) {
                        onBitmapReady?.invoke(resource.bitmap)
                    }
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
        try {
            Glide.with(incoming.context.applicationContext).clear(incoming)
        } catch (_: Exception) {}
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