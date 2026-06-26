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
import com.sspd.servicemgmt.api.SaleReturnDetailDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.SaleReturnDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleReturnDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val vm: SaleReturnDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ret = state.saleReturn

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ret?.returnCode ?: "Return အသေးစိတ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
                    IconButton(onClick = onEdit)        { Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AppLoading()
            }
            return@Scaffold
        }
        if (ret == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("ဒေတာ မတွေ့ပါ", color = TextMuted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ret.returnCode ?: "#${ret.id}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                            Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                                Text("Sale Return", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger)
                            }
                        }
                        Text(ret.returnDate?.take(16)?.replace("T", "  ") ?: "—", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }

            // Info
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetInfoRow(Icons.Outlined.Person,  "ဖောက်သည်",    ret.customerName ?: "—")
                        HorizontalDivider(color = BorderColor)
                        RetInfoRow(Icons.Outlined.Receipt, "Sale Code",    ret.saleCode     ?: "—")
                        if (!ret.reason.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            RetInfoRow(Icons.Outlined.Notes, "အကြောင်းအရင်း", ret.reason)
                        }
                        if (!ret.paymentMethodName.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            RetInfoRow(Icons.Outlined.AccountBalance, "ငွေပြန်ပေးနည်း", ret.paymentMethodName)
                        }
                        if (!ret.transactionNo.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            RetInfoRow(Icons.Outlined.ConfirmationNumber, "Transaction No", ret.transactionNo)
                        }
                    }
                }
            }

            // Items
            if (!ret.details.isNullOrEmpty()) {
                item {
                    Text("ပြန်ခံပစ္စည်းများ (${ret.details.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                items(ret.details) { detail -> RetDetailCard(detail) }
            }

            // Summary
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DangerBg), border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ပစ္စည်းစုစုပေါင်း ပြန်တန်ဖိုး", fontSize = 13.sp, color = Danger)
                            Text("${String.format("%,.0f", ret.totalReturnAmount ?: 0.0)} Ks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Danger)
                        }
                        HorizontalDivider(color = Danger.copy(alpha = 0.2f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ငွေပြန်ပေးသည်", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                            Text("${String.format("%,.0f", ret.refundAmount ?: 0.0)} Ks", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RetInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp).padding(top = 1.dp))
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RetDetailCard(detail: SaleReturnDetailDTO) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(detail.productName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("${detail.qty ?: 1} × ${String.format("%,.0f", detail.unitPrice ?: 0.0)} Ks", fontSize = 11.sp, color = TextMuted)
                }
                Text("${String.format("%,.0f", detail.subtotal ?: 0.0)} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
            }
            if (!detail.serialNumbers.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("S/N: ${detail.serialNumbers.joinToString(", ")}", fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}

