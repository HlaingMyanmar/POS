package com.sspd.servicemgmt.feature.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.util.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebModuleScreen(
    title: String,
    endpoint: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager(context) }
    val baseUri = remember { Uri.parse(ApiClient.rawBaseUrl) }
    val url = remember(endpoint) {
        val hash = endpoint.trimStart('/')
        "${ApiClient.rawBaseUrl.trimEnd('/')}/#/$hash"
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessionInjected by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary,
                    navigationIconContentColor = OnPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        if (prefs.refreshToken.isNotBlank()) {
                            CookieManager.getInstance().setCookie(
                                ApiClient.rawBaseUrl,
                                "refreshToken=${prefs.refreshToken}; Path=/"
                            )
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?,
                            ): Boolean {
                                fileCallback?.onReceiveValue(null)
                                fileCallback = filePathCallback
                                val mime = fileChooserParams?.acceptTypes?.firstOrNull()?.ifBlank { "*/*" } ?: "*/*"
                                filePicker.launch(mime)
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = request.url.host != baseUri.host

                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: SslError,
                            ) {
                                handler.proceed()
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                loading = true
                                error = null
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                if (!sessionInjected && (prefs.refreshToken.isNotBlank() || prefs.authToken.isNotBlank())) {
                                    sessionInjected = true
                                    view.evaluateJavascript(sessionBootstrapJs(prefs), null)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                webError: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    error = webError.description.toString()
                                }
                            }
                        }
                        loadUrl(url)
                    }
                },
            )

            if (loading) CircularProgressIndicator()
            error?.let { Text(it) }
        }
    }
}

private fun sessionBootstrapJs(prefs: PreferenceManager): String {
    val user = JSONObject()
        .put("username", prefs.username)
        .put("name", prefs.displayName)
        .put("phone", prefs.phone)
        .put("roles", JSONArray(prefs.rolesStr.split(',').filter { it.isNotBlank() }))
        .put("permissions", JSONArray(prefs.permissionsStr.split(',').filter { it.isNotBlank() }))
    if (prefs.staffId > 0) user.put("staffId", prefs.staffId)
    val refresh = prefs.refreshToken.ifBlank { prefs.authToken }
    return """
        (function() {
          try {
            sessionStorage.setItem('sspd_refresh', ${JSONObject.quote(refresh)});
            sessionStorage.setItem('sspd_user', ${JSONObject.quote(user.toString())});
            if (!sessionStorage.getItem('sspd_android_boot')) {
              sessionStorage.setItem('sspd_android_boot', '1');
              location.reload();
            }
          } catch (e) {}
        })();
    """.trimIndent()
}
