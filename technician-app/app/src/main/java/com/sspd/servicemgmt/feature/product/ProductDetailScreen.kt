package com.sspd.servicemgmt.feature.product

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppLoading

import com.sspd.servicemgmt.core.util.fmtWarranty
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import com.sspd.servicemgmt.core.ui.scanner.BarcodeScannerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(onBack: () -> Unit, onEdit: () -> Unit = {}) {
    val vm: ProductDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingUploadSerial  by remember { mutableStateOf<ProductSerialDTO?>(null) }
    var pendingProductPhoto  by remember { mutableStateOf(false) }
    var viewingPhoto         by remember { mutableStateOf<String?>(null) }   // data-uri string
    var showSourceSheet      by remember { mutableStateOf(false) }
    var showAddSerialDialog  by remember { mutableStateOf(false) }
    var newSerialNumber      by remember { mutableStateOf("") }

    fun handleBitmap(bitmap: Bitmap) {
        val b64 = bitmapToBase64(bitmap)
        if (pendingProductPhoto) {
            vm.uploadProductPhoto(b64)
            pendingProductPhoto = false
        } else {
            pendingUploadSerial?.id?.let { vm.uploadSerialPhoto(it, b64) }
            pendingUploadSerial = null
        }
    }

    fun handleUri(uri: Uri) {
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        handleBitmap(bmp)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleUri(it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let { handleBitmap(it) }
    }

    // Auto-scroll to highlighted serial
    LaunchedEffect(state.scannedSerial) {
        val serial = state.scannedSerial ?: return@LaunchedEffect
        val idx = state.serials.indexOfFirst { it.serialNumber == serial }
        if (idx >= 0) {
            // items: header(0), price(1), stock(2), details(3), serial-header(4), then pairs
            val pairIdx = idx / 2
            scope.launch { listState.animateScrollToItem(5 + pairIdx) }
        }
    }

    LaunchedEffect(state.scanError)    { state.scanError?.let    { snackbarHostState.showSnackbar(it); vm.clearScanError() } }
    LaunchedEffect(state.uploadError)  { state.uploadError?.let  { snackbarHostState.showSnackbar(it); vm.clearUploadError() } }
    LaunchedEffect(state.uploadSuccess){ state.uploadSuccess?.let{ snackbarHostState.showSnackbar("ပုံ upload အောင်မြင်ပါသည်"); vm.clearUploadSuccess() } }

    // Full-screen photo viewer
    val selectedPhoto = viewingPhoto
    if (selectedPhoto != null) {
        Dialog(
            onDismissRequest = { viewingPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)).clickable { viewingPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(selectedPhoto) { decodeDataUri(selectedPhoto) }
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = state.product?.name ?: "Product photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.55f))
                }
                Text("Tap anywhere to close",
                    color = Color.White.copy(0.5f), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
            }
        }
    }

    // Camera / Gallery chooser
    if (showSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("ပုံ ရွေးချယ်ရန်", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 12.dp))
                ListItem(
                    headlineContent = { Text("ကင်မရာ") },
                    leadingContent  = { Icon(Icons.Outlined.CameraAlt, null, tint = Primary) },
                    modifier = Modifier.clickable { showSourceSheet = false; cameraLauncher.launch(null) }
                )
                ListItem(
                    headlineContent = { Text("Gallery") },
                    leadingContent  = { Icon(Icons.Outlined.PhotoLibrary, null, tint = Primary) },
                    modifier = Modifier.clickable { showSourceSheet = false; galleryLauncher.launch("image/*") }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddSerialDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.addingSerial) showAddSerialDialog = false },
            icon = { Icon(Icons.Outlined.QrCode2, null, tint = Primary) },
            title = { Text("Serial အသစ်ထည့်ရန်", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = newSerialNumber,
                    onValueChange = { newSerialNumber = it },
                    label = { Text("Serial number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.addSerial(newSerialNumber)
                        newSerialNumber = ""
                        showAddSerialDialog = false
                    },
                    enabled = !state.addingSerial,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (state.addingSerial) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("ထည့်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showAddSerialDialog = false }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("ကုန်ပစ္စည်း အချက်အလက်", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Primary) }
                    },
                    actions = {
                        if (!BuildConfig.TECHNICIAN_ONLY) {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Outlined.Edit, "ပြင်ဆင်ရန်", tint = Primary)
                            }
                        }
                        if (state.product?.hasSerial == true) {
                            IconButton(onClick = { vm.showScanner() }) {
                                Icon(Icons.Outlined.QrCodeScanner, "ဘားကုဒ် ဖတ်ရန်", tint = Primary)
                            }
                        }
                        IconButton(onClick = { vm.load() }) {
                            Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Primary)
                )
            }
        ) { padding ->
            if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
                return@Scaffold
            }

            val p = state.product ?: run {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(state.error ?: "ကုန်ပစ္စည်း မတွေ့ပါ", color = if (state.error != null) Danger else TextMuted)
                    }
                }
                return@Scaffold
            }

            val avail = p.availableSerialCount ?: p.stockQty
            val serialPairs = state.serials.chunked(2)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Header card (matches RN) ──────────────────────────────
                item {
                    Card(
                        shape  = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 140dp product photo box
                            Box(
                                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (state.uploadingProductPhoto) {
                                    Box(Modifier.fillMaxSize().background(ScreenBg), contentAlignment = Alignment.Center) {
                                        AppLoading()
                                    }
                                } else {
                                    val photoUri = p.photoBase64
                                    val bmp = remember(photoUri) { photoUri?.let { decodeDataUri(it) } }
                                    if (bmp != null) {
                                        Image(bitmap = bmp.asImageBitmap(), contentDescription = p.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize())
                                        // Overlay buttons row — camera | view | share
                                        Row(Modifier.fillMaxWidth().height(34.dp)) {
                                            Box(Modifier.weight(1f).fillMaxHeight()
                                                .background(Color.Black.copy(0.50f))
                                                .clickable { pendingProductPhoto = true; showSourceSheet = true },
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                            Box(Modifier.weight(1f).fillMaxHeight()
                                                .background(Color(0xCC6366F1))
                                                .clickable { viewingPhoto = photoUri },
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                            Box(Modifier.weight(1f).fillMaxHeight()
                                                .background(Color(0xCC16A34A))
                                                .clickable {
                                                    val shareText = "${p.name}\nCode: ${p.productCode}\n${p.sellingPrice.fmt()} Ks"
                                                    shareContent(context, bmp, shareText)
                                                },
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else {
                                        Box(Modifier.fillMaxSize()
                                            .background(ScreenBg, RoundedCornerShape(12.dp))
                                            .clickable { pendingProductPhoto = true; showSourceSheet = true },
                                            contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Outlined.CameraAlt, null, tint = TextMuted, modifier = Modifier.size(28.dp))
                                                Text("ကုန်ပစ္စည်းပုံ ထည့်ရန်", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                            Spacer(Modifier.height(10.dp))
                            TypeBadge(p.productType)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val stockBg    = if (avail > 0) SuccessBg else DangerBg
                                val stockColor = if (avail > 0) Success    else Danger
                                val stockText  = if (avail > 0) "လက်ကျန် $avail ခု" else "ပစ္စည်းကုန်"
                                Surface(color = stockBg, shape = RoundedCornerShape(8.dp)) {
                                    Text(stockText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stockColor)
                                }
                                if (p.hasSerial == true) {
                                    Surface(color = PrimaryLight, shape = RoundedCornerShape(8.dp)) {
                                        Text("Serial ဖြင့်ထိန်းထားသည်", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Remark ───────────────────────────────────────────────
                if (!p.remark.isNullOrBlank()) item {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("REMARK", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Warning, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(p.remark, fontSize = 13.sp, color = TextMain, lineHeight = 20.sp)
                        }
                    }
                }

                if (!p.warrantyTerms.isNullOrBlank()) item {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("WARRANTY TERMS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Success, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(p.warrantyTerms, fontSize = 13.sp, color = TextMain, lineHeight = 20.sp)
                        }
                    }
                }

                // ── Details table ─────────────────────────────────────────
                item {
                    Card(shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()) {
                        Column {
                            listOf(
                                "ကုန်ပစ္စည်းကုဒ်"  to p.productCode,
                                "အမျိုးအစား"       to (p.categoryName ?: "—"),
                                "Brand"            to (p.brandName ?: "—"),
                                "Unit"             to (p.unitName ?: "—"),
                                "ရောင်းဈေး"        to "${p.sellingPrice.fmt()} Ks",
                                "လက်ကျန်"          to "$avail ခု",
                                "Reorder Level"    to (p.reorderLevel?.let { "$it ခု" } ?: "—"),
                                "အာမခံ"            to (p.warrantyMonths?.let { fmtWarranty(it).ifEmpty { "—" } } ?: "—"),
                            ).forEachIndexed { i, (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = TextMuted, modifier = Modifier.weight(1f))
                                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = TextMain, modifier = Modifier.weight(1f))
                                }
                                if (i < 11) HorizontalDivider(color = BorderColor)
                            }
                        }
                    }
                }

                // ── Serial section ────────────────────────────────────────
                if (p.hasSerial == true) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("SERIAL NUMBERS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                    color = TextMuted, letterSpacing = 0.6.sp)
                                if (state.serials.isNotEmpty())
                                    Text("(${state.serials.size})", fontSize = 11.sp, color = TextMuted)
                            }
                            if (state.scannedSerial != null) {
                                TextButton(onClick = { vm.clearHighlight() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text("Highlight ရှင်းမည်", fontSize = 11.sp, color = Primary)
                                }
                            }
                            TextButton(onClick = { newSerialNumber = ""; showAddSerialDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp), tint = Primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Serial ထည့်", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (state.scannedSerial != null) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = PrimaryLight, shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Scan ဖတ်ရ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary)
                                    Text(state.scannedSerial ?: "", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                                }
                            }
                        }
                    }

                    // 2-column grid rows
                    items(serialPairs) { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { serial ->
                                SerialCard(
                                    modifier      = Modifier.weight(1f),
                                    serial        = serial,
                                    isHighlighted = serial.serialNumber == state.scannedSerial,
                                    isUploading   = state.uploadingSerialId == serial.id,
                                    isDeleting    = state.deletingSerialId == serial.id,
                                    onViewPhoto   = { viewingPhoto = it },
                                    onUploadPhoto = {
                                        pendingUploadSerial = serial
                                        showSourceSheet = true
                                    },
                                    onDelete      = { vm.deleteSerial(serial) },
                                    onSharePhoto  = { bmp ->
                                        val shareText = buildString {
                                            appendLine("🏷 ${p.name}")
                                            if (!p.brandName.isNullOrBlank()) appendLine("Brand: ${p.brandName}")
                                            appendLine("ရောင်းဈေး: ${p.sellingPrice.fmt()} Ks")
                                            appendLine("လက်ကျန်: ${p.availableSerialCount ?: p.stockQty} ခု")
                                            val warranty = when {
                                                !p.warrantyTerms.isNullOrBlank() -> p.warrantyTerms
                                                else -> fmtWarranty(p.warrantyMonths).ifEmpty { null }
                                            }
                                            if (warranty != null) appendLine("အာမခံ: $warranty")
                                            if (!p.remark.isNullOrBlank()) appendLine("မှတ်ချက်: ${p.remark}")
                                            appendLine("─────────────────")
                                            appendLine("Serial: ${serial.serialNumber}")
                                            if (!serial.condition.isNullOrBlank()) appendLine("Condition: ${serial.condition}")
                                            val snWLabel = fmtWarranty(serial.warrantyMonths)
                                            if (snWLabel.isNotEmpty()) appendLine("အာမခံ: $snWLabel")
                                            append("Status: ${serial.status ?: "—"}")
                                        }
                                        shareContent(context, bmp, shareText)
                                    }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (state.showScanner) {
            BarcodeScannerView(onResult = { vm.onScanResult(it) }, onClose = { vm.dismissScanner() })
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun shareContent(context: Context, bitmap: Bitmap?, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
    if (bitmap != null) {
        val file = File(context.cacheDir, "share_photo.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        intent.type = "image/jpeg"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        intent.type = "text/plain"
    }
    intent.putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val maxDim = 400
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodeDataUri(uri: String): Bitmap? = runCatching {
    val raw = uri.substringAfter("base64,", uri)
    val bytes = Base64.decode(raw, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun TypeBadge(type: String) {
    val (bg, color, label) = when (type.uppercase()) {
        "NEW"        -> Triple(Color(0xFF4ADE80), Color(0xFF14532D), "NEW")
        "SECOND_NEW" -> Triple(Color(0xFFA78BFA), Color(0xFF4C1D95), "SECOND NEW")
        else         -> Triple(Color(0xFFFBBF24), Color(0xFF78350F), "SECOND")
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color,
            letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SerialCard(
    modifier:      Modifier = Modifier,
    serial:        ProductSerialDTO,
    isHighlighted: Boolean = false,
    isUploading:   Boolean = false,
    isDeleting:    Boolean = false,
    onViewPhoto:   (String) -> Unit = {},
    onUploadPhoto: () -> Unit = {},
    onDelete:      () -> Unit = {},
    onSharePhoto:  (Bitmap?) -> Unit = {}
) {
    val (statusBg, statusColor, statusLabel) = when (serial.status?.uppercase()) {
        "AVAILABLE"      -> Triple(SuccessBg, Success, "Available")
        "SOLD"           -> Triple(DangerBg,  Danger,  "Sold")
        "USED_IN_SERVICE",
        "IN_SERVICE"     -> Triple(WarningBg, Warning, "In Service")
        "DEFECTIVE",
        "DAMAGED"        -> Triple(DangerBg,  Danger,  "Damaged")
        "LOST","RETURNED"-> Triple(BorderColor, TextMuted, serial.status)
        else             -> Triple(BorderColor, TextMuted, serial.status ?: "—")
    }

    val photoUri = serial.photoBase64
    val photoBmp = remember(photoUri) { photoUri?.let { decodeDataUri(it) } }

    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(if (isHighlighted) 2.dp else 1.dp,
                                if (isHighlighted) Primary else BorderColor)
    ) {
        Column {
            // ── Square photo area ─────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isUploading) {
                    Box(Modifier.fillMaxSize().background(ScreenBg), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                } else if (photoBmp != null) {
                    Image(bitmap = photoBmp.asImageBitmap(), contentDescription = serial.serialNumber,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    // Overlay buttons — camera | view | share
                    Row(Modifier.fillMaxWidth().height(32.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(Color.Black.copy(0.50f))
                            .clickable { onUploadPhoto() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(Color(0xBF6366F1))
                            .clickable { photoUri?.let(onViewPhoto) },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(Color(0xBF16A34A))
                            .clickable { onSharePhoto(photoBmp) },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(ScreenBg)
                        .clickable { onUploadPhoto() },
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.CameraAlt, null,
                                tint = if (isHighlighted) Primary else TextMuted,
                                modifier = Modifier.size(26.dp))
                            Text("Tap to add photo", fontSize = 9.sp,
                                color = if (isHighlighted) Primary else TextMuted,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Info area ─────────────────────────────────────────────
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = (if (isHighlighted) "▶ " else "") + serial.serialNumber,
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (isHighlighted) Primary else TextMain,
                    maxLines = 1
                )
                if (!serial.condition.isNullOrBlank()) {
                    Surface(color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))) {
                        Text(serial.condition,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                    }
                }
                val wLabel = fmtWarranty(serial.warrantyMonths)
                if (wLabel.isNotEmpty()) {
                    Text("🛡 $wLabel", fontSize = 10.sp, color = Color(0xFF0891B2))
                }
                Surface(color = statusBg, shape = RoundedCornerShape(5.dp)) {
                    Text(statusLabel ?: "—",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Danger.copy(0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = Danger, modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.DeleteOutline, null, tint = Danger, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ဖျက်", color = Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun Double.fmt() = if (this % 1.0 == 0.0) String.format("%,.0f", this) else String.format("%,.2f", this)
