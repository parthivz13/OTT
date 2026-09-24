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
import com.example.ott.sott.presenter.ListFragment
import com.example.ott.sott.presenter.ScreenType

class MainActivity : FragmentActivity() {

    private lateinit var navContainer: FrameLayout
    private var navExpanded = false
    private var selectedNavPill: View? = null
    private var selectedIndicator: View? = null
    private var widthAnimator: ValueAnimator? = null

    private var currentBackdropUrl: String? = null
    private val backdropImageView: ImageView? by lazy { findViewById(R.id.iv_global_backdrop) }
    private val backdropScrimView: View? by lazy { findViewById(R.id.view_backdrop_scrim) }

    private data class NavItemConfig(
        val pillId: Int,
        val indicatorId: Int,
        val iconId: Int,
        val labelId: Int,
        val screenType: ScreenType,
        val title: String
    )

    private val navItems by lazy {
        listOf(
            NavItemConfig(R.id.nav_connect_phone, R.id.nav_connect_phone_indicator, R.id.nav_connect_phone_icon, R.id.nav_connect_phone_label, ScreenType.CONNECT_PHONE, "Connect Phone"),
            NavItemConfig(R.id.nav_search, R.id.nav_search_indicator, R.id.nav_search_icon, R.id.nav_search_label, ScreenType.SEARCH, "Search"),
            NavItemConfig(R.id.nav_home, R.id.nav_home_indicator, R.id.nav_home_icon, R.id.nav_home_label, ScreenType.HOME, "Home"),
            NavItemConfig(R.id.nav_tv, R.id.nav_tv_indicator, R.id.nav_tv_icon, R.id.nav_tv_label, ScreenType.TV, "TV"),
            NavItemConfig(R.id.nav_movies, R.id.nav_movies_indicator, R.id.nav_movies_icon, R.id.nav_movies_label, ScreenType.MOVIES, "Movies"),
            NavItemConfig(R.id.nav_sports, R.id.nav_sports_indicator, R.id.nav_sports_icon, R.id.nav_sports_label, ScreenType.SPORTS, "Sports"),
            NavItemConfig(R.id.nav_categories, R.id.nav_categories_indicator, R.id.nav_categories_icon, R.id.nav_categories_label, ScreenType.CATEGORIES, "Categories"),
            NavItemConfig(R.id.nav_my_space, R.id.nav_my_space_indicator, R.id.nav_my_space_icon, R.id.nav_my_space_label, ScreenType.MY_SPACE, "My Space")
        )
    }

    fun updateGlobalBackdrop(imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            clearGlobalBackdrop()
            return
        }
        if (currentBackdropUrl == imageUrl) return
        currentBackdropUrl = imageUrl

        val iv = backdropImageView ?: return
        val scrim = backdropScrimView ?: return

        scrim.animate().cancel()
        scrim.animate().alpha(1f).setDuration(300).start()

        com.bumptech.glide.Glide.with(this)
            .load(imageUrl)
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
            .into(iv)

