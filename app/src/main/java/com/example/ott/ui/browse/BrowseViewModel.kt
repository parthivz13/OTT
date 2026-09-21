package com.example.ott.ui.browse

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ott.data.model.SampleTitles
import com.example.ott.data.model.Title
import com.example.ott.di.ServiceLocator
import kotlinx.coroutines.launch

data class RowGroup(val title: String, val items: List<Title>)

class BrowseViewModel : ViewModel() {

    private val repository = ServiceLocator.tvMazeRepository

    private val _rowGroups = MutableLiveData<List<RowGroup>>()
    val rowGroups: LiveData<List<RowGroup>> = _rowGroups

    private val _heroTitles = MutableLiveData<List<Title>>()
    val heroTitles: LiveData<List<Title>> = _heroTitles

    val heroConfig = com.example.ott.data.model.HeroCarouselConfig(
        rowId = "hero_carousel_browse",
        autoRotateEnabled = true,
        autoRotateDurationSec = 6
    )

    init {
        Log.d(TAG, "init: fetching shows")
        viewModelScope.launch {
            // Falls back to offline sample data on failure so the rails still have content.
            val result = runCatching { repository.getShows() }
            result.exceptionOrNull()?.let { Log.w(TAG, "tvmaze fetch failed, using fallback", it) }
            val titles = result.getOrNull()?.takeIf { it.isNotEmpty() } ?: SampleTitles.fallback
            val heroList = titles.sortedByDescending { it.rating }.take(10)
            _heroTitles.value = heroList
            val groups = buildRowGroups(titles)
            Log.d(TAG, "posting ${groups.size} row groups from ${titles.size} titles")
            _rowGroups.value = groups
        }
    }

    private fun buildRowGroups(titles: List<Title>): List<RowGroup> {
        val popular = RowGroup("Popular Shows", titles.sortedByDescending { it.rating }.take(15))
        val byGenre = titles.groupBy { it.genre }
            .filterKeys { it.isNotBlank() && it != "Other" }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { (genre, items) -> RowGroup(genre, items.sortedByDescending { it.rating }.take(15)) }
        return listOf(popular) + byGenre
    }

    companion object {
        private const val TAG = "BrowseViewModel"
    }
}
