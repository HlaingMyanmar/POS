package com.sspd.servicemgmt.core.network

import com.sspd.servicemgmt.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var _baseUrl = BuildConfig.DEFAULT_BASE_URL.trimEnd('/') + "/api/v1/"
    private var retrofit: Retrofit? = null

    fun setBaseUrl(url: String) {
        val cleaned = url.trimEnd('/') + "/api/v1/"
        if (cleaned != _baseUrl) {
            _baseUrl = cleaned
            retrofit = null
        }
    }

    /** Absolute URL for Company Settings logo image. */
    fun brandingLogoUrl(cacheBust: Long = System.currentTimeMillis()): String =
        _baseUrl.trimEnd('/') + "/customer-portal/branding/logo?t=$cacheBust"

    fun originUrl(): String = _baseUrl.removeSuffix("api/v1/").trimEnd('/')

    fun resolveApkDownloadUrl(serverUrl: String, apkFileName: String): String {
        val compact = serverUrl.trim().replace(" ", "")
        if (compact.isEmpty()) return ""
        val base = originUrl()
        val path = if (compact.contains("/app/")) {
            compact.substring(compact.indexOf("/app/"))
        } else {
            "/app/$apkFileName"
        }
        return base + path
    }


    private fun client(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code == 401 && CustomerRetryPolicy.shouldEndSessionOnUnauthorized(
                        request.header("Authorization"),
                        request.url.encodedPath
                    )
                ) {
                    AuthEventBus.notifyTokenExpired()
                }
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    val service: ApiService
        get() = (retrofit ?: Retrofit.Builder()
            .baseUrl(_baseUrl)
            .client(client())
            .addConverterFactory(GsonConverterFactory.create())
            .build().also { retrofit = it }).create(ApiService::class.java)

    fun socketClient(): OkHttpClient = client().newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun socketUrl(): String = _baseUrl.removeSuffix("api/v1/")
        .replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws-native"

    fun bearer(token: String) = "Bearer $token"

}
