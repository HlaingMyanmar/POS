package com.sspd.servicemgmt.feature.sale

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.TextMuted

private val PAPER_KEYS = listOf("A5", "A4", "POS_80MM", "POS_58MM")
private val PAPER_LABELS = mapOf(
    "A4" to "A4",
    "A5" to "A5",
    "POS_58MM" to "58mm",
    "POS_80MM" to "80mm",
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalePrintScreen(onBack: () -> Unit) {
    val vm: SalePrintViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var paper by remember { mutableStateOf("A5") }
    var showPaper by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var webLoading by remember { mutableStateOf(true) }

    LaunchedEffect(paper) { vm.loadHtml(paper) }
    LaunchedEffect(state.htmlContent) {
        if (state.htmlContent != null) webLoading = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Preview", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showPaper = true }) {
                            Text(
                                PAPER_LABELS[paper] ?: paper,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Outlined.ExpandMore, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showPaper, onDismissRequest = { showPaper = false }) {
                            PAPER_KEYS.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(PAPER_LABELS[p] ?: p) },
                                    onClick = {
                                        paper = p
                                        showPaper = false
                                    },
                                    leadingIcon = if (p == paper) ({
                                        Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }) else null,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { webViewRef.value?.let { printWebView(context, it, vm.saleId) } },
                        enabled = state.htmlContent != null && !state.loading && !webLoading,
                    ) {
                        Icon(Icons.Outlined.Print, "ပရင့်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(state.error ?: "ချိတ်ဆက်မှု မအောင်မြင်ပါ", color = TextMuted, fontSize = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = { vm.loadHtml(paper) }) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ထပ်မံ ကြိုးစားမည်")
                        }
                    }
                }
                else -> {
                    PrintWebView(
                        htmlContent = state.htmlContent,
                        baseUrl = ApiClient.rawBaseUrl,
                        onCreated = { webViewRef.value = it },
                        onPageStarted = { webLoading = true },
                        onPageFinished = { webLoading = false },
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
private fun PrintWebView(
    htmlContent: String?,
    baseUrl: String,
    onCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPageStarted()
                    override fun onPageFinished(view: WebView, url: String) = onPageFinished()

                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel()
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

private fun printWebView(context: Context, webView: WebView, saleId: Int) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(
        "sale-invoice-$saleId",
        webView.createPrintDocumentAdapter("sale-invoice-$saleId"),
        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A5).build(),
    )
}
