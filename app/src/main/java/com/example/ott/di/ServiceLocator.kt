package com.example.ott.di

import com.example.ott.BuildConfig
import com.example.ott.data.remote.TmdbApi
import com.example.ott.data.remote.TvMazeApi
import com.example.ott.data.repository.TmdbRepository
import com.example.ott.data.repository.TvMazeRepository
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServiceLocator {

    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    private const val TVMAZE_BASE_URL = "https://api.tvmaze.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val tmdbAuthInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${BuildConfig.TMDB_API_KEY}")
            .addHeader("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val tmdbOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(tmdbAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val plainOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val tmdbRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(tmdbOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // No API key required: https://www.tvmaze.com/api
    private val tvMazeRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TVMAZE_BASE_URL)
            .client(plainOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val tmdbApi: TmdbApi by lazy { tmdbRetrofit.create(TmdbApi::class.java) }
    private val tvMazeApi: TvMazeApi by lazy { tvMazeRetrofit.create(TvMazeApi::class.java) }

    val tmdbRepository: TmdbRepository by lazy { TmdbRepository(tmdbApi) }
    val tvMazeRepository: TvMazeRepository by lazy { TvMazeRepository(tvMazeApi) }
}
