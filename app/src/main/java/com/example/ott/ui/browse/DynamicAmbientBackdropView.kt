package com.example.ott.ui.browse

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class DynamicAmbientBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val baseColor = Color.parseColor("#0B0E14")
    private var currentColor: Int = Color.parseColor("#1B2A38")
    private var targetColor: Int = currentColor

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = baseColor
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val topScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var colorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()

    init {
        setWillNotDraw(false)
    }

    fun setAmbientColor(newColor: Int, animate: Boolean = true) {
        if (newColor == targetColor) return
        targetColor = newColor

        colorAnimator?.cancel()

        if (!animate || width == 0 || height == 0) {
            currentColor = newColor
            updateShaders()
            invalidate()
            return
        }

        val startColor = currentColor
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                currentColor = argbEvaluator.evaluate(fraction, startColor, targetColor) as Int
                updateShaders()
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
    }

    private fun updateShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Ambient radial spotlight glow centered in top-center region behind hero carousel
        val centerX = w * 0.42f
        val centerY = h * 0.18f
        val radius = w * 0.82f

        // Construct gradient stops with varying alphas of current ambient color
        val alphaFull = Color.argb(190, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
        val alphaMid = Color.argb(95, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
        val alphaLow = Color.argb(30, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
        val alphaZero = Color.argb(0, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))

        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(alphaFull, alphaMid, alphaLow, alphaZero),
            floatArrayOf(0f, 0.35f, 0.70f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // Subtle vertical gradient to ensure bottom trays seamlessly fade into deep dark base
        topScrimPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(40, 11, 14, 20),
                Color.argb(220, 11, 14, 20),
                baseColor
            ),
            floatArrayOf(0f, 0.40f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Solid deep dark canvas
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Dynamic extracted color ambient spotlight glow
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // 3. Bottom & edge scrim for content clarity
        canvas.drawRect(0f, 0f, w, h, topScrimPaint)
    }
}
