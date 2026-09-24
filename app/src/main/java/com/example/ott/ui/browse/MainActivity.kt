package com.example.ott.ui.browse

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import com.example.ott.R
import com.example.ott.sott.presenter.ListFragment
import com.example.ott.sott.presenter.ScreenType
import com.example.ott.ui.navigation.SideNavView

class MainActivity : FragmentActivity() {

    private lateinit var sideNavView: SideNavView

    private var currentBackdropUrl: String? = null
    private val backdropImageView: ImageView? by lazy { findViewById(R.id.iv_global_backdrop) }
    private val backdropScrimView: View? by lazy { findViewById(R.id.view_backdrop_scrim) }

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
        return sideNavView.focusSelectedNavItem()
    }

    fun closeSideNav() {
        sideNavView.closeSideNav()
    }

    private fun setUpSideNav() {
        sideNavView = findViewById(R.id.side_nav_view)

        // 1. Direct function to set top side brand image
        sideNavView.setTopImage(R.drawable.ic_brand_spark)

        // 2. Configure navigation items with nested sub-items matching JioHotstar
        sideNavView.setItems(
            listOf(
                SideNavView.SideNavItem("connect_phone", getString(R.string.nav_connect_phone), R.drawable.ic_nav_connect_phone, tag = ScreenType.CONNECT_PHONE),
                SideNavView.SideNavItem("search", getString(R.string.nav_search), R.drawable.ic_nav_search, tag = ScreenType.SEARCH),
                SideNavView.SideNavItem(
                    id = "home",
                    title = getString(R.string.nav_home),
                    iconRes = R.drawable.ic_nav_home,
                    tag = ScreenType.HOME,
                    subItems = listOf(
                        SideNavView.SideNavItem("tv", getString(R.string.nav_tv), R.drawable.ic_nav_tv, tag = ScreenType.TV),
                        SideNavView.SideNavItem("movies", getString(R.string.nav_movies), R.drawable.ic_nav_movies, tag = ScreenType.MOVIES),
                        SideNavView.SideNavItem("sports", getString(R.string.nav_sports), R.drawable.ic_nav_sports, tag = ScreenType.SPORTS)
                    )
                ),
                SideNavView.SideNavItem("categories", getString(R.string.nav_categories), R.drawable.ic_nav_categories, tag = ScreenType.CATEGORIES),
                SideNavView.SideNavItem("my_space", getString(R.string.nav_my_space), R.drawable.ic_nav_myspace_avatar, isBottomItem = true, tag = ScreenType.MY_SPACE)
            )
        )

        // 3. Set default active tab
        sideNavView.setSelectedItemId("home")

        // 4. Handle selection
        sideNavView.setOnItemSelectedListener { item ->
            val screenType = item.tag as? ScreenType ?: ScreenType.HOME
            handleNavSelection(item, screenType)
        }

        // 5. Handle Right-Exit from expanded sidebar into content grid
        sideNavView.setOnRightExitListener {
            focusMainContent()
        }
    }

    private fun handleNavSelection(item: SideNavView.SideNavItem, screenType: ScreenType) {
        Log.d(TAG, "Nav selected: ${item.title} (${item.id})")
        val listFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? ListFragment
        listFragment?.loadTab(screenType)

        when (screenType) {
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

    private fun focusMainContent(): Boolean {
        val hero = findViewById<View>(R.id.hero_card)
        if (hero != null && hero.isShown) {
            return hero.requestFocus()
        }
        val listFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? ListFragment
        if (listFragment != null) {
            if (listFragment.requestChildFocus()) return true
            if (listFragment.view != null) {
                return listFragment.view?.requestFocus() ?: false
            }
        }
        return false
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

                // If focus is already inside side nav, let SideNavView handle D-pad navigation
                if (before != null && sideNavView.isNavFocused()) {
                    return sideNavView.dispatchKeyEvent(event)
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
                    // Pressing left now opens the side nav on selected item!
                    sideNavView.focusSelectedNavItem()
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

                if (before != null && !sideNavView.isNavFocused()) {
                    if (after != null && sideNavView.isNavFocused()) {
                        // Focus entered side nav: guarantee it lands on the active selected tab
                        sideNavView.focusSelectedNavItem()
                        return true
                    } else if (!handled || (rowInfo?.position == 0 && after === before)) {
                        // Already at first item of the row and pressing left escapes to side nav
                        sideNavView.focusSelectedNavItem()
                        return true
                    }
                }
                return handled
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (sideNavView.isNavFocused()) {
                    sideNavView.closeSideNav()
                    return focusMainContent()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val TAG = "MainActivity"

        var instance: MainActivity? = null
            private set
    }
}
