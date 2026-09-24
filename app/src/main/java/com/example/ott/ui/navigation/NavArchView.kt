package com.example.ott.ui.navigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Custom frosted glass navigation backdrop that seamlessly renders an elegant
 * curved arch along the right edge when expanded, replicating the signature
 * JioHotstar menu_arch_view aesthetic with no harsh border lines.
 */
class NavArchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val density = resources.displayMetrics.density
    private val collapsedThresholdPx = 76f * density
    private val maxBulgePx = 22f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Calculate arch expansion factor (0.0 when collapsed, 1.0 when fully open)
        val progress = if (w <= collapsedThresholdPx) {
            0f
        } else {
            ((w - collapsedThresholdPx) / (150f * density)).coerceIn(0f, 1f)
        }

        val bulge = maxBulgePx * progress
        val topX = (w - bulge).coerceAtLeast(0f)

        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(topX, 0f)

        if (bulge > 1f) {
            // Majestic convex bezier arch towards screen center (borderless)
            path.cubicTo(
                w + (bulge * 0.15f), h * 0.32f,
                w + (bulge * 0.15f), h * 0.68f,
                topX, h
            )
        } else {
            path.lineTo(w, h)
        }

        path.lineTo(0f, h)
        path.close()

        // Deep frosted glass obsidian gradient blending seamlessly into content
        fillPaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(
                Color.parseColor("#FC04070D"),
                Color.parseColor("#F5060A13"),
                Color.parseColor("#E0080D18"),
                Color.parseColor("#4005080F"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 0.80f, 0.95f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(path, fillPaint)
    }
}
