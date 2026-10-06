package com.example.ott.data.model
data class HeroCarouselConfig(
    val rowId: String = "hero_carousel_main",
    val autoRotateEnabled: Boolean = true,
    val autoRotateDurationSec: Int = 6,
    val focusHoldAutoplayMs: Long = 600L
)