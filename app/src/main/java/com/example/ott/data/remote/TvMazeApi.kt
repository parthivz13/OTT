package com.example.ott.data.remote
import retrofit2.http.GET
import retrofit2.http.Query
interface TvMazeApi {
    @GET("shows")
    suspend fun getShows(@Query("page") page: Int = 0): List<ShowDto>
}