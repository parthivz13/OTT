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
        val isCollapsed = w < (80f * density)
        val gradWidth = if (isCollapsed) w else (w * 0.78f).coerceAtLeast(40f * density)
        fillPaint.shader = LinearGradient(
            0f, 0f, gradWidth, 0f,
            intArrayOf(
                Color.parseColor("#8C0084FF"), 
                Color.parseColor("#3B64748B"), 
                Color.TRANSPARENT             
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = LinearGradient(
            0f, 0f, gradWidth, 0f,
            intArrayOf(
                Color.parseColor("#CC60A5FA"), 
                Color.parseColor("#4D94A3B8"), 
                Color.TRANSPARENT             
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
            fillPaint.alpha = 255
            strokePaint.alpha = 255
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(strokeRectF, cornerRadius, cornerRadius, strokePaint)
        } else if (isSelected) {
            if (isCollapsed) {
                fillPaint.alpha = 255
                strokePaint.alpha = 255
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
                canvas.drawRoundRect(strokeRectF, cornerRadius, cornerRadius, strokePaint)
            } else {
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