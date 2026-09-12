package com.sspd.servicemgmt.core.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.core.network.ProductDTO
import com.sspd.servicemgmt.core.util.AssetUrls
import com.sspd.servicemgmt.core.util.ImageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ProductPhotoLoader {
    private val client: OkHttpClient by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun thumbSource(product: ProductDTO): String? {
        val slot1 = product.photos.find { it.slot == 1 } ?: product.photos.firstOrNull()
        return AssetUrls.resolve(slot1?.thumbnailPath)
            ?: AssetUrls.resolve(slot1?.imagePath)
            ?: AssetUrls.resolve(product.thumbnailPath)
            ?: AssetUrls.resolve(product.imagePath)
            ?: product.photoBase64?.takeIf { it.isNotBlank() }
    }

    fun fullSource(product: ProductDTO): String? {
        val slot1 = product.photos.find { it.slot == 1 } ?: product.photos.firstOrNull()
        return AssetUrls.resolve(slot1?.imagePath)
            ?: AssetUrls.resolve(product.imagePath)
            ?: product.photoBase64?.takeIf { it.isNotBlank() }
            ?: AssetUrls.resolve(slot1?.thumbnailPath)
            ?: AssetUrls.resolve(product.thumbnailPath)
    }

    fun thumbSourceForSlot(product: ProductDTO, slot: Int): String? {
        val ph = product.photos.find { it.slot == slot }
        return AssetUrls.resolve(ph?.thumbnailPath)
            ?: AssetUrls.resolve(ph?.imagePath)
            ?: if (slot == 1) thumbSource(product) else null
    }

    fun fullSourceForSlot(product: ProductDTO, slot: Int): String? {
        val ph = product.photos.find { it.slot == slot }
        return AssetUrls.resolve(ph?.imagePath)
            ?: AssetUrls.resolve(ph?.thumbnailPath)
            ?: if (slot == 1) fullSource(product) else null
    }

    fun resolveSource(pathOrData: String?): String? =
        AssetUrls.resolve(pathOrData) ?: pathOrData?.takeIf { it.isNotBlank() }

    suspend fun loadBitmap(source: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (source.isNullOrBlank()) return@withContext null
        if (source.startsWith("data:")) return@withContext ImageCodec.decodeDataUri(source)
        runCatching {
            client.newCall(Request.Builder().url(source).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                decodeBytes(bytes)
            }
        }.getOrNull()
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes))
            }.getOrNull()?.let { return it }
        }
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

@Composable
fun ProductPhotoImage(
    source: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(source) { mutableStateOf(source != null) }

    LaunchedEffect(source) {
        loading = source != null
        bitmap = ProductPhotoLoader.loadBitmap(source)
        loading = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
            loading -> CircularProgressIndicator(modifier = Modifier.fillMaxSize(0.35f), strokeWidth = 2.dp)
            else -> placeholder()
        }
    }
}
