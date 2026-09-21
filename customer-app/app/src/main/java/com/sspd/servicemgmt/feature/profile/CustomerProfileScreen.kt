package com.sspd.servicemgmt.feature.profile

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingSummary
import com.sspd.servicemgmt.core.network.CustomerAuthResponse
import com.sspd.servicemgmt.core.network.CustomerJob
import com.sspd.servicemgmt.core.network.CustomerOrder
import com.sspd.servicemgmt.core.network.CustomerPurchase
import com.sspd.servicemgmt.core.network.LoyaltyPoints
import com.sspd.servicemgmt.core.ui.component.PaperSizeSelectionDialog
import com.sspd.servicemgmt.core.ui.component.SaleInvoiceViewerDialog
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
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
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.core.ui.theme.WarningBg
import com.sspd.servicemgmt.core.util.PasswordRules
import com.sspd.servicemgmt.core.util.SaleInvoiceOpener
import com.sspd.servicemgmt.core.util.formatWarranty
import com.sspd.servicemgmt.core.util.formatWarrantyState
import com.sspd.servicemgmt.feature.home.JobServiceModeChip
import com.sspd.servicemgmt.feature.home.JobTimelineAndCrew
import kotlinx.coroutines.launch
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
    onNavigateToOrders: () -> Unit = {},
    onNavigateToWishlist: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onSave: (name: String, phone: String, address: String) -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String, onSuccess: () -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var editingProfile by remember { mutableStateOf(false) }
    var showCreditDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf(profile.name.orEmpty()) }
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
    var address by remember { mutableStateOf(profile.address.orEmpty()) }

    LaunchedEffect(profile.name, profile.phone, profile.address) {
        name = profile.name.orEmpty()
        phone = profile.phone.orEmpty()
        address = profile.address.orEmpty()
        editingProfile = false
    }

    if (loading && profile.name.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 1. Header Bar: "အကောင့်" + Settings Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "အကောင့်",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            IconButton(
                onClick = { editingProfile = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = TextMain,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Compact Profile Card (Avatar + Customer Name + Phone/Email + Subtitle)
        Surface(
            onClick = { editingProfile = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PrimaryLight)
                        .border(1.dp, Primary.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.name.initials(),
                        color = Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name.orEmpty().ifBlank { "SSPD Customer" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = profile.phone.orEmpty().ifBlank { profile.email.orEmpty().ifBlank { "မထည့်ရသေးပါ" } },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "ပရိုဖိုင်ကြည့်ရန် / ပြင်ဆင်ရန် ➔",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 3. 2x2 Action Cards Grid (Orders, Wishlist, Payments, Addresses)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileGridCard(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    label = "အော်ဒါများ",
                    countBadge = orderCount,
                    onClick = onNavigateToOrders,
                    modifier = Modifier.weight(1f)
                )
                ProfileGridCard(
                    icon = Icons.Outlined.FavoriteBorder,
                    label = "စိတ်ကြိုက်များ",
                    onClick = onNavigateToWishlist,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileGridCard(
                    icon = Icons.Rounded.CreditCard,
                    label = "ငွေပေးချေမှုများ",
                    onClick = { showCreditDialog = true },
                    modifier = Modifier.weight(1f)
                )
                ProfileGridCard(
                    icon = Icons.Outlined.LocationOn,
                    label = "လိပ်စာများ",
                    onClick = { editingProfile = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Section 1: "သင့်အတွက် အကျိုးခံစားခွင့်များ"
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "သင့်အတွက် အကျိုးခံစားခွင့်များ",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )

            ProfileMenuRowItem(
                icon = Icons.Outlined.EmojiEvents,
                label = "SSPD ဆုလက်ဆောင်များ (${loyaltyPoints?.currentPoints ?: 0} Points)",
                onClick = { showPointsDialog = true }
            )
            HorizontalDivider(color = ScreenBg)

            ProfileMenuRowItem(
                icon = Icons.Outlined.ConfirmationNumber,
                label = "ဘောက်ချာများ / မှတ်တမ်း",
                onClick = { showHistoryDialog = true }
            )
            HorizontalDivider(color = ScreenBg)

            ProfileMenuRowItem(
                icon = Icons.Outlined.CardGiftcard,
                label = "သူငယ်ချင်းများကို ဖိတ်ခေါ်ရန်",
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SSPD Customer App")
                        putExtra(Intent.EXTRA_TEXT, "SSPD Customer App ကို အသုံးပြုပြီး ပစ္စည်းများ မှာယူနိုင်ပါသည်။ https://sspdmyanmar.com")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "App ဖိတ်ခေါ်ရန်"))
                }
            )
        }

        // 5. Section 2: "အထွေထွေ"
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "အထွေထွေ",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )

            ProfileMenuRowItem(
                icon = Icons.Outlined.HelpOutline,
                label = "အကူအညီပေးဌာန (Customer Support)",
                onClick = onNavigateToChat
            )
            HorizontalDivider(color = ScreenBg)

            ProfileMenuRowItem(
                icon = Icons.Outlined.Description,
                label = "စည်းကမ်းသတ်မှတ်ချက်များ",
                onClick = { showTermsDialog = true }
            )
            HorizontalDivider(color = ScreenBg)

            ProfileMenuRowItem(
                icon = Icons.Rounded.Lock,
                label = "စကားဝှက် ပြောင်းရန်",
                onClick = { showPasswordDialog = true }
            )
            HorizontalDivider(color = ScreenBg)

            ProfileMenuRowItem(
                icon = Icons.AutoMirrored.Outlined.Logout,
                label = "အကောင့်မှ ထွက်မည်",
                tint = Danger,
                onClick = onLogout
            )

            Spacer(Modifier.height(84.dp))
        }
    }

    // Edit Profile Modal Dialog
    if (editingProfile) {
        EditProfileModalDialog(
            profile = profile,
            name = name,
            phone = phone,
            address = address,
            saving = saving,
            onNameChange = { name = it },
            onPhoneChange = { phone = it },
            onAddressChange = { address = it },
            onSave = { onSave(name, phone, address) },
            onDismiss = { editingProfile = false }
        )
    }

    // Credit Status Dialog
    if (showCreditDialog) {
        AlertDialog(
            onDismissRequest = { showCreditDialog = false },
            title = { Text("ငွေပေးချေမှု & အကြွေးဝယ်ယူခွင့်", fontWeight = FontWeight.Bold) },
            text = { CreditStatusCard(profile) },
            confirmButton = {
                TextButton(onClick = { showCreditDialog = false }) { Text("ပိတ်မည်") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // History / Vouchers Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("ဝယ်ယူမှု & Service မှတ်တမ်း", fontWeight = FontWeight.Bold) },
            text = {
                CustomerHistoryCard(
                    purchases = purchases,
                    jobs = jobs,
                    orders = orders,
                    bookings = bookings
                )
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("ပိတ်မည်") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Points Rewards Dialog
    if (showPointsDialog) {
        AlertDialog(
            onDismissRequest = { showPointsDialog = false },
            title = { Text("SSPD ဆုလက်ဆောင်များ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("သင့်ထံတွင် စုစုပေါင်း Points ${loyaltyPoints?.currentPoints ?: 0} ရှိပါသည်။", style = MaterialTheme.typography.bodyMedium)
                    Text("ဝယ်ယူမှုများနှင့် Service ခေါ်ယူမှုများပြုလုပ်၍ Reward Points များ ပိုမို ရယူနိုင်ပါသည်။", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPointsDialog = false }) { Text("သိပြီ") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Terms Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("စည်းကမ်းသတ်မှတ်ချက်များ", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("၁။ SSPD Customer App မှတစ်ဆင့် ပစ္စည်းများ တိုက်ရိုက် မှာယူနိုင်ပါသည်။", style = MaterialTheme.typography.bodySmall)
                    Text("၂။ Service တောင်းဆိုမှုများကို ဆိုင်မှ အကြောင်းပြန်အတည်ပြုပြီး အကြောင်းကြားပေးပါမည်။", style = MaterialTheme.typography.bodySmall)
                    Text("၃။ ငွေပေးချေမှုနှင့် ဘောက်ချာများအား App အတွင်း တိုက်ရိုက် စစ်ဆေးနိုင်ပါသည်။", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) { Text("လက်ခံသည်") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Password Change Dialog
    if (showPasswordDialog) {
        ChangePasswordDialog(
            saving = passwordSaving,
            onDismiss = { if (!passwordSaving) showPasswordDialog = false },
            onConfirm = { current, newPass ->
                onChangePassword(current, newPass) { showPasswordDialog = false }
            }
        )
    }
}
}

@Composable
private fun ProfileGridCard(
    icon: ImageVector,
    label: String,
    countBadge: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextMain,
                    modifier = Modifier.size(26.dp)
                )
                if (countBadge != null && countBadge > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 8.dp, y = (-4).dp)
                            .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                            .clip(CircleShape)
                            .background(Primary)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countBadge.toString(),
                            color = OnPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileMenuRowItem(
    icon: ImageVector,
    label: String,
    tint: Color = TextMain,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EditProfileModalDialog(
    profile: CustomerAuthResponse,
    name: String,
    phone: String,
    address: String,
    saving: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ကိုယ်ရေးအချက်အလက် ပြင်ဆင်ရန်", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = PrimaryDark)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceSoft)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))

                ProfileTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = "အမည်",
                    icon = Icons.Rounded.Person
                )
                ProfileTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = "ဖုန်းနံပါတ်",
                    icon = Icons.Outlined.Phone,
                    keyboardType = KeyboardType.Phone
                )
                ProfileTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = "လိပ်စာ",
                    icon = Icons.Outlined.LocationOn,
                    singleLine = false
                )

                ProfileInfoRow(Icons.Outlined.Email, "Email", profile.email.orEmpty().ifBlank { "မရှိသေးပါ" })

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("မလုပ်တော့ပါ")
                    }
                    Button(
                        onClick = onSave,
                        enabled = !saving && phone.trim().length >= 6 && address.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
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
            }
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
            IconButton(onClick = onToggleVisibility) {
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
    val kind: HistoryKind,
    val rawItem: Any? = null
)

private enum class HistoryKind { PURCHASE, SERVICE, ORDER, BOOKING }

@Composable
private fun CustomerHistoryCard(
    purchases: List<CustomerPurchase>,
    jobs: List<CustomerJob>,
    orders: List<CustomerOrder>,
    bookings: List<BookingSummary>
) {
    var selectedItem by remember { mutableStateOf<HistoryPreviewItem?>(null) }

    val history = buildList {
        purchases.take(2).forEach {
            add(HistoryPreviewItem(
                title = it.saleCode.orEmpty().ifBlank { "ဝယ်ယူမှု" },
                detail = listOfNotNull(it.saleDate?.replace('T', ' ')?.take(10), it.paymentStatus).joinToString(" • "),
                amount = it.netAmount,
                kind = HistoryKind.PURCHASE,
                rawItem = it
            ))
        }
        jobs.take(2).forEach {
            add(HistoryPreviewItem(
                title = it.jobNo.orEmpty().ifBlank { "Service" },
                detail = listOfNotNull(it.itemName, it.status?.replace('_', ' ')).joinToString(" • "),
                amount = it.netAmount,
                kind = HistoryKind.SERVICE,
                rawItem = it
            ))
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
            add(HistoryPreviewItem(
                title = it.orderNo.orEmpty().ifBlank { "App အော်ဒါ" },
                detail = listOfNotNull(it.createdAt?.replace('T', ' ')?.take(10), statusLabel, products).joinToString(" • "),
                amount = it.total,
                kind = HistoryKind.ORDER,
                rawItem = it
            ))
        }
        bookings.take(2).forEach {
            add(HistoryPreviewItem(
                title = it.bookingNo.orEmpty().ifBlank { "Service တောင်းချက်" },
                detail = listOfNotNull(
                    it.appointmentDate?.replace('T', ' ')?.take(10),
                    it.requestedServiceName,
                    when (it.status?.uppercase()) {
                        "REJECTED" -> "ငြင်းပယ်ထား"
                        "CANCELED", "CANCELLED" -> "ပယ်ဖျက်ထား"
                        else -> it.status?.replace('_', ' ')
                    }
                ).joinToString(" • "),
                amount = null,
                kind = HistoryKind.BOOKING,
                rawItem = it
            ))
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
                    Text("လတ်တလော ဝယ်ယူမှုနှင့် Service မှတ်တမ်း", style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
                    HistoryPreviewRow(item = item, onClick = { selectedItem = item })
                    if (index < history.lastIndex) HorizontalDivider(color = ScreenBg)
                }
            }
        }
    }

    selectedItem?.let { item ->
        HistoryDetailDialog(item = item, onDismiss = { selectedItem = null })
    }
}

@Composable
private fun HistoryPreviewRow(item: HistoryPreviewItem, onClick: () -> Unit) {
    val icon = when (item.kind) {
        HistoryKind.PURCHASE -> Icons.Outlined.Payments
        HistoryKind.SERVICE -> Icons.Outlined.Handyman
        HistoryKind.ORDER -> Icons.Outlined.ShoppingBag
        HistoryKind.BOOKING -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
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
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "အသေးစိတ် ကြည့်မည်",
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HistoryDetailDialog(
    item: HistoryPreviewItem,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (item.kind) {
                                HistoryKind.PURCHASE -> Icons.Outlined.Payments
                                HistoryKind.SERVICE -> Icons.Outlined.Handyman
                                HistoryKind.ORDER -> Icons.Outlined.ShoppingBag
                                HistoryKind.BOOKING -> Icons.AutoMirrored.Outlined.ReceiptLong
                            }
                            Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                when (item.kind) {
                                    HistoryKind.PURCHASE -> "ဝယ်ယူမှု အသေးစိတ်"
                                    HistoryKind.SERVICE -> "Service Job အသေးစိတ်"
                                    HistoryKind.ORDER -> "အော်ဒါ အသေးစိတ်"
                                    HistoryKind.BOOKING -> "Booking အသေးစိတ်"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryDark
                            )
                            Text(item.title, style = MaterialTheme.typography.labelMedium, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceSoft)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "ပိတ်မည်", tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))

                when (val raw = item.rawItem) {
                    is CustomerJob -> JobDetailContent(raw)
                    is CustomerPurchase -> PurchaseDetailContent(raw)
                    is CustomerOrder -> OrderDetailContent(raw)
                    is BookingSummary -> BookingDetailContent(raw)
                    else -> {
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        item.amount?.let {
                            Text("ကျသင့်ငွေ · ${money(it)}", fontWeight = FontWeight.Bold, color = Primary)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("ပိတ်မည်", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun JobDetailContent(job: CustomerJob) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showInAppInvoiceDialog by remember { mutableStateOf(false) }
    var showPaperSizePicker by remember { mutableStateOf(false) }
    var openingInvoice by remember { mutableStateOf(false) }
    var invoiceError by remember { mutableStateOf<String?>(null) }

    val canOpenInvoice = job.canOpenServiceInvoice()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canOpenInvoice) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showInAppInvoiceDialog = true },
                shape = RoundedCornerShape(14.dp),
                color = SuccessBg,
                border = BorderStroke(1.dp, Success.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Service Job Invoice (ဝန်ဆောင်မှု + Part)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Success,
                            modifier = Modifier.weight(1f)
                        )
                        job.jobNo?.takeIf { it.isNotBlank() }?.let { jobNo ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Success.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    jobNo,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Success,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showInAppInvoiceDialog = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text("PDF ဖွင့်", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                showPaperSizePicker = true
                            },
                            enabled = !openingInvoice,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Send", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    invoiceError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                }
            }
        }

        val (statusText, statusFg, statusBg) = when (job.status?.uppercase()) {
            "COMPLETED", "DELIVERED", "READY_FOR_DELIVERY" -> Triple("✓ ပြီးစီးပါပြီ (${job.status})", Success, SuccessBg)
            "IN_PROGRESS", "REPAIRING", "WAITING_PARTS" -> Triple("⚙ ပြုပြင်နေသည် (${job.status})", Primary, PrimaryLight)
            "CHECKING", "INSPECTING", "DIAGNOSED" -> Triple("🔍 စစ်ဆေးနေသည် (${job.status})", Warning, WarningBg)
            else -> Triple(job.status?.replace('_', ' ') ?: "—", TextMuted, SurfaceSoft)
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = statusBg,
            border = BorderStroke(1.dp, statusFg.copy(alpha = 0.2f))
        ) {
            Text(
                statusText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = statusFg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceSoft,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text("ပစ္စည်း / စက်အမည်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        job.itemName.orEmpty().ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                }

                if (!job.deviceType.isNullOrBlank()) {
                    Column {
                        Text("စက်အမျိုးအစား", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(job.deviceType, style = MaterialTheme.typography.bodyMedium, color = TextMain)
                    }
                }

                if (!job.problemDesc.isNullOrBlank()) {
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                    Column {
                        Text("ဖြစ်ပွားသည့် ပြဿနာ / တောင်းဆိုချက်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            job.problemDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    if (showInAppInvoiceDialog) {
        SaleInvoiceViewerDialog(
            serviceJobId = job.id,
            saleCode = job.jobNo,
            onDismiss = { showInAppInvoiceDialog = false }
        )
    }

    if (showPaperSizePicker) {
        PaperSizeSelectionDialog(
            title = "Paper Size ရွေးပါ",
            subtitle = "Service Job ဘောက်ချာ ပို့လိုသော Paper Size ရွေးပါ",
            onSelect = { paperSize ->
                showPaperSizePicker = false
                openingInvoice = true
                invoiceError = null
                scope.launch {
                    invoiceError = SaleInvoiceOpener.shareForServiceJob(context, job.id, job.jobNo, paperSize)
                    openingInvoice = false
                }
            },
            onDismiss = { showPaperSizePicker = false }
        )
    }
}

@Composable
private fun PurchaseDetailContent(purchase: CustomerPurchase) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailInfoRow(label = "ဘောင်ချာ နံပါတ်", value = purchase.saleCode.orEmpty().ifBlank { "—" })
        purchase.saleDate?.takeIf { it.isNotBlank() }?.let {
            DetailInfoRow(label = "ဝယ်ယူခဲ့သည့် ရက်စွဲ", value = it.replace('T', ' ').take(16))
        }
        if (!purchase.paymentStatus.isNullOrBlank()) {
            DetailInfoRow(label = "ငွေပေးချေမှု အခြေအနေ", value = purchase.paymentStatus)
        }

        val lines = purchase.lines.orEmpty()
        if (lines.isNotEmpty()) {
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            Text("ဝယ်ယူခဲ့သော ပစ္စည်းများ (${lines.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
            lines.forEach { line ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ScreenBg,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                line.productName.orEmpty().ifBlank { "Product" },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(money(line.subtotal ?: 0.0), fontWeight = FontWeight.Bold, color = PrimaryDark, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("× ${line.qty ?: 1} · ${money(line.unitPrice ?: 0.0)} / ခု", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }

        HorizontalDivider(color = BorderColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("စုစုပေါင်း ကုန်ကျငွေ", fontWeight = FontWeight.Bold, color = PrimaryDark)
            Text(money(purchase.netAmount ?: 0.0), fontWeight = FontWeight.Bold, color = Primary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OrderDetailContent(order: CustomerOrder) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailInfoRow(label = "အော်ဒါ နံပါတ်", value = order.orderNo.orEmpty().ifBlank { "—" })
        order.createdAt?.takeIf { it.isNotBlank() }?.let {
            DetailInfoRow(label = "မှာယူသည့် ရက်စွဲ", value = it.replace('T', ' ').take(16))
        }
        DetailInfoRow(label = "အမျိုးအစား", value = if (order.orderType.equals("PICKUP", true)) "ဆိုင်မှာ လာယူမည်" else "သွားရောက် ပို့ဆောင်မည်")
        DetailInfoRow(label = "အော်ဒါ အခြေအနေ", value = order.status?.replace('_', ' ') ?: "—")

        val lines = order.lines.orEmpty()
        if (lines.isNotEmpty()) {
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            Text("မှာယူခဲ့သော ပစ္စည်းများ (${lines.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
            lines.forEach { line ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ScreenBg,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName.orEmpty().ifBlank { "Product" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("× ${line.qty ?: 1} · ${money(line.unitPrice ?: 0.0)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text(money(line.subtotal ?: 0.0), fontWeight = FontWeight.Bold, color = PrimaryDark, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        HorizontalDivider(color = BorderColor)

        val discount = order.discountAmount ?: 0.0
        if (discount > 0.0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Overall လျှော့", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Danger)
                Text("-${money(discount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Danger)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("စုစုပေါင်း", fontWeight = FontWeight.Bold, color = PrimaryDark)
            Text(money(order.total ?: 0.0), fontWeight = FontWeight.Bold, color = Primary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BookingDetailContent(booking: BookingSummary) {
    val bookingStatus = booking.status?.uppercase()
    val closed = bookingStatus in setOf("CANCELED", "CANCELLED", "REJECTED")
    val rejectionMessage = when (bookingStatus) {
        "REJECTED" -> booking.rejectionReason?.trim()?.takeIf { it.isNotEmpty() }
            ?: "သင့် service တောင်းဆိုမှုကို ဆိုင်မှ လက်မခံနိုင်ပါ။ နောက်ထပ်အသေးစိတ်အတွက် ဆိုင်သို့ ဆက်သွယ်ပေးပါ။"
        "CANCELED", "CANCELLED" -> "ဤ Booking ကို ပယ်ဖျက်ထားပါသည်။ အသေးစိတ်အတွက် ဆိုင်သို့ ဆက်သွယ်နိုင်ပါသည်။"
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailInfoRow(label = "Booking နံပါတ်", value = booking.bookingNo.orEmpty().ifBlank { "—" })
        booking.serviceDate?.takeIf { it.isNotBlank() }?.let {
            DetailInfoRow(label = "Service date", value = it)
        }
        booking.arrivalWindowId?.let {
            DetailInfoRow(label = "Arrival window", value = "Window #$it")
        }
        when {
            booking.preferredAnytime == false && !booking.preferredTime.isNullOrBlank() ->
                DetailInfoRow(label = "Preferred time", value = booking.preferredTime!!.take(5))
            booking.serviceDate != null ->
                DetailInfoRow(label = "Preferred time", value = "Anytime")
        }
        booking.customerPreferenceNote?.takeIf { it.isNotBlank() }?.let {
            DetailInfoRow(label = "Preference note", value = it)
        }
        booking.appointmentDate?.takeIf { it.isNotBlank() }?.let {
            DetailInfoRow(label = "ချိန်းဆိုသည့် ရက်စွဲ", value = it.replace('T', ' ').take(16))
        }
        DetailInfoRow(
            label = "အခြေအနေ",
            value = when (bookingStatus) {
                "REJECTED" -> "ငြင်းပယ်ထား"
                "CANCELED", "CANCELLED" -> "ပယ်ဖျက်ထား"
                "CONFIRMED" -> "လက်ခံထား"
                "ARRIVED" -> "ဆိုင်ရောက်"
                else -> booking.status?.replace('_', ' ') ?: "—"
            }
        )
        if (closed && !rejectionMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF1F2),
                border = BorderStroke(1.dp, Color(0xFFFECDD3))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "ဆိုင်မှ အသိပေးချက်",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9F1239)
                    )
                    Text(
                        rejectionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF881337)
                    )
                    booking.rejectedAt?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "ငြင်းပယ်ချိန် · ${it.replace('T', ' ').take(16)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9F1239)
                        )
                    }
                }
            }
        }
        if (!booking.requestedServiceName.isNullOrBlank() || !booking.serviceNameSnapshot.isNullOrBlank()) {
            DetailInfoRow(
                label = "ရွေးထားသော Service",
                value = booking.serviceNameSnapshot ?: booking.requestedServiceName.orEmpty()
            )
        }
        booking.servicePriceType?.takeIf { it.isNotBlank() }?.let { type ->
            val priceText = when (type.uppercase()) {
                "INSPECTION_REQUIRED" -> "စစ်ဆေးပြီးမှ ဈေးနှုန်း"
                "STARTING_FROM" -> booking.servicePriceSnapshot?.let { "${it.toInt()} Ks မှ စတင်" } ?: "Starting from"
                else -> booking.servicePriceSnapshot?.let { "${it.toInt()} Ks" } ?: "—"
            }
            DetailInfoRow(label = "Service ဈေး (booking လုပ်စဉ်)", value = priceText)
        }
        booking.estimateApprovalStatus?.takeIf { it.isNotBlank() }?.let {
            DetailInfoRow(
                label = "Estimate အခြေအနေ",
                value = when (it.uppercase()) {
                    "NOT_REQUIRED" -> "ခန့်မှန်းဈေး မလို"
                    "PENDING" -> "ဆိုင်အတည်ပြုရန်"
                    "APPROVED" -> "အတည်ပြုပြီး"
                    "REJECTED" -> "ငြင်းပယ်"
                    else -> it
                }
            )
        }
        if (!booking.requestType.isNullOrBlank()) {
            DetailInfoRow(label = "တောင်းဆိုမှုအမျိုးအစား", value = booking.requestType.replace('_', ' '))
        }
        listOfNotNull(booking.deviceCategory, booking.deviceName).takeIf { it.isNotEmpty() }?.let {
            DetailInfoRow(label = "ပစ္စည်း", value = it.joinToString(" · "))
        }
        if (!booking.requestedServiceMode.isNullOrBlank()) {
            DetailInfoRow(label = "ဝန်ဆောင်မှုပုံစံ", value = booking.requestedServiceMode.replace('_', ' '))
        }
        if (!booking.urgency.isNullOrBlank()) {
            DetailInfoRow(label = "ဦးစားပေးမှု", value = booking.urgency)
        }
        if (!booking.contactPreference.isNullOrBlank()) {
            DetailInfoRow(label = "ဆက်သွယ်ရန်", value = booking.contactPreference)
        }
        if (!booking.serviceAddress.isNullOrBlank()) {
            DetailInfoRow(label = "ဝန်ဆောင်မှုလိပ်စာ", value = booking.serviceAddress)
        }
        if (!booking.complaintNote.isNullOrBlank()) {
            DetailInfoRow(label = "တောင်းဆိုချက် / ပြဿနာ", value = booking.complaintNote)
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = PrimaryDark, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
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

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
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

private fun String?.initials(): String {
    val words = this.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "C"
        words.size == 1 -> words.first().take(1).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

private fun money(amount: Double): String {
    val safe = if (amount.isFinite()) amount else 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F9FA, widthDp = 390, heightDp = 840)
@Composable
private fun CustomerProfilePreview() {
    AppTheme {
        CustomerProfileScreen(
            profile = CustomerAuthResponse(
                customerId = 128,
                name = "Hlaing",
                phone = "09 765 432 100",
                email = "hlaing@example.com",
                address = "No. 28, Hlaing Township, Yangon",
                needsProfile = false,
                creditAllowed = true,
                creditLimit = 1_500_000.0,
                creditDays = 30,
                creditHold = false
            ),
            orderCount = 12,
            serviceCount = 4,
            purchases = emptyList(),
            jobs = emptyList(),
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
