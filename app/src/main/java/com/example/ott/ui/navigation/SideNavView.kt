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
 * Self-contained, modular Side Navigation component designed for Android TV OTT applications,
 * faithfully replicating the JioHotstar navigation architecture with:
 * - Vertically centered menu items between top logo and bottom profile.
 * - Distinct fluid animations for when Home is active vs when an Inner Tab (TV, Movies, Sports) is active.
 * - Dynamic parent icon replacement when collapsed without icon-swap flicker during expansion.
 * - Hierarchical parent/sub-item navigation structure.
 * - Signature curved arch backdrop ([NavArchView]).
 * - Glassmorphic pills with custom gradient fill and strokes ([NavPillDrawable]).
 * - Strict TV D-Pad handling:
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

    /** Model representing a side navigation item (root or nested sub-item). */
    data class SideNavItem(
        val id: String,
        val title: String,
        @param:DrawableRes val iconRes: Int,
        val isBottomItem: Boolean = false,
        val isSubItem: Boolean = false,
        val parentId: String? = null,
        val subItems: List<SideNavItem> = emptyList(),
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
    private val centerItemsContainer: LinearLayout
    private val bottomItemsContainer: LinearLayout

    private val itemViewsMap = mutableMapOf<String, ItemViewHolder>()
    private val allFlatItems = mutableListOf<SideNavItem>()

    private class ItemViewHolder(
        val item: SideNavItem,
        val pillView: FrameLayout,
        val indicatorView: View,
        val iconView: ImageView,
        val labelView: TextView,
        @DrawableRes val originalIconRes: Int
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
        val brandMarginBottom = (4f * density).toInt()

        topBrandImageView = ImageView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(brandSize, brandSize).apply {
                marginStart = brandMarginStart
                topMargin = brandMarginTop
                bottomMargin = brandMarginBottom
            }
            contentDescription = "Brand Emblem"
            scaleType = ImageView.ScaleType.FIT_CENTER
            val defaultRes = context.resources.getIdentifier("ic_brand_spark", "drawable", context.packageName)
            if (defaultRes != 0) {
                setImageResource(defaultRes)
            }
        }
        mainContentLayout.addView(topBrandImageView)

        // 4. Top flexible spacer to vertically center menu items (Matching JioHotstar)
        val topSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1.0f)
        }
        mainContentLayout.addView(topSpacer)

        // 5. Center Menu Items Container (Vertically centered)
        centerItemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }
        mainContentLayout.addView(centerItemsContainer)

        // 6. Bottom flexible spacer to balance vertical center and push profile to bottom
        val bottomSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1.0f)
        }
        mainContentLayout.addView(bottomSpacer)

        // 7. Bottom Menu Items Container (My Space profile)
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
     * Get the top side ImageView for advanced customizations.
     */
    fun getTopImageView(): ImageView = topBrandImageView

    /**
     * Set all navigation items dynamically with support for nested sub-items.
     */
    fun setItems(items: List<SideNavItem>) {
        allFlatItems.clear()
        centerItemsContainer.removeAllViews()
        bottomItemsContainer.removeAllViews()
        itemViewsMap.clear()

        fun addRecursive(item: SideNavItem, isSub: Boolean = false, parentId: String? = null) {
            val normalized = item.copy(isSubItem = isSub, parentId = parentId)
            allFlatItems.add(normalized)
            val holder = createItemViewHolder(normalized)
            itemViewsMap[normalized.id] = holder

            if (normalized.isBottomItem) {
                bottomItemsContainer.addView(holder.pillView)
            } else {
                centerItemsContainer.addView(holder.pillView)
            }

            item.subItems.forEach { sub ->
                addRecursive(sub, isSub = true, parentId = item.id)
            }
        }

        items.forEach { item ->
            addRecursive(item, isSub = item.isSubItem, parentId = item.parentId)
        }

        // Re-apply selection or default
        if (selectedItemId != null && itemViewsMap.containsKey(selectedItemId)) {
            setSelectedItemId(selectedItemId, triggerCallback = false)
        } else if (allFlatItems.isNotEmpty()) {
            val defaultItem = allFlatItems.firstOrNull { it.id == "home" } ?: allFlatItems.first()
            setSelectedItemId(defaultItem.id, triggerCallback = false)
        }

        updateInitialState()
    }

    /**
     * Set active selected item by ID.
     */
    fun setSelectedItemId(id: String?, triggerCallback: Boolean = false) {
        selectedItemId = id
        val targetHolder = id?.let { itemViewsMap[it] }

        // Clear all previous selections
        itemViewsMap.values.forEach { holder ->
            holder.pillView.isSelected = false
            holder.indicatorView.visibility = View.INVISIBLE
            holder.pillView.invalidate()
        }

        if (targetHolder != null) {
            targetHolder.pillView.isSelected = true
            selectedPillView = targetHolder.pillView
            selectedIndicatorView = targetHolder.indicatorView

            val item = targetHolder.item
            if (item.isSubItem && item.parentId != null) {
                val parentHolder = itemViewsMap[item.parentId]
                if (navExpanded) {
                    // In expanded mode: Parent stays Home with Home icon; sub-item has indicator
                    parentHolder?.iconView?.setImageResource(parentHolder.originalIconRes)
                    parentHolder?.pillView?.isSelected = false
                    parentHolder?.indicatorView?.visibility = View.INVISIBLE
                    targetHolder.indicatorView.visibility = View.VISIBLE
                } else {
                    // In collapsed rail mode: Parent slot displays the selected sub-item's icon
                    parentHolder?.iconView?.setImageResource(item.iconRes)
                    parentHolder?.pillView?.isSelected = true
                    parentHolder?.indicatorView?.visibility = View.VISIBLE
                    targetHolder.indicatorView.visibility = View.INVISIBLE
                }
                parentHolder?.pillView?.invalidate()
            } else {
                // Root item selected: restore original parent icon
                targetHolder.iconView.setImageResource(targetHolder.originalIconRes)
                targetHolder.indicatorView.visibility = View.VISIBLE

                // Restore other parent root items to their default icons
                itemViewsMap.values.filter { !it.item.isSubItem }.forEach { rootHolder ->
                    if (rootHolder !== targetHolder) {
                        rootHolder.iconView.setImageResource(rootHolder.originalIconRes)
                    }
                }
            }

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
    fun getSelectedItem(): SideNavItem? = selectedItemId?.let { id -> allFlatItems.firstOrNull { it.id == id } }

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
     * Request focus on the active selected nav item (or parent if sub-item).
     */
    fun focusSelectedNavItem(): Boolean {
        var target = selectedPillView
        if (target != null && target.visibility == View.VISIBLE) {
            return target.requestFocus()
        }
        val curItem = getSelectedItem()
        if (curItem?.parentId != null) {
            val parentHolder = itemViewsMap[curItem.parentId]
            if (parentHolder?.pillView?.visibility == View.VISIBLE) {
                return parentHolder.pillView.requestFocus()
            }
        }
        val firstChild = centerItemsContainer.getChildAt(0)
        return firstChild?.requestFocus() ?: false
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
     * Open (expand) the sidebar with distinct smooth animations depending on selection state.
     */
    fun openSideNav() {
        if (!navExpanded) {
            navExpanded = true
            handleOpenAnimation()
            animateNavWidth(EXPANDED_WIDTH_DP)
            onNavStateChangeListener?.invoke(true)
        }
    }

    /**
     * Close (collapse) the sidebar with smooth transitions.
     */
    fun closeSideNav() {
        if (navExpanded) {
            navExpanded = false
            handleCloseAnimation()
            animateNavWidth(COLLAPSED_WIDTH_DP)
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
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        return true // Consumed: prevent sidebar collapse
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val current = findFocus()
                    if (current != null && isNavDescendant(current)) {
                        closeSideNav()
                        if (onRightExitListener?.invoke() == true) {
                            return true
                        }
                        return false // Let focus move to right content naturally
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
        val pillHeight = (if (item.isSubItem) 38f else 44f) * density
        val pillMarginBottom = (if (item.isSubItem) 4f else 6f) * density

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
                pillHeight.toInt()
            ).apply {
                bottomMargin = pillMarginBottom.toInt()
            }
        }

        // 2. Cyan Active Indicator Line
        val indicatorWidth = (3f * density).toInt()
        val indicatorHeight = (if (item.isSubItem) 12f else 16f) * density
        val indicatorMarginStart = (if (item.isSubItem) 18f else 2f) * density

        val indicator = View(context).apply {
            id = View.generateViewId()
            val indRes = context.resources.getIdentifier("nav_active_indicator", "drawable", context.packageName)
            if (indRes != 0) {
                background = ContextCompat.getDrawable(context, indRes)
            } else {
                setBackgroundColor(Color.parseColor("#00F0FF"))
            }
            visibility = View.INVISIBLE
            layoutParams = LayoutParams(indicatorWidth, indicatorHeight.toInt()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = indicatorMarginStart.toInt()
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
        val iconSize = (if (item.isSubItem) 20f else 24f) * density
        val iconMarginStart = (if (item.isSubItem) 28f else 12f) * density
        val iconMarginEnd = (if (item.isSubItem) 10f else 12f) * density

        val iconView = ImageView(context).apply {
            id = View.generateViewId()
            setImageResource(item.iconRes)
            contentDescription = item.title
            isDuplicateParentStateEnabled = true
            val tintColorList = ContextCompat.getColorStateList(context, R.color.nav_icon_tint)
            if (tintColorList != null) {
                imageTintList = tintColorList
            }
            layoutParams = LinearLayout.LayoutParams(iconSize.toInt(), iconSize.toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = iconMarginStart.toInt()
                marginEnd = iconMarginEnd.toInt()
            }
        }
        row.addView(iconView)

        // 5. Label
        val labelMarginStart = (4f * density).toInt()
        val labelMarginEnd = (16f * density).toInt()

        val labelView = TextView(context).apply {
            id = View.generateViewId()
            text = item.title
            textSize = if (item.isSubItem) 13f else 14f
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
            v.animate()
                .scaleX(if (hasFocus) 1.04f else 1.0f)
                .scaleY(if (hasFocus) 1.04f else 1.0f)
                .setDuration(160)
                .start()

            if (hasFocus) {
                openSideNav()
            }
        }

        // Click / Enter: Select item & CLOSE sidebar
        pill.setOnClickListener {
            setSelectedItemId(item.id, triggerCallback = true)
            closeSideNav()
        }

        return ItemViewHolder(item, pill, indicator, iconView, labelView, item.iconRes)
    }

    private fun updateInitialState() {
        itemViewsMap.values.forEach { holder ->
            if (holder.item.isSubItem) {
                holder.pillView.visibility = if (navExpanded) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Handles opening animation:
     * - If Home is active: Staggered accordion slide-down & fade-in for sub-items.
     * - If an Inner Item (TV, Movies, Sports) is active: Sub-items reveal directly at their respective rows
     *   while Home immediately displays its Home icon/text with zero icon replacement flicker.
     */
    private fun handleOpenAnimation() {
        val curItem = getSelectedItem()
        val isSubItemSelected = curItem?.isSubItem == true

        if (isSubItemSelected) {
            // Case 2: Inner item selected (TV / Movies / Sports)
            // 1. Immediately restore parent (Home) to original Home icon & normal state
            val parentId = curItem?.parentId
            if (parentId != null) {
                val parentHolder = itemViewsMap[parentId]
                parentHolder?.iconView?.setImageResource(parentHolder.originalIconRes)
                parentHolder?.indicatorView?.visibility = View.INVISIBLE
                parentHolder?.pillView?.isSelected = false
                parentHolder?.pillView?.invalidate()
            }

            // 2. Reveal sub-items smoothly in-place
            itemViewsMap.values.forEach { holder ->
                if (holder.item.isSubItem) {
                    holder.pillView.visibility = View.VISIBLE
                    holder.pillView.translationY = 0f
                    holder.pillView.alpha = 1f
                }
            }

            // 3. Highlight the selected sub-item
            val targetHolder = curItem?.id?.let { itemViewsMap[it] }
            targetHolder?.pillView?.isSelected = true
            targetHolder?.indicatorView?.visibility = View.VISIBLE
            targetHolder?.pillView?.invalidate()

            // 4. Reveal labels smoothly
            animateAllLabels(true)
        } else {
            // Case 1: Home (or root item) selected
            // 1. Restore root icons
            itemViewsMap.values.filter { !it.item.isSubItem }.forEach { rootHolder ->
                rootHolder.iconView.setImageResource(rootHolder.originalIconRes)
            }

            // 2. Cascade stagger animation for sub-items
            var subIndex = 0
            itemViewsMap.values.forEach { holder ->
                if (holder.item.isSubItem) {
                    holder.pillView.visibility = View.VISIBLE
                    holder.pillView.translationY = -14f * density
                    holder.pillView.alpha = 0f
                    holder.pillView.animate().cancel()
                    holder.pillView.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setStartDelay(subIndex * 35L)
                        .setDuration(200L)
                        .setInterpolator(DecelerateInterpolator(1.6f))
                        .start()
                    subIndex++
                }
            }

            // 3. Reveal labels smoothly
            animateAllLabels(true)
        }
    }

    /**
     * Handles closing animation:
     * - Collapses sub-items and switches parent slot to display active sub-item icon in collapsed rail.
     */
    private fun handleCloseAnimation() {
        val curItem = getSelectedItem()
        val isSubItemSelected = curItem?.isSubItem == true

        if (isSubItemSelected) {
            val parentId = curItem?.parentId
            if (parentId != null) {
                val parentHolder = itemViewsMap[parentId]
                parentHolder?.iconView?.setImageResource(curItem.iconRes)
                parentHolder?.indicatorView?.visibility = View.VISIBLE
                parentHolder?.pillView?.isSelected = true
                parentHolder?.pillView?.invalidate()
            }
        }

        animateAllLabels(false)
    }

    private fun findNextItemFocus(currentFocus: View, isUp: Boolean): View? {
        val visiblePills = mutableListOf<View>()
        for (i in 0 until centerItemsContainer.childCount) {
            val child = centerItemsContainer.getChildAt(i)
            if (child.visibility == View.VISIBLE) visiblePills.add(child)
        }
        for (i in 0 until bottomItemsContainer.childCount) {
            val child = bottomItemsContainer.getChildAt(i)
            if (child.visibility == View.VISIBLE) visiblePills.add(child)
        }

        var currentIndex = -1
        for (i in visiblePills.indices) {
            val p = visiblePills[i]
            if (p === currentFocus || isViewInside(currentFocus, p)) {
                currentIndex = i
                break
            }
        }

        if (currentIndex == -1) return null

        val targetIndex = if (isUp) currentIndex - 1 else currentIndex + 1
        return if (targetIndex in visiblePills.indices) visiblePills[targetIndex] else null
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
                    .withEndAction {
                        label.visibility = View.GONE
                        if (!navExpanded) {
                            updateInitialState()
                        }
                    }
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
