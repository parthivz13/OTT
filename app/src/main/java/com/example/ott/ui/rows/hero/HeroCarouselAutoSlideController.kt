package com.example.ott.ui.rows.hero

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.leanback.widget.HorizontalGridView

/**
 * Controller for automatic horizontal carousel sliding in Leanback HorizontalGridView.
 *
 * Automatically rotates cards at the configured interval while pausing smoothly
 * during active user D-pad navigation to prevent focus collisions.
 */
object HeroCarouselAutoSlideController {

    private const val TAG = "HeroAutoSlide"

    private data class RowSlideState(
        val gridView: HorizontalGridView,
        val itemCount: Int,
        val intervalMs: Long,
        val handler: Handler = Handler(Looper.getMainLooper()),
        var runnable: Runnable? = null,
        var isProgrammatic: Boolean = false,
        var isPaused: Boolean = false
    )

    private val activeRows = mutableMapOf<String, RowSlideState>()

    fun registerRow(
        rowId: String,
        gridView: HorizontalGridView,
        itemCount: Int,
        autoRotateEnabled: Boolean,
        intervalMs: Long
    ) {
        if (!autoRotateEnabled || itemCount <= 1) {
            unregisterRow(rowId)
            return
        }

        val existing = activeRows[rowId]
        if (existing != null && existing.gridView === gridView && existing.itemCount == itemCount) {
            return
        }

        unregisterRow(rowId)

        val state = RowSlideState(
            gridView = gridView,
            itemCount = itemCount,
            intervalMs = intervalMs
        )

        val slideRunnable = object : Runnable {
            override fun run() {
                if (state.isPaused) return
                val currentPos = state.gridView.selectedPosition
                val nextPos = (currentPos + 1) % state.itemCount
                Log.d(TAG, "Auto-sliding row $rowId: $currentPos -> $nextPos")
                state.isProgrammatic = true
                state.gridView.setSelectedPositionSmooth(nextPos)
                state.handler.postDelayed({
                    state.isProgrammatic = false
                }, 400L)
                state.handler.postDelayed(this, state.intervalMs)
            }
        }

        state.runnable = slideRunnable
        activeRows[rowId] = state
        state.handler.postDelayed(slideRunnable, intervalMs)
        Log.d(TAG, "Registered auto-slide for row $rowId ($itemCount items, ${intervalMs}ms interval)")
    }

    fun unregisterRow(rowId: String) {
        activeRows.remove(rowId)?.let { state ->
            state.runnable?.let { state.handler.removeCallbacks(it) }
            Log.d(TAG, "Unregistered auto-slide for row $rowId")
        }
    }

    fun notifyUserInteraction(rowId: String) {
        activeRows[rowId]?.let { state ->
            state.runnable?.let { runnable ->
                state.handler.removeCallbacks(runnable)
                state.handler.postDelayed(runnable, state.intervalMs)
            }
        }
    }

    fun isProgrammaticChange(rowId: String): Boolean {
        return activeRows[rowId]?.isProgrammatic ?: false
    }

    fun pauseRow(rowId: String) {
        activeRows[rowId]?.let { state ->
            state.isPaused = true
            state.runnable?.let { state.handler.removeCallbacks(it) }
        }
    }

    fun resumeRow(rowId: String) {
        activeRows[rowId]?.let { state ->
            state.isPaused = false
            state.runnable?.let { runnable ->
                state.handler.removeCallbacks(runnable)
                state.handler.postDelayed(runnable, state.intervalMs)
            }
        }
    }
}
