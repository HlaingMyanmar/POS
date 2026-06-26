package com.sspd.servicemgmt.api

import com.sspd.servicemgmt.BuildConfig
import okhttp3.OkHttpClient
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

    // Trust all certificates — for internal HTTPS server with self-signed cert
    private fun buildTrustAllClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401) AuthEventBus.notifyTokenExpired()
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                        else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun build(): Retrofit =
        Retrofit.Builder()
            .baseUrl(_baseUrl)
            .client(buildTrustAllClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val service: ApiService
        get() {
            if (retrofit == null) retrofit = build()
            return retrofit!!.create(ApiService::class.java)
        }

    fun bearer(token: String) = "Bearer $token"

    val pingUrl: String get() = _baseUrl

    /** Base URL without the /api/v1/ suffix, e.g. "https://192.168.x.x:8080/" */
    val rawBaseUrl: String get() = _baseUrl.removeSuffix("api/v1/")

    fun buildPingClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /** OkHttpClient for persistent WebSocket connections.
     *  - readTimeout = 0  (no timeout — socket stays open)
     *  - pingInterval = 30s  (OkHttp sends WebSocket PING frames to keep the connection alive)
     */
    fun wsClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // no read timeout for WebSocket
            .pingInterval(30, TimeUnit.SECONDS)       // keep-alive ping every 30 s
            .build()
    }

    /** wss:// URL for the backend's native STOMP WebSocket endpoint. */
    val wsNativeUrl: String
        get() = rawBaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws-native"
}
