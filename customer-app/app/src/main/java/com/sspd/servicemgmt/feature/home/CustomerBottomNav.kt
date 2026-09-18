package com.sspd.servicemgmt.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.feature.CustomerAppFeatures
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun CustomerBottomNav(
    homeSelected: Boolean,
    serviceSelected: Boolean,
    productsSelected: Boolean,
    wishlistSelected: Boolean,
    cartSelected: Boolean,
    profileSelected: Boolean,
    cartCount: Int,
    onHome: () -> Unit,
    onService: () -> Unit,
    onProducts: () -> Unit,
    onWishlist: () -> Unit,
    onCart: () -> Unit,
    onProfile: () -> Unit,
    wishlistEnabled: Boolean = CustomerAppFeatures.WISHLIST
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(32.dp),
            color = CardBg,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PillNavItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Home,
                    label = "ပင်မ",
                    selected = homeSelected,
                    onClick = onHome
                )
                PillNavItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Handyman,
                    label = "Service",
                    selected = serviceSelected,
                    onClick = onService
                )
                PillNavItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Search,
                    label = "ရှာမည်",
                    selected = productsSelected,
                    onClick = onProducts
                )
                PillNavItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ShoppingCart,
                    label = "ခြင်းတောင်း",
                    selected = cartSelected,
                    badge = cartCount.takeIf { it > 0 },
                    onClick = onCart
                )
                PillNavItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Person,
                    label = "အကောင့်",
                    selected = profileSelected,
                    onClick = onProfile
                )
            }
        }
    }
}

@Composable
private fun PillNavItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 28.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) PrimaryLight else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) Primary else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-2).dp)
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape)
                        .background(Danger)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (badge > 9) "9+" else badge.toString(),
                        color = CardBg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) PrimaryDark else TextMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 100)
@Composable
private fun CustomerBottomNavPreview() {
    AppTheme {
        Surface(color = ScreenBg) {
            CustomerBottomNav(
                homeSelected = false,
                serviceSelected = false,
                productsSelected = false,
                wishlistSelected = false,
                cartSelected = true,
                profileSelected = false,
                cartCount = 1,
                onHome = {},
                onService = {},
                onProducts = {},
                onWishlist = {},
                onCart = {},
                onProfile = {}
            )
        }
    }
}
