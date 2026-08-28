package com.sspd.servicemgmt.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AppLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OperationsLoader(Modifier.size(176.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "လုပ်ငန်းဒေတာများ ပြင်ဆင်နေသည်...",
                color = Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Service jobs၊ ပစ္စည်းများနှင့် အခြေအနေများကို ရယူနေသည်",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OperationsLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "operations_loader")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val card = Color.White
        val border = Color(0xFFE2E8F0)
        val shadow = Color(0x14334155)
        val ink = Color(0xFF334155)
        val muted = Color(0xFF94A3B8)
        val success = Color(0xFF16A34A)
        val warning = Color(0xFFD97706)
        val danger = Color(0xFFDC2626)
        val blue = Primary

        drawRoundRect(shadow, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.70f), CornerRadius(18f))
        drawRoundRect(card, Offset(w * 0.10f, h * 0.08f), Size(w * 0.80f, h * 0.70f), CornerRadius(18f))
        drawRoundRect(border, Offset(w * 0.10f, h * 0.08f), Size(w * 0.80f, h * 0.70f), CornerRadius(18f), style = Stroke(2f))

        val headerY = h * 0.18f
        drawCircle(blue, 7f + pulse * 2f, Offset(w * 0.22f, headerY))
        drawRoundRect(ink.copy(alpha = 0.82f), Offset(w * 0.30f, headerY - 7f), Size(w * 0.35f, 7f), CornerRadius(4f))
        drawRoundRect(muted.copy(alpha = 0.55f), Offset(w * 0.30f, headerY + 6f), Size(w * 0.24f, 5f), CornerRadius(3f))

        val rows = listOf(
            Triple(success, 0.34f, 0.56f),
            Triple(warning, 0.48f, 0.44f),
            Triple(danger, 0.62f, 0.50f)
        )
        rows.forEachIndexed { index, (color, yRatio, lenRatio) ->
            val y = h * yRatio
            drawRoundRect(color.copy(alpha = 0.12f), Offset(w * 0.18f, y - 12f), Size(w * 0.64f, 24f), CornerRadius(10f))
            drawCircle(color, 5.5f, Offset(w * 0.24f, y))
            drawRoundRect(ink.copy(alpha = 0.68f), Offset(w * 0.31f, y - 6f), Size(w * lenRatio, 5f), CornerRadius(3f))
            drawRoundRect(muted.copy(alpha = 0.45f), Offset(w * 0.31f, y + 5f), Size(w * 0.22f, 4f), CornerRadius(3f))

            val scanX = w * (0.19f + ((sweep + index * 0.18f) % 1f) * 0.62f)
            drawRoundRect(blue.copy(alpha = 0.22f), Offset(scanX, y - 13f), Size(5f, 26f), CornerRadius(3f))
        }

        val dotY = h * 0.92f
        val dotGap = 17f
        fun bounce(offset: Float): Float {
            val p = ((sweep + offset) % 1f).toDouble() * PI
            return -(6f * sin(p).toFloat().coerceAtLeast(0f))
        }
        drawCircle(blue, 4.5f, Offset(w / 2f - dotGap, dotY + bounce(0f)))
        drawCircle(blue, 4.5f, Offset(w / 2f, dotY + bounce(0.25f)))
        drawCircle(blue, 4.5f, Offset(w / 2f + dotGap, dotY + bounce(0.5f)))
    }
}
