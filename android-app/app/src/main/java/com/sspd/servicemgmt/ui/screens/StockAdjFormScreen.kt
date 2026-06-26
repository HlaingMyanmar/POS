package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.api.StockAdjustmentDTO
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.viewmodel.StockAdjFormViewModel

private val AdjColor = Color(0xFF0891B2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjFormScreen(
    onBack:    () -> Unit,
    onSuccess: (StockAdjustmentDTO) -> Unit = {}
) {
    val vm: StockAdjFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Product picker bottom sheet
    if (state.showProductPicker) {
        ProductPickerSheet(
            query     = state.productQuery,
            results   = state.productResults,
            onQuery   = vm::setProductQuery,
            onSelect  = vm::selectProduct,
            onDismiss = vm::dismissProductPicker
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Adjustment အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = AdjColor,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color           = Color.White
            ) {
                Button(
                    onClick  = { vm.save(onSuccess) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = AdjColor),
                    enabled = !state.saving
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("သိမ်းဆည်းရန်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding  = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Error banner ───────────────────────────────────────────────
            state.saveError?.let { err ->
                item {
                    Surface(
                        color  = DangerBg,
                        shape  = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Danger.copy(0.3f))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = Danger, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Close, null, tint = Danger, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── Reason field ───────────────────────────────────────────────
            item {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("အကြောင်းအရင်း", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextMain)
                        OutlinedTextField(
                            value         = state.reason,
                            onValueChange = vm::setReason,
                            modifier      = Modifier.fillMaxWidth(),
                            placeholder   = { Text("ဥပမာ - ပါနည်းသောကြောင့်, ပျက်စီးသောကြောင့်...") },
                            minLines      = 2,
                            shape         = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // ── Lines section header ───────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "ပစ္စည်းများ (${state.lines.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 13.sp,
                        color      = TextMain
                    )
                    OutlinedButton(
                        onClick      = vm::openProductPicker,
                        shape        = RoundedCornerShape(10.dp),
                        border       = BorderStroke(1.dp, AdjColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = AdjColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ထည့်ရန်", fontSize = 12.sp, color = AdjColor)
                    }
                }
            }

            // ── Line items ─────────────────────────────────────────────────
            if (state.lines.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openProductPicker() }
                            .background(Color(0xFFECFEFF), RoundedCornerShape(12.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.AddBox, null, tint = AdjColor, modifier = Modifier.size(40.dp))
                            Text("ပစ္စည်း ထည့်ရန် နှိပ်ပါ", color = AdjColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                itemsIndexed(state.lines) { index, line ->
                    AdjLineCard(
                        index    = index,
                        line     = line,
                        onQty    = { vm.setLineQty(index, it) },
                        onType   = { vm.setLineType(index, it) },
                        onRemark = { vm.setLineRemark(index, it) },
                        onRemove = { vm.removeLine(index) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AdjLineCard(
    index:    Int,
    line:     StockAdjFormViewModel.AdjLine,
    onQty:    (String) -> Unit,
    onType:   (String) -> Unit,
    onRemark: (String) -> Unit,
    onRemove: () -> Unit
) {
    val isGain    = line.type == "GAIN"
    val gainColor = Color(0xFF16A34A)
    val lossColor = Danger

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row: product name + remove
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${line.productName}",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        color      = TextMain
                    )
                    if (line.productCode.isNotBlank()) {
                        Text(line.productCode, fontSize = 11.sp, color = TextMuted)
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.RemoveCircleOutline, "ဖယ်ရှားရန်", tint = Danger, modifier = Modifier.size(18.dp))
                }
            }

            // Type toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isGain,
                    onClick  = { onType("GAIN") },
                    label    = { Text("ရရှိ (GAIN)", fontSize = 12.sp) },
                    leadingIcon = {
                        if (isGain) Icon(Icons.Outlined.TrendingUp, null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor     = gainColor.copy(0.12f),
                        selectedLabelColor         = gainColor,
                        selectedLeadingIconColor   = gainColor
                    )
                )
                FilterChip(
                    selected = !isGain,
                    onClick  = { onType("LOSS") },
                    label    = { Text("ဆုံးရှုံး (LOSS)", fontSize = 12.sp) },
                    leadingIcon = {
                        if (!isGain) Icon(Icons.Outlined.TrendingDown, null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor     = lossColor.copy(0.10f),
                        selectedLabelColor         = lossColor,
                        selectedLeadingIconColor   = lossColor
                    )
                )
            }

            // Qty field
            OutlinedTextField(
                value         = line.qtyStr,
                onValueChange = onQty,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("ပမာဏ (Qty)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp)
            )

            // Remark field
            OutlinedTextField(
                value         = line.remark,
                onValueChange = onRemark,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("မှတ်ချက် (ရွေးချယ်နိုင်)") },
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(
    query:     String,
    results:   List<ProductDTO>,
    onQuery:   (String) -> Unit,
    onSelect:  (ProductDTO) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text("ကုန်ပစ္စည်း ရွေးချယ်ရန်", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextMain)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value         = query,
                onValueChange = onQuery,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("ကုန်ပစ္စည်း Code / နာမည် ရှာပါ...") },
                leadingIcon   = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (query.isBlank()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("ကုန်ပစ္စည်း နာမည် ရိုက်ပါ", color = TextMuted, fontSize = 13.sp)
                }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else {
                results.forEach { product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(product) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                            Text(product.productCode, fontSize = 11.sp, color = TextMuted)
                        }
                        Text(
                            "ကျန်: ${product.stockQty}",
                            fontSize = 11.sp,
                            color    = if (product.stockQty > 0) Color(0xFF16A34A) else Danger
                        )
                    }
                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
