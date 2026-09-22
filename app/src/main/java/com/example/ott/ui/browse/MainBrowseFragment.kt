package com.example.ott.ui.browse

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.ott.R
import com.example.ott.data.model.Title
import com.example.ott.ui.rows.hero.HeroCarouselCardPresenter
import com.example.ott.ui.rows.stack.HeroCarouselRowPresenter
import com.example.ott.ui.rows.top10.Top10Item

/**
 * Thin host fragment — all row/rail construction lives exclusively in ListFragment.
 *
 * This fragment is kept only to hold the Leanback BackgroundManager integration,
 * player release on destroy, and the background-update debounce triggered by
 * item selection.  It no longer builds any rows itself.
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private lateinit var backgroundManager: BackgroundManager
    private val backgroundHandler = Handler(Looper.getMainLooper())
    private var pendingBackgroundUpdate: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = ContextCompat.getColor(requireContext(), R.color.brand_accent)

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val title = when (item) {
                is Title -> item
                is Top10Item -> item.title
                else -> null
            }
            if (title != null) {
                Log.d(TAG, "Clicked: ${title.name}")
                android.widget.Toast.makeText(
                    requireContext(),
                    "Playing: ${title.name} (${title.qualityTag})",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            val title = when (item) {
                is Title -> item
                is Top10Item -> item.title
                else -> null
            }
            if (title != null) scheduleBackgroundUpdate(title)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundManager = BackgroundManager.getInstance(requireActivity())

        // Remove the title group so rows start at the very top.
        view.findViewById<View>(androidx.leanback.R.id.browse_title_group)?.let { titleGroup ->
            (titleGroup.parent as? ViewGroup)?.removeView(titleGroup)
        }
        setTitleView(null)
    }

    override fun onStart() {
        super.onStart()
        rowsSupportFragment?.verticalGridView?.let { gridView ->
            val density = resources.displayMetrics.density
            gridView.windowAlignment = androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_LOW_EDGE
            gridView.setWindowAlignmentOffset((28 * density).toInt())
            gridView.windowAlignmentOffsetPercent =
                androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        }
    }

    override fun onDestroyView() {
        HeroCarouselRowPresenter.releasePlayer()
        HeroCarouselCardPresenter.releasePlayer()
        pendingBackgroundUpdate?.let { backgroundHandler.removeCallbacks(it) }
        super.onDestroyView()
    }

    // Debounced so fast D-pad scrolling doesn't fire a Glide load per frame.
    private fun scheduleBackgroundUpdate(title: Title) {
        pendingBackgroundUpdate?.let { backgroundHandler.removeCallbacks(it) }
        val runnable = Runnable { updateBackground(title) }
        pendingBackgroundUpdate = runnable
        backgroundHandler.postDelayed(runnable, BACKGROUND_UPDATE_DELAY_MS)
    }

    private fun updateBackground(title: Title) {
        val url = title.backdropUrl ?: title.posterUrl ?: return
        val metrics = resources.displayMetrics
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>(metrics.widthPixels, metrics.heightPixels) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    backgroundManager.setBitmap(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }

    companion object {
        private const val TAG = "MainBrowseFragment"
        private const val BACKGROUND_UPDATE_DELAY_MS = 300L
    }
}
