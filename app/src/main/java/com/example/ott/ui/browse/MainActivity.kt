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
            val listFragment = com.example.ott.sott.presenter.ListFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, listFragment)
                .commit()
        }

        setUpSideNav()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                val before = currentFocus
                val handled = super.dispatchKeyEvent(event)
                // Leanback's HorizontalGridView moves focus to the previous card asynchronously, so
                // checking currentFocus immediately would misfire on every interior column. Defer to
                // the next message loop turn, and only escape to the nav if the key truly went unhandled.
                if (!handled && before != null && !isNavDescendant(before)) {
                    before.post {
                        if (currentFocus === before) {
                            (selectedNavIcon ?: findViewById<View>(R.id.nav_home)).requestFocus()
                        }
                    }
                }
                return handled
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val focused = currentFocus
                if (focused != null && isNavDescendant(focused)) {
                    closeSideNav()
                    val hero = findViewById<View>(R.id.hero_card)
                    if (hero != null && hero.isShown) {
                        hero.requestFocus()
                        return true
                    }
                    val listFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment)
                    if (listFragment?.view != null) {
                        listFragment.view?.requestFocus()
                        return true
                    }
                }
            }
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
                // Defer until focus settles, so moving between nav items doesn't flicker closed for a frame.
                view.post { updateNavExpansion() }
            }

            icon.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    icon.performClick()
                    true
                } else {
                    false
                }
            }

            icon.setOnClickListener {
                Log.d(TAG, "Nav clicked: $label ($iconId)")
                selectNavItem(icon)
                closeSideNav()
                val listFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? com.example.ott.sott.presenter.ListFragment
                when (iconId) {
                    R.id.nav_home -> {
                        listFragment?.loadTab(com.example.ott.sott.presenter.ScreenType.HOME)
                        listFragment?.view?.post {
                            findViewById<View>(R.id.hero_card)?.requestFocus() ?: listFragment.view?.requestFocus()
                        }
                    }
                    R.id.nav_movies -> {
                        listFragment?.loadTab(com.example.ott.sott.presenter.ScreenType.MOVIES)
                        listFragment?.view?.post {
                            listFragment.view?.requestFocus()
                        }
                    }
                    else -> {
                        Toast.makeText(this, "$label - coming soon", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        selectNavItem(findViewById(R.id.nav_home))
    }

    fun closeSideNav() {
        navExpanded = false
        animateNavWidth(COLLAPSED_WIDTH_DP)
        listOf(
            R.id.nav_home_label,
            R.id.nav_search_label,
            R.id.nav_movies_label,
            R.id.nav_shows_label,
            R.id.nav_profile_label
        ).forEach { labelId ->
            findViewById<TextView>(labelId)?.let { animateLabel(it, false) }
        }
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
        // Cancel any in-flight resize, or rapid focus changes stack animators fighting over width.
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
        private const val COLLAPSED_WIDTH_DP = 64
        private const val EXPANDED_WIDTH_DP = 240
        private const val NAV_ANIM_DURATION = 220L
    }
}
