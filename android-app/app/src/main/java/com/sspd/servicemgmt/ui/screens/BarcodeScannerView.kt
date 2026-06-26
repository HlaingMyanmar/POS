package com.sspd.servicemgmt.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScannerView(
    onResult: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanned = remember { AtomicBoolean(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(pv.surfaceProvider)
                            }
                            val scannerOptions = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .build()
                            val barcodeScanner = BarcodeScanning.getClient(scannerOptions)
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                                val mediaImage = proxy.image
                                if (mediaImage != null && !scanned.get()) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage, proxy.imageInfo.rotationDegrees
                                    )
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            barcodes.firstOrNull()?.rawValue?.let { value ->
                                                if (scanned.compareAndSet(false, true)) {
                                                    onResult(value)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                } else {
                                    proxy.close()
                                }
                            }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview, analysis
                                )
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning overlay — 4 rects around the clear centre box
            Canvas(Modifier.fillMaxSize()) {
                val boxPx = 260.dp.toPx()
                val l = (size.width - boxPx) / 2f
                val t = (size.height - boxPx) / 2f
                val overlay = Color(0x99000000)
                drawRect(overlay, topLeft = Offset(0f, 0f), size = Size(size.width, t))
                drawRect(overlay, topLeft = Offset(0f, t + boxPx), size = Size(size.width, size.height - t - boxPx))
                drawRect(overlay, topLeft = Offset(0f, t), size = Size(l, boxPx))
                drawRect(overlay, topLeft = Offset(l + boxPx, t), size = Size(size.width - l - boxPx, boxPx))

                val corner = 36.dp.toPx()
                val sw = 4.dp.toPx()
                // top-left
                drawLine(Color.White, Offset(l, t), Offset(l + corner, t), sw)
                drawLine(Color.White, Offset(l, t), Offset(l, t + corner), sw)
                // top-right
                drawLine(Color.White, Offset(l + boxPx - corner, t), Offset(l + boxPx, t), sw)
                drawLine(Color.White, Offset(l + boxPx, t), Offset(l + boxPx, t + corner), sw)
                // bottom-left
                drawLine(Color.White, Offset(l, t + boxPx - corner), Offset(l, t + boxPx), sw)
                drawLine(Color.White, Offset(l, t + boxPx), Offset(l + corner, t + boxPx), sw)
                // bottom-right
                drawLine(Color.White, Offset(l + boxPx, t + boxPx - corner), Offset(l + boxPx, t + boxPx), sw)
                drawLine(Color.White, Offset(l + boxPx - corner, t + boxPx), Offset(l + boxPx, t + boxPx), sw)
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Outlined.Close, "ပိတ်ရန်", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                "QR / Barcode ကို အကွက်ထဲ ထားပါ",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
            )
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ကင်မရာ ခွင့်ပြုချက် လိုအပ်သည်", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("ခွင့်ပြုမည်")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) {
                    Text("မလုပ်တော့ပါ", color = Color.Gray)
                }
            }
        }
    }
}
