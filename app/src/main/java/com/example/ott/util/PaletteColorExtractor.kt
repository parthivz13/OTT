package com.example.ott.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

object PaletteColorExtractor {

    private val colorCache = LruCache<String, Int>(100)

    val DEFAULT_AMBIENT_COLOR = Color.parseColor("#121A28") // Deep cinematic slate navy

    fun getCachedColor(key: String): Int? {
        return colorCache.get(key)
    }

    /**
     * Extracts an ambient color swatch matching the true background atmosphere of the artwork.
     * Prevents unwanted muddy orange/yellow skin-tone selections.
     */
    fun extractColorFromBitmap(bitmap: Bitmap, cacheKey: String? = null): Int {
        val palette = Palette.from(bitmap)
            .maximumColorCount(32)
            .generate()

        val swatches = palette.swatches
        if (swatches.isEmpty()) {
            return DEFAULT_AMBIENT_COLOR
        }

        val totalPopulation = swatches.sumOf { it.population }.coerceAtLeast(1)
        var bestSwatch: Palette.Swatch? = null
        var bestScore = -999999f

        val hsv = FloatArray(3)
        for (swatch in swatches) {
            Color.colorToHSV(swatch.rgb, hsv)
            val hue = hsv[0]        // 0.0 .. 360.0
            val sat = hsv[1]        // 0.0 .. 1.0
            val value = hsv[2]      // 0.0 .. 1.0

            // Skip extreme darkness (near pitch black) or washed-out white
            if (value < 0.08f || (sat < 0.08f && value > 0.85f)) {
                continue
            }

            val popRatio = swatch.population.toFloat() / totalPopulation.toFloat()
            var score = (popRatio * 100f) + (sat * 35f) + (value * 15f)

            // 1. Filter out muddy orange / human skin tones (hues 16° to 45°)
            val isSkinToneOrMuddyOrange = (hue in 16f..45f) && (sat < 0.80f)
            if (isSkinToneOrMuddyOrange) {
                // If it's not the overwhelming majority of the image, strongly penalize
                if (popRatio < 0.50f) {
                    score -= 90f
                } else {
                    score -= 25f
                }
            }

            // 2. Bonus for cinematic atmospheric color ranges
            when {
                hue in 160f..260f -> score += 30f // Ocean Cyan / Deep Sky / Midnight Blue
                hue in 260f..340f -> score += 25f // Plum / Violet / Magenta
                hue >= 340f || hue <= 15f -> score += 25f // Deep Crimson / Scarlet
                hue in 80f..160f -> score += 20f // Forest / Teal Green
                hue in 46f..65f && sat >= 0.60f -> score += 10f // True vibrant gold
            }

            if (score > bestScore) {
                bestScore = score
                bestSwatch = swatch
            }
        }

        // Fallback to palette swatches if custom scoring didn't find a candidate
        val selectedSwatch = bestSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.darkMutedSwatch
            ?: palette.vibrantSwatch
            ?: palette.dominantSwatch

        val finalColor = if (selectedSwatch != null) {
            adjustColorForAmbientGlow(selectedSwatch.rgb)
        } else {
            DEFAULT_AMBIENT_COLOR
        }

        if (cacheKey != null) {
            colorCache.put(cacheKey, finalColor)
        }
        return finalColor
    }

    /**
     * Pre-fetches the image with Glide and extracts the ambient glow color asynchronously.
     */
    fun extractColorFromUrl(
        context: Context,
        imageUrl: String?,
        fallbackId: Any? = null,
        onColorExtracted: (Int) -> Unit
    ) {
        if (imageUrl.isNullOrBlank()) {
            onColorExtracted(DEFAULT_AMBIENT_COLOR)
            return
        }

        val cached = colorCache.get(imageUrl)
        if (cached != null) {
            onColorExtracted(cached)
            return
        }

        Glide.with(context.applicationContext)
            .asBitmap()
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>(250, 140) {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    try {
                        val color = extractColorFromBitmap(resource, imageUrl)
                        onColorExtracted(color)
                    } catch (_: Exception) {}
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }

    /**
     * Adjusts saturation and luminance of the extracted color to provide a rich,
     * cinematic backdrop glow without washing out or turning muddy.
     */
    private fun adjustColorForAmbientGlow(rgb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(rgb, hsv)

        // Ensure healthy saturation
        if (hsv[1] < 0.35f) {
            hsv[1] = (hsv[1] * 1.6f).coerceIn(0.35f, 0.85f)
        }

        // Keep brightness in rich ambient range (0.30 - 0.58)
        hsv[2] = hsv[2].coerceIn(0.30f, 0.58f)

        return Color.HSVToColor(hsv)
    }
}
