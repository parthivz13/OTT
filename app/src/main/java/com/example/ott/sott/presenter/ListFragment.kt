package com.example.ott.sott.presenter

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ListRowView
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.example.ott.R
import com.example.ott.data.repository.HomeScreenRepository
import com.example.ott.sott.models.BaseCategory
import com.example.ott.sott.models.CustomAsset
import com.example.ott.sott.models.NavigationInfoModel
import com.example.ott.sott.models.PredefinePlaylistType
import com.example.ott.sott.networking.RailCommonData
import com.example.ott.sott.utils.IconHeaderItem
import com.example.ott.sott.utils.IconHeaderItemPresenter
import com.example.ott.sott.utils.LogUtils
import com.example.ott.sott.utils.SharedPrefHelper
import com.example.ott.sott.utils.enums.RailTypes
import com.example.ott.types.Asset
import com.example.ott.types.StringValue
import com.example.ott.ui.rows.hero.ExpandableHeroCarouselRow
import com.example.ott.ui.rows.hero.ExpandableHeroCarouselRowPresenter
import com.example.ott.ui.rows.hero.HeroCarouselCardPresenter
import com.example.ott.ui.rows.stack.HeroCarouselRowPresenter
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Collections
import com.example.ott.data.model.Title
import com.example.ott.sott.models.CustomKalturaAsset
import com.example.ott.sott.utils.AppCommonMethod
import com.example.ott.sott.utils.constants.AppConstants
import com.example.ott.ui.browse.MainActivity

enum class ScreenType {
    CONNECT_PHONE,
    SEARCH,
    HOME,
    TV,
    MOVIES,
    SPORTS,
    CATEGORIES,
    MY_SPACE
}

class ListFragment : RowsSupportFragment() {

    private var onItemInteractionListener: OnItemInteractionListener? = null
    private val homeRepository = HomeScreenRepository()

    fun setOnItemInteractionListener(listener: OnItemInteractionListener) {
        onItemInteractionListener = listener
    }

    val hashMap = HashMap<String, NavigationInfoModel>()

