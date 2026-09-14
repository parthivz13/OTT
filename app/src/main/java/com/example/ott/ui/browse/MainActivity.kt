package com.example.ott.ui.browse

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import com.example.ott.R

class MainActivity : FragmentActivity() {

    private lateinit var navContainer: FrameLayout
    private var navExpanded = false
    private var selectedNavIcon: ImageView? = null
    private var widthAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackgroundManager.getInstance(this).attach(window)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainBrowseFragment())
                .commit()
        }

        setUpSideNav()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
            val before = currentFocus
            val handled = super.dispatchKeyEvent(event)
            // A view can legitimately consume DPAD_LEFT without moving Android focus at all -
            // e.g. the hero carousel swaps its displayed slide in place. Only escape to the side
            // nav when the key event actually went unhandled, not merely when focus didn't move.
            //
            // Leanback's HorizontalGridView can move focus to the previous card asynchronously
            // (posted, not synchronous within this call), so checking currentFocus immediately
            // here would misfire on every interior column, not just column 0. Defer the check to
            // the next message loop turn, after any focus change Leanback queued has applied.
            if (!handled && before != null && !isNavDescendant(before)) {
                before.post {
                    if (currentFocus === before) {
                        navContainer.requestFocus()
                    }
                }
            }
            return handled
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setUpSideNav() {
        navContainer = findViewById(R.id.nav_container)

        val items = listOf(
            Triple(R.id.nav_home, R.id.nav_home_label, "Home"),
            Triple(R.id.nav_search, R.id.nav_search_label, "Search"),
            Triple(R.id.nav_movies, R.id.nav_movies_label, "Movies"),
            Triple(R.id.nav_shows, R.id.nav_shows_label, "TV Shows"),
            Triple(R.id.nav_profile, R.id.nav_profile_label, "Profile")
        )

        items.forEach { (iconId, labelId, label) ->
            val icon = findViewById<ImageView>(iconId)
            val labelView = findViewById<TextView>(labelId)

            icon.setOnFocusChangeListener { view, hasFocus ->
                animateLabel(labelView, hasFocus)
                // Check after this focus change has fully settled, so moving focus between
                // two nav items (one loses focus, the next gains it) collapses-then-immediately
                // re-expands instead of flickering closed for a frame.
                view.post { updateNavExpansion() }
            }

            icon.setOnClickListener {
                if (iconId == R.id.nav_home) {
                    Log.d(TAG, "Nav: Home (already showing)")
                } else {
                    Toast.makeText(this, "$label - coming soon", Toast.LENGTH_SHORT).show()
                }
                selectNavItem(icon)
            }
        }

        selectNavItem(findViewById(R.id.nav_home))
    }

    /** Persistent "current section" indicator - independent of transient D-pad focus. */
    private fun selectNavItem(icon: ImageView) {
        selectedNavIcon?.isSelected = false
        icon.isSelected = true
        selectedNavIcon = icon
    }

    private fun updateNavExpansion() {
        val focused = currentFocus
        val shouldExpand = focused != null && isNavDescendant(focused)
        if (shouldExpand == navExpanded) return
        navExpanded = shouldExpand
        animateNavWidth(if (shouldExpand) EXPANDED_WIDTH_DP else COLLAPSED_WIDTH_DP)
    }

    private fun isNavDescendant(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === navContainer) return true
            parent = parent.parent
        }
        return false
    }

    private fun animateLabel(label: TextView, show: Boolean) {
        label.animate().cancel()
        if (show) {
            label.visibility = View.VISIBLE
            label.animate().alpha(1f).setDuration(NAV_ANIM_DURATION).start()
        } else {
            label.animate().alpha(0f).setDuration(NAV_ANIM_DURATION)
                .withEndAction { label.visibility = View.GONE }
                .start()
        }
    }

    private fun animateNavWidth(targetDp: Int) {
        // Cancel any in-flight resize first - without this, rapidly moving focus in and out of
        // the nav (e.g. flicking through icons) stacks multiple animators fighting over the same
        // width, causing jank or a final width that doesn't match the last real focus state.
        widthAnimator?.cancel()
        val density = resources.displayMetrics.density
        val startPx = navContainer.width.takeIf { it > 0 } ?: (COLLAPSED_WIDTH_DP * density).toInt()
        val endPx = (targetDp * density).toInt()
        widthAnimator = ValueAnimator.ofInt(startPx, endPx).apply {
            duration = NAV_ANIM_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                navContainer.layoutParams = navContainer.layoutParams.apply {
                    width = anim.animatedValue as Int
                }
            }
            start()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val COLLAPSED_WIDTH_DP = 88
        private const val EXPANDED_WIDTH_DP = 240
        private const val NAV_ANIM_DURATION = 220L
    }
}
