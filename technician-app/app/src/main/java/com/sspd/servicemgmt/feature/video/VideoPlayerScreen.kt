package com.sspd.servicemgmt.feature.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

private enum class PlayerStatus { Loading, Ready, Error }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    youtubeId: String,
    title: String?,
    description: String?,
    category: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val deviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var forcedLandscape by rememberSaveable { mutableStateOf(false) }
    val immersive = deviceLandscape || forcedLandscape
    val resolvedId = remember(youtubeId) { parseYoutubeId(Uri.decode(youtubeId).trim()) }
    var status by remember(resolvedId) { mutableStateOf(PlayerStatus.Loading) }
    var errorMessage by remember(resolvedId) { mutableStateOf<String?>(null) }
    var retryToken by rememberSaveable(resolvedId) { mutableIntStateOf(0) }

    fun exitImmersiveOrBack() {
        if (forcedLandscape) {
            forcedLandscape = false
        } else {
            onBack()
        }
    }

    BackHandler(onBack = { exitImmersiveOrBack() })

    DisposableEffect(forcedLandscape) {
        activity?.requestedOrientation = if (forcedLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    DisposableEffect(immersive) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (immersive) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val validId = isValidYoutubeId(resolvedId)

    LaunchedEffect(resolvedId, retryToken) {
        status = PlayerStatus.Loading
        errorMessage = null
        if (!validId) {
            errorMessage = "Video ID မမှန်ပါ"
            status = PlayerStatus.Error
        } else if (!hasNetwork(context)) {
            errorMessage = "အင်တာနက် ချိတ်ဆက်မှု မရှိပါ"
            status = PlayerStatus.Error
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (immersive) Color.Black else ScreenBg)
            .then(if (immersive) Modifier else Modifier.statusBarsPadding())
    ) {
        if (!immersive) {
            TopAppBar(
                title = { Text(title?.takeIf { it.isNotBlank() } ?: "ဗီဒီယို", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (immersive) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (validId && status != PlayerStatus.Error) {
                EmbeddedYouTubePlayer(
                    youtubeId = resolvedId.orEmpty(),
                    retryToken = retryToken,
                    onReady = { status = PlayerStatus.Ready },
                    onError = { message ->
                        errorMessage = message
                        status = PlayerStatus.Error
                    }
                )
            }
            if (status == PlayerStatus.Loading && errorMessage == null) {
                CircularProgressIndicator(color = Color.White)
            }
            if (status == PlayerStatus.Error) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(errorMessage ?: "Video ဖွင့်မရပါ", color = Color.White, fontWeight = FontWeight.Bold)
                    Button(onClick = { retryToken += 1 }) { Text("ပြန်ကြိုးစားရန်") }
                }
            }
            if (immersive) {
                IconButton(
                    onClick = { exitImmersiveOrBack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                }
            }
            IconButton(
                onClick = { forcedLandscape = !forcedLandscape },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (immersive) Modifier.statusBarsPadding() else Modifier)
                    .padding(4.dp)
            ) {
                Icon(
                    if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    if (immersive) "ပုံမှန်အရွယ်" else "မျက်နှာပြင်အပြည့်",
                    tint = Color.White
                )
            }
        }

        if (!immersive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title?.takeIf { it.isNotBlank() } ?: "Video", fontWeight = FontWeight.ExtraBold, color = TextMain, fontSize = 18.sp)
                if (!category.isNullOrBlank()) {
                    Text(category, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!description.isNullOrBlank()) {
                    Text(description, color = TextMuted, fontSize = 14.sp)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedYouTubePlayer(
    youtubeId: String,
    retryToken: Int,
    onReady: () -> Unit,
    onError: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(lifecycleOwner, webView) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    key(youtubeId, retryToken) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val origin = "https://${ctx.packageName}/"
                val headers = mapOf(
                    "Referer" to origin,
                    "Referrer-Policy" to "strict-origin-when-cross-origin"
                )
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowContentAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = settings.userAgentString
                        .replace("; wv", "")
                        .replace("Version/4.0 ", "")
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            if (scheme != "http" && scheme != "https") return true
                            val url = request.url.toString()
                            val host = request.url.host.orEmpty()
                            if (url.contains("/embed/")) return false
                            if (host.contains("youtube.com") || host.contains("youtu.be") || host.contains("youtube-nocookie.com")) {
                                view.loadUrl(url, headers)
                                return true
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (!url.startsWith("about:")) onReady()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                onError(
                                    if (!hasNetwork(ctx)) "အင်တာနက် ချိတ်ဆက်မှု မရှိပါ"
                                    else "Video ဖွင့်မရပါ"
                                )
                            }
                        }
                    }
                    webView = this
                    loadUrl(youtubeEmbedUrl(youtubeId, origin), headers)
                }
            },
            onRelease = { view ->
                if (webView === view) webView = null
                view.stopLoading()
                view.loadUrl("about:blank")
            }
        )
    }
}

private fun youtubeEmbedUrl(youtubeId: String, origin: String): String {
    val id = youtubeId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    return "https://www.youtube.com/embed/$id" +
        "?playsinline=1&rel=0&fs=0&modestbranding=0&enablejsapi=1" +
        "&origin=${Uri.encode(origin.trimEnd('/'))}"
}

private fun hasNetwork(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: SecurityException) {
        true
    }
}
