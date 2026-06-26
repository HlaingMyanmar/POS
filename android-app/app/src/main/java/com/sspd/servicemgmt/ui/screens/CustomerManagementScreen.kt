package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.sspd.servicemgmt.api.CustomerDTO
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.viewmodel.CustomerManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementScreen(onBack: () -> Unit) {
    val vm: CustomerManagementViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val filtered = state.customers.filter {
        val q = state.search.trim()
        q.isBlank() || listOf(it.name, it.phone, it.address).joinToString(" ").contains(q, ignoreCase = true)
    }
    val normal = state.customers.count { !it.creditHold && !it.blacklisted }
    val hold = state.customers.count { it.creditHold }
    val blacklist = state.customers.count { it.blacklisted }

    state.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = Danger) },
            title = { Text("သတိပေးချက်", fontWeight = FontWeight.ExtraBold) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("အိုကေ") } }
        )
    }

    if (state.showEditor) CustomerEditorDialog(vm, state)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဖောက်သည်စီမံခန့်ခွဲမှု", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                actions = {
                    IconButton(onClick = vm::load) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::openCreate, containerColor = Primary) {
                Icon(Icons.Outlined.PersonAdd, "ဖောက်သည်အသစ်", tint = Color.White)
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).background(ScreenBg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomerStatCard("စုစုပေါင်း", state.customers.size.toString(), Icons.Outlined.Groups, Primary, Modifier.weight(1f))
                CustomerStatCard("Normal", normal.toString(), Icons.Outlined.CheckCircle, Success, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomerStatCard("Credit Hold", hold.toString(), Icons.Outlined.PauseCircle, Warning, Modifier.weight(1f))
                CustomerStatCard("Blacklist", blacklist.toString(), Icons.Outlined.Block, Danger, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("အမည် / ဖုန်း / လိပ်စာ ရှာပါ") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(filtered, key = { it.id ?: it.name }) { customer ->
                    val saleCount = state.sales.count { it.customerId == customer.id }
                    CustomerCard(
                        customer = customer,
                        saleCount = saleCount,
                        deleting = state.deletingId == customer.id,
                        onClick = { vm.openEdit(customer) },
                        onDelete = { vm.delete(customer) }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "ဖောက်သည် မတွေ့ပါ",
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color.copy(0.12f), shape = RoundedCornerShape(10.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp).size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(label, fontSize = 11.sp, color = TextMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerDTO, saleCount: Int, deleting: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val color = when {
        customer.blacklisted -> Danger
        customer.creditHold -> Warning
        else -> Success
    }
    val label = when {
        customer.blacklisted -> "Blacklist"
        customer.creditHold -> "Credit Hold"
        else -> "Normal"
    }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = PrimaryLight, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Person, null, tint = Primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(customer.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text(customer.phone.orEmpty().ifBlank { "-" }, fontSize = 12.sp, color = TextMuted)
                    if (!customer.address.isNullOrBlank()) Text(customer.address, fontSize = 12.sp, color = TextMuted, maxLines = 2)
                }
                AssistChip(onClick = {}, label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) }, colors = AssistChipDefaults.assistChipColors(labelColor = color))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ရောင်းချမှု $saleCount ကြိမ်", fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = onClick) { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("ပြင်မည်") }
                IconButton(onClick = onDelete, enabled = !deleting, modifier = Modifier.size(36.dp)) {
                    if (deleting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomerEditorDialog(vm: CustomerManagementViewModel, state: CustomerManagementViewModel.CustomerManagementUiState) {
    AlertDialog(
        onDismissRequest = { if (!state.saving) vm.closeEditor() },
        title = { Text(if (state.editingCustomer == null) "ဖောက်သည်အသစ်" else "ဖောက်သည် ပြင်ဆင်ရန်", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(state.formName, vm::setFormName, label = { Text("ဖောက်သည်အမည် *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(state.formPhone, vm::setFormPhone, label = { Text("ဖုန်းနံပါတ် *") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(state.formAddress, vm::setFormAddress, label = { Text("လိပ်စာ *") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                HorizontalDivider(color = BorderColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Credit ခွင့်ပြုမည်", fontWeight = FontWeight.Bold, color = TextMain)
                        Text("အကြွေး limit / days သတ်မှတ်ရန်", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(state.formCreditAllowed, vm::setFormCreditAllowed)
                }
                if (state.formCreditAllowed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.formCreditLimit, vm::setFormCreditLimit, label = { Text("Credit Limit") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(state.formCreditDays, vm::setFormCreditDays, label = { Text("Days") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Credit Hold", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(state.formCreditHold, vm::setFormCreditHold)
                }
                if (state.formCreditHold) OutlinedTextField(state.formCreditHoldReason, vm::setFormCreditHoldReason, label = { Text("Hold အကြောင်းရင်း") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blacklist", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(state.formBlacklisted, vm::setFormBlacklisted)
                }
                if (state.formBlacklisted) OutlinedTextField(state.formBlacklistReason, vm::setFormBlacklistReason, label = { Text("Blacklist အကြောင်းရင်း") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = vm::save, enabled = !state.saving, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                if (state.saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("သိမ်းမည်", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton(onClick = vm::closeEditor, enabled = !state.saving) { Text("မလုပ်တော့") } }
    )
}
