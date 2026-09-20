package com.sspd.servicemgmt.feature.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.security.TechnicianLocalSettings
import com.sspd.servicemgmt.core.security.ThemeMode
import com.sspd.servicemgmt.core.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
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
                title = { Text("လုံခြုံရေးနှင့် ဆက်တင်များ", fontWeight = FontWeight.ExtraBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White)
                            ) {
                                Text("PIN သတ်မှတ်မည်", fontSize = 12.sp, color = Color.White)
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
                        Text("အပြင်အဆင် (Theme)", fontWeight = FontWeight.ExtraBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Day",
                            ThemeMode.DARK to "Night"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { scope.launch { localSettings.setThemeMode(mode) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
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
                    label = { Text("PIN အတည်ပြုရန်") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPin.length < 4 -> errorMsg = "ဂဏန်း ၄ လုံး အပြည့်ထည့်ပါ။"
                        newPin != confirmPin -> errorMsg = "PIN များ မတူညီပါ။"
                        else -> onSave(newPin)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White)
            ) {
                Text("သိမ်းမည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ပယ်ဖျက်")
            }
        }
    )
}

@Preview(name = "Security Settings Preview", showBackground = true, widthDp = 360, heightDp = 700)
@Composable
private fun SecuritySettingsScreenPreview() {
    AppTheme {
        SecuritySettingsScreen(onBack = {})
    }
}
