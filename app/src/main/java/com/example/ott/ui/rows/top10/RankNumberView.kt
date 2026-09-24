package com.example.ott.ui.rows.top10

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Custom View rendering the iconic Netflix-style 3D outlined rank numeral (1..10).
 * Draws an outer stroke with drop shadow and a metallic gradient fill.
 */
class RankNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var rankText: String = "1"
        set(value) {
            field = value
            invalidate()
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        color = 0xFF595E6B.toInt()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val strokeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = 0xFFD1D5DB.toInt()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    var isCardFocused: Boolean = false
        set(value) {
            field = value
            updateColors()
            invalidate()
        }

    private fun updateColors() {
        val density = resources.displayMetrics.density
        if (isCardFocused) {
            strokePaint.color = 0xFF00E5FF.toInt() // Vibrant cyan glow when focused
            strokePaint.strokeWidth = 6f * density
            strokeHighlightPaint.color = 0xFFFFFFFF.toInt()
        } else {
            strokePaint.color = 0xFF4A5260.toInt()
            strokePaint.strokeWidth = 4f * density
            strokeHighlightPaint.color = 0xFFB0B7C3.toInt()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val textSize = h * 0.92f
        strokePaint.textSize = textSize
        strokeHighlightPaint.textSize = textSize
        fillPaint.textSize = textSize

        // Metallic gradient fill from dark charcoal to rich obsidian
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF282D38.toInt(), 0xFF14171F.toInt(), 0xFF0A0C10.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val textBounds = android.graphics.Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rankText.isEmpty()) return

        fillPaint.getTextBounds(rankText, 0, rankText.length, textBounds)
        val density = resources.displayMetrics.density
        val x = (4f * density) - textBounds.left.toFloat()
        // Vertically center text baseline
        val fontMetrics = fillPaint.fontMetrics
        val y = (height - (fontMetrics.ascent + fontMetrics.descent)) / 2f

        // 1. Draw outer stroke outline
        canvas.drawText(rankText, x, y, strokePaint)
        // 2. Draw sharp highlight stroke
        canvas.drawText(rankText, x, y, strokeHighlightPaint)
        // 3. Draw metallic gradient interior fill
        canvas.drawText(rankText, x, y, fillPaint)
    }
}
