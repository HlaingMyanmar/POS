package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.ShelfLocationDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.ShelfLocationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfLocationScreen(onBack: () -> Unit) {
    val vm: ShelfLocationViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saveSuccess) {
        state.saveSuccess?.let { snackbar.showSnackbar(it); vm.clearSaveSuccess() }
    }

    if (state.showDialog) {
        ShelfLocationDialog(
            target   = state.dialogTarget,
            saving   = state.saving,
            error    = state.saveError,
            onDismiss = { vm.closeDialog() },
            onSave   = { code, label, active -> vm.save(code, label, active) }
        )
    }

    val filtered = state.items.filter {
        state.search.isBlank() ||
        it.code.contains(state.search, true) ||
        it.label?.contains(state.search, true) == true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ကန့်တည်နေရာများ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { vm.openAddDialog() },
                containerColor = Primary,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Outlined.Add, null) },
                text           = { Text("နေရာ ထည့်ရန်", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {

            OutlinedTextField(
                value         = state.search,
                onValueChange = { vm.setSearch(it) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder   = { Text("ကန့်တည်နေရာ ရှာဖွေရန်...") },
                leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                trailingIcon  = {
                    if (state.search.isNotBlank())
                        IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Outlined.Clear, "ရှင်းရန်", tint = TextMuted) }
                },
                singleLine = true, shape = RoundedCornerShape(12.dp)
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.LocationOff, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ကန့်တည်နေရာ မရှိပါ", color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { vm.openAddDialog() }) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ထည့်ရန်")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val activeCount = filtered.count { it.active }
                        Surface(color = VioletBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, null, tint = Violet, modifier = Modifier.size(16.dp))
                                    Text("စုစုပေါင်း ${filtered.size} နေရာ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Violet)
                                }
                                Text("တက်ကြွ $activeCount ခု", fontSize = 12.sp, color = Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    items(filtered) { loc ->
                        Card(
                            shape  = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = BorderStroke(1.dp, if (loc.active) BorderColor else BorderColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(if (loc.active) VioletBg else ScreenBg, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        loc.code.take(3),
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = if (loc.active) Violet else TextMuted
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        loc.code,
                                        fontSize   = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = if (loc.active) TextMain else TextMuted
                                    )
                                    if (!loc.label.isNullOrBlank())
                                        Text(loc.label, fontSize = 12.sp, color = TextMuted)
                                }
                                Surface(
                                    color = if (loc.active) SuccessBg else BorderColor,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (loc.active) "တက်ကြွ" else "ရပ်နား",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (loc.active) Success else TextMuted
                                    )
                                }
                                IconButton(onClick = { vm.openEditDialog(loc) }) {
                                    Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

// ── Add / Edit Dialog ─────────────────────────────────────────────────────────

@Composable
private fun ShelfLocationDialog(
    target:    ShelfLocationDTO?,
    saving:    Boolean,
    error:     String?,
    onDismiss: () -> Unit,
    onSave:    (code: String, label: String, active: Boolean) -> Unit
) {
    var code   by remember { mutableStateOf(target?.code   ?: "") }
    var label  by remember { mutableStateOf(target?.label  ?: "") }
    var active by remember { mutableStateOf(target?.active ?: true) }
    var codeError by remember { mutableStateOf("") }

    val isEdit = target != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isEdit) Icons.Outlined.Edit else Icons.Outlined.AddLocation,
                    null, tint = Primary, modifier = Modifier.size(22.dp)
                )
                Text(
                    if (isEdit) "ကန့်တည်နေရာ ပြင်ဆင်ရန်" else "ကန့်တည်နေရာ ထည့်ရန်",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Code field
                OutlinedTextField(
                    value         = code,
                    onValueChange = { code = it.uppercase(); codeError = "" },
                    label         = { Text("ကုဒ် (Code) *") },
                    placeholder   = { Text("ဥပမာ: A-01, B-03") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(10.dp),
                    isError       = codeError.isNotBlank(),
                    supportingText = if (codeError.isNotBlank()) {{ Text(codeError, color = MaterialTheme.colorScheme.error) }} else null
                )

                // Label field
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text("ဖော်ပြချက် (optional)") },
                    placeholder   = { Text("ဥပမာ: Row A, Slot 1 — ဖုန်းများ") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(10.dp)
                )

                // Active toggle
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = if (active) SuccessBg else ScreenBg),
                    border = BorderStroke(1.dp, if (active) Success.copy(0.4f) else BorderColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "အသုံးပြုနိုင်သည်",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (active) Success else TextMuted
                            )
                            Text(
                                if (active) "ဤနေရာကို အသုံးပြုနိုင်သည်" else "ဤနေရာကို ရပ်ဆိုင်းထားသည်",
                                fontSize = 11.sp, color = TextMuted
                            )
                        }
                        Switch(
                            checked         = active,
                            onCheckedChange = { active = it },
                            colors          = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = SuccessBg)
                        )
                    }
                }

                if (!error.isNullOrBlank()) {
                    Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                            Text(error, fontSize = 12.sp, color = Danger)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isBlank()) { codeError = "ကုဒ် ရိုက်ထည့်ပါ"; return@Button }
                    onSave(code.trim(), label.trim(), active)
                },
                enabled = !saving,
                colors  = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (saving)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEdit) "ပြင်ဆင်မည်" else "သိမ်းဆည်းမည်", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("မလုပ်တော့ပါ") }
        }
    )
}

