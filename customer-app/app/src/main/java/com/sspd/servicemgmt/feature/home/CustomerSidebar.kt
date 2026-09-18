package com.sspd.servicemgmt.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.feature.CustomerAppFeatures
import com.sspd.servicemgmt.core.network.CustomerAuthResponse
import com.sspd.servicemgmt.core.network.LoyaltyPoints
import com.sspd.servicemgmt.core.ui.theme.*

@Composable
fun CustomerSidebarContent(
    profile: CustomerAuthResponse,
    loyaltyPoints: LoyaltyPoints?,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        drawerContainerColor = ScreenBg,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Sidebar User Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(PrimaryDark, Primary)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(OnPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name?.take(1)?.uppercase() ?: "C",
                            color = OnPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    }
                    Text(
                        text = profile.name.takeUnless { it.isNullOrBlank() } ?: "Customer",
                        color = OnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (!profile.phone.isNullOrBlank()) {
                        Text(
                            text = profile.phone,
                            color = OnPrimary.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                    if (loyaltyPoints != null) {
                        Surface(
                            shape = CircleShape,
                            color = OnPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "⭐ Points: ${loyaltyPoints.currentPoints}",
                                color = OnPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sidebar Menu Items
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SidebarMenuItem(
                    icon = Icons.Outlined.Person,
                    label = "ကျွန်ုပ်၏ Profile",
                    selected = selectedTab == 4,
                    onClick = { onSelectTab(4) }
                )
                SidebarMenuItem(
                    icon = Icons.Outlined.ShoppingBag,
                    label = "App အော်ဒါများ",
                    selected = selectedTab == 3,
                    onClick = { onSelectTab(3) }
                )
                SidebarMenuItem(
                    icon = Icons.Outlined.Handyman,
                    label = "Service Job များ",
                    selected = selectedTab == 8,
                    onClick = { onSelectTab(8) }
                )
                SidebarMenuItem(
                    icon = Icons.Outlined.Payments,
                    label = "Sale History (ဝယ်ယူမှု မှတ်တမ်း)",
                    selected = selectedTab == 7,
                    onClick = { onSelectTab(7) }
                )
                SidebarMenuItem(
                    icon = Icons.Outlined.Build,
                    label = "Service History (Service မှတ်တမ်း)",
                    selected = selectedTab == 9,
                    onClick = { onSelectTab(9) }
                )
                SidebarMenuItem(
                    icon = Icons.Outlined.Chat,
                    label = "Customer Support Chat",
                    selected = selectedTab == 6,
                    onClick = { onSelectTab(6) }
                )
                SidebarMenuItem(
                    icon = Icons.Outlined.ShoppingCart,
                    label = "ခြင်းတောင်း",
                    selected = selectedTab == 2,
                    onClick = { onSelectTab(2) }
                )
                if (CustomerAppFeatures.WISHLIST) {
                    SidebarMenuItem(
                        icon = Icons.Outlined.Favorite,
                        label = "အကြိုက်စာရင်း",
                        selected = selectedTab == 5,
                        onClick = { onSelectTab(5) }
                    )
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                SidebarMenuItem(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    label = "အကောင့်မှ ထွက်မည်",
                    selected = false,
                    tint = Danger,
                    onClick = onLogout
                )
            }
        }
    }
}

@Composable
private fun SidebarMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tint: Color = if (selected) Primary else TextMuted,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PrimaryLight else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                color = if (selected) PrimaryDark else TextMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}
