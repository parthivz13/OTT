package com.example.ott.data.repository

import com.example.ott.data.model.Title
import com.example.ott.di.ServiceLocator
import com.example.ott.sott.models.BaseCategory
import com.example.ott.sott.models.PredefinePlaylistType
import com.example.ott.sott.networking.RailCommonData
import com.example.ott.sott.utils.enums.RailTypes
import com.example.ott.types.Asset
import com.example.ott.types.AssetImage
import com.example.ott.types.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class HomeScreenRepository {

    private val tvMazeRepository = ServiceLocator.tvMazeRepository

    fun getHomeScreenRailConfigs(): List<Pair<BaseCategory, RailTypes>> {
        return listOf(
            BaseCategory(
                Id = "widget_hero",
                name = "Featured Spotlight",
                displayOrder = 0,
                type = "CAROUSEL",
                autoPlay = true,
                autoRotate = true,
                autoRotateDuration = 8
            ) to RailTypes.CAROUSEL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "widget_con_w",
                name = "Continue Watching",
                displayOrder = 1,
                predefPlaylistType = PredefinePlaylistType.CON_W.name,
                brandingHeader = false
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "widget_top10",
                name = "🔥 Top 10 Shows Today",
                displayOrder = 2,
                top10Rails = true
            ) to RailTypes.HORIZONTAL_PR_POSTER,

            BaseCategory(
                Id = "widget_drama",
                name = "Critically Acclaimed Drama",
                displayOrder = 3
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "widget_action",
                name = "Action & Sci-Fi Hits",
                displayOrder = 4
            ) to RailTypes.HORIZONTAL_PR_POSTER,

            BaseCategory(
                Id = "widget_comedy",
                name = "Binge-Worthy Comedy",
                displayOrder = 5
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "widget_watchlist",
                name = "My Watchlist",
                displayOrder = 6,
                predefPlaylistType = PredefinePlaylistType.WATCHLIST.name
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE
        )
    }

    fun getMovieScreenRailConfigs(): List<Pair<BaseCategory, RailTypes>> {
        return listOf(
            BaseCategory(
                Id = "movie_widget_hero",
                name = "Blockbuster Premieres",
                displayOrder = 0,
                type = "CAROUSEL",
                autoPlay = true,
                autoRotate = false
            ) to RailTypes.CAROUSEL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "movie_widget_action",
                name = "Action Blockbusters",
                displayOrder = 1
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "movie_widget_top10",
                name = "🔥 Top 10 Movies",
                displayOrder = 2,
                top10Rails = true
            ) to RailTypes.HORIZONTAL_PR_POSTER,

            BaseCategory(
                Id = "movie_widget_scifi",
                name = "Sci-Fi & Fantasy Epics",
                displayOrder = 3
            ) to RailTypes.HORIZONTAL_PR_POSTER,

            BaseCategory(
                Id = "movie_widget_comedy",
                name = "Comedy Highlights",
                displayOrder = 4
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE,

            BaseCategory(
                Id = "movie_widget_watchlist",
                name = "My Movie Watchlist",
                displayOrder = 5,
                predefPlaylistType = PredefinePlaylistType.WATCHLIST.name
            ) to RailTypes.HORIZONTAL_LDS_LANDSCAPE
        )
    }

    suspend fun fetchMovieRailData(screenWidget: BaseCategory, railType: RailTypes): RailCommonData = withContext(Dispatchers.IO) {
        when (screenWidget.Id) {
            "movie_widget_hero" -> delay(350)
            "movie_widget_action" -> delay(550)
            "movie_widget_top10" -> delay(700)
            "movie_widget_scifi" -> delay(850)
            "movie_widget_comedy" -> delay(1000)
            "movie_widget_watchlist" -> delay(400)
            else -> delay(500)
        }

        val allTitles = runCatching { tvMazeRepository.getShows() }.getOrElse { emptyList() }

        val assetsList = when (screenWidget.Id) {
            "movie_widget_hero" -> {
                allTitles.sortedByDescending { it.rating }.take(8).map { titleToAsset(it) }
            }
            "movie_widget_action" -> {
                allTitles.filter { it.genre.equals("Action", ignoreCase = true) || it.genre.equals("Crime", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.take(8)?.map { titleToAsset(it) }
                    ?: allTitles.drop(4).take(8).map { titleToAsset(it) }
            }
            "movie_widget_top10" -> {
                allTitles.sortedByDescending { it.rating }.take(10).map { titleToAsset(it) }
            }
            "movie_widget_scifi" -> {
                allTitles.filter { it.genre.equals("Science-Fiction", ignoreCase = true) || it.genre.equals("Drama", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.take(7)?.map { titleToAsset(it) }
                    ?: allTitles.drop(10).take(7).map { titleToAsset(it) }
            }
            "movie_widget_comedy" -> {
                allTitles.filter { it.genre.equals("Comedy", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.take(6)?.map { titleToAsset(it) }
                    ?: allTitles.drop(14).take(6).map { titleToAsset(it) }
            }
            "movie_widget_watchlist" -> {
                emptyList()
            }
            else -> {
                allTitles.take(8).map { titleToAsset(it) }
            }
        }

        RailCommonData(
            railType = railType,
            screenWidget = screenWidget,
            assets = ArrayList(assetsList)
        )
    }

    suspend fun fetchRailData(screenWidget: BaseCategory, railType: RailTypes): RailCommonData = withContext(Dispatchers.IO) {
        // Stagger API network response slightly to simulate realistic asynchronous per-rail API arrivals
        when (screenWidget.Id) {
            "widget_hero" -> delay(350)
            "widget_con_w" -> delay(250) // Returns 0 items -> rail will be removed!
            "widget_top10" -> delay(600)
            "widget_drama" -> delay(750)
            "widget_action" -> delay(900)
            "widget_comedy" -> delay(1050)
            "widget_watchlist" -> delay(400) // Returns 0 items -> rail will be removed!
            else -> delay(500)
        }

        val allTitles = runCatching { tvMazeRepository.getShows() }.getOrElse { emptyList() }

        val assetsList = when (screenWidget.Id) {
            "widget_hero" -> {
                // Return 8 items (less than 10 dummy items -> trims remaining 2 dummy items)
                allTitles.sortedByDescending { it.rating }.take(8).map { titleToAsset(it) }
            }
            "widget_con_w" -> {
                // Return 0 items -> triggers automatic removal of empty dummy rail!
                emptyList()
            }
            "widget_top10" -> {
                // Return full 10 items
                allTitles.sortedByDescending { it.rating }.take(10).map { titleToAsset(it) }
            }
            "widget_drama" -> {
                // Return 7 items -> trims remaining 3 dummy items
                allTitles.filter { it.genre.equals("Drama", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.take(7)?.map { titleToAsset(it) }
                    ?: allTitles.take(7).map { titleToAsset(it) }
            }
            "widget_action" -> {
                // Return 8 items -> trims remaining 2 dummy items
                allTitles.filter { it.genre.equals("Action", ignoreCase = true) || it.genre.equals("Crime", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.take(8)?.map { titleToAsset(it) }
                    ?: allTitles.drop(5).take(8).map { titleToAsset(it) }
            }
            "widget_comedy" -> {
                // Return 6 items -> trims remaining 4 dummy items
                allTitles.filter { it.genre.equals("Comedy", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.take(6)?.map { titleToAsset(it) }
                    ?: allTitles.drop(12).take(6).map { titleToAsset(it) }
            }
            "widget_watchlist" -> {
                // Return 0 items -> triggers automatic removal of empty dummy rail!
                emptyList()
            }
            else -> {
                allTitles.take(8).map { titleToAsset(it) }
            }
        }

        RailCommonData(
            railType = railType,
            screenWidget = screenWidget,
            assets = ArrayList(assetsList)
        )
    }

    private fun titleToAsset(title: Title): Asset {
        return Asset(
            id = title.id.toString(),
            name = title.name,
            externalId = title.id.toString(),
            images = listOf(
                AssetImage(url = title.backdropUrl ?: title.posterUrl, ratio = "16:9", width = 880, height = 390),
                AssetImage(url = title.posterUrl ?: title.backdropUrl, ratio = "2:3", width = 260, height = 390)
            ),
            mediaFiles = listOf(
                MediaFile(type = "Preview", url = title.trailerUrl ?: title.videoUrl, duration = title.durationSeconds.toLong())
            ),
            metas = mutableMapOf(
                "LongSummary" to title.overview,
                "star_rating" to String.format("%.1f", title.rating),
                "ParentalRating" to title.contentRating,
                "Quality" to title.qualityTag,
                "Year" to title.year,
                "Genre" to title.genre,
                "Badge" to (title.badge ?: "")
            )
        )
    }
}
