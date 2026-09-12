package com.sspd.servicemgmt.core.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import java.util.concurrent.TimeUnit

object ProductPhotoLoader {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of memory allocated for photo cache

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
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

    suspend fun loadBitmap(source: String?, maxDim: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        if (source.isNullOrBlank()) return@withContext null
        val cacheKey = "${source.hashCode()}_$maxDim"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val bitmap = if (source.startsWith("data:")) {
            ImageCodec.decodeDataUri(source, maxDim)
        } else {
            runCatching {
                client.newCall(Request.Builder().url(source).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    decodeBytes(bytes, maxDim)
                }
            }.getOrNull()
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    private fun decodeBytes(bytes: ByteArray, maxTargetDim: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val (w, h) = options.outWidth to options.outHeight
        if (w <= 0 || h <= 0) return null

        var inSampleSize = 1
        if (h > maxTargetDim || w > maxTargetDim) {
            val halfH = h / 2
            val halfW = w / 2
            while (halfH / inSampleSize >= maxTargetDim && halfW / inSampleSize >= maxTargetDim) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
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
