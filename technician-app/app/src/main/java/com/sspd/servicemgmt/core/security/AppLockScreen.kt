package com.sspd.servicemgmt.core.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.ui.theme.Accent
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    onLogoutClick: (() -> Unit)? = null,
    error: String? = null
) {
    BackHandler { }
    val context = LocalContext.current
    val settings = remember { TechnicianLocalSettings(context.applicationContext) }
    val hasPin by settings.hasPin.collectAsState(initial = false)

    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var verificationInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun handleKeyPress(key: String) {
        if (!verificationInProgress && enteredPin.length < 4) {
            val newPin = enteredPin + key
            enteredPin = newPin
            pinError = null
            if (newPin.length == 4) {
                scope.launch {
                    verificationInProgress = true
                    when (val result = settings.verifyPin(newPin)) {
                        PinVerification.Verified -> onUnlock()
                        is PinVerification.Invalid -> {
                            pinError = "PIN မှားနေပါသည်။ ${result.remainingAttempts} ကြိမ် ထပ်စမ်းနိုင်ပါသည်။"
                        }
                        is PinVerification.Locked -> {
                            val seconds = ((result.untilMillis - System.currentTimeMillis())
                                .coerceAtLeast(1L) + 999L) / 1000L
                            pinError = "စမ်းသပ်မှုများလွန်းသဖြင့် ${seconds} စက္ကန့် ယာယီပိတ်ထားပါသည်။"
                        }
                        PinVerification.Unavailable -> {
                            pinError = "PIN မရှိပါ။ Biometric / Device Lock ကို အသုံးပြုပါ။"
                        }
                    }
                    enteredPin = ""
                    verificationInProgress = false
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            pinError = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrimaryDark, Primary)))
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(color = Color(0xFFE8F5F3), shape = CircleShape) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.padding(14.dp).size(30.dp)
                    )
                }

                Text(
                    "App Locked",
                    color = Color(0xFF1E293B),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    "လုံခြုံရေးအတွက် ပိတ်ထားပါသည်။",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                (pinError ?: error)?.let {
                    Text(it, color = Color(0xFFDC2626), fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }

                if (hasPin) {
                    // PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) Primary else Color(0xFFE2E8F0))
                                    .border(1.dp, if (isFilled) Primary else Color(0xFFCBD5E1), CircleShape)
                            )
                        }
                    }

                    // Keypad 3x4
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val keyRows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9")
                        )

                        for (row in keyRows) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (key in row) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .clickable { handleKeyPress(key) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    }
                                }
                            }
                        }

                        // Bottom row: Biometric / 0 / Backspace
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Biometric button slot
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE8F5F3))
                                    .clickable { onBiometricClick?.invoke() ?: onUnlock() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Fingerprint, "Biometric", tint = Accent, modifier = Modifier.size(24.dp))
                            }

                            // 0 key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable { handleKeyPress("0") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("0", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }

                            // Backspace key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable { handleBackspace() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Backspace, "Backspace", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
                    // No PIN set
                    Text(
                        "PIN သတ်မှတ်ထားခြင်း မရှိသေးပါ။",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { onBiometricClick?.invoke() ?: onUnlock() },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White, disabledContainerColor = BorderColor, disabledContentColor = TextMuted),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fingerprint / Face ဖြင့် ဖွင့်မည်", fontWeight = FontWeight.Bold)
                    }
                }

                if (onLogoutClick != null) {
                    TextButton(onClick = onLogoutClick) {
                        Text("အကောင့်မှ ထွက်မည်", color = Color(0xFFDC2626), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
