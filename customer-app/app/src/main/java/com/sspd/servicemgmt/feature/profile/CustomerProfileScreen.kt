package com.sspd.servicemgmt.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.CustomerAuthResponse
import com.sspd.servicemgmt.core.network.BookingSummary
import com.sspd.servicemgmt.core.network.CustomerJob
import com.sspd.servicemgmt.core.network.CustomerOrder
import com.sspd.servicemgmt.core.network.CustomerPurchase
import com.sspd.servicemgmt.core.network.LoyaltyPoints
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.core.ui.theme.WarningBg
import com.sspd.servicemgmt.core.util.PasswordRules
import kotlin.math.abs

@Composable
fun CustomerProfileScreen(
    profile: CustomerAuthResponse,
    loyaltyPoints: LoyaltyPoints? = null,
    orderCount: Int,
    serviceCount: Int,
    purchases: List<CustomerPurchase>,
    jobs: List<CustomerJob>,
    orders: List<CustomerOrder>,
    bookings: List<BookingSummary>,
    loading: Boolean,
    saving: Boolean,
    passwordSaving: Boolean,
    biometricEnabled: Boolean = false,
    onBiometricEnabledChange: (Boolean) -> Unit = {},
    onSave: (name: String, phone: String, address: String) -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String, onSuccess: () -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(profile.name.orEmpty()) }
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
    var address by remember { mutableStateOf(profile.address.orEmpty()) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(profile.name, profile.phone, profile.address) {
        name = profile.name.orEmpty()
        phone = profile.phone.orEmpty()
        address = profile.address.orEmpty()
        editing = false
    }

    if (loading && profile.name.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileHeader(profile)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStat(
                value = orderCount.toString(),
                label = "ဝယ်ယူမှု",
                modifier = Modifier.weight(1f)
            )
            ProfileStat(
                value = serviceCount.toString(),
                label = "Service",
                modifier = Modifier.weight(1f)
            )
            ProfileStat(
                value = loyaltyPoints?.currentPoints?.toString() ?: "0",
                label = "Points",
                modifier = Modifier.weight(1f)
            )
            ProfileStat(
                value = if (profile.needsProfile == true) "လိုအပ်" else "ပြည့်စုံ",
                label = "Profile",
                modifier = Modifier.weight(1f)
            )
        }

        CreditStatusCard(profile)

        SecurityCard(onChangePassword = { showPasswordDialog = true })
        BiometricCard(biometricEnabled, onBiometricEnabledChange)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ကိုယ်ရေးအချက်အလက်", fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (editing) "ပြင်ဆင်ပြီး သိမ်းမည်ကို နှိပ်ပါ" else "သင့်အကောင့်၏ ဆက်သွယ်ရန်အချက်အလက်",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!editing) {
                        OutlinedButton(
                            onClick = { editing = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ပြင်မည်")
                        }
                    }
                }

                HorizontalDivider(color = ScreenBg)

                if (editing) {
                    ProfileTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "အမည်",
                        icon = Icons.Rounded.Person
                    )
                    ProfileTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "ဖုန်းနံပါတ်",
                        icon = Icons.Outlined.Phone,
                        keyboardType = KeyboardType.Phone
                    )
                    ProfileTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "လိပ်စာ",
                        icon = Icons.Outlined.LocationOn,
                        singleLine = false
                    )
                    ProfileInfoRow(Icons.Outlined.Email, "Email", profile.email.orEmpty().ifBlank { "မရှိသေးပါ" })
                    Text(
                        "Email ကို အကောင့်လုံခြုံရေးနှင့် Password Reset အတွက် အသုံးပြုပါသည်။",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                name = profile.name.orEmpty()
                                phone = profile.phone.orEmpty()
                                address = profile.address.orEmpty()
                                editing = false
                            },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("မသိမ်းတော့ပါ")
                        }
                        Button(
                            onClick = { onSave(name, phone, address) },
                            enabled = !saving && phone.trim().length >= 6 && address.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = OnPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("သိမ်းမည်")
                            }
                        }
                    }
                } else {
                    ProfileInfoRow(Icons.Rounded.Person, "အမည်", profile.name.orEmpty().ifBlank { "မထည့်ရသေးပါ" })
                    ProfileInfoRow(Icons.Outlined.Phone, "ဖုန်း", profile.phone.orEmpty().ifBlank { "မထည့်ရသေးပါ" })
                    ProfileInfoRow(Icons.Outlined.Email, "Email", profile.email.orEmpty().ifBlank { "မရှိသေးပါ" })
                    ProfileInfoRow(Icons.Outlined.LocationOn, "လိပ်စာ", profile.address.orEmpty().ifBlank { "မထည့်ရသေးပါ" })
                }
            }
        }

        CustomerHistoryCard(
            purchases = purchases,
            jobs = jobs,
            orders = orders,
            bookings = bookings
        )

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("အကောင့်မှ ထွက်မည်", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            saving = passwordSaving,
            onDismiss = { if (!passwordSaving) showPasswordDialog = false },
            onConfirm = { current, new ->
                onChangePassword(current, new) { showPasswordDialog = false }
            }
        )
    }
}

@Composable
private fun BiometricCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Biometric login", fontWeight = FontWeight.ExtraBold)
                Text("Require fingerprint or face unlock when reopening the app", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun SecurityCard(onChangePassword: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("အကောင့်လုံခြုံရေး", fontWeight = FontWeight.ExtraBold)
                Text("သင့်စကားဝှက်ကို အချိန်မရွေး ပြောင်းနိုင်ပါတယ်", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onChangePassword, shape = RoundedCornerShape(12.dp)) {
                Text("Password ပြောင်းမည်", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPassword: String, newPassword: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Primary)
            }
        },
        title = {
            Text("စကားဝှက် ပြောင်းရန်", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    "လက်ရှိစကားဝှက်နှင့် စကားဝှက်အသစ်ကို ထည့်ပါ။",
                    style = MaterialTheme.typography.bodySmall
                )
                PasswordField(
                    value = current,
                    onValueChange = {
                        current = it
                        localError = null
                    },
                    label = "လက်ရှိ စကားဝှက်",
                    visible = showCurrent,
                    onToggleVisibility = { showCurrent = !showCurrent }
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = "စကားဝှက်အသစ် (${PasswordRules.HINT})",
                    visible = showNew,
                    onToggleVisibility = { showNew = !showNew }
                )
                PasswordField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        localError = null
                    },
                    label = "စကားဝှက်အသစ် ထပ်ရိုက်ပါ",
                    visible = showNew,
                    onToggleVisibility = { showNew = !showNew }
                )
                localError?.let {
                    Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    localError = when {
                        current.isBlank() -> "လက်ရှိ စကားဝှက် ထည့်ပါ"
                        !PasswordRules.isStrong(newPassword) -> PasswordRules.HINT
                        newPassword != confirm -> "စကားဝှက်အသစ် နှစ်ခု မတူပါ"
                        current == newPassword -> "စကားဝှက်အသစ်သည် လက်ရှိစကားဝှက်နှင့် မတူရပါ"
                        else -> null
                    }
                    if (localError == null) onConfirm(current, newPassword)
                },
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OnPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (saving) "ပြောင်းနေသည်..." else "ပြောင်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("မလုပ်တော့ပါ")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = TextMuted) },
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "ဖျောက်ရန်" else "ပြရန်"
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun CreditStatusCard(profile: CustomerAuthResponse) {
    val allowed = profile.creditAllowed == true && profile.creditHold != true
    val statusColor = if (allowed) Success else Warning
    val statusBackground = if (allowed) SuccessBg else WarningBg

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(statusBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = statusColor)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("အကြွေးဝယ်ယူခွင့်", fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (allowed) "ခွင့်ပြုထားပါသည်" else "ခွင့်မပြုထားပါ",
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(statusBackground)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (allowed) "ACTIVE" else "NOT ACTIVE",
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            HorizontalDivider(color = ScreenBg)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CreditValue(
                    label = "အများဆုံးပမာဏ",
                    value = if (allowed) money(profile.creditLimit ?: 0.0) else "—",
                    modifier = Modifier.weight(1f)
                )
                CreditValue(
                    label = "အကြွေးရက်",
                    value = if (allowed && (profile.creditDays ?: 0) > 0) "${profile.creditDays} ရက်" else "—",
                    modifier = Modifier.weight(1f)
                )
            }

            if (!allowed) {
                val reason = profile.creditStatusReason?.takeIf { it.isNotBlank() }
                    ?: if (profile.creditHold == true) "အကြွေးအကောင့်ကို ယာယီပိတ်ထားပါသည်။" else "ဆိုင်မှ အကြွေးဝယ်ယူခွင့် မသတ်မှတ်ရသေးပါ။"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (profile.creditHold == true) DangerBg else WarningBg)
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        reason,
                        color = if (profile.creditHold == true) Danger else Warning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(ScreenBg)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(3.dp))
        Text(value, color = PrimaryDark, fontWeight = FontWeight.ExtraBold)
    }
}

private data class HistoryPreviewItem(
    val title: String,
    val detail: String,
    val amount: Double? = null,
    val kind: HistoryKind
)

private enum class HistoryKind { PURCHASE, SERVICE, ORDER, BOOKING }

@Composable
private fun CustomerHistoryCard(
    purchases: List<CustomerPurchase>,
    jobs: List<CustomerJob>,
    orders: List<CustomerOrder>,
    bookings: List<BookingSummary>
) {
    val history = buildList {
        purchases.take(2).forEach {
            add(HistoryPreviewItem(it.saleCode.orEmpty().ifBlank { "ဝယ်ယူမှု" }, listOfNotNull(it.saleDate?.take(10), it.paymentStatus).joinToString(" • "), it.netAmount, HistoryKind.PURCHASE))
        }
        jobs.take(2).forEach {
            add(HistoryPreviewItem(it.jobNo.orEmpty().ifBlank { "Service" }, listOfNotNull(it.itemName, it.status).joinToString(" • "), it.netAmount, HistoryKind.SERVICE))
        }
        orders.take(2).forEach {
            val products = (it.lines ?: emptyList())
                .joinToString(" · ") { line -> "${line.productName} × ${line.qty}" }
                .ifBlank { "ပစ္စည်း မရှိ" }
            val statusLabel = when (it.status?.uppercase()) {
                "CONFIRMED", "APPROVED" -> "Approved"
                "CANCELLED", "CANCELED" -> "ပယ်ဖျက်"
                "PENDING" -> "စောင့်ဆိုင်း"
                else -> it.status.orEmpty()
            }
            add(
                HistoryPreviewItem(
                    it.orderNo.orEmpty().ifBlank { "App အော်ဒါ" },
                    listOfNotNull(it.createdAt?.take(10), statusLabel, products).joinToString(" • "),
                    it.total,
                    HistoryKind.ORDER
                )
            )
        }
        bookings.take(2).forEach {
            add(HistoryPreviewItem(it.bookingNo.orEmpty().ifBlank { "Service တောင်းချက်" }, listOfNotNull(it.appointmentDate?.take(10), it.status).joinToString(" • "), null, HistoryKind.BOOKING))
        }
    }.take(6)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Customer History", fontWeight = FontWeight.ExtraBold)
                    Text("လတ်တလော ဝယ်ယူမှုနှင့် Service မှတ်တမ်း", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ScreenBg)

            if (history.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("မှတ်တမ်း မရှိသေးပါ", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                history.forEachIndexed { index, item ->
                    HistoryPreviewRow(item)
                    if (index < history.lastIndex) HorizontalDivider(color = ScreenBg)
                }
                Text(
                    "မှတ်တမ်းအားလုံးကို အောက်ခြေ “မှတ်တမ်း” tab မှာ ကြည့်နိုင်ပါတယ်။",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = Primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HistoryPreviewRow(item: HistoryPreviewItem) {
    val icon = when (item.kind) {
        HistoryKind.PURCHASE -> Icons.Outlined.Payments
        HistoryKind.SERVICE -> Icons.Outlined.Handyman
        HistoryKind.ORDER -> Icons.Outlined.ShoppingBag
        HistoryKind.BOOKING -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
    Row(
        modifier = Modifier.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(item.detail.ifBlank { "—" }, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        item.amount?.let {
            Text(money(it), color = PrimaryDark, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun money(amount: Double): String {
    val safe = if (amount.isFinite()) amount else 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

@Composable
private fun ProfileHeader(profile: CustomerAuthResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(OnPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.initials(),
                    color = OnPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.orEmpty().ifBlank { "SSPD Customer" },
                    color = OnPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    profile.email.orEmpty().ifBlank { profile.phone.orEmpty() },
                    color = OnPrimary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
                profile.customerId?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "CUSTOMER • ${it.toString().padStart(5, '0')}",
                        color = PrimaryDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(OnPrimary.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Primary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = TextMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

private fun String?.initials(): String {
    val parts = this.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "C"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 820)
@Composable
private fun CustomerProfilePreview() {
    AppTheme {
        CustomerProfileScreen(
            profile = CustomerAuthResponse(
                customerId = 128,
                name = "Aung Kyaw Moe",
                phone = "09 765 432 100",
                email = "aungkyaw@example.com",
                address = "No. 28, 35th Street, Mandalay",
                needsProfile = false,
                creditAllowed = true,
                creditLimit = 1_500_000.0,
                creditDays = 30,
                creditHold = false
            ),
            orderCount = 12,
            serviceCount = 4,
            purchases = listOf(
                CustomerPurchase(
                    id = 1,
                    saleCode = "SALE-000128",
                    saleDate = "2026-09-05T10:30:00",
                    netAmount = 285000.0,
                    paymentStatus = "PAID"
                )
            ),
            jobs = listOf(
                CustomerJob(
                    id = 1,
                    jobNo = "JOB-000042",
                    status = "COMPLETED",
                    itemName = "Laptop",
                    netAmount = 45000.0
                )
            ),
            orders = emptyList(),
            bookings = emptyList(),
            loading = false,
            saving = false,
            passwordSaving = false,
            onSave = { _, _, _ -> },
            onChangePassword = { _, _, done -> done() },
            onLogout = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390)
@Composable
private fun ChangePasswordDialogPreview() {
    AppTheme {
        ChangePasswordDialog(
            saving = false,
            onDismiss = {},
            onConfirm = { _, _ -> }
        )
    }
}
