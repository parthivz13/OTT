package com.example.ott.sott.utils

import android.content.Context

class SharedPrefHelper private constructor() {
    companion object {
        @Volatile
        private var instance: SharedPrefHelper? = null

        fun getInstance(): SharedPrefHelper =
            instance ?: synchronized(this) {
                instance ?: SharedPrefHelper().also { instance = it }
            }
    }

    private val trailerMap = mutableMapOf<String, String>()

    fun setTrailerInMap(externalId: String, trailerUrl: String) {
        trailerMap[externalId] = trailerUrl
    }

    fun getTrailerFromMap(context: Context, externalId: String): Any? {
        return trailerMap[externalId]
    }
}
