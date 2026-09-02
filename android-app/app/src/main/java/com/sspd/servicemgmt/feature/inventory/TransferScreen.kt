package com.sspd.servicemgmt.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.ui.theme.*

private val Indigo   = Primary
private val IndigoBg = PrimaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(onBack: () -> Unit) {
    val vm: TransferViewModel = viewModel()
    val s by vm.uiState.collectAsStateWithLifecycle()

    // Success snackbar
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.success) {
        if (s.success) {
            snackbar.showSnackbar("Transfer လုပ်ဆောင်ပြီးပါပြီ ✓")
            vm.clearSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ငွေပြောင်းလဲမှု", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Cash / KBZ / Bank Transfer", fontSize = 11.sp, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်သို့")
                    }
                },
                actions = {
                    IconButton(onClick = vm::loadMethods) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary,
                    navigationIconContentColor = OnPrimary,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = ScreenBg
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Info banner ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndigoBg)
                    .border(1.dp, Indigo.copy(0.25f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.SwapHoriz, null, tint = Indigo, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("ငွေပေးချေနည်းကြား ပြောင်းလဲမှု", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Indigo)
                    Text(
                        "KBZ → Cash, Cash → KPay / Wave / Bank ပြောင်းလဲရန် ဤနေရာတွင် အသုံးပြုပါ",
                        fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp
                    )
                }
            }

            // ── Transfer form card ────────────────────────────────────────────
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Text("ငွေပြောင်း အချက်အလက်", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = TextMain)

                    // From method
                    MethodDropdown(
                        label     = "မှ (From)",
                        subLabel  = "ငွေထုတ်မည့် method",
                        selected  = s.fromMethod,
                        methods   = s.methods,
                        isError   = s.sameMethodError && s.fromMethod != null,
                        onSelect  = vm::selectFrom
                    )

                    // Arrow indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(IndigoBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, null, tint = Indigo, modifier = Modifier.size(18.dp))
                        }
                    }

                    // To method
                    MethodDropdown(
                        label     = "သို့ (To)",
                        subLabel  = "ငွေသွင်းမည့် method",
                        selected  = s.toMethod,
                        methods   = s.methods,
                        isError   = s.sameMethodError && s.toMethod != null,
                        onSelect  = vm::selectTo
                    )

                    HorizontalDivider(color = BorderColor)

                    // Amount
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ငွေပမာဏ (Ks)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                        OutlinedTextField(
                            value         = s.amountStr,
                            onValueChange = vm::setAmount,
                            modifier      = Modifier.fillMaxWidth(),
                            placeholder   = { Text("0", color = TextMuted) },
                            leadingIcon   = {
                                Text("Ks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted,
                                    modifier = Modifier.padding(start = 14.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine    = true,
                            shape         = RoundedCornerShape(10.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Indigo,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }

                    // Transaction No
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Transaction No. (ရှိလျှင်)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                        OutlinedTextField(
                            value         = s.transactionNo,
                            onValueChange = vm::setTxnNo,
                            modifier      = Modifier.fillMaxWidth(),
                            placeholder   = { Text("e.g. TXN-20240610-001", color = TextMuted) },
                            singleLine    = true,
                            shape         = RoundedCornerShape(10.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Indigo,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }

                    // Note
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("မှတ်ချက် (ရှိလျှင်)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                        OutlinedTextField(
                            value         = s.note,
                            onValueChange = vm::setNote,
                            modifier      = Modifier.fillMaxWidth().height(88.dp),
                            placeholder   = { Text("e.g. KBZ ထဲမှ Cash ထုတ်", color = TextMuted) },
                            maxLines      = 3,
                            shape         = RoundedCornerShape(10.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Indigo,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }

                    // Error
                    s.saveError?.let { err ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFF1F2))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                            Text(err, fontSize = 12.sp, color = Danger, modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Close, null, tint = Danger, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Submit button
                    Button(
                        onClick   = vm::transfer,
                        enabled   = !s.saving,
                        modifier  = Modifier.fillMaxWidth().height(50.dp),
                        shape     = RoundedCornerShape(12.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = Indigo)
                    ) {
                        if (s.saving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Saving...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Transfer လုပ်ဆောင်မည်", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // ── Quick presets ─────────────────────────────────────────────────
            if (s.methods.size >= 2) {
                val cashMethod = s.methods.firstOrNull { "cash" in it.methodName.lowercase() }
                val kbzMethod  = s.methods.firstOrNull {
                    val n = it.methodName.lowercase()
                    "kbz" in n || "kpay" in n || "wave" in n || "aya" in n || "bank" in n
                }
                if (cashMethod != null && kbzMethod != null) {
                    Text(
                        "မကြာခဏ သုံးသော ပြောင်းလဲမှု",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PresetChip(
                            label = "KBZ → Cash",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.selectFrom(kbzMethod); vm.selectTo(cashMethod) }
                        )
                        PresetChip(
                            label = "Cash → ${kbzMethod.methodName}",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.selectFrom(cashMethod); vm.selectTo(kbzMethod) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodDropdown(
    label:    String,
    subLabel: String,
    selected: PaymentMethodDTO?,
    methods:  List<PaymentMethodDTO>,
    isError:  Boolean,
    onSelect: (PaymentMethodDTO) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            Text(subLabel, fontSize = 10.sp, color = TextMuted)
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value         = selected?.methodName ?: "",
                onValueChange = {},
                readOnly      = true,
                modifier      = Modifier.fillMaxWidth().menuAnchor(),
                placeholder   = { Text("Method ရွေးပါ", color = TextMuted) },
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                isError       = isError,
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Indigo,
                    unfocusedBorderColor = if (isError) Danger else BorderColor,
                    errorBorderColor     = Danger
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                methods.forEach { m ->
                    DropdownMenuItem(
                        text    = { Text(m.methodName, fontSize = 14.sp) },
                        onClick = { onSelect(m); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(IndigoBg)
            .border(1.dp, Indigo.copy(0.25f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.SwapHoriz, null, tint = Indigo, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Indigo, maxLines = 1)
    }
}
