package com.sspd.servicemgmt.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareUpdateScreen(onBack: () -> Unit) {
    val vm: VersionCheckViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.checkForce() }

    val update = state.update
    val hasUpdate = update != null
    val isDownloading = state.downloadProgress?.let { it < 1f } ?: false
    val isDone = state.downloadProgress == 1f && state.apkFile != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Software Update", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(48.dp).background(Primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.PhoneAndroid, null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("လက်ရှိအသုံးပြုနေသောဗားရှင်း", fontSize = 11.sp, color = TextMuted)
                            Text("v${BuildConfig.VERSION_NAME}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                            Text("Version Code ${BuildConfig.VERSION_CODE}", fontSize = 11.sp, color = TextMuted)
                        }
                        StatusPill(
                            text = if (hasUpdate) "Update ရှိ" else "နောက်ဆုံး",
                            bg = if (hasUpdate) Color(0xFFFFEDD5) else Color(0xFFDCFCE7),
                            fg = if (hasUpdate) Color(0xFFEA580C) else Color(0xFF16A34A)
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoBox("Check", if (state.checked) "ပြီး" else "စစ်နေ", Modifier.weight(1f))
                        InfoBox("Download", if (isDone) "ပြီး" else if (isDownloading) "လုပ်နေ" else "မလုပ်ရသေး", Modifier.weight(1f))
                    }
                }
            }

            if (!state.checked) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        Text("Update စစ်နေသည်...", fontSize = 13.sp, color = TextMuted)
                    }
                }
            }

            if (hasUpdate && update != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = BorderStroke(1.dp, Color(0xFFFDBA74))
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.size(42.dp).background(Color(0xFFFFEDD5), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.SystemUpdate, null, tint = Color(0xFFF97316), modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("ဗားရှင်းအသစ်ရှိပါသည်", fontSize = 11.sp, color = Color(0xFFEA580C), fontWeight = FontWeight.Bold)
                                Text("v${update.versionName}", fontSize = 18.sp, color = Color(0xFF9A3412), fontWeight = FontWeight.ExtraBold)
                                Text("Version Code ${update.versionCode}", fontSize = 11.sp, color = Color(0xFF9A3412))
                            }
                            if (update.forceUpdate) StatusPill("Force", Color(0xFFFEE2E2), Color(0xFFDC2626))
                        }

                        if (update.forceUpdate) {
                            Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.PriorityHigh, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    Text("ဒီ update ကို မလုပ်ဘဲ app ဆက်သုံးလို့မရပါ", fontSize = 12.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (update.changelog.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFFDBA74))
                            Text("ပြောင်းလဲထားသောအချက်များ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEA580C))
                            Surface(color = Color.White.copy(alpha = 0.65f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFFED7AA))) {
                                Text(
                                    update.changelog,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    fontSize = 13.sp,
                                    color = Color(0xFF7C2D12),
                                    lineHeight = 21.sp
                                )
                            }
                        }

                        if (isDownloading) {
                            val progress = state.downloadProgress ?: 0f
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = Color(0xFFF97316),
                                    trackColor = Color(0xFFFED7AA)
                                )
                                Text("Downloading... ${(progress * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFEA580C), fontWeight = FontWeight.Medium)
                            }
                        }

                        if (state.downloadError != null) {
                            Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(10.dp)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ErrorOutline, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    Text(state.downloadError ?: "", fontSize = 11.sp, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isDownloading) return@Button
                                if (isDone) vm.triggerInstall(context) else vm.downloadAndInstall()
                            },
                            enabled = !isDownloading && (isDone || update.downloadUrl.isNotBlank()),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Downloading...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(if (isDone) Icons.Outlined.SystemUpdate else Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isDone) "Install APK" else if (state.downloadError != null) "ထပ်စမ်းမည်" else "Download & Install",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (state.checked && !hasUpdate) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("နောက်ဆုံး version ကို အသုံးပြုနေပါသည်", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF15803D))
                            Text("Update အသစ်မရှိသေးပါ", fontSize = 11.sp, color = Color(0xFF16A34A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFFF8FAFC), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp, color = TextMain, fontWeight = FontWeight.ExtraBold)
        }
    }
}
