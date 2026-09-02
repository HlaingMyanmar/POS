package com.sspd.servicemgmt.feature.booking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*

private data class PaperOption(
    val key: String,
    val label: String,
    val hint: String,
    val previewWidth: Dp?,
)

private val BOOKING_PAPER_OPTIONS = listOf(
    PaperOption("POS_80MM", "80mm", "Thermal", 300.dp),
    PaperOption("POS_58MM", "58mm", "Thermal", 228.dp),
    PaperOption("A5", "A5", "Half page", 360.dp),
    PaperOption("A4", "A4", "Full page", null),
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingPrintScreen(onBack: () -> Unit) {
    val vm: BookingPrintViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var paper by remember { mutableStateOf("POS_80MM") }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var webLoading by remember { mutableStateOf(true) }

    val readyToPrint = state.htmlContent != null && !state.loading && !webLoading

    LaunchedEffect(paper) { vm.loadHtml(paper) }

    LaunchedEffect(state.htmlContent) {
        if (state.htmlContent != null) webLoading = true
    }

    Scaffold(
        containerColor = ScreenBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("လက်ခံ Voucher", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text(
                            "Booking #${vm.bookingId}",
                            fontSize = 11.sp,
                            color = OnPrimary.copy(alpha = 0.85f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = OnPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadHtml(paper) }, enabled = !state.loading) {
                        Icon(Icons.Outlined.Refresh, "Refresh", tint = OnPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary,
                ),
            )
        },
        bottomBar = {
            Surface(color = CardBg, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = BorderColor)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("ပိတ်မည်", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                webViewRef.value?.let {
                                    printBookingWebView(context, it, vm.bookingId, paper)
                                }
                            },
                            enabled = readyToPrint,
                            modifier = Modifier.weight(1.6f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Icon(Icons.Outlined.Print, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ပရင့်ထုတ်မည်", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.error != null -> BookingPrintErrorState(
                message = state.error ?: "ချိတ်ဆက်မှု မအောင်မြင်ပါ",
                onRetry = { vm.loadHtml(paper) },
                modifier = Modifier.padding(padding),
            )

            else -> BookingPrintPreviewContent(
                modifier = Modifier.padding(padding),
                paper = paper,
                onPaperChange = { paper = it },
                htmlContent = state.htmlContent,
                loading = state.loading || (state.htmlContent != null && webLoading),
                onWebViewCreated = { webViewRef.value = it },
                onPageStarted = { webLoading = true },
                onPageFinished = { webLoading = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingPrintPreviewContent(
    paper: String,
    onPaperChange: (String) -> Unit,
    htmlContent: String?,
    loading: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = BOOKING_PAPER_OPTIONS.firstOrNull { it.key == paper } ?: BOOKING_PAPER_OPTIONS.first()
    val previewWidth = selected.previewWidth

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("စက္ကူ အရွယ်အစား", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BOOKING_PAPER_OPTIONS.forEach { option ->
                    val isSelected = option.key == paper
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPaperChange(option.key) },
                        label = {
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text(option.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(option.hint, fontSize = 10.sp, color = if (isSelected) Primary else TextMuted)
                            }
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryLight,
                            selectedLabelColor = Primary,
                            selectedLeadingIconColor = Primary,
                        ),
                    )
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .then(
                        if (previewWidth != null) Modifier.width(previewWidth)
                        else Modifier.fillMaxWidth()
                    )
                    .shadow(6.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BorderColor),
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                    BookingPrintWebView(
                        htmlContent = htmlContent,
                        baseUrl = ApiClient.rawBaseUrl,
                        onCreated = onWebViewCreated,
                        onPageStarted = onPageStarted,
                        onPageFinished = onPageFinished,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (loading) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Color.White.copy(alpha = 0.92f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppLoading()
                                Spacer(Modifier.height(10.dp))
                                Text("Voucher ပြင်ဆင်နေသည်…", fontSize = 13.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ZoomIn, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("ချုံ့/ချဲ့ ကြည့်ရန် နှစ်ချောင်းနှိပ်ပါ", fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun BookingPrintErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(color = DangerBg, shape = RoundedCornerShape(16.dp)) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    null,
                    tint = Danger,
                    modifier = Modifier.padding(18.dp).size(36.dp),
                )
            }
            Text("Voucher မပြနိုင်ပါ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Text(message, fontSize = 13.sp, color = TextMuted)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ထပ်မံ ကြိုးစားမည်", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BookingPrintWebView(
    htmlContent: String?,
    baseUrl: String,
    onCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPageStarted()
                    override fun onPageFinished(view: WebView, url: String) = onPageFinished()

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.proceed()
                    }
                }
                onCreated(this)
            }
        },
        update = { webView ->
            if (htmlContent != null) {
                webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
            }
        },
    )
}

private fun printBookingWebView(context: Context, webView: WebView, bookingId: Int, paper: String) {
    val mediaSize = when (paper) {
        "A5" -> PrintAttributes.MediaSize.ISO_A5
        "POS_58MM", "POS_80MM" -> PrintAttributes.MediaSize.UNKNOWN_PORTRAIT
        else -> PrintAttributes.MediaSize.ISO_A4
    }
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(
        "booking-voucher-$bookingId",
        webView.createPrintDocumentAdapter("booking-voucher-$bookingId"),
        PrintAttributes.Builder().setMediaSize(mediaSize).build(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Voucher — paper selector", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun BookingPrintPaperSelectorPreview() {
    AppTheme {
        Surface(color = ScreenBg) {
            var paper by remember { mutableStateOf("POS_80MM") }
            Column {
                Spacer(Modifier.height(12.dp))
                BookingPrintPreviewContent(
                    paper = paper,
                    onPaperChange = { paper = it },
                    htmlContent = null,
                    loading = true,
                    onWebViewCreated = {},
                    onPageStarted = {},
                    onPageFinished = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(name = "Voucher — error", showBackground = true, widthDp = 390, heightDp = 400)
@Composable
private fun BookingPrintErrorPreview() {
    AppTheme {
        BookingPrintErrorState(
            message = "Server error 500",
            onRetry = {},
        )
    }
}
