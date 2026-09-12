package com.sspd.servicemgmt.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.feature.CustomerAppFeatures

private val ShopFabSize = 54.dp
private val BarHeight = 72.dp
/** Air gap between FAB edge and the circular hollow. */
private val CradleGap = 5.dp
/** Animated multicolor rim around the shop FAB. */
private val RainbowBorder = 2.5.dp

/** Seamless sweep colors for the spinning rim. */
private val ShopRimColors = listOf(
    Color(0xFF22D3EE),
    Color(0xFF818CF8),
    Color(0xFFE879F9),
    Color(0xFFF472B6),
    Color(0xFFFB923C),
    Color(0xFFFACC15),
    Color(0xFF4ADE80),
    Color(0xFF22D3EE)
)

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
    val fabOuter = ShopFabSize + RainbowBorder * 2
    val cradleDiameter = fabOuter + CradleGap * 2

    val shopMotion = rememberInfiniteTransition(label = "shopMotion")
    val shopBob by shopMotion.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shopBob"
    )
    val rimSpin by shopMotion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rimSpin"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(CardBg)
    ) {
        // ScreenBg behind the cradle so the hollow reads as open space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .background(ScreenBg)
        ) {
            // White bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BarHeight)
                    .shadow(
                        elevation = 12.dp,
                        spotColor = Color(0x33172033),
                        ambientColor = Color(0x22172033)
                    )
                    .background(CardBg)
            ) {
                // Circular hollow: ScreenBg disk bites into the bar top edge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = -(cradleDiameter / 2))
                        .size(cradleDiameter)
                        .clip(CircleShape)
                        .background(ScreenBg)
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        BottomNavItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Home,
                            label = "ပင်မ",
                            selected = homeSelected,
                            onClick = onHome
                        )
                        BottomNavItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Handyman,
                            label = "Service",
                            selected = serviceSelected,
                            onClick = onService
                        )
                    }
                    Spacer(modifier = Modifier.width(cradleDiameter))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        if (wishlistEnabled) {
                            BottomNavItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Favorite,
                                label = "အကြိုက်",
                                selected = wishlistSelected,
                                onClick = onWishlist
                            )
                        }
                        BottomNavItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.ShoppingCart,
                            label = "ခြင်း",
                            selected = cartSelected,
                            badge = cartCount.takeIf { it > 0 },
                            onClick = onCart
                        )
                    }
                }
            }

            // FAB nestled in cradle, with spinning multicolor rim
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(BarHeight - fabOuter / 2) + shopBob.dp)
                    .size(fabOuter)
                    .shadow(
                        elevation = if (productsSelected) 12.dp else 8.dp,
                        shape = CircleShape,
                        clip = false,
                        spotColor = Primary.copy(alpha = 0.28f),
                        ambientColor = Primary.copy(alpha = 0.12f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onProducts
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Rotating rainbow sweep → colorful outer rim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rimSpin }
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(ShopRimColors))
                )
                // Button face leaves RainbowBorder as the visible color rim
                Box(
                    modifier = Modifier
                        .size(ShopFabSize)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    if (productsSelected) PrimaryDark else Primary,
                                    if (productsSelected) Primary else Color(0xFF818CF8)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Storefront,
                        contentDescription = "ပစ္စည်းဝယ်မည်",
                        tint = OnPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                "ဝယ်မည်",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onProducts
                    ),
                color = if (productsSelected) Primary else TextMuted,
                fontSize = 10.sp,
                fontWeight = if (productsSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) Primary else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
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
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) Primary else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun BottomNavPreviewHost(
    home: Boolean = false,
    service: Boolean = false,
    products: Boolean = false,
    cart: Boolean = false,
    profile: Boolean = false,
    cartCount: Int = 0
) {
    AppTheme {
        Surface(color = ScreenBg) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(ScreenBg)
            ) {
                Text(
                    "Customer bottom bar",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp),
                    color = PrimaryDark,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    CustomerBottomNav(
                        homeSelected = home,
                        wishlistSelected = false,
                        serviceSelected = service,
                        productsSelected = products,
                        cartSelected = cart,
                        profileSelected = profile,
                        cartCount = cartCount,
                        onHome = {},
                        onService = {},
                        onProducts = {},
                        onWishlist = {},
                        onCart = {},
                        onProfile = {},
                        wishlistEnabled = CustomerAppFeatures.WISHLIST
                    )
                }
            }
        }
    }
}

@Preview(name = "Bottom — Home", showBackground = true, widthDp = 390, heightDp = 220)
@Composable
private fun BottomNavHomePreview() {
    BottomNavPreviewHost(home = true)
}

@Preview(name = "Bottom — Service", showBackground = true, widthDp = 390, heightDp = 220)
@Composable
private fun BottomNavServicePreview() {
    BottomNavPreviewHost(service = true)
}

@Preview(name = "Bottom — Shop", showBackground = true, widthDp = 390, heightDp = 220)
@Composable
private fun BottomNavShopPreview() {
    BottomNavPreviewHost(products = true)
}

@Preview(name = "Bottom — Cart badge", showBackground = true, widthDp = 390, heightDp = 220)
@Composable
private fun BottomNavCartPreview() {
    BottomNavPreviewHost(cart = true, cartCount = 3)
}

@Preview(name = "Bottom — Profile", showBackground = true, widthDp = 390, heightDp = 220)
@Composable
private fun BottomNavProfilePreview() {
    BottomNavPreviewHost(profile = true)
}
