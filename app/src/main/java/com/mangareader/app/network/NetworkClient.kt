package com.mangareader.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val BASE_URL = "https://www.manhuaren.com"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-TW,zh-CN;q=0.9,zh;q=0.8,en;q=0.7")
            chain.proceed(requestBuilder.build())
        }
        .build()

    // Dedicated OkHttpClient for Coil image requests
    val imageOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val req = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun getHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        return response.body?.string() ?: ""
    }
}
