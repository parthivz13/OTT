package com.example.ott.ui.navigation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.ott.R

/**
 * Self-contained, modular Side Navigation component designed for Android TV OTT applications.
 *
 * Features:
 * - Signature curved arch backdrop ([NavArchView]).
 * - Dynamic customizable menu items and bottom profile item.
 * - Dedicated [setTopImage] function to dynamically set the top brand logo.
 * - Glassmorphic pills with custom gradient fill and strokes ([NavPillDrawable]).
 * - Smooth expansion/collapse width animations with animated label transitions.
 * - Fluid focus scale micro-interactions.
 * - Precise TV D-Pad handling:
 *     * When expanded: DPAD_UP, DPAD_DOWN, and DPAD_LEFT keep the sidebar open.
 *     * Closing ONLY occurs on item selection (DPAD_CENTER/ENTER/Click) or DPAD_RIGHT.
 *
 * Easy drop-in usage for any Android TV project:
 * ```xml
 * <com.example.ott.ui.navigation.SideNavView
 *     android:id="@+id/side_nav_view"
 *     android:layout_width="68dp"
 *     android:layout_height="match_parent" />
 * ```
 */
class SideNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Model representing a side navigation item. */
    data class SideNavItem(
        val id: String,
        val title: String,
        @DrawableRes val iconRes: Int,
        val isBottomItem: Boolean = false,
        val tag: Any? = null
    )

    private val density = resources.displayMetrics.density
    private var collapsedWidthPx = (COLLAPSED_WIDTH_DP * density).toInt()
    private var expandedWidthPx = (EXPANDED_WIDTH_DP * density).toInt()

    private var navExpanded = false
    private var widthAnimator: ValueAnimator? = null

    private var selectedItemId: String? = null
    private var selectedPillView: View? = null
    private var selectedIndicatorView: View? = null

    private var onItemSelectedListener: ((SideNavItem) -> Unit)? = null
    private var onNavStateChangeListener: ((isExpanded: Boolean) -> Unit)? = null
    private var onRightExitListener: (() -> Boolean)? = null

    private val navArchView: NavArchView
    private val mainContentLayout: LinearLayout
    private val topBrandImageView: ImageView
    private val topItemsContainer: LinearLayout
    private val bottomItemsContainer: LinearLayout

    private val itemViewsMap = mutableMapOf<String, ItemViewHolder>()
    private val itemList = mutableListOf<SideNavItem>()

    private class ItemViewHolder(
        val item: SideNavItem,
        val pillView: FrameLayout,
        val indicatorView: View,
        val iconView: ImageView,
        val labelView: TextView
    )

    init {
        clipChildren = false
        clipToPadding = false

        // 1. Curved Glassmorphic Arch Backdrop
        navArchView = NavArchView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(navArchView)

        // 2. Main vertical layout
        mainContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            val padH = (10f * density).toInt()
            val padTop = (18f * density).toInt()
            val padBottom = (16f * density).toInt()
            setPadding(padH, padTop, padH, padBottom)
            clipChildren = false
            clipToPadding = false
        }
        addView(mainContentLayout)

        // 3. Top Brand Image / Logo
        val brandSize = (24f * density).toInt()
        val brandMarginStart = (12f * density).toInt()
        val brandMarginTop = (2f * density).toInt()
        val brandMarginBottom = (16f * density).toInt()

        topBrandImageView = ImageView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(brandSize, brandSize).apply {
                marginStart = brandMarginStart
                topMargin = brandMarginTop
                bottomMargin = brandMarginBottom
            }
            contentDescription = "Brand Emblem"
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Default placeholder logo if present in project
            val defaultRes = context.resources.getIdentifier("ic_brand_spark", "drawable", context.packageName)
            if (defaultRes != 0) {
                setImageResource(defaultRes)
            }
        }
        mainContentLayout.addView(topBrandImageView)

        // 4. Top Menu Items Container
        topItemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }
        mainContentLayout.addView(topItemsContainer)

        // 5. Flexible Spacer to push bottom item(s) to the bottom
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1.0f)
        }
        mainContentLayout.addView(spacer)

        // 6. Bottom Menu Items Container
        bottomItemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }
        mainContentLayout.addView(bottomItemsContainer)
    }

    // =========================================================================
    // Public APIs
    // =========================================================================

    /**
     * Set the top side brand image / logo directly via Drawable resource ID.
     */
    fun setTopImage(@DrawableRes resId: Int) {
        topBrandImageView.setImageResource(resId)
        topBrandImageView.visibility = View.VISIBLE
    }

    /**
     * Set the top side brand image / logo directly via Drawable.
     */
    fun setTopImage(drawable: Drawable?) {
        topBrandImageView.setImageDrawable(drawable)
        topBrandImageView.visibility = if (drawable != null) View.VISIBLE else View.GONE
    }

    /**
     * Set the top side brand image / logo directly via Bitmap.
     */
    fun setTopImageBitmap(bitmap: Bitmap?) {
        topBrandImageView.setImageBitmap(bitmap)
        topBrandImageView.visibility = if (bitmap != null) View.VISIBLE else View.GONE
    }

    /**
     * Controls visibility of the top side image / logo.
     */
    fun setTopImageVisible(visible: Boolean) {
        topBrandImageView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Get the top side ImageView for advanced customizations (e.g. Glide loading).
     */
    fun getTopImageView(): ImageView = topBrandImageView

    /**
     * Set all navigation items dynamically.
     */
    fun setItems(items: List<SideNavItem>) {
        itemList.clear()
        itemList.addAll(items)
        topItemsContainer.removeAllViews()
        bottomItemsContainer.removeAllViews()
        itemViewsMap.clear()

        items.forEach { item ->
            val holder = createItemViewHolder(item)
            itemViewsMap[item.id] = holder
            if (item.isBottomItem) {
                bottomItemsContainer.addView(holder.pillView)
            } else {
                topItemsContainer.addView(holder.pillView)
            }
        }

        // Re-apply selection if available, or default to first item
        if (selectedItemId != null && itemViewsMap.containsKey(selectedItemId)) {
            setSelectedItemId(selectedItemId, triggerCallback = false)
        } else if (items.isNotEmpty()) {
            val defaultItem = items.firstOrNull { it.id == "home" } ?: items.first()
            setSelectedItemId(defaultItem.id, triggerCallback = false)
        }
    }

    /**
     * Set active selected item by ID.
     */
    fun setSelectedItemId(id: String?, triggerCallback: Boolean = false) {
        selectedItemId = id
        val targetHolder = id?.let { itemViewsMap[it] }

        // Deselect previous
        selectedPillView?.isSelected = false
        selectedIndicatorView?.visibility = View.INVISIBLE
        val oldPill = selectedPillView

        if (targetHolder != null) {
            targetHolder.pillView.isSelected = true
            targetHolder.indicatorView.visibility = View.VISIBLE
            selectedPillView = targetHolder.pillView
            selectedIndicatorView = targetHolder.indicatorView

            oldPill?.invalidate()
            targetHolder.pillView.invalidate()

            if (triggerCallback) {
                onItemSelectedListener?.invoke(targetHolder.item)
            }
        }
    }

    /**
     * Get currently selected item ID.
     */
    fun getSelectedItemId(): String? = selectedItemId

    /**
     * Get currently selected item.
     */
    fun getSelectedItem(): SideNavItem? = selectedItemId?.let { id -> itemList.firstOrNull { it.id == id } }

    /**
     * Register a callback to be invoked when an item is selected.
     */
    fun setOnItemSelectedListener(listener: (SideNavItem) -> Unit) {
        this.onItemSelectedListener = listener
    }

    /**
     * Register a callback to be invoked when navigation expands or collapses.
     */
    fun setOnNavStateChangeListener(listener: (isExpanded: Boolean) -> Unit) {
        this.onNavStateChangeListener = listener
    }

    /**
     * Register a callback to be invoked when user presses DPAD_RIGHT from within the sidebar.
     * Return true if handled (e.g. focused content grid), false to let system handle focus.
     */
    fun setOnRightExitListener(listener: () -> Boolean) {
        this.onRightExitListener = listener
    }

    /**
     * Request focus on the active selected nav item (or first item).
     */
    fun focusSelectedNavItem(): Boolean {
        val target = selectedPillView ?: topItemsContainer.getChildAt(0)
        return target?.requestFocus() ?: false
    }

    /**
     * Whether the sidebar is currently in expanded mode.
     */
    fun isExpanded(): Boolean = navExpanded

    /**
     * Check if focus is currently within this sidebar.
     */
    fun isNavFocused(): Boolean {
        val f = findFocus()
        return f != null && (f === this || isNavDescendant(f))
    }

    /**
     * Open (expand) the sidebar.
     */
    fun openSideNav() {
        if (!navExpanded) {
            navExpanded = true
            animateNavWidth(EXPANDED_WIDTH_DP)
            animateAllLabels(true)
            onNavStateChangeListener?.invoke(true)
        }
    }

    /**
     * Close (collapse) the sidebar.
     */
    fun closeSideNav() {
        if (navExpanded) {
            navExpanded = false
            animateNavWidth(COLLAPSED_WIDTH_DP)
            animateAllLabels(false)
            onNavStateChangeListener?.invoke(false)
        }
    }

    /**
     * Toggle sidebar expansion state.
     */
    fun toggleSideNav() {
        if (navExpanded) closeSideNav() else openSideNav()
    }

    // =========================================================================
    // Strict D-Pad Key Handling
    // =========================================================================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    // Navigate to previous item in the sidebar; do NOT close the sidebar!
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        val prev = findNextItemFocus(current, isUp = true)
                        if (prev != null) {
                            prev.requestFocus()
                        }
                        return true // Consumed: sidebar stays open
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    // Navigate to next item in the sidebar; do NOT close the sidebar!
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        val next = findNextItemFocus(current, isUp = false)
                        if (next != null) {
                            next.requestFocus()
                        }
                        return true // Consumed: sidebar stays open
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    // Already at the leftmost element: do NOT close or escape!
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        return true // Consumed: prevent sidebar collapse or focus loss
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Moving RIGHT from expanded sidebar:
                    // ONLY here and on item select should the sidebar close!
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        closeSideNav()
                        if (onRightExitListener?.invoke() == true) {
                            return true
                        }
                        return false // Let focus move to the right content naturally
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        current.performClick()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // =========================================================================
    // Internal Item Construction & Animation
    // =========================================================================

    private fun createItemViewHolder(item: SideNavItem): ItemViewHolder {
        val pillHeight = (44f * density).toInt()
        val pillMarginBottom = (6f * density).toInt()

        // 1. Pill container FrameLayout
        val pill = FrameLayout(context).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            clipChildren = false
            clipToPadding = false
            background = NavPillDrawable(context)

            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                pillHeight
            ).apply {
                bottomMargin = pillMarginBottom
            }
        }

        // 2. Cyan Active Indicator Line
        val indicatorWidth = (3f * density).toInt()
        val indicatorHeight = (16f * density).toInt()
        val indicatorMarginStart = (2f * density).toInt()

        val indicator = View(context).apply {
            id = View.generateViewId()
            val indRes = context.resources.getIdentifier("nav_active_indicator", "drawable", context.packageName)
            if (indRes != 0) {
                background = ContextCompat.getDrawable(context, indRes)
            } else {
                setBackgroundColor(Color.parseColor("#00F0FF"))
            }
            visibility = View.INVISIBLE
            layoutParams = LayoutParams(indicatorWidth, indicatorHeight).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = indicatorMarginStart
            }
        }
        pill.addView(indicator)

        // 3. Content horizontal row
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            isDuplicateParentStateEnabled = true
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        }

        // 4. Icon
        val iconSize = (24f * density).toInt()
        val iconMarginStart = (12f * density).toInt()
        val iconMarginEnd = (12f * density).toInt()

        val iconView = ImageView(context).apply {
            id = View.generateViewId()
            setImageResource(item.iconRes)
            contentDescription = item.title
            isDuplicateParentStateEnabled = true
            val tintColorList = ContextCompat.getColorStateList(context, R.color.nav_icon_tint)
            if (tintColorList != null) {
                imageTintList = tintColorList
            }
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = iconMarginStart
                marginEnd = iconMarginEnd
            }
        }
        row.addView(iconView)

        // 5. Label
        val labelMarginStart = (4f * density).toInt()
        val labelMarginEnd = (16f * density).toInt()

        val labelView = TextView(context).apply {
            id = View.generateViewId()
            text = item.title
            textSize = 14f
            maxLines = 1
            isDuplicateParentStateEnabled = true
            val labelColorList = ContextCompat.getColorStateList(context, R.color.nav_label_color)
            if (labelColorList != null) {
                setTextColor(labelColorList)
            } else {
                setTextColor(Color.WHITE)
            }
            visibility = if (navExpanded) View.VISIBLE else View.GONE
            alpha = if (navExpanded) 1f else 0f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = labelMarginStart
                marginEnd = labelMarginEnd
            }
        }
        row.addView(labelView)
        pill.addView(row)

        // Micro-interactions & Focus Handlers
        pill.setOnFocusChangeListener { v, hasFocus ->
            // Subtle fluid focus scale animation
            v.animate()
                .scaleX(if (hasFocus) 1.04f else 1.0f)
                .scaleY(if (hasFocus) 1.04f else 1.0f)
                .setDuration(160)
                .start()

            // When focus moves to any item in this sidebar, ensure expanded mode
            if (hasFocus) {
                openSideNav()
            }
        }

        // Click / Enter: Select item & CLOSE sidebar
        pill.setOnClickListener {
            setSelectedItemId(item.id, triggerCallback = true)
            closeSideNav()
        }

        return ItemViewHolder(item, pill, indicator, iconView, labelView)
    }

    private fun findNextItemFocus(currentFocus: View, isUp: Boolean): View? {
        val allPills = mutableListOf<View>()
        for (i in 0 until topItemsContainer.childCount) {
            allPills.add(topItemsContainer.getChildAt(i))
        }
        for (i in 0 until bottomItemsContainer.childCount) {
            allPills.add(bottomItemsContainer.getChildAt(i))
        }

        var currentIndex = -1
        for (i in allPills.indices) {
            val p = allPills[i]
            if (p === currentFocus || isViewInside(currentFocus, p)) {
                currentIndex = i
                break
            }
        }

        if (currentIndex == -1) return null

        val targetIndex = if (isUp) currentIndex - 1 else currentIndex + 1
        return if (targetIndex in allPills.indices) allPills[targetIndex] else null
    }

    private fun isViewInside(child: View, parent: View): Boolean {
        var p = child.parent
        while (p != null) {
            if (p === parent) return true
            p = p.parent
        }
        return false
    }

    private fun isNavDescendant(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }

    private fun animateAllLabels(show: Boolean) {
        itemViewsMap.values.forEach { holder ->
            val label = holder.labelView
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
    }

    private fun animateNavWidth(targetDp: Int) {
        widthAnimator?.cancel()
        val startPx = width.takeIf { it > 0 } ?: (COLLAPSED_WIDTH_DP * density).toInt()
        val endPx = (targetDp * density).toInt()
        widthAnimator = ValueAnimator.ofInt(startPx, endPx).apply {
            duration = NAV_ANIM_DURATION
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                layoutParams = layoutParams.apply {
                    width = anim.animatedValue as Int
                }
                navArchView.invalidate()
            }
            start()
        }
    }

    companion object {
        const val COLLAPSED_WIDTH_DP = 68
        const val EXPANDED_WIDTH_DP = 226
        const val NAV_ANIM_DURATION = 200L
    }
}
