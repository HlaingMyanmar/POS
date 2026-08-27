package com.sspd.servicemgmt.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val AppColorScheme = lightColorScheme(
    primary            = Primary,
    onPrimary          = Color.White,
    primaryContainer   = PrimaryLight,
    onPrimaryContainer = Primary,
    secondary          = Violet,
    onSecondary        = Color.White,
    background         = ScreenBg,
    onBackground       = TextMain,
    surface            = CardBg,
    onSurface          = TextMain,
    surfaceVariant     = ScreenBg,
    outline            = BorderColor,
    error              = Danger,
    onError            = Color.White,
)

private val MyanmarFontFamily = FontFamily.SansSerif

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography(
    displaySmall   = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = 0.sp, color = TextMain),
    headlineMedium = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 0.sp, color = TextMain),
    headlineSmall  = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Bold,      fontSize = 18.sp, letterSpacing = 0.sp, color = TextMain),
    titleLarge     = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Bold,      fontSize = 16.sp, letterSpacing = 0.sp, color = TextMain),
    titleMedium    = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, letterSpacing = 0.sp, color = TextMain),
    titleSmall     = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.SemiBold,  fontSize = 13.sp, letterSpacing = 0.sp, color = TextMain),
    bodyLarge      = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Normal,    fontSize = 16.sp, letterSpacing = 0.sp, color = TextMain),
    bodyMedium     = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Normal,    fontSize = 14.sp, letterSpacing = 0.sp, color = TextMain),
    bodySmall      = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Normal,    fontSize = 12.sp, letterSpacing = 0.sp, color = TextMuted),
    labelLarge     = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.SemiBold,  fontSize = 13.sp, letterSpacing = 0.sp),
    labelMedium    = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Medium,    fontSize = 11.sp, letterSpacing = 0.sp),
    labelSmall     = TextStyle(fontFamily = MyanmarFontFamily, fontWeight = FontWeight.Medium,    fontSize = 10.sp, letterSpacing = 0.sp, color = TextMuted),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}
