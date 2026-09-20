package com.sspd.servicemgmt.core.network

import android.content.Context
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.util.PreferenceManager
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private var _baseUrl = BuildConfig.DEFAULT_BASE_URL.trimEnd('/') + "/api/v1/"
    private var retrofit: Retrofit? = null
    private var prefs: PreferenceManager? = null
    private val refreshLock = Any()

    fun initialize(context: Context) {
        prefs = PreferenceManager(context.applicationContext)
        val configuredUrl = prefs?.serverUrl.orEmpty()
        if (configuredUrl.isNotBlank()) setBaseUrl(configuredUrl)
    }

    fun setBaseUrl(url: String) {
        val cleaned = url.trimEnd('/') + "/api/v1/"
        if (cleaned != _baseUrl) {
            _baseUrl = cleaned
            retrofit = null
        }
    }

    /** OkHttp uses Android system TLS & Connection Pooling for fast HTTP keep-alive socket reuse. */
    private fun buildApiClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .authenticator { _, response ->
                refreshAndRetry(response)
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Client-Type", "mobile")
                    .build()
                val response = try {
                    chain.proceed(request)
                } catch (error: IllegalStateException) {
                    // OkHttp may surface a broken HTTP/1 codec state as a runtime
                    // exception. Convert it to an I/O failure so Retrofit delivers
                    // it to the calling coroutine instead of killing the process.
                    throw IOException("Server connection was reset. Please retry.", error)
                }
                if (response.code == 401) AuthEventBus.notifyTokenExpired()
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                        else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    private fun refreshAndRetry(response: okhttp3.Response): okhttp3.Request? {
        val preferences = prefs ?: return null
        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            .orEmpty()

        synchronized(refreshLock) {
            val latestToken = preferences.authToken
            when (TokenRetryPolicy.decide(
                responseCount = responseCount(response),
                requestPath = response.request.url.encodedPath,
                responseBody = response.peekBody(2_048).string(),
                requestAccessToken = requestToken,
                latestAccessToken = latestToken,
                refreshToken = preferences.refreshToken
            )) {
                TokenRetryAction.STOP -> return null
                TokenRetryAction.USE_LATEST_ACCESS_TOKEN -> {
                    return response.request.newBuilder()
                        .header("Authorization", bearer(latestToken))
                        .build()
                }
                TokenRetryAction.REFRESH -> Unit
            }

            val refreshToken = preferences.refreshToken
            val refreshClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("X-Client-Type", "mobile")
                            .build()
                    )
                }
                .build()
            val refreshService = Retrofit.Builder()
                .baseUrl(_baseUrl)
                .client(refreshClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
            val refreshed = runCatching {
                refreshService.refreshSession(RefreshTokenRequest(refreshToken)).execute()
            }.getOrNull()
            val auth = refreshed?.takeIf { it.isSuccessful && it.body()?.success == true }?.body()?.data
                ?: return null
            if (auth.accessToken.isBlank()) return null

            preferences.authToken = auth.accessToken
            preferences.refreshToken = auth.refreshToken ?: refreshToken
            preferences.username = auth.username
            preferences.displayName = auth.name ?: auth.username
            preferences.phone = auth.phone ?: ""
            preferences.staffId = auth.staffId ?: 0
            preferences.rolesStr = auth.roles.joinToString(",")
            preferences.permissionsStr = auth.permissions.joinToString(",")
            return response.request.newBuilder()
                .header("Authorization", bearer(auth.accessToken))
                .build()
        }
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun build(): Retrofit =
        Retrofit.Builder()
            .baseUrl(_baseUrl)
            .client(buildApiClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val service: ApiService
        get() {
            val client = retrofit ?: build().also { retrofit = it }
            return client.create(ApiService::class.java)
        }

    fun bearer(token: String) = "Bearer $token"

    val pingUrl: String get() = _baseUrl

    /** Base URL without the /api/v1/ suffix, e.g. "https://192.168.x.x:8080/" */
    val rawBaseUrl: String get() = _baseUrl.removeSuffix("api/v1/")

    /** APK update links from the server may use a hostname phones cannot resolve. */
    fun resolveApkDownloadUrl(serverUrl: String, apkFileName: String): String {
        val compact = serverUrl.trim().replace(" ", "")
        if (compact.isEmpty()) return ""
        val base = rawBaseUrl.trimEnd('/')
        val path = if (compact.contains("/app/")) {
            compact.substring(compact.indexOf("/app/"))
        } else {
            "/app/$apkFileName"
        }
        return base + path
    }

    fun buildPingClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /** OkHttpClient for persistent WebSocket connections.
     *  - readTimeout = 0  (no timeout — socket stays open)
     *  - pingInterval = 30s  (OkHttp sends WebSocket PING frames to keep the connection alive)
     */
    fun wsClient(): OkHttpClient {
        return OkHttpClient.Builder()
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
