package com.sspd.servicemgmt.feature.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.sspd.servicemgmt.core.network.VideoDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun VideoListScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoDTO) -> Unit
) {
    val vm: VideoListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    VideoListContent(
        state = state,
        onBack = onBack,
        onSearch = vm::setSearch,
        onRetry = vm::load,
        onOpen = onOpenVideo
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListContent(
    state: VideoListViewModel.UiState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit = {},
    onOpen: (VideoDTO) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("လေ့ကျင့်ရေး ဗီဒီယို", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("ခေါင်းစဉ် / အမျိုးအစား") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            Text(
                "YouTube တွင် SSPD account email ဖြင့် login ဝင်ထားရန်လိုသည်",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                fontSize = 11.sp,
                color = TextMuted
            )

            when {
                state.loading -> AppLoading()
                state.error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error ?: "", color = TextMuted)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text("ပြန်ကြိုးစားရန်")
                    }
                }
                state.filtered.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.VideoLibrary, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Text("ဗီဒီယို မရှိသေးပါ", color = TextMuted, fontWeight = FontWeight.Bold)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.filtered,
                        key = { video ->
                            if (video.id > 0) "video-${video.id}" else "video-${video.providerVideoId}-${video.title.orEmpty()}-${video.sortOrder}"
                        }
                    ) { video ->
                        VideoCard(video) { onOpen(video) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoDTO, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VideoThumbnail(videoThumbUrl(video))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (video.featured == true) {
                        Icon(Icons.Outlined.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                    }
                    Text(video.title.orEmpty().ifBlank { "Video" }, fontWeight = FontWeight.ExtraBold, color = TextMain, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!video.category.isNullOrBlank()) {
                    Text(video.category, fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
                }
                if (!video.description.isNullOrBlank()) {
                    Text(video.description, fontSize = 12.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val thumbnailClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

@Composable
private fun VideoThumbnail(url: String?) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || !isAllowedThumbUrl(url)) return@LaunchedEffect
        val call = runCatching { thumbnailClient.newCall(Request.Builder().url(url).build()) }.getOrNull()
            ?: return@LaunchedEffect
        try {
            bitmap = withContext(Dispatchers.IO) {
                runCatching { decodeThumbnail(call) }.getOrNull()
            }
        } finally {
            call.cancel()
        }
    }
    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

private fun decodeThumbnail(call: okhttp3.Call): Bitmap? {
    call.execute().use { response ->
        if (!response.isSuccessful) return null
        val bytes = response.body?.bytes() ?: return null
        if (bytes.isEmpty() || bytes.size > 512_000) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = BitmapFactory.Options().apply {
            inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight, 224, 144)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sample)
    }
}

private fun thumbnailSampleSize(width: Int, height: Int, targetW: Int, targetH: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / sample > targetW * 2 || height / sample > targetH * 2) {
        sample *= 2
    }
    return sample
}

private val previewVideos = listOf(
    VideoDTO(
        id = 1,
        title = "Laptop Motherboard Diagnosis",
        description = "Board-level fault finding for field technicians",
        category = "Diagnosis",
        targetAudience = "TECHNICIAN",
        featured = true,
        sortOrder = 1
    ),
    VideoDTO(
        id = 2,
        title = "CCTV Installation Guide",
        description = "Camera placement, cabling, and NVR setup",
        category = "Installation",
        targetAudience = "TECHNICIAN",
        sortOrder = 2
    ),
    VideoDTO(
        id = 3,
        title = "Network Troubleshooting",
        description = "Router, switch, and Wi-Fi checks on site",
        category = "Network",
        targetAudience = "BOTH",
        sortOrder = 3
    ),
    VideoDTO(
        id = 4,
        title = "Company Introduction",
        description = "Shared welcome video for both apps",
        category = "Orientation",
        targetAudience = "BOTH",
        sortOrder = 4
    )
)

@Preview(name = "ဗီဒီယိုစာရင်း", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VideoListPreview() {
    AppTheme {
        VideoListContent(
            state = VideoListViewModel.UiState(videos = previewVideos),
            onBack = {},
            onSearch = {},
            onOpen = {}
        )
    }
}

@Preview(name = "ဗလာ", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VideoListEmptyPreview() {
    AppTheme {
        VideoListContent(
            state = VideoListViewModel.UiState(videos = emptyList()),
            onBack = {},
            onSearch = {},
            onOpen = {}
        )
    }
}

@Preview(name = "အမှား", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VideoListErrorPreview() {
    AppTheme {
        VideoListContent(
            state = VideoListViewModel.UiState(error = "ချိတ်ဆက်မှု မအောင်မြင်ပါ"),
            onBack = {},
            onSearch = {},
            onOpen = {}
        )
    }
}

@Preview(name = "ကတ်", showBackground = true, widthDp = 390)
@Composable
private fun VideoCardPreview() {
    AppTheme {
        Box(Modifier.background(ScreenBg).padding(16.dp)) {
            VideoCard(previewVideos.first()) {}
        }
    }
}
