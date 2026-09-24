package com.example.ott.ui.navigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Custom background drawable for side navigation pill items that renders:
 * 1. Fill color gradient: Blue on left -> Gray in middle -> Transparent on right.
 * 2. Border stroke gradient: Blue on left -> Gray in middle -> Transparent on right.
 * 3. Shows prominent stroke ONLY on the focused item, avoiding dual-stroke clutter.
 * 4. Ensures the gradient and stroke cleanly dissolve before reaching the curved arch boundary.
 */
class NavPillDrawable(context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val cornerRadius = 12f * density
    private val strokeWidthPx = 1.4f * density

    private val rectF = RectF()
    private val strokeRectF = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private var isFocused = false
    private var isSelected = false

    override fun onStateChange(state: IntArray): Boolean {
        var newFocused = false
        var newSelected = false

        for (s in state) {
            if (s == android.R.attr.state_focused) newFocused = true
            if (s == android.R.attr.state_selected) newSelected = true
        }

        if (newFocused != isFocused || newSelected != isSelected) {
            isFocused = newFocused
            isSelected = newSelected
            invalidateSelf()
            return true
        }
        return super.onStateChange(state)
    }

    override fun isStateful(): Boolean = true

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updatePaints(bounds)
    }

    private fun updatePaints(bounds: Rect) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        // Effective gradient width: fades to 100% transparent well before the right edge
        // (78% of width), guaranteeing it never spills over the curved navigation arch.
        val gradWidth = (w * 0.78f).coerceAtLeast(40f * density)

        // Fill gradient: Blue -> Gray -> Transparent
        fillPaint.shader = LinearGradient(
            0f, 0f, gradWidth, 0f,
            intArrayOf(
                Color.parseColor("#8C0084FF"), // Blue (left)
                Color.parseColor("#3B64748B"), // Gray (middle)
                Color.TRANSPARENT             // Transparent (right)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )

        // Stroke gradient: Luminous Blue -> Muted Slate Gray -> Transparent
        strokePaint.shader = LinearGradient(
            0f, 0f, gradWidth, 0f,
            intArrayOf(
                Color.parseColor("#CC60A5FA"), // Luminous Blue stroke
                Color.parseColor("#4D94A3B8"), // Slate Gray stroke
                Color.TRANSPARENT             // Transparent stroke (no right border!)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val inset = strokeWidthPx / 2f
        rectF.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        strokeRectF.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)

        updatePaints(b)

        val isCollapsed = b.width() < (80 * density)

        if (isFocused) {
            // Focused item gets prominent gradient fill and radiant gradient stroke!
            fillPaint.alpha = 255
            strokePaint.alpha = 255
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(strokeRectF, cornerRadius, cornerRadius, strokePaint)
        } else if (isSelected) {
            if (isCollapsed) {
                // In collapsed rail mode, show compact active indicator pill
                fillPaint.alpha = 255
                strokePaint.alpha = 255
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
                canvas.drawRoundRect(strokeRectF, cornerRadius, cornerRadius, strokePaint)
            } else {
                // In expanded drawer mode, selected item shows gentle gradient fill
                // and a subtle matching gradient stroke that stays contained inside the arch.
                fillPaint.alpha = 110
                strokePaint.alpha = 80
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
                canvas.drawRoundRect(strokeRectF, cornerRadius, cornerRadius, strokePaint)
                fillPaint.alpha = 255
                strokePaint.alpha = 255
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
