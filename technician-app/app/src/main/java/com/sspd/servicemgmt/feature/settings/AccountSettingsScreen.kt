package com.sspd.servicemgmt.feature.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.security.TechnicianLocalSettings
import com.sspd.servicemgmt.core.security.ThemeMode
import com.sspd.servicemgmt.core.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val vm: AccountSettingsViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localSettings = remember { TechnicianLocalSettings(context.applicationContext) }
    val themeMode by localSettings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val lockEnabled by localSettings.lockEnabled.collectAsState(initial = true)
    val hasPin by localSettings.hasPin.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    var showPinDialog by remember { mutableStateOf(false) }
    var showDeletePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("အကောင့်သတ်မှတ်ချက်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)
                .verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile card
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (state.username.firstOrNull()?.uppercaseChar() ?: 'U').toString(),
                            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(state.displayName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(state.username, fontSize = 13.sp, color = TextMuted)
                }
            }

            // Note about password change
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PrimaryLight), border = BorderStroke(1.dp, Primary.copy(0.3f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text("စကားဝှက်ပြောင်းလဲရန် admin ထံ ဆက်သွယ်ပါ သို့မဟုတ် web system ကိုသုံးပါ", fontSize = 12.sp, color = TextMain, lineHeight = 18.sp)
                }
            }

            // Security Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = Primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("App လုံခြုံရေး", fontWeight = FontWeight.ExtraBold)
                            Text("၅ မိနစ်မသုံးပါက Fingerprint / Face / PIN တောင်းမည်", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = lockEnabled,
                            onCheckedChange = { enabled -> scope.launch { localSettings.setLockEnabled(enabled) } }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // PIN Management Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, null, tint = Primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("App PIN သတ်မှတ်ချက်", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (hasPin) "PIN သတ်မှတ်ထားပြီးပါပြီ" else "PIN မသတ်မှတ်ရသေးပါ",
                                fontSize = 12.sp,
                                color = if (hasPin) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (hasPin) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showPinDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("ပြောင်းမည်", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { showDeletePinDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("ဖျက်မည်", fontSize = 12.sp, color = Color(0xFFDC2626))
                                }
                            }
                        } else {
                            Button(
                                onClick = { showPinDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("PIN သတ်မှတ်မည်", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Theme Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Palette, null, tint = Primary)
                        Spacer(Modifier.width(10.dp))
                        Text("အပြင်အဆင်", fontWeight = FontWeight.ExtraBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Day",
                            ThemeMode.DARK to "Night (Soon)"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { if (mode != ThemeMode.DARK) scope.launch { localSettings.setThemeMode(mode) } },
                                label = { Text(label) },
                                enabled = mode != ThemeMode.DARK
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                scope.launch { localSettings.setAppPin(pin) }
                showPinDialog = false
                Toast.makeText(context, "PIN သတ်မှတ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDeletePinDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePinDialog = false },
            title = { Text("PIN ဖျက်မည်လား?", fontWeight = FontWeight.Bold) },
            text = { Text("App PIN ကို ဖျက်လိုက်ပါက Fingerprint သို့မဟုတ် Device Lock ဖြင့်သာ Unlock ပြုလုပ်နိုင်ပါမည်။", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { localSettings.clearAppPin() }
                        showDeletePinDialog = false
                        Toast.makeText(context, "PIN ကို ဖျက်လိုက်ပါပြီ", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("ဖျက်မည်", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePinDialog = false }) {
                    Text("မဖျက်ပါ")
                }
            }
        )
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App PIN သတ်မှတ်ပါ", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("App Locked ဖြစ်သွားပါက ဖွင့်ရန် ဂဏန်း ၄ လုံး PIN သတ်မှတ်ပါ။", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("PIN အသစ် (ဂဏန်း ၄ လုံး)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("PIN အသစ် ထပ်မံ အတည်ပြုပါ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPin.length != 4 -> errorMsg = "PIN သည် ဂဏန်း ၄ လုံး ဖြစ်ရပါမည်။"
                        newPin != confirmPin -> errorMsg = "PIN များ တူညီမှု မရှိပါ။ ပြန်စမ်းကြည့်ပါ။"
                        else -> onSave(newPin)
                    }
                }
            ) {
                Text("သိမ်းဆည်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("မလုပ်တော့ပါ")
            }
        }
    )
}