    private val listRowPresenter = object : ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE) {
        override fun isUsingDefaultListSelectEffect() = false

        @SuppressLint("RestrictedApi")
        override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
            val viewHolder = super.createRowViewHolder(parent)
            val rowView = viewHolder.view as ListRowView
            val gridView = rowView.gridView

            gridView.itemAnimator = null
            gridView.setSaveChildrenPolicy(BaseGridView.SAVE_NO_CHILD)
            gridView.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ITEM)
            gridView.overScrollMode = View.OVER_SCROLL_NEVER
            gridView.clipChildren = false
            gridView.clipToPadding = false
            gridView.itemAnimator = SmoothGridItemAnimator()
            return viewHolder
        }

        override fun initializeRowViewHolder(holder: RowPresenter.ViewHolder) {
            super.initializeRowViewHolder(holder)
            val listRowHolder = holder as? ListRowPresenter.ViewHolder ?: return
            val gridView = listRowHolder.gridView
            val density = gridView.resources.displayMetrics.density

            gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
            gridView.windowAlignmentOffset = (17 * density).toInt()
            gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            gridView.itemAlignmentOffsetPercent = 0f
            gridView.itemAlignmentOffset = 0
            listRowHolder.view.setPadding(0, listRowHolder.view.paddingTop, listRowHolder.view.paddingRight, listRowHolder.view.paddingBottom)
            gridView.setPadding(0, gridView.paddingTop, gridView.paddingRight, gridView.paddingBottom)
        }

        override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
            super.onBindRowViewHolder(holder, item)
            val listRowHolder = holder as? ListRowPresenter.ViewHolder ?: return
            val gridView = listRowHolder.gridView
            val density = gridView.resources.displayMetrics.density

            gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
            gridView.windowAlignmentOffset = (17 * density).toInt()
            gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            gridView.itemAlignmentOffsetPercent = 0f
            gridView.itemAlignmentOffset = 0
            listRowHolder.view.setPadding(0, listRowHolder.view.paddingTop, listRowHolder.view.paddingRight, listRowHolder.view.paddingBottom)
            gridView.setPadding(0, gridView.paddingTop, gridView.paddingRight, gridView.paddingBottom)
        }
    }.apply {
        shadowEnabled = false
        selectEffectEnabled = false
        headerPresenter = IconHeaderItemPresenter()
    }

    private val heroCarouselRowPresenter = HeroCarouselRowPresenter()
    // Movies carousel: standard expandable behaviour (last card collapses when focus leaves)
    private val expandableHeroRowPresenter = ExpandableHeroCarouselRowPresenter(
        keepExpandedWhenUnfocused = false
    ).apply {
        headerPresenter = IconHeaderItemPresenter()
    }

    var currentScreenType = ScreenType.HOME
        private set

    private fun isHeroCarousel(screenWidget: BaseCategory?): Boolean {
        return currentScreenType == ScreenType.HOME &&
            (screenWidget?.Id == "widget_hero" || (screenWidget?.type == "CAROUSEL" && (screenWidget.displayOrder ?: 0) == 0))
    }

    private val presenterSelector = object : PresenterSelector() {
        override fun getPresenters(): Array<Presenter> {
            return arrayOf(heroCarouselRowPresenter, expandableHeroRowPresenter, listRowPresenter)
        }

        override fun getPresenter(item: Any?): Presenter {
            if (item is ExpandableHeroCarouselRow) {
                return expandableHeroRowPresenter
            }
            val row = item as? ListRow
            val widgetId = row?.contentDescription?.toString()
            val railCommonData = hashMap[widgetId]?.railCommonData
            val railType = railCommonData?.railType
            val screenWidget = railCommonData?.screenWidget

            return when {
                railType == RailTypes.CAROUSEL_LDS_LANDSCAPE -> {
                    if (isHeroCarousel(screenWidget)) {
                        heroCarouselRowPresenter
                    } else {
                        expandableHeroRowPresenter
                    }
                }
                else -> listRowPresenter
            }
        }
    }

    val rowsAdapter = ArrayObjectAdapter(presenterSelector)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter
        verticalGridView?.itemAnimator = SmoothGridItemAnimator()
        setOnItemViewSelectedListener(ItemViewSelectedListener())
        setOnItemViewClickedListener(ItemViewClickListener())

        val alignmentPx = resources.getDimensionPixelSize(R.dimen.row_alignment_offset)
        val density = resources.displayMetrics.density
        verticalGridView?.apply {
            windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
            windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            windowAlignmentOffset = alignmentPx
            itemAlignmentOffset = 0
            itemAlignmentOffsetPercent = 0f
            setItemSpacing((8 * density).toInt())
        }

        setupInstantVerticalNavigation()

        loadHomeScreenRails()
    }

    fun loadTab(screenType: ScreenType) {
        currentScreenType = screenType
        when (screenType) {
            ScreenType.HOME -> loadHomeScreenRails()
            ScreenType.MOVIES -> loadMovieScreenRails()
            ScreenType.TV, ScreenType.SPORTS, ScreenType.CATEGORIES, ScreenType.MY_SPACE, ScreenType.SEARCH, ScreenType.CONNECT_PHONE -> {
                loadHomeScreenRails()
            }
        }
    }

    fun loadHomeScreenRails() {
        currentScreenType = ScreenType.HOME
        resetForNewMenu()

        // 1. Fetch screen configurations
        val configs = homeRepository.getHomeScreenRailConfigs()
        val dummyRails = configs.map { (category, railType) ->
            RailCommonData(
                railType = railType,
                screenWidget = category,
                assets = ArrayList()
            )
        }

        // 2. Set expected count and build 10 dummy items per rail immediately
        setExpectedRailCount(dummyRails.size)
        appendData(dummyRails)

        // 3. Launch independent asynchronous API call for each rail
        configs.forEach { (category, railType) ->
            viewLifecycleOwner.lifecycleScope.launch {
                val railData = homeRepository.fetchRailData(category, railType)
                updateRow(railData)
            }
        }
    }

    fun loadMovieScreenRails() {
        currentScreenType = ScreenType.MOVIES
        resetForNewMenu()

        val configs = homeRepository.getMovieScreenRailConfigs()
        val dummyRails = configs.map { (category, railType) ->
            RailCommonData(
                railType = railType,
                screenWidget = category,
                assets = ArrayList()
            )
        }

        setExpectedRailCount(dummyRails.size)
        appendData(dummyRails)

        configs.forEach { (category, railType) ->
            viewLifecycleOwner.lifecycleScope.launch {
                val railData = homeRepository.fetchMovieRailData(category, railType)
                updateRow(railData)
            }
        }
    }

    fun isCenterStayRail(row: Row?, railCommonData: RailCommonData?): Boolean {
        if (row is ExpandableHeroCarouselRow) return false
        val screenWidget = railCommonData?.screenWidget
        if (isHeroCarousel(screenWidget)) return false
        val predef = screenWidget?.predefPlaylistType
        if (predef.equals(PredefinePlaylistType.CON_W.name, ignoreCase = true)) return false
        if (screenWidget?.railCardType.equals("EXPANDED", ignoreCase = true)) return false
        return true
    }

    private fun updateDynamicBackdrop(item: Any?) {
        val imageUrl = when (item) {
            is Title -> item.backdropUrl ?: item.posterUrl
            is Asset -> {
                item.images?.takeIf { it.isNotEmpty() }?.let {
                    AppCommonMethod.getCardwiseImage(it, AppConstants.RATIO_16X9_cover, 1920, 1080)
                } ?: item.images?.firstOrNull()?.url
            }
            is CustomAsset -> null
            is CustomKalturaAsset -> item.images?.firstOrNull()?.url
            is com.example.ott.EnveuCategoryServices.Asset -> item.images?.firstOrNull()?.url
            else -> null
        }
        (activity as? MainActivity)?.updateGlobalBackdrop(imageUrl)
    }

    fun updateRowAlignment(hasBrandingLogo: Boolean, railCommonData: RailCommonData?) {
        val isCenter = isCenterStayRail(null, railCommonData)
        if (isCenter) {
            verticalGridView?.windowAlignmentOffsetPercent = 42f
            verticalGridView?.windowAlignmentOffset = 0
            return
        }
        val alignment = if (hasBrandingLogo) {
            resources.getDimensionPixelSize(R.dimen.row_alignment_offset1)
        } else {
            resources.getDimensionPixelSize(R.dimen.row_alignment_offset)
        }
        verticalGridView?.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        if (verticalGridView?.windowAlignmentOffset != alignment) {
            verticalGridView?.windowAlignmentOffset = alignment
        }
    }

    private fun setupInstantVerticalNavigation() {
        verticalGridView?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        verticalGridView?.setOnKeyInterceptListener { event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyInterceptListener false

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> jumpToRow(selectedPosition - 1)
                KeyEvent.KEYCODE_DPAD_DOWN -> jumpToRow(selectedPosition + 1)
                else -> false
            }
        }
    }

    private fun jumpToRow(targetPosition: Int): Boolean {
        if (targetPosition < 0 || targetPosition >= rowsAdapter.size()) return false

        val gridView = verticalGridView ?: return false

        val row = rowsAdapter.get(targetPosition) as? Row
        val key = (row as? ListRow)?.contentDescription?.toString()
        val railCommonData = hashMap[key]?.railCommonData
        val isCenter = isCenterStayRail(row, railCommonData)
        val alignmentPx = resources.getDimensionPixelSize(R.dimen.row_alignment_offset)

        if (isCenter) {
            gridView.windowAlignmentOffsetPercent = 42f
            gridView.windowAlignmentOffset = 0
        } else {
            gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            gridView.windowAlignmentOffset = if (isHeroCarousel(railCommonData?.screenWidget)) 0 else alignmentPx
        }

        setSelectedPosition(targetPosition)

        gridView.post {
            val holder = gridView.findViewHolderForAdapterPosition(targetPosition)
            if (holder is ListRowPresenter.ViewHolder) {
                val hGridView = holder.gridView
                val childHolder = hGridView.findViewHolderForAdapterPosition(hGridView.selectedPosition)
                if (childHolder != null) {
                    childHolder.itemView.requestFocus()
                } else {
                    hGridView.post {
                        val ch = hGridView.findViewHolderForAdapterPosition(hGridView.selectedPosition)
                        ch?.itemView?.requestFocus() ?: holder.itemView.requestFocus()
                    }
                }
            } else {
                holder?.itemView?.apply {
                    animate().cancel()
                    alpha = 1f
                    requestFocus()
                }
            }
        }

        return true
    }

    fun requestChildFocus(): Boolean {
        val gridView = verticalGridView ?: return false
        val holder = gridView.findViewHolderForAdapterPosition(selectedPosition)
        if (holder is ListRowPresenter.ViewHolder) {
            val hGridView = holder.gridView
            val childHolder = hGridView.findViewHolderForAdapterPosition(hGridView.selectedPosition)
            if (childHolder != null) {
                return childHolder.itemView.requestFocus()
            }
        }
        return holder?.itemView?.requestFocus() ?: gridView.requestFocus()
    }

    fun resetForNewMenu() {
        if (!isAdded) return
        HeroCarouselRowPresenter.stopActiveVideo()
        HeroCarouselCardPresenter.stopActiveVideo()
        view?.animate()?.cancel()
        rowsAdapter.clear()
        hashMap.clear()
        selectedPosition = 0
        adapter = rowsAdapter
        cancelScheduledFlush()
        pendingRemovalWidgetIds.clear()
        expectedRailCount = 0
        completedRailCount = 0
    }

    private val pendingRemovalWidgetIds = mutableSetOf<String>()
    private var expectedRailCount = 0
    private var completedRailCount = 0

    private val flushHandler = Handler(Looper.getMainLooper())
    private var flushRunnable: Runnable? = null
    private var safetyRunnable: Runnable? = null

    private val FLUSH_SAFETY_TIMEOUT_MS = 4000L
    private val FLUSH_DEBOUNCE_MS = 250L

    private val rowDiffCallback = object : DiffCallback<ListRow>() {
        override fun areItemsTheSame(
            oldItem: ListRow,
            newItem: ListRow
        ): Boolean {
            return oldItem.contentDescription == newItem.contentDescription
        }

        override fun areContentsTheSame(
            oldItem: ListRow,
            newItem: ListRow
        ): Boolean {
            return true
        }
    }

    fun setExpectedRailCount(count: Int) {
        expectedRailCount = count
        completedRailCount = 0
        pendingRemovalWidgetIds.clear()
        cancelScheduledFlush()
    }

    private fun onRailResponseReceived(widgetId: String, isValid: Boolean) {
        completedRailCount++
        if (!isValid) {
            pendingRemovalWidgetIds.add(widgetId)
        }

        if (expectedRailCount > 0 && completedRailCount >= expectedRailCount) {
            flushPendingRemovals()
        } else if (pendingRemovalWidgetIds.isNotEmpty()) {
            scheduleDebouncedFlush()
        }
    }

    private fun scheduleDebouncedFlush() {
        flushRunnable?.let { flushHandler.removeCallbacks(it) }
        val runnable = Runnable { flushPendingRemovals() }
        flushRunnable = runnable
        flushHandler.postDelayed(runnable, FLUSH_DEBOUNCE_MS)
        if (safetyRunnable == null) {
            val safety = Runnable {
                safetyRunnable = null
                if (pendingRemovalWidgetIds.isNotEmpty()) flushPendingRemovals()
            }
            safetyRunnable = safety
            flushHandler.postDelayed(safety, FLUSH_SAFETY_TIMEOUT_MS)
        }
    }

    private fun cancelScheduledFlush() {
        flushRunnable?.let { flushHandler.removeCallbacks(it) }
        flushRunnable = null
        safetyRunnable?.let { flushHandler.removeCallbacks(it) }
        safetyRunnable = null
    }

    private fun flushPendingRemovals() {
        cancelScheduledFlush()
        if (pendingRemovalWidgetIds.isEmpty()) return
        if (!isAdded) return

        val idsToRemove = pendingRemovalWidgetIds.toSet()
        pendingRemovalWidgetIds.clear()

        val finalRows = ArrayList<ListRow>(rowsAdapter.size())
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(i) as? ListRow ?: continue
            val widgetId = row.contentDescription?.toString()
            if (widgetId != null && widgetId in idsToRemove) {
                hashMap.remove(widgetId)
                LogUtils.e("removeEmptyRow678", "batched removal id=$widgetId name=")
            } else {
                finalRows.add(row)
            }
        }

        LogUtils.e(
            "removeEmptyRow678",
            "flushPendingRemovals: removed=${idsToRemove.size} remaining=${finalRows.size}"
        )

        @Suppress("UNCHECKED_CAST")
        rowsAdapter.setItems(finalRows as List<Any?>, rowDiffCallback as DiffCallback<Any?>)
    }

    fun updateRow(result: RailCommonData) {
        val widgetId = result.screenWidget?.Id ?: return
        val screenWidget = result.screenWidget ?: return

        val isTop10Invalid = screenWidget.top10Rails == true && result.assets.size < 10
        val isEmpty = result.assets.isEmpty()

        if (isTop10Invalid || isEmpty) {
            LogUtils.e("PredefinePlaylistType12345321", screenWidget.name ?: "")
            onRailResponseReceived(widgetId, isValid = false)
            return
        }

        val navigationInfoModel = hashMap[widgetId]

        if (navigationInfoModel != null) {
            val adapter = navigationInfoModel.assetsAdapter
            val existingCount = adapter.size()
            val newCount = result.assets.size

            if (screenWidget.predefPlaylistType == PredefinePlaylistType.AT_BYW.name || screenWidget.predefPlaylistType == PredefinePlaylistType.BYSL.name) {
                LogUtils.e("PredefinePlaylistType12345321", screenWidget.name ?: "")
                updateHeaderForWidget(widgetId, screenWidget.name ?: "")
            }

            result.assets.forEachIndexed { i, asset ->
                asset.metas["PositionForRail"] = StringValue((i + 1).toString())

                if (i < existingCount) {
                    adapter.replace(i, asset)
                } else {
                    adapter.add(asset)
                }
            }
            if (newCount < existingCount) {
                adapter.removeItems(newCount, existingCount - newCount)
            }
            navigationInfoModel.railCommonData = result
            onRailResponseReceived(widgetId, isValid = true)
        } else {
            addNewRail(result, screenWidget, widgetId)
            onRailResponseReceived(widgetId, isValid = true)
        }
    }

    private fun updateHeaderForWidget(widgetId: String, name: String) {
        val size = rowsAdapter.size()
        for (i in 0 until size) {
            val listRow = rowsAdapter.get(i) as? ListRow ?: continue
            if (widgetId == listRow.contentDescription) {
                listRow.headerItem = IconHeaderItem(listRow.headerItem.id, name, "")
                break
            }
        }
    }

    private fun addNewRail(result: RailCommonData, screenWidget: BaseCategory, widgetId: String) {
        val positionForWidget = getPositionForAdapter(screenWidget)
        val arrayObjectAdapter = ArrayObjectAdapter(setUIData(result))
        if (result.assets.isEmpty()) {
            removeEmptyRow(screenWidget)
            return
        }

        result.assets.forEachIndexed { i, asset ->
            asset.metas["PositionForRail"] = StringValue((i + 1).toString())
            arrayObjectAdapter.add(asset)
        }

        val gridItemPresenterHeader = IconHeaderItem(
            0, screenWidget.name ?: "", screenWidget.widgetImageorLogo ?: ""
        )
        val isHero = isHeroCarousel(screenWidget)
        val listRow = if (result.railType == RailTypes.CAROUSEL_LDS_LANDSCAPE && !isHero) {
            ExpandableHeroCarouselRow(gridItemPresenterHeader, arrayObjectAdapter).apply {
                contentDescription = widgetId
            }
        } else {
            ListRow(gridItemPresenterHeader, arrayObjectAdapter).apply {
                contentDescription = widgetId
            }
        }

        listRowPresenter.headerPresenter = IconHeaderItemPresenter()
        listRowPresenter.selectEffectEnabled = false

        if (positionForWidget <= rowsAdapter.size()) {
            rowsAdapter.add(positionForWidget, listRow)
        } else {
            rowsAdapter.add(listRow)
        }

        hashMap[widgetId] = NavigationInfoModel(
            arrayObjectAdapter, rowsAdapter.size() - 1, screenWidget, result
        )
    }

    private fun getPositionForAdapter(baseCategory: BaseCategory): Int {
        val entryList: List<Map.Entry<String, NavigationInfoModel>?> =
            ArrayList<Map.Entry<String, NavigationInfoModel>?>(hashMap.entries)

        Collections.sort<Map.Entry<String?, NavigationInfoModel?>?>(
            entryList,
            object : Comparator<Map.Entry<String?, NavigationInfoModel?>?> {
                override fun compare(
                    o1: Map.Entry<String?, NavigationInfoModel?>?,
                    o2: Map.Entry<String?, NavigationInfoModel?>?,
                ): Int {
                    return Integer.compare(
                        o1?.value?.baseCategory?.displayOrder ?: 0,
                        o2?.value?.baseCategory?.displayOrder ?: 0
                    )
                }
            })
        val currentDisplayOrder = baseCategory.displayOrder ?: 0
        for (index in entryList.indices) {
            val order = entryList[index]?.value?.baseCategory?.displayOrder ?: 0
            if (currentDisplayOrder < order) {
                return index
            }
        }
        return entryList.size
    }

    fun removeEmptyRow(baseCategory: BaseCategory) {
        LogUtils.e("removeEmptyRow678", "id: ${baseCategory.Id}")
        for (i in 0 until rowsAdapter.size()) {
            val listRow = rowsAdapter.get(i) as ListRow
            if (baseCategory.Id == listRow.contentDescription) {
                LogUtils.e("removeEmptyRow678", "name: ${baseCategory.name}")
                LogUtils.e("removeEmptyRow678", "baseid deleted ${baseCategory.Id}")
                LogUtils.e("removeEmptyRow678", "${listRow.contentDescription}")
                rowsAdapter.removeItems(i, 1)
                break
            }
        }
    }

    private val presenterCache = mutableMapOf<String, Presenter>()

    private fun setUIData(result: RailCommonData): Presenter {
        val railType = result.railType
        val screenWidget = result.screenWidget
        val railCardSize = screenWidget?.railCardSize
        val top10 = screenWidget?.top10Rails == true
        val autoPlay = screenWidget?.autoPlay == true
        val autoPlayMode = screenWidget?.autoPlayMode
        val transparentBgColor = screenWidget?.transparentBgColor
        val progressBarColor = screenWidget?.progressBarColor
        val isContinueWatching = screenWidget?.predefPlaylistType == "CON_W"
        val isBrandingHeader = screenWidget?.brandingHeader == true

        val isHero = isHeroCarousel(screenWidget)
        val cacheKey =
            "${railType}_${railCardSize}_${top10}_${autoPlay}_${autoPlayMode}_${isContinueWatching}_${isBrandingHeader}_${transparentBgColor}_${progressBarColor}_${screenWidget?.railCardType}_${isHero}"
        presenterCache[cacheKey]?.let { return it }

        val presenter = when (railType) {
            RailTypes.CAROUSEL_LDS_LANDSCAPE -> HeroCarouselCardPresenter(
                result,
                // On HOME screen the top hero carousel keeps the last focused card expanded (hero mode).
                // On all other expandable rails (including 4th rail on HOME or MOVIES), the card collapses when focus leaves (rail mode).
                keepExpandedWhenUnfocused = isHero
            )
            RailTypes.HORIZONTAL_LDS_LANDSCAPE -> {
                when {
                    isContinueWatching -> ContinueWatchingPresenter(result)
                    top10 -> TopTenCardPresenter(result)
                    else -> LandScapeCardPresenter(result)
                }
            }

            RailTypes.HORIZONTAL_CIR_CIRCLE -> CircleCardPresenter(result)
            RailTypes.HORIZONTAL_CIR_CIRCLE_TRANSPARENT ->
                CircleTransparentCardPresenter(result, transparentBgColor ?: "")

            RailTypes.HORIZONTAL_SQR_SQUARE,
            RailTypes.HORIZONTAL_SQR_SQUARE_TRANSPARENT -> SquareMediumPresenter(result)

            RailTypes.HORIZONTAL_PR_POTRAIT_9x16 -> {
                when {
                    top10 -> TopTenPortraitNineSixteenPresenter(result)
                    autoPlay && autoPlayMode != "Rail" -> LandScapeAutoPlayCardPresenter(result)
                    else -> NineSixteenCardPresenter(result)
                }
            }

            RailTypes.HORIZONTAL_PR_POSTER,
            RailTypes.HORIZONTAL_PR_POTRAIT -> {
                when {
                    top10 -> TopTenPortraitPresenter(result)
                    autoPlay && autoPlayMode == "Rail" -> LandScapeAutoPlayCardPresenter(result)
                    else -> ItemPresenter(result)
                }
            }

            RailTypes.HERO_LDS_BANNER -> HeroCardPresenter(result)
        }

        presenterCache[cacheKey] = presenter
        return presenter
    }

    fun appendData(newRails: List<RailCommonData>) {
        view?.post {
            if (!isAdded || context == null) return@post
            processAndAddRails(newRails)
        }
    }

    private fun processAndAddRails(newRails: List<RailCommonData>) {
        val visibleCount = 3
        val firstBatch = newRails.take(visibleCount)
        val restBatch = newRails.drop(visibleCount)
        buildAndAddRows(firstBatch)
        if (restBatch.isNotEmpty()) {
            view?.postOnAnimation {
                buildAndAddRows(restBatch)
            }
        }
    }

    private fun buildAndAddRows(rails: List<RailCommonData>) {
        val newRows = mutableListOf<ListRow>()
        val userName = SharedPrefHelper.getInstance().getUserFirstName() ?: "You"

        rails.forEach { rail ->
            val arrayAdapter = ArrayObjectAdapter(setUIData(rail))
            // Initialize every rail with 10 dummy skeleton items
            repeat(10) { arrayAdapter.add(CustomAsset().apply { Id = it }) }

            val headerText = if (rail.screenWidget?.predefPlaylistType == "CON_W") {
                "${rail.screenWidget?.name} ${getString(R.string.for_text)} $userName"
            } else {
                rail.screenWidget?.name ?: ""
            }
            val headerItem = IconHeaderItem(0, headerText, rail.screenWidget?.widgetImageorLogo ?: "")
            val isHero = isHeroCarousel(rail.screenWidget)
            val listRow = if (rail.railType == RailTypes.CAROUSEL_LDS_LANDSCAPE && !isHero) {
                ExpandableHeroCarouselRow(headerItem, arrayAdapter).apply {
                    contentDescription = rail.screenWidget?.Id ?: ""
                }
            } else {
                ListRow(headerItem, arrayAdapter).apply {
                    contentDescription = rail.screenWidget?.Id ?: ""
                }
            }
            if (arrayAdapter.size() > 0) {
                newRows.add(listRow)
                rail.screenWidget?.Id?.let { id ->
                    hashMap[id] = NavigationInfoModel(
                        arrayAdapter, rowsAdapter.size() + newRows.size - 1, rail.screenWidget!!, rail
                    )
                }
            }
        }
        rowsAdapter.addAll(rowsAdapter.size(), newRows)
    }

    private fun getItemPosition(row: Row, item: Any): Int =
        ((row as ListRow).adapter as ArrayObjectAdapter).indexOf(item)

    private val heroUpdateHandler = Handler(Looper.getMainLooper())
    private var pendingHeroUpdate: Runnable? = null
    private val HERO_UPDATE_DELAY = 200L

    inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row,
        ) {
            val position = if (item != null) ((row as? ListRow)?.adapter as? ArrayObjectAdapter)?.indexOf(item) ?: -1 else -1
            val rowPosition = rowsAdapter.indexOf(row)
            val key = (row as? ListRow)?.contentDescription?.toString()
            val railCommonData = hashMap[key]?.railCommonData ?: run {
                LogUtils.e("ListFragment", "railCommonData is null for key: $key")
                return
            }

            val isCenter = isCenterStayRail(row, railCommonData)
            val alignmentPx = resources.getDimensionPixelSize(R.dimen.row_alignment_offset)

            if (isCenter) {
                verticalGridView?.windowAlignmentOffsetPercent = 42f
                verticalGridView?.windowAlignmentOffset = 0
                updateDynamicBackdrop(item)
            } else {
                verticalGridView?.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
                verticalGridView?.windowAlignmentOffset = if (isHeroCarousel(railCommonData.screenWidget)) 0 else alignmentPx
                (activity as? MainActivity)?.clearGlobalBackdrop()
            }

            pendingHeroUpdate?.let { heroUpdateHandler.removeCallbacks(it) }
            pendingHeroUpdate = Runnable {
                onItemInteractionListener?.onItemSelected1(
                    itemViewHolder, item, rowViewHolder, row,
                    position, rowPosition, railCommonData, rowsAdapter.size()
                )
            }.also { heroUpdateHandler.postDelayed(it, HERO_UPDATE_DELAY) }
        }
    }

    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    inner class ItemViewClickListener : OnItemViewClickedListener {
        override fun onItemClicked(
            vh: Presenter.ViewHolder?,
            item: Any?,
            rowVH: RowPresenter.ViewHolder?,
            row: Row?,
        ) {
            if (item !is Asset) return
            val rowItem = row as? ListRow ?: return
            val key = rowItem.contentDescription?.toString() ?: return
            val rail = hashMap[key]?.railCommonData ?: return
            LogUtils.e("setupPersonalized1234", Gson().toJson(rail.screenWidget))

            onItemInteractionListener?.onItemClicked1(
                vh,
                item,
                rowVH,
                row,
                getItemPosition(row, item),
                rowsAdapter.indexOf(row),
                rail
            )
        }
    }

    override fun onDestroy() {
        rowsAdapter.clear()
        setOnItemViewClickedListener(null)
        setOnItemViewSelectedListener(null)
        super.onDestroy()
    }

    override fun onDestroyView() {
        view?.animate()?.cancel()
        view?.removeCallbacks(null)
        (view as? ViewGroup)?.removeAllViewsInLayout()

        rowsAdapter.clear()
        adapter = null

        presenterCache.clear()
        hashMap.clear()
        cancelScheduledFlush()
        pendingRemovalWidgetIds.clear()

        onItemInteractionListener = null
        setOnItemViewClickedListener(null)
        setOnItemViewSelectedListener(null)

        HeroCarouselRowPresenter.releasePlayer()
        HeroCarouselCardPresenter.releasePlayer()
        (activity as? MainActivity)?.clearGlobalBackdrop()
        super.onDestroyView()
    }
}
