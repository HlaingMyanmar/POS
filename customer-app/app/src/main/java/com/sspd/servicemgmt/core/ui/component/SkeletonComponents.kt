package com.sspd.servicemgmt.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft

@Composable
fun SkeletonItem(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 16.dp,
    corner: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.size(width = width, height = height) else Modifier.fillMaxWidth().height(height))
            .clip(RoundedCornerShape(corner))
            .background(SurfaceSoft.copy(alpha = alpha))
    )
}

@Composable
fun OrderSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonItem(modifier = Modifier.weight(1f), height = 18.dp)
                SkeletonItem(width = 72.dp, height = 18.dp)
            }
            SkeletonItem(height = 12.dp)
            SkeletonItem(height = 12.dp, width = 160.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonItem(modifier = Modifier.weight(1f), height = 14.dp)
                SkeletonItem(width = 80.dp, height = 14.dp)
            }
        }
    }
}

@Composable
fun OrderSkeletonList(count: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count) { OrderSkeleton() }
    }
}

@Composable
fun CatalogProductSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonItem(height = 110.dp, corner = 12.dp)
            SkeletonItem(height = 14.dp)
            SkeletonItem(height = 12.dp, width = 90.dp)
            SkeletonItem(height = 16.dp, width = 70.dp)
        }
    }
}

@Composable
fun CatalogSkeletonGrid(count: Int = 6) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(count) { CatalogProductSkeleton() }
    }
}

@Composable
fun WishlistSkeletonList(count: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonItem(width = 72.dp, height = 72.dp, corner = 12.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonItem(height = 16.dp)
                        SkeletonItem(height = 12.dp, width = 120.dp)
                        Spacer(Modifier.height(4.dp))
                        SkeletonItem(height = 14.dp, width = 80.dp)
                    }
                }
            }
        }
    }
}