        iv.animate().cancel()
        iv.animate().alpha(1f).setDuration(300).start()
    }

    fun clearGlobalBackdrop() {
        if (currentBackdropUrl == null) return
        currentBackdropUrl = null

        val iv = backdropImageView ?: return
        val scrim = backdropScrimView ?: return

        iv.animate().cancel()
        iv.animate().alpha(0f).setDuration(300).withEndAction {
            iv.setImageDrawable(null)
        }.start()

        scrim.animate().cancel()
        scrim.animate().alpha(0f).setDuration(300).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        BackgroundManager.getInstance(this).attach(window)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            val listFragment = ListFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, listFragment)
                .commit()
        }

        setUpSideNav()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun focusSelectedNavItem(): Boolean {
        val target = selectedNavPill ?: findViewById<View>(R.id.nav_home)
        return target?.requestFocus() ?: false
    }

    @Suppress("UNCHECKED_CAST")
    private fun getCarouselPosition(view: View?): Int? {
        if (view == null) return null
        val provider = view.getTag(R.id.hero_card) as? (() -> Int)
        if (provider != null) return provider.invoke()
        if (view.id == R.id.hero_card) {
            val p = view.getTag(R.id.hero_card) as? (() -> Int)
            return p?.invoke() ?: 0
        }
        var parent = view.parent
        while (parent != null) {
            if (parent is View && parent.id == R.id.hero_card) {
                val p = parent.getTag(R.id.hero_card) as? (() -> Int)
                return p?.invoke() ?: 0
            }
            parent = (parent as? View)?.parent
        }
        return null
    }

    private data class RowFocusInfo(val recyclerView: androidx.recyclerview.widget.RecyclerView, val position: Int)

    private fun findRowAdapterPosition(view: View?): RowFocusInfo? {
        if (view == null) return null
        var current: View? = view
        var parent = current?.parent
        while (parent != null) {
            if (parent is androidx.recyclerview.widget.RecyclerView) {
                // Ignore the vertical grid view that hosts entire rows
                if (parent is androidx.leanback.widget.VerticalGridView) {
                    return null
                }
                val child = current ?: return null
                val pos = parent.getChildAdapterPosition(child)
                if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    return RowFocusInfo(parent, pos)
                }
            }
            current = parent as? View
            parent = current?.parent
        }
        return null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                val before = currentFocus

                // Check if focus is already inside the side nav
                if (before != null && isNavDescendant(before)) {
                    return super.dispatchKeyEvent(event)
                }

                // 1. CAROUSEL CHECK:
                // Check carousel position first! If carousel is not at first position (pos > 0),
                // it MUST step backwards to the first position before opening the side nav!
                val carouselPos = getCarouselPosition(before)
                if (carouselPos != null) {
                    if (carouselPos > 0) {
                        // Carousel is at position > 0: let it advance backwards to position 0
                        return super.dispatchKeyEvent(event)
                    }
                    // Carousel is ALREADY at first position (pos == 0):
                    // Pressing left now opens the side nav!
                    focusSelectedNavItem()
                    return true
                }

                // 2. RECYCLERVIEW / ROW CHECK:
                // If focus is in a horizontal rail and NOT at the first item (pos > 0),
                // move left between cards within the row.
                val rowInfo = findRowAdapterPosition(before)
                if (rowInfo != null && rowInfo.position > 0) {
                    return super.dispatchKeyEvent(event)
                }

                val handled = super.dispatchKeyEvent(event)
                val after = currentFocus

                if (before != null && !isNavDescendant(before)) {
                    if (after != null && isNavDescendant(after)) {
                        // Focus entered side nav: guarantee it lands on the active selected tab
                        val target = selectedNavPill ?: findViewById<View>(R.id.nav_home)
                        if (target != null && after !== target) {
                            target.requestFocus()
                        }
                        return true
                    } else if (!handled || (rowInfo?.position == 0 && after === before)) {
                        // Already at first item of the row and pressing left escapes to side nav
                        focusSelectedNavItem()
                        return true
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
                    val listFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? ListFragment
                    if (listFragment != null) {
                        if (listFragment.requestChildFocus()) return true
                        if (listFragment.view != null) {
                            listFragment.view?.requestFocus()
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setUpSideNav() {
        navContainer = findViewById(R.id.nav_container)

        navItems.forEach { item ->
            val pill = findViewById<View>(item.pillId)
            val indicator = findViewById<View>(item.indicatorId)
            val labelView = findViewById<TextView>(item.labelId)

            // Custom gradient fill + gradient stroke drawable (dissolves cleanly before curve)
            pill.background = com.example.ott.ui.navigation.NavPillDrawable(this)

            pill.setOnFocusChangeListener { v, hasFocus ->
                // Fluid scale micro-interaction
                v.animate()
                    .scaleX(if (hasFocus) 1.04f else 1.0f)
                    .scaleY(if (hasFocus) 1.04f else 1.0f)
                    .setDuration(160)
                    .start()

                // Defer until focus settles, so moving between nav items doesn't flicker closed
                v.post { updateNavExpansion() }
            }

            pill.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    pill.performClick()
                    true
                } else {
                    false
                }
            }

            pill.setOnClickListener {
                Log.d(TAG, "Nav clicked: ${item.title} (${item.pillId})")
                selectNavItem(pill, indicator)
                closeSideNav()

                val listFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? ListFragment
                listFragment?.loadTab(item.screenType)

                when (item.screenType) {
                    ScreenType.HOME -> {
                        listFragment?.view?.post {
                            findViewById<View>(R.id.hero_card)?.requestFocus() ?: listFragment.view?.requestFocus()
                        }
                    }
                    ScreenType.MOVIES -> {
                        listFragment?.view?.post {
                            listFragment.view?.requestFocus()
                        }
                    }
                    else -> {
                        Toast.makeText(this, "${item.title} section", Toast.LENGTH_SHORT).show()
                        listFragment?.view?.post {
                            listFragment.view?.requestFocus()
                        }
                    }
                }
            }
        }

        // Default active tab is Home
        val homePill = findViewById<View>(R.id.nav_home)
        val homeIndicator = findViewById<View>(R.id.nav_home_indicator)
        selectNavItem(homePill, homeIndicator)
    }

    fun closeSideNav() {
        navExpanded = false
        animateNavWidth(COLLAPSED_WIDTH_DP)
        navItems.forEach { item ->
            findViewById<TextView>(item.labelId)?.let { animateLabel(it, false) }
        }
    }

    /** Persistent active screen indicator - independent of transient D-pad focus. */
    private fun selectNavItem(pill: View, indicator: View) {
        val oldPill = selectedNavPill
        selectedNavPill?.isSelected = false
        selectedIndicator?.visibility = View.INVISIBLE

        pill.isSelected = true
        indicator.visibility = View.VISIBLE

        selectedNavPill = pill
        selectedIndicator = indicator

        oldPill?.invalidate()
        pill.invalidate()
    }

    private fun updateNavExpansion() {
        val focused = currentFocus
        val shouldExpand = focused != null && isNavDescendant(focused)
        if (shouldExpand == navExpanded) return
        navExpanded = shouldExpand
        animateNavWidth(if (shouldExpand) EXPANDED_WIDTH_DP else COLLAPSED_WIDTH_DP)
        navItems.forEach { item ->
            findViewById<TextView>(item.labelId)?.let { labelView ->
                animateLabel(labelView, shouldExpand)
            }
        }
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
            label.translationX = -12f
            label.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(NAV_ANIM_DURATION)
                .start()
        } else {
            label.animate()
                .alpha(0f)
                .translationX(-8f)
                .setDuration(NAV_ANIM_DURATION)
                .withEndAction { label.visibility = View.GONE }
                .start()
        }
    }

    private val navArchView: View? by lazy { findViewById(R.id.nav_arch_view) }

    private fun animateNavWidth(targetDp: Int) {
        widthAnimator?.cancel()
        val density = resources.displayMetrics.density
        val startPx = navContainer.width.takeIf { it > 0 } ?: (COLLAPSED_WIDTH_DP * density).toInt()
        val endPx = (targetDp * density).toInt()
        widthAnimator = ValueAnimator.ofInt(startPx, endPx).apply {
            duration = NAV_ANIM_DURATION
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                navContainer.layoutParams = navContainer.layoutParams.apply {
                    width = anim.animatedValue as Int
                }
                navArchView?.invalidate()
            }
            start()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val COLLAPSED_WIDTH_DP = 68
        private const val EXPANDED_WIDTH_DP = 226
        private const val NAV_ANIM_DURATION = 200L

        var instance: MainActivity? = null
            private set
    }
}
