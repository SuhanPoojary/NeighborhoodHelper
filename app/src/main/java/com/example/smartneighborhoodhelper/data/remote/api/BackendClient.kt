package com.example.smartneighborhoodhelper.data.remote.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object BackendClient {

    // 🔥 STEP 1: Yaha baad me Render URL daalenge
    private const val BASE_URL = "https://neighborhoodhelper.onrender.com/"

    // Optional (leave empty for now)
    private const val EVENTS_SECRET = ""

    private val httpClient: OkHttpClient by lazy {
        val log = HttpLoggingInterceptor()
        log.level = HttpLoggingInterceptor.Level.BODY

        OkHttpClient.Builder()
            .addInterceptor(log)
            .addInterceptor { chain ->
                val reqBuilder = chain.request().newBuilder()
                if (EVENTS_SECRET.isNotBlank()) {
                    reqBuilder.header("x-events-secret", EVENTS_SECRET)
                }
                chain.proceed(reqBuilder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val api: BackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(BackendApi::class.java)
    }
}