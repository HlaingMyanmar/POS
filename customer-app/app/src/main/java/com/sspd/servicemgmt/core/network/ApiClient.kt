package com.sspd.servicemgmt.core.network

import com.sspd.servicemgmt.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ssl = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code == 401 && shouldEndSessionOn401(request)) {
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

    private fun shouldEndSessionOn401(request: okhttp3.Request): Boolean {
        val auth = request.header("Authorization").orEmpty()
        if (!auth.startsWith("Bearer ") || auth.length < 16) return false
        val path = request.url.encodedPath.lowercase()
        if (path.contains("/auth/")) return false
        if (path.contains("ws-native") || path.contains("ws-clinic")) return false
        return true
    }
}
