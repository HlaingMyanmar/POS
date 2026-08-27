package com.sspd.servicemgmt.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.core.network.AppVersionDTO
import com.sspd.servicemgmt.core.ui.theme.Primary

@Composable
fun UpdateDialog(
    update: AppVersionDTO,
    downloadProgress: Float?,
    apkFile: String?,
    downloadError: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDone = downloadProgress == 1f && apkFile != null
    val isDownloading = downloadProgress != null && !isDone

    Dialog(
        onDismissRequest = { if (!update.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !update.forceUpdate,
            dismissOnClickOutside = !update.forceUpdate
        )
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).background(Primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.SystemUpdate, null, tint = Primary, modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    if (update.forceUpdate) "Update လုပ်ရန်လိုအပ်ပါသည်" else "Update အသစ်ရှိပါသည်",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text("v${update.versionName} (code ${update.versionCode})", fontSize = 13.sp, color = Primary, fontWeight = FontWeight.SemiBold)

                if (update.forceUpdate) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PriorityHigh, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            Text("ဒီဗားရှင်းကို မဖြစ်မနေ update လုပ်ရပါမည်", fontSize = 12.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (update.changelog.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text("ပြောင်းလဲထားသောအချက်များ", fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                        Text(
                            update.changelog,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (isDownloading) {
                    val progress = downloadProgress ?: 0f
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Primary,
                            trackColor = Primary.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Downloading... ${(progress * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (downloadError != null) {
                    Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(10.dp)) {
                        Text(downloadError, modifier = Modifier.fillMaxWidth().padding(10.dp), fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        if (isDone) onInstall() else onDownload()
                    },
                    enabled = !isDownloading && (isDone || update.downloadUrl.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(if (isDone) Icons.Outlined.SystemUpdate else Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isDone) "Install APK" else if (downloadError != null) "Retry Download" else "Download APK", fontWeight = FontWeight.Bold)
                    }
                }

                if (!update.forceUpdate) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss, enabled = !isDownloading, modifier = Modifier.fillMaxWidth()) {
                        Text("နောက်မှလုပ်မည်", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
