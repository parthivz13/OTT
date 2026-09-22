package com.example.ott.sott.presenter

import androidx.recyclerview.widget.DefaultItemAnimator

class SmoothGridItemAnimator : DefaultItemAnimator() {
    init {
        addDuration = 220L
        removeDuration = 200L
        moveDuration = 220L
        changeDuration = 200L
        supportsChangeAnimations = false
    }
}
