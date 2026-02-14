package com.sentinelx.com.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

object SentinelNetwork {
    private const val BASE_URL = "https://aa4e-128-185-112-57.ngrok-free.app/api/v1/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        // NGROK BYPASS HEADER
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("ngrok-skip-browser-warning", "true")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val api: SentinelApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SentinelApiService::class.java)
    }

    // --- [FIX] ADDED MISSING FUNCTION ---
    fun generateCorrelationId(): String = UUID.randomUUID().toString()
}