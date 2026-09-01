package com.sspd.servicemgmt.feature.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.component.AppCard
import com.sspd.servicemgmt.core.ui.component.AppEmptyState
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppScaffold
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.Gold
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.feature.inventory.OpeningStockViewModel.EntryMode
import com.sspd.servicemgmt.feature.inventory.OpeningStockViewModel.StatusFilter

@Composable
fun OpeningStockScreen(onBack: () -> Unit) {
    val vm: OpeningStockViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var confirmSave by remember { mutableStateOf(false) }
    var showStaff by remember { mutableStateOf(false) }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("သတိပေးချက်", fontWeight = FontWeight.ExtraBold) },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("အိုကေ") } }
        )
    }
    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("ကနဦး ကုန်လက်ကျန်", fontWeight = FontWeight.ExtraBold) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("အိုကေ") } }
        )
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("${state.rowsToSave.size} မျိုးကို ကနဦးလက်ကျန်အဖြစ် သိမ်းမည်", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Stock မရှိသေးသော quantity-only ပစ္စည်းများအတွက်သာ သိမ်းပါမည်။ Product Master တွင် ဝယ်ဈေးရှိမှ journal ထွက်နိုင်ပါသည်။") },
            confirmButton = {
                Button(onClick = { confirmSave = false; vm.saveAll() }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text("သိမ်းမည်")
                }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("မလုပ်တော့") } }
        )
    }
    if (showStaff) {
        AlertDialog(
            onDismissRequest = { showStaff = false },
            title = { Text("Staff ရွေးပါ", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    state.staff.forEach { staff ->
                        TextButton(onClick = { vm.setStaff(staff.id); showStaff = false }) {
                            Text("${staff.name}${if (staff.role.isNotBlank()) " (${staff.role})" else ""}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStaff = false }) { Text("ပိတ်မည်") } }
        )
    }

    AppScaffold(
        title = "ကနဦး ကုန်လက်ကျန်",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::load, enabled = !state.loading && !state.saving) {
                Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်")
            }
        }
    ) { padding ->
        if (state.loading) {
            AppLoading(Modifier.fillMaxSize().padding(padding))
            return@AppScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.saving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(color = Gold.copy(0.12f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Gold.copy(0.35f))) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Info, null, tint = Gold, modifier = Modifier.size(18.dp))
                            Text(
                                "Opening Stock သည် correction မဟုတ်ပါ။ ရောင်း/ဝယ် လည်ပတ်ပြီးသား stock ကို Stock Adjustment တွင်ပြင်ပါ။ Serial ပစ္စည်းကို Purchase မှ ထည့်ပါ။",
                                fontSize = 12.sp,
                                color = TextMain
                            )
                        }
                    }
                }
                item {
                    AppCard {
                        TextButton(onClick = { showStaff = true }, enabled = !state.saving) {
                            Text(state.staff.find { it.id == state.staffId }?.name ?: "Staff ရွေးပါ")
                        }
                        OutlinedTextField(
                            value = state.referenceNo,
                            onValueChange = vm::setReferenceNo,
                            label = { Text("Reference") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.countDate,
                            onValueChange = vm::setCountDate,
                            label = { Text("Count date (yyyy-MM-dd)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.sessionNote,
                            onValueChange = vm::setSessionNote,
                            label = { Text("Session note") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = state.mode == EntryMode.EMPTY_ONLY,
                                onClick = { vm.setMode(EntryMode.EMPTY_ONLY) },
                                label = { Text("Empty only", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = state.mode == EntryMode.TARGET_QTY,
                                onClick = { vm.setMode(EntryMode.TARGET_QTY) },
                                label = { Text("Target count", fontSize = 12.sp) }
                            )
                            TextButton(onClick = vm::resetEntries, enabled = !state.saving) { Text("Qty ရှင်း") }
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricChip("Ready", state.summary.ready.toString(), Success)
                        MetricChip("Entered", state.summary.entered.toString(), Primary)
                        MetricChip("Qty", state.summary.totalQty.toString(), TextMain)
                        MetricChip("Existing", state.summary.existing.toString(), Gold)
                        MetricChip("No cost", state.summary.noCost.toString(), Danger)
                    }
                }
                item {
                    AppSearchField(state.query, vm::setQuery, placeholder = "ကုန်ပစ္စည်း / code / category ရှာပါ")
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = state.category == "ALL", onClick = { vm.setCategory("ALL") }, label = { Text("All") })
                        state.categories.forEach { cat ->
                            FilterChip(
                                selected = state.category == cat,
                                onClick = { vm.setCategory(if (state.category == cat) "ALL" else cat) },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.status == filter,
                                onClick = { vm.setStatus(filter) },
                                label = { Text(filter.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                if (state.filteredRows.isEmpty()) {
                    item { AppEmptyState(title = "ပြသရန် ပစ္စည်း မရှိပါ", subtitle = "Filter ပြောင်းကြည့်ပါ") }
                }
                items(state.filteredRows, key = { it.productId }) { row ->
                    OpeningStockRow(
                        row = row,
                        mode = state.mode,
                        enabled = !state.saving,
                        onQty = { vm.setQty(row.productId, it) },
                        onClear = { vm.clearQty(row.productId) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            Surface(color = CardBg, shadowElevation = 8.dp, border = BorderStroke(1.dp, BorderColor)) {
                Button(
                    onClick = { confirmSave = true },
                    enabled = !state.saving && state.rowsToSave.isNotEmpty() && state.staffId != null && state.summary.invalid == 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (state.saving) CircularProgressIndicator(color = OnPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("သိမ်းမည် (${state.rowsToSave.size})", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

private val StatusFilter.label: String
    get() = when (this) {
        StatusFilter.ALL -> "All"
        StatusFilter.READY -> "Ready"
        StatusFilter.ENTERED -> "Entered"
        StatusFilter.EMPTY -> "Empty"
        StatusFilter.SERIAL -> "Serial"
        StatusFilter.EXISTING -> "Existing"
        StatusFilter.NO_COST -> "No cost"
        StatusFilter.INVALID -> "Invalid"
    }

@Composable
private fun MetricChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(0.1f), border = BorderStroke(1.dp, color.copy(0.25f))) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 16.sp, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun OpeningStockRow(
    row: OpeningStockViewModel.StockRow,
    mode: EntryMode,
    enabled: Boolean,
    onQty: (String) -> Unit,
    onClear: () -> Unit
) {
    val editable = row.isEditable(mode)
    val invalid = row.openingQty.isNotBlank() && row.parsedQty() == null
    val status = when {
        invalid -> "Invalid" to Danger
        row.hasSerial -> "Serial" to Gold
        !row.hasCost && row.currentStock == 0 -> "No cost" to Danger
        !editable && row.currentStock > 0 -> "Existing" to TextMuted
        row.saved || row.openingQty.isNotBlank() -> "Entered" to Success
        else -> "Ready" to Primary
    }
    val hint = when {
        row.hasSerial -> "Purchase မှ serial number ဖြင့်ထည့်ပါ"
        !row.hasCost && row.currentStock == 0 -> "Product Master တွင် ဝယ်ဈေးထည့်ပြီးမှ သိမ်းနိုင်သည်"
        !editable && row.currentStock > 0 -> "Stock ရှိပြီးသားဖြစ်၍ Stock Adjustment တွင်ပြင်ပါ"
        else -> "Go-live ကနဦး Qty ထည့်နိုင်သည်"
    }
    AppCard {
        Text(row.productName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
        Text("${row.productCode} · ${row.category} · ${row.brand}", fontSize = 11.sp, color = TextMuted)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("လက်ရှိ ${row.currentStock} ${row.unit}", fontSize = 12.sp, color = TextMain, fontWeight = FontWeight.Bold)
            Text(
                if (row.hasCost) "${"%,.0f".format(row.costPrice)} Ks" else "ဝယ်ဈေး မရှိ",
                fontSize = 12.sp,
                color = if (row.hasCost) TextMain else Danger,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.openingQty,
                onValueChange = onQty,
                enabled = enabled && editable,
                modifier = Modifier.weight(1f),
                label = { Text("ကနဦး Qty") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (row.openingQty.isNotBlank() && editable) {
                TextButton(onClick = onClear, enabled = enabled) { Text("ရှင်း") }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(status.first, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = status.second)
        Text(hint, fontSize = 11.sp, color = TextMuted)
    }
}
