package com.example.ott.data.remote

import retrofit2.http.GET

interface TmdbApi {
    @GET("trending/all/week")
    suspend fun getTrendingAllWeek(): TrendingResponseDto
}
