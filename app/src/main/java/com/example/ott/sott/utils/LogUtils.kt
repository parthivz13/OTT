package com.example.ott.sott.utils

import android.util.Log

object LogUtils {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
}
