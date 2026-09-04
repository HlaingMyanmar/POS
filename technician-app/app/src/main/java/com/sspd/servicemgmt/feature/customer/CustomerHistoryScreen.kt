package com.sspd.servicemgmt.feature.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.network.SaleItemDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import java.text.NumberFormat
import java.time.LocalDate

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomerHistoryScreen(
    onBack: () -> Unit,
    onJobClick: (Int) -> Unit,
    onSaleClick: (Int) -> Unit = {},
) {
    val vm: CustomerHistoryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.selected == null) "Customer History" else state.selected?.name.orEmpty(), fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { if (state.selected != null) vm.clearSelection() else onBack() }) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        containerColor = ScreenBg
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.selected == null) {
                CustomerSearchContent(state, vm)
            } else {
                CustomerDetailContent(state, vm, onJobClick, onSaleClick)
            }
        }
    }
}

@Composable
private fun CustomerSearchContent(state: CustomerHistoryViewModel.State, vm: CustomerHistoryViewModel) {
    OutlinedTextField(
        value = state.search,
        onValueChange = vm::setSearch,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        label = { Text("Customer အမည်၊ ဖုန်း၊ လိပ်စာဖြင့် ရှာရန်") },
        placeholder = { Text("ဥပမာ - မောင်မောင် / 09 / ရန်ကုန်") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
    when {
        state.loading -> LoadingBlock()
        state.error != null -> ErrorBlock(state.error, vm::loadCustomers)
        state.filteredCustomers.isEmpty() -> EmptyBlock("Customer မတွေ့ပါ")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.filteredCustomers, key = { it.id ?: it.hashCode() }) { customer ->
                CustomerCard(customer) { vm.selectCustomer(customer) }
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerDTO, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, null, tint = Primary, modifier = Modifier.size(38.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(customer.name, fontWeight = FontWeight.Bold, color = TextMain)
                customer.phone?.let { Text(it, fontSize = 13.sp, color = TextMuted) }
                customer.address?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Outlined.History, "History", tint = Primary)
        }
    }
}

@Composable
private fun CustomerDetailContent(
    state: CustomerHistoryViewModel.State,
    vm: CustomerHistoryViewModel,
    onJobClick: (Int) -> Unit,
    onSaleClick: (Int) -> Unit,
) {
    val customer = state.selected ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PrimaryLight), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(customer.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    customer.phone?.let { InfoRow(Icons.Outlined.Phone, it) }
                    customer.address?.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Outlined.LocationOn, it) }
                    HorizontalDivider(color = BorderColor)
                    Text("Service Job စုစုပေါင်း ${state.jobs.size} ခု", fontWeight = FontWeight.Bold, color = TextMain)
                    val reworkCount = state.jobs.count { it.rework == true }
                    if (reworkCount > 0) {
                        Text("Rework ${reworkCount} ခု", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ReworkColor)
                    }
                    if (state.canViewSales) {
                        Text("Sale စုစုပေါင်း ${state.sales.size} ခု", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SaleColor)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Build, null, tint = Primary)
                Text("Service History", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            }
        }
        when {
            state.historyLoading -> item { LoadingBlock() }
            state.error != null -> item { ErrorBlock(state.error) { vm.selectCustomer(customer) } }
            state.jobs.isEmpty() -> item { EmptyBlock("Service history မရှိသေးပါ") }
            else -> items(state.jobs, key = { it.id ?: it.hashCode() }) { job ->
                JobHistoryCard(job) { job.id?.let(onJobClick) }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.ReceiptLong, null, tint = SaleColor)
                Text("Sale History", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            }
        }
        if (!state.canViewSales) {
            item { SalePermissionLocked() }
        } else if (!state.historyLoading) {
            when {
                state.saleError != null -> item { ErrorBlock(state.saleError) { vm.selectCustomer(customer) } }
                state.sales.isEmpty() -> item { EmptyBlock("Sale history မရှိသေးပါ") }
                else -> items(state.sales, key = { it.id ?: it.hashCode() }) { sale ->
                    SaleHistoryCard(sale) { sale.id?.let(onSaleClick) }
                }
            }
        }
    }
}

@Composable
private fun SalePermissionLocked() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.Lock, null, tint = TextMuted, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Sale History ကြည့်ခွင့်မရှိပါ", fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(
                    "Admin မှ CAN_ACCESS_SALE_READ permission ပေးပြီး ပြန်လည် Login ဝင်ပါ။",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun SaleHistoryCard(sale: SaleDTO, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SaleBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sale.saleCode ?: "Sale #${sale.id}", fontWeight = FontWeight.ExtraBold, color = SaleColor)
                Text(sale.saleDate?.take(10).orEmpty(), fontSize = 12.sp, color = TextMuted)
            }
            sale.details.orEmpty().forEach { item -> SaleItemRow(item) }
            if (sale.details.isNullOrEmpty()) {
                Text("ပစ္စည်းအသေးစိတ်မရှိပါ", fontSize = 12.sp, color = TextMuted)
            }
            HorizontalDivider(color = BorderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sale.paymentStatus.orEmpty().replace('_', ' '), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text(
                    "${NumberFormat.getNumberInstance().format(sale.netAmount ?: sale.totalAmount ?: 0.0)} Ks",
                    fontWeight = FontWeight.ExtraBold,
                    color = TextMain
                )
            }
            Text("နှိပ်ပြီး Invoice Preview ကြည့်ရန်", fontSize = 11.sp, color = SaleColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SaleItemRow(item: SaleItemDTO) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SaleBackground, RoundedCornerShape(9.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.productName ?: "Product", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("Qty ${item.qty ?: 0}", fontSize = 12.sp, color = TextMuted)
        }
        item.serialNumbers.orEmpty().takeIf { it.isNotEmpty() }?.let {
            Text("Serial: ${it.joinToString()}", fontSize = 11.sp, color = SaleColor)
        }
        item.warrantyExpiryDate?.takeIf { it.isNotBlank() }?.let {
            Text("Warranty Expiry: ${it.take(10)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = warrantyColor(it))
        }
    }
}

@Composable
private fun JobHistoryCard(job: ServiceJobDTO, onClick: () -> Unit) {
    val isRework = job.rework == true
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isRework) ReworkBackground else Color.White),
        border = BorderStroke(if (isRework) 1.5.dp else 1.dp, if (isRework) ReworkBorder else BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (isRework) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = ReworkColor, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            "REWORK",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    job.parentJobNo?.takeIf { it.isNotBlank() }?.let {
                        Text("မူလ Job: $it", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ReworkColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(job.jobNo ?: "Job #${job.id}", fontWeight = FontWeight.ExtraBold, color = Primary)
                Text(job.status.orEmpty().replace('_', ' '), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor(job.status))
            }
            Text(job.itemName ?: job.deviceType ?: "Service Job", fontWeight = FontWeight.Bold, color = TextMain)
            job.problemDesc?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (isRework) {
                ReworkDetails(job)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(job.receivedDate?.take(10).orEmpty(), fontSize = 12.sp, color = TextMuted)
                val amount = job.netAmount ?: job.finalCost ?: job.estimatedCost
                amount?.let { Text("${NumberFormat.getNumberInstance().format(it)} Ks", fontWeight = FontWeight.Bold, color = TextMain) }
            }
            Text("နှိပ်ပြီး Invoice Preview ကြည့်ရန်", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReworkDetails(job: ServiceJobDTO) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        job.reworkType?.takeIf { it.isNotBlank() }?.let { ReworkInfo("အမျိုးအစား", it) }
        job.replacementReason?.takeIf { it.isNotBlank() }?.let { ReworkInfo("အကြောင်းရင်း", it) }
        job.resolutionMode?.takeIf { it.isNotBlank() }?.let { ReworkInfo("ဖြေရှင်းပုံ", it.replace('_', ' ')) }
        job.originalPartName?.takeIf { it.isNotBlank() }?.let { ReworkInfo("မူလပစ္စည်း", it) }
        job.replacementProductName?.takeIf { it.isNotBlank() }?.let { ReworkInfo("အစားထိုးပစ္စည်း", it) }
        job.refundAmount?.takeIf { it > 0 }?.let {
            ReworkInfo("ပြန်အမ်းငွေ", "${NumberFormat.getNumberInstance().format(it)} Ks")
        }
        if (job.reworkType.isNullOrBlank() && job.replacementReason.isNullOrBlank() &&
            job.resolutionMode.isNullOrBlank() && job.originalPartName.isNullOrBlank() &&
            job.replacementProductName.isNullOrBlank() && (job.refundAmount ?: 0.0) <= 0
        ) {
            Text("Rework အသေးစိတ်ကို Job Detail တွင်ကြည့်ပါ", fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun ReworkInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ReworkColor)
        Text(value, fontSize = 12.sp, color = TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = TextMain)
    }
}

@Composable private fun LoadingBlock() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Primary)
        Spacer(Modifier.height(10.dp))
        Text("ရယူနေပါသည်…", color = TextMuted)
    }
}

@Composable private fun ErrorBlock(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = retry) { Icon(Icons.Outlined.Refresh, null); Text(" ပြန်စမ်းမယ်") }
    }
}

@Composable private fun EmptyBlock(message: String) {
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Build, null, tint = TextMuted, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextMuted)
    }
}

private fun statusColor(status: String?): Color = when (status?.uppercase()) {
    "COMPLETED", "DELIVERED" -> Color(0xFF15803D)
    "CANCELLED" -> Color(0xFFB91C1C)
    "WAITING_PARTS" -> Color(0xFFB45309)
    else -> Primary
}

@Preview(
    name = "Customer History - Phone",
    showBackground = true,
    showSystemUi = true,
    widthDp = 390,
    heightDp = 844
)
@Composable
private fun CustomerHistoryPreview() {
    val customer = CustomerDTO(
        id = 1,
        name = "မောင်မောင်",
        phone = "09 123 456 789",
        address = "လမ်းမတော်မြို့နယ်၊ ရန်ကုန်"
    )
    val jobs = listOf(
        ServiceJobDTO(
            id = 101,
            jobNo = "JOB-2026-0101",
            itemName = "ASUS Laptop",
            problemDesc = "Power မတက်ခြင်းနှင့် battery အားမဝင်ခြင်း",
            status = "IN_PROGRESS",
            receivedDate = "2026-08-28T10:30:00",
            estimatedCost = 45000.0
        ),
        ServiceJobDTO(
            id = 88,
            jobNo = "JOB-2026-0088",
            itemName = "Epson Printer",
            problemDesc = "Paper jam ဖြစ်နေခြင်း",
            status = "COMPLETED",
            receivedDate = "2026-07-12T09:15:00",
            netAmount = 25000.0
        ),
        ServiceJobDTO(
            id = 89,
            jobNo = "JOB-2026-0088-R1",
            parentJobNo = "JOB-2026-0088",
            rework = true,
            reworkType = "PART_REPLACEMENT",
            itemName = "Epson Printer",
            problemDesc = "ပြုပြင်ပြီးနောက် paper feed ပြန်ဖြစ်ခြင်း",
            replacementReason = "အသုံးပြုထားသော roller ချို့ယွင်းခြင်း",
            resolutionMode = "REPLACE_PART",
            originalPartName = "Paper Feed Roller",
            replacementProductName = "Epson Feed Roller အသစ်",
            status = "IN_PROGRESS",
            receivedDate = "2026-08-20T14:00:00",
            estimatedCost = 0.0
        )
    )

    val previewSale = SaleDTO(
        id = 501,
        saleCode = "SALE-2026-0501",
        saleDate = "2026-05-10T11:00:00",
        netAmount = 1850000.0,
        paymentStatus = "PAID",
        details = listOf(
            SaleItemDTO(
                productName = "ASUS Vivobook 15",
                qty = 1,
                serialNumbers = listOf("ASV15-2026-00125"),
                warrantyMonths = 12,
                warrantyExpiryDate = "2027-05-10"
            )
        )
    )

    Surface(color = ScreenBg) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Primary).padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ArrowBack, null, tint = Color.White)
                Text(
                    "Customer History",
                    modifier = Modifier.padding(start = 16.dp),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                label = { Text("Customer အမည်၊ ဖုန်း၊ လိပ်စာဖြင့် ရှာရန်") },
                placeholder = { Text("ဥပမာ - မောင်မောင် / 09 / ရန်ကုန်") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { CustomerCard(customer, onClick = {}) }
                item { Text("Service History (${jobs.size})", fontWeight = FontWeight.ExtraBold, color = TextMain) }
                items(jobs) { job -> JobHistoryCard(job, onClick = {}) }
                item { Text("Sale History", fontWeight = FontWeight.ExtraBold, color = TextMain) }
                item { SaleHistoryCard(previewSale, onClick = {}) }
            }
        }
    }
}

private val ReworkColor = Color(0xFFC2410C)
private val ReworkBorder = Color(0xFFF97316)
private val ReworkBackground = Color(0xFFFFF7ED)
private val SaleColor = Color(0xFF1D4ED8)
private val SaleBorder = Color(0xFF93C5FD)
private val SaleBackground = Color(0xFFEFF6FF)

private fun warrantyColor(expiry: String): Color = runCatching {
    if (LocalDate.parse(expiry.take(10)).isBefore(LocalDate.now())) Color(0xFFB91C1C)
    else Color(0xFF15803D)
}.getOrDefault(TextMuted)
