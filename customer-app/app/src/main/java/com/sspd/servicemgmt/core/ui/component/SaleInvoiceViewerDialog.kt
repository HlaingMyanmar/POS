package com.sspd.servicemgmt.core.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.util.SaleInvoiceOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SaleInvoiceViewerDialog(
    orderId: Int? = null,
    purchaseSaleId: Int? = null,
    serviceJobId: Int? = null,
    saleCode: String? = null,
    paymentReceipt: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isServiceJob = serviceJobId != null
    val title = when {
        paymentReceipt -> "ငွေလက်ခံပြေစာ"
        isServiceJob -> "Service Job Invoice (ဆိုင်ဘောင်ချာ)"
        else -> "Sale Invoice (ဆိုင်ဘောင်ချာ)"
    }
    val subtitle = if (paymentReceipt) {
        saleCode?.ifBlank { null } ?: "Payment Receipt"
    } else {
        saleCode?.ifBlank { null } ?: "In-App Voucher View"
    }

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPaperSize by remember { mutableStateOf("A4") }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun loadPdf() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = when {
                paymentReceipt && orderId != null ->
                    SaleInvoiceOpener.fetchPaymentReceiptPdfFile(context, orderId, saleCode, selectedPaperSize)
                serviceJobId != null ->
                    SaleInvoiceOpener.fetchServiceJobPdfFile(context, serviceJobId, saleCode, selectedPaperSize)
                orderId != null && !paymentReceipt ->
                    SaleInvoiceOpener.fetchOrderPdfFile(context, orderId, saleCode, selectedPaperSize)
                purchaseSaleId != null -> SaleInvoiceOpener.fetchPurchasePdfFile(context, purchaseSaleId, saleCode, selectedPaperSize)
                else -> Result.failure(IllegalArgumentException("Invoice ID မရှိပါ"))
            }

            result.fold(
                onSuccess = { file ->
                    pdfFile = file
                    withContext(Dispatchers.IO) {
                        try {
                            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(pfd)
                            val renderedPages = mutableListOf<Bitmap>()
                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                val targetScale = 2.5f
                                val width = (page.width * targetScale).toInt().coerceAtLeast(1)
                                val height = (page.height * targetScale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(AndroidColor.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                renderedPages.add(bitmap)
                            }
                            renderer.close()
                            pfd.close()
                            withContext(Dispatchers.Main) {
                                pages = renderedPages
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMessage = "PDF ရုပ်ပုံ ပြောင်းမရပါ: ${e.message}"
                                isLoading = false
                            }
                        }
                    }
                },
                onFailure = { err ->
                    errorMessage = err.message ?: if (paymentReceipt) "ငွေလက်ခံပြေစာ ဒေါင်းလုဒ်မရပါ" else "Invoice ဒေါင်းလုဒ်မရပါ"
                    isLoading = false
                }
            )
        }
    }

    LaunchedEffect(orderId, purchaseSaleId, serviceJobId, paymentReceipt, selectedPaperSize) {
        loadPdf()
    }

    DisposableEffect(Unit) {
        onDispose {
            pages.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            color = ScreenBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Surface(
                    color = CardBg,
                    border = BorderStroke(1.dp, BorderColor),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDark
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        if (pdfFile != null) {
                            IconButton(
                                onClick = {
                                    pdfFile?.let {
                                        SaleInvoiceOpener.openFileInExternalApp(
                                            context,
                                            it,
                                            share = true,
                                            title = when {
                                                paymentReceipt -> "ငွေလက်ခံပြေစာ ပို့မည်"
                                                isServiceJob -> "Service Job Invoice ပို့မည်"
                                                else -> "Send Invoice"
                                            }
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "Share",
                                    tint = Primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    pdfFile?.let {
                                        SaleInvoiceOpener.openFileInExternalApp(
                                            context,
                                            it,
                                            share = false,
                                            title = when {
                                                paymentReceipt -> "ငွေလက်ခံပြေစာ"
                                                isServiceJob -> "Service Job Invoice"
                                                else -> "Sale Invoice"
                                            }
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = "External PDF App",
                                    tint = Primary
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Close",
                                tint = TextMain
                            )
                        }
                    }
                }

                // Paper Size Selector Strip
                Surface(
                    color = SurfaceSoft,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = PrimaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Paper Size:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDark
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "A4" to "📄 A4",
                                "A5" to "📄 A5",
                                "80mm" to "🧾 POS 80mm"
                            ).forEach { (code, label) ->
                                val isSelected = selectedPaperSize.equals(code, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Primary else CardBg,
                                    border = BorderStroke(1.dp, if (isSelected) Primary else BorderColor),
                                    modifier = Modifier.clickable {
                                        if (!isSelected) {
                                            selectedPaperSize = code
                                        }
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) OnPrimary else TextMain
                                    )
                                }
                            }
                        }
                    }
                }

                // Content View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFEFEFEF)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                                Text(
                                    if (paymentReceipt) "ငွေလက်ခံပြေစာ ရယူနေသည်..."
                                    else if (isServiceJob) "Service Job Invoice ရယူနေသည်..."
                                    else "Sale Invoice ရယူနေသည်...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        errorMessage != null -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CardBg,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = errorMessage ?: "မအောင်မြင်ပါ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Danger,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Button(
                                        onClick = { loadPdf() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("ပြန်ကြိုးစားမည်", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        pages.isNotEmpty() -> {
                            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                                val newScale = (scale * zoomChange).coerceIn(1f, 4.5f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offset += panChange
                                } else {
                                    offset = Offset.Zero
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (scale > 1f) {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else {
                                                    scale = 2.2f
                                                }
                                            }
                                        )
                                    }
                                    .transformable(state = transformableState)
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(pages) { index, bitmap ->
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color(0xFFDDDDDD))
                                        ) {
                                            Column {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "Invoice Page ${index + 1}",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                                                    contentScale = ContentScale.Fit
                                                )
                                                if (pages.size > 1) {
                                                    Surface(
                                                        color = Color(0xFFF8F9FA),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "Page ${index + 1} of ${pages.size}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextMuted,
                                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Zoom Control Toolbar
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = PrimaryDark.copy(alpha = 0.88f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scale = (scale - 0.5f).coerceIn(1f, 4.5f)
                                                if (scale == 1f) offset = Offset.Zero
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                                        }

                                        Text(
                                            text = "${(scale * 100).toInt()}%",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                scale = (scale + 0.5f).coerceIn(1f, 4.5f)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                                        }

                                        if (scale > 1f) {
                                            IconButton(
                                                onClick = {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Info
                Surface(
                    color = CardBg,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when {
                                paymentReceipt ->
                                    "ဆိုင်က ငွေဝင်မှု အတည်ပြုပြီး ထုတ်ပေးသော ပြေစာ ဖြစ်သည်။ Sale ဘောင်ချာသည် ဘောင်ချာထုတ်ပြီးမှ ရပါမည်။"
                                isServiceJob ->
                                    "Service နဲ့ Part ကို web POS voucher အတိုင်း တစ်ထည်တည်း ကြည့်နေပါသည်။ Sale သက်သက်သည် Sale မှတ်တမ်းတွင်သာ ရှိသည်။"
                                else ->
                                    "ဆိုင် POS ဘောင်ချာ template အတိုင်း customer-app တွင် တိုက်ရိုက် ကြည့်ရှုနေပါသည်။"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("ပိတ်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaperSizeSelectionDialog(
    title: String = "Paper Size ရွေးပါ",
    subtitle: String = "ဘောက်ချာ ပို့လိုသော Paper Size ကို ရွေးချယ်ပါ",
    onSelect: (paperSize: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryDark
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceSoft)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))

                listOf(
                    PaperSizeOption("A4", "A4 Size", "Standard full-page voucher template (A4)", "📄"),
                    PaperSizeOption("A5", "A5 Size", "Compact half-page voucher template (A5)", "📄"),
                    PaperSizeOption("80mm", "POS 80mm", "Thermal receipt printer slip format (80mm)", "🧾")
                ).forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option.code)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceSoft,
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(option.icon, fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    option.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMain
                                )
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Icon(
                                Icons.Outlined.Send,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("မလုပ်တော့ပါ", color = TextMuted)
                }
            }
        }
    }
}

private data class PaperSizeOption(
    val code: String,
    val title: String,
    val description: String,
    val icon: String
)
