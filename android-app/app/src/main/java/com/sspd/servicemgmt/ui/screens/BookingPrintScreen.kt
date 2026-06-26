package com.sspd.servicemgmt.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.BookingPrintViewModel

private val BOOKING_PAPER_KEYS   = listOf("A4", "A5", "POS_58MM", "POS_80MM")
private val BOOKING_PAPER_LABELS = mapOf("A4" to "A4", "A5" to "A5", "POS_58MM" to "58mm", "POS_80MM" to "80mm")

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingPrintScreen(onBack: () -> Unit) {
    val vm: BookingPrintViewModel = viewModel()
    val state   by vm.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    var paper      by remember { mutableStateOf("A4") }
    var showPaper  by remember { mutableStateOf(false) }
    val webViewRef  = remember { mutableStateOf<WebView?>(null) }
    var webLoading by remember { mutableStateOf(true) }

    LaunchedEffect(paper) { vm.loadHtml(paper) }

    LaunchedEffect(state.htmlContent) {
        if (state.htmlContent != null) webLoading = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("လက်ခံဘောင်ချာ အစမ်းကြည့်ရန်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    // Paper size picker
                    Box {
                        TextButton(onClick = { showPaper = true }) {
                            Text(
                                BOOKING_PAPER_LABELS[paper] ?: paper,
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Outlined.ExpandMore, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showPaper, onDismissRequest = { showPaper = false }) {
                            BOOKING_PAPER_KEYS.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(BOOKING_PAPER_LABELS[p] ?: p) },
                                    onClick = { paper = p; showPaper = false },
                                    leadingIcon = if (p == paper) ({
                                        Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }) else null
                                )
                            }
                        }
                    }
                    // Print button
                    IconButton(
                        onClick  = { webViewRef.value?.let { printBookingWebView(context, it, vm.bookingId) } },
                        enabled  = state.htmlContent != null && !state.loading && !webLoading
                    ) {
                        Icon(Icons.Outlined.Print, "ပရင့်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(state.error ?: "ချိတ်ဆက်မှု မအောင်မြင်ပါ", color = TextMuted, fontSize = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = { vm.loadHtml(paper) }) {
                            Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ထပ်မံ ကြိုးစားမည်")
                        }
                    }
                }
                else -> {
                    BookingPrintWebView(
                        htmlContent    = state.htmlContent,
                        baseUrl        = ApiClient.rawBaseUrl,
                        onCreated      = { webViewRef.value = it },
                        onPageStarted  = { webLoading = true },
                        onPageFinished = { webLoading = false }
                    )
                    if (state.loading || (state.htmlContent != null && webLoading)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppLoading()
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BookingPrintWebView(
    htmlContent:    String?,
    baseUrl:        String,
    onCreated:      (WebView) -> Unit,
    onPageStarted:  () -> Unit,
    onPageFinished: () -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory  = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled    = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort      = true
                settings.builtInZoomControls  = true
                settings.displayZoomControls  = false
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
        }
    )
}

private fun printBookingWebView(context: Context, webView: WebView, bookingId: Int) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(
        "booking-$bookingId",
        webView.createPrintDocumentAdapter("booking-$bookingId"),
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
    )
}

