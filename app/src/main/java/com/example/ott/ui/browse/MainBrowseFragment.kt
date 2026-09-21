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
import androidx.fragment.app.viewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.ott.R
import com.example.ott.data.model.Title
import com.example.ott.ui.rows.common.SimpleCardPresenter
import com.example.ott.ui.rows.stack.HeroCarouselRow
import com.example.ott.ui.rows.stack.HeroCarouselRowPresenter
import com.example.ott.ui.rows.top10.Top10CardPresenter
import com.example.ott.ui.rows.top10.Top10Item
import com.example.ott.ui.rows.top10.Top10Row
import com.example.ott.ui.rows.top10.Top10RowPresenter

// Rail-specific presenters (e.g. HeroCarouselRowPresenter) are registered via a
// ClassPresenterSelector alongside the stock ListRowPresenter, so new rail designs can be
// added without touching this fragment's plumbing.
class MainBrowseFragment : BrowseSupportFragment() {

    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var backgroundManager: BackgroundManager
    private val backgroundHandler = Handler(Looper.getMainLooper())
    private var pendingBackgroundUpdate: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Must be set before onCreateView() builds the content fragment - setting these any
        // later leaves the content fragment never created, even though adapter looks populated.
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = ContextCompat.getColor(requireContext(), R.color.brand_accent)

        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(HeroCarouselRow::class.java, HeroCarouselRowPresenter())
            addClassPresenter(Top10Row::class.java, Top10RowPresenter())
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }
        rowsAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = rowsAdapter

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

        // setTitleView(null) alone only clears the text - the TitleView still occupies layout
        // space, so remove it entirely to let rows start at the top.
        view.findViewById<View>(androidx.leanback.R.id.browse_title_group)?.let { titleGroup ->
            (titleGroup.parent as? ViewGroup)?.removeView(titleGroup)
        }
        setTitleView(null)

        viewModel.rowGroups.observe(viewLifecycleOwner) { groups ->
            Log.d(TAG, "observed ${groups.size} row groups")
            showRows(groups)
        }
    }

    override fun onStart() {
        super.onStart()
        // BrowseSupportFragment reserves ~167dp above the row list as a window-alignment keyline
        // for the title bar; this is applied programmatically, not via styles, so theme attrs
        // can't override it. Zero it directly now that the title bar is removed.
        rowsSupportFragment?.verticalGridView?.let { gridView ->
            gridView.windowAlignment = androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_NO_EDGE
            gridView.setWindowAlignmentOffset(0)
            gridView.windowAlignmentOffsetPercent = androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        }
    }

    override fun onDestroyView() {
        pendingBackgroundUpdate?.let { backgroundHandler.removeCallbacks(it) }
        super.onDestroyView()
    }

    private fun showRows(groups: List<RowGroup>) {
        rowsAdapter.clear()

        // Hero carousel reuses "Popular Shows" as its content, so exclude that group below
        // to avoid showing it twice.
        val spotlight = groups.firstOrNull()
        spotlight?.let {
            val heroAdapter = ArrayObjectAdapter().apply {
                addAll(0, it.items.take(HERO_CAROUSEL_ASSET_LIMIT))
            }
            val header = HeaderItem(HERO_ROW_ID, it.title)
            rowsAdapter.add(HeroCarouselRow(header, heroAdapter))

            // Netflix-style Top 10 curated row directly below Hero Carousel
            val top10Adapter = ArrayObjectAdapter(Top10CardPresenter()).apply {
                val top10List = it.items.take(10).mapIndexed { idx, title ->
                    Top10Item(rank = idx + 1, title = title)
                }
                addAll(0, top10List)
            }
            val top10Header = HeaderItem(TOP_10_ROW_ID, "🔥 Top 10 Shows Today")
            rowsAdapter.add(Top10Row(top10Header, top10Adapter))
        }

        groups.forEachIndexed { index, group ->
            if (group === spotlight) return@forEachIndexed
            val cardAdapter = ArrayObjectAdapter(SimpleCardPresenter()).apply {
                addAll(0, group.items)
            }
            val header = HeaderItem(index.toLong(), group.title)
            rowsAdapter.add(ListRow(header, cardAdapter))
        }
        Log.d(TAG, "showRows: rowsAdapter now has ${rowsAdapter.size()} row(s)")
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
        private const val HERO_ROW_ID = -1L
        private const val TOP_10_ROW_ID = -2L
        private const val HERO_CAROUSEL_ASSET_LIMIT = 10
    }
}
