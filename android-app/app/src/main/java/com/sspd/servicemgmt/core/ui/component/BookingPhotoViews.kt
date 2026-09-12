package com.sspd.servicemgmt.core.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.core.network.BookingItemPhotoDTO
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.util.AssetUrls
import com.sspd.servicemgmt.core.util.ImageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object BookingPhotoLoader {
    private val client by lazy { ApiClient.buildPingClient() }

    fun thumbSource(photo: BookingItemPhotoDTO): String? =
        AssetUrls.resolve(photo.thumbnailPath)
            ?: AssetUrls.resolve(photo.imagePath)
            ?: photo.dataUrl?.takeIf { it.isNotBlank() }

    fun fullSource(photo: BookingItemPhotoDTO): String? =
        AssetUrls.resolve(photo.imagePath)
            ?: photo.dataUrl?.takeIf { it.isNotBlank() }
            ?: AssetUrls.resolve(photo.thumbnailPath)

    suspend fun loadBitmap(source: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (source.isNullOrBlank()) return@withContext null
        if (source.startsWith("data:")) return@withContext ImageCodec.decodeDataUri(source)
        runCatching {
            client.newCall(Request.Builder().url(source).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            }
        }.getOrNull()
    }
}

@Composable
fun BookingItemPhotoThumb(
    photo: BookingItemPhotoDTO,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    onClick: (() -> Unit)? = null,
) {
    val source = remember(photo) { BookingPhotoLoader.thumbSource(photo) }
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(source) { mutableStateOf(source != null) }

    LaunchedEffect(source) {
        loading = source != null
        bitmap = BookingPhotoLoader.loadBitmap(source)
        loading = false
    }

    val shape = RoundedCornerShape(8.dp)
    Card(
        shape = shape,
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = photo.fileName ?: "Device photo ${photo.slot ?: ""}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else -> Icon(Icons.Outlined.PhotoCamera, null, tint = TextMuted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun BookingPhotoViewerDialog(photo: BookingItemPhotoDTO, onDismiss: () -> Unit) {
    val source = remember(photo) { BookingPhotoLoader.fullSource(photo) }
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(source) { mutableStateOf(true) }

    LaunchedEffect(source) {
        loading = true
        bitmap = BookingPhotoLoader.loadBitmap(source)
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = photo.fileName ?: "ပစ္စည်းပုံ ${photo.slot ?: ""}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close", tint = OnPrimary)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        bitmap != null -> Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = photo.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        loading -> CircularProgressIndicator(color = Color.White)
                        else -> Text("ပုံ ဖတ်လို့မရပါ", color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}
