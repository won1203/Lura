package com.example.lura.data.remote

import android.util.Log
import com.example.lura.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object BackendApiProvider {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun createRetrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val apiBaseUrls: List<String> by lazy {
        BuildConfig.LURA_API_BASE_URLS
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .also { Log.i(TAG, "Using Lura API base URL candidates: $it") }
    }

    val luraBackendApi: LuraBackendApi by lazy {
        createRetrofit(apiBaseUrls.first()).create(LuraBackendApi::class.java)
    }

    val luraBackendApis: List<LuraBackendApi> by lazy {
        apiBaseUrls.map { baseUrl ->
            createRetrofit(baseUrl).create(LuraBackendApi::class.java)
        }
    }

    private const val CONNECT_TIMEOUT_SECONDS = 4L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 10L
    private const val TAG = "BackendApiProvider"
}
