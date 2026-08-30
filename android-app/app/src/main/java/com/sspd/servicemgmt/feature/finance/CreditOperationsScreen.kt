package com.sspd.servicemgmt.feature.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppScaffold
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditOperationsScreen(onBack: () -> Unit) {
    val vm: CreditOperationsViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val selected = state.customers.find { it.id == state.selectedCustomerId }
    val filtered = state.customers.filter {
        state.search.isBlank() || listOf(it.name, it.phone, it.address).joinToString(" ").contains(state.search, ignoreCase = true)
    }
    val dueByCustomer = state.sales.filter { (it.dueAmount ?: 0.0) > 0.0 }.groupBy { it.customerId }.mapValues { row -> row.value.sumOf { it.dueAmount ?: 0.0 } }
    val overdueCount = state.sales.count { (it.dueAmount ?: 0.0) > 0.0 && isOverdue(it.dueDate) }
    val totalDue = dueByCustomer.values.sum()

    state.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = Danger) },
            title = { Text("သတိပေးချက်", fontWeight = FontWeight.ExtraBold) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("အိုကေ") } }
        )
    }

    AppScaffold(
        title = "ခရက်ဒစ် စားပွဲ",
        onBack = onBack,
        actions = { IconButton(onClick = vm::load) { Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်ရန်", tint = OnPrimary) } }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@AppScaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).background(ScreenBg).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreditStatCard("ကျန်ငွေ", money(totalDue), Icons.Outlined.CreditCard, Danger, Modifier.weight(1f))
                CreditStatCard("ရက်ကျော်", "$overdueCount ခု", Icons.Outlined.EventBusy, Warning, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreditStatCard("ရပ်ဆိုင်း", state.customers.count { it.creditHold }.toString(), Icons.Outlined.PauseCircle, Warning, Modifier.weight(1f))
                CreditStatCard("ပိတ်စာရင်း", state.customers.count { it.blacklisted }.toString(), Icons.Outlined.Block, Danger, Modifier.weight(1f))
            }

            AppSearchField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = "ဖောက်သည် ရှာပါ"
            )

            Text("ဖောက်သည်စာရင်း", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered, key = { index, customer -> customer.id ?: "c-$index-${customer.name}" }) { _, customer ->
                    CustomerCreditRow(
                        customer = customer,
                        due = dueByCustomer[customer.id] ?: 0.0,
                        selected = customer.id == state.selectedCustomerId,
                        onClick = { customer.id?.let(vm::selectCustomer) }
                    )
                }
            }

            selected?.let { customer ->
                CreditDetailPanel(vm, state, customer, state.sales.filter { it.customerId == customer.id && (it.dueAmount ?: 0.0) > 0.0 })
            } ?: Text("ဖောက်သည်ရွေးပါ", color = TextMuted, modifier = Modifier.fillMaxWidth().padding(24.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CreditStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Text(label, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun CustomerCreditRow(customer: CustomerDTO, due: Double, selected: Boolean, onClick: () -> Unit) {
    val statusColor = when {
        customer.blacklisted -> Danger
        customer.creditHold -> Warning
        else -> Success
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) PrimaryLight else CardBg),
        border = BorderStroke(1.dp, if (selected) Primary.copy(0.35f) else BorderColor)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = statusColor.copy(0.12f), shape = RoundedCornerShape(10.dp)) {
                Icon(if (customer.blacklisted) Icons.Outlined.Block else Icons.Outlined.Person, null, tint = statusColor, modifier = Modifier.padding(8.dp).size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(customer.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(customer.phone.orEmpty().ifBlank { "-" }, fontSize = 11.sp, color = TextMuted)
            }
            Text(money(due), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (due > 0) Danger else TextMuted)
        }
    }
}

@Composable
private fun CreditDetailPanel(vm: CreditOperationsViewModel, state: CreditOperationsViewModel.CreditOperationsUiState, customer: CustomerDTO, dueSales: List<SaleDTO>) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(customer.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text("${customer.phone.orEmpty()}  ${customer.address.orEmpty()}", fontSize = 12.sp, color = TextMuted)
                }
                AssistChip(onClick = {}, label = { Text(if (customer.blacklisted) "ပိတ်စာရင်း" else if (customer.creditHold) "ရပ်ဆိုင်း" else "ပုံမှန်") })
            }

            Text("အကောင့် ထိန်းချုပ်မှု", fontWeight = FontWeight.ExtraBold, color = TextMain)
            ControlSwitch("ခရက်ဒစ် ရပ်ဆိုင်း", state.formCreditHold, vm::setFormCreditHold)
            if (state.formCreditHold) OutlinedTextField(state.formCreditHoldReason, vm::setFormCreditHoldReason, label = { Text("ရပ်ဆိုင်း အကြောင်းရင်း") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            ControlSwitch("ပိတ်စာရင်း", state.formBlacklisted, vm::setFormBlacklisted)
            if (state.formBlacklisted) OutlinedTextField(state.formBlacklistReason, vm::setFormBlacklistReason, label = { Text("ပိတ်စာရင်း အကြောင်းရင်း") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = vm::saveControls, enabled = !state.savingControls, colors = ButtonDefaults.buttonColors(containerColor = Primary), modifier = Modifier.fillMaxWidth()) {
                if (state.savingControls) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("ထိန်းချုပ်မှု သိမ်းမည်", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = BorderColor)
            Text("ခရက်ဒစ် စည်းကမ်း", fontWeight = FontWeight.ExtraBold, color = TextMain)
            ControlSwitch("ခရက်ဒစ် ခွင့်ပြုမည်", state.formCreditAllowed, vm::setFormCreditAllowed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.formCreditLimit, vm::setFormCreditLimit, label = { Text("ကန့်သတ်ငွေ") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = state.formCreditAllowed, singleLine = true)
                OutlinedTextField(state.formCreditDays, vm::setFormCreditDays, label = { Text("ရက်") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = state.formCreditAllowed, singleLine = true)
            }
            Button(onClick = vm::saveTerms, enabled = !state.savingTerms, colors = ButtonDefaults.buttonColors(containerColor = Primary), modifier = Modifier.fillMaxWidth()) {
                if (state.savingTerms) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("စည်းကမ်း သိမ်းမည်", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = BorderColor)
            Text("လက်ကျန် Invoice များ", fontWeight = FontWeight.ExtraBold, color = TextMain)
            if (dueSales.isEmpty()) Text("လက်ကျန်မရှိပါ", fontSize = 12.sp, color = TextMuted)
            dueSales.take(8).forEach { sale ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sale.saleCode ?: "#${sale.id}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Text("Due: ${sale.dueDate ?: "-"}", fontSize = 11.sp, color = if (isOverdue(sale.dueDate)) Danger else TextMuted)
                    }
                    Text(money(sale.dueAmount ?: 0.0), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                }
            }
        }
    }
}

@Composable
private fun ControlSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = TextMain)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun money(v: Double): String = "%,.0f Ks".format(v)

private fun isOverdue(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    return runCatching {
        val date = java.time.LocalDate.parse(value.take(10))
        date.isBefore(java.time.LocalDate.now())
    }.getOrDefault(false)
}
