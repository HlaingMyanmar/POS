package com.sspd.servicemgmt.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
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
    secondaryContainer = VioletBg,
    onSecondaryContainer = Violet,
    tertiary           = Accent,
    onSecondary        = Color.White,
    background         = ScreenBg,
    onBackground       = TextMain,
    surface            = CardBg,
    onSurface          = TextMain,
    onSurfaceVariant   = TextMuted,
    surfaceTint        = Color.Transparent,
    surfaceVariant     = SurfaceSoft,
    surfaceContainer  = SurfaceSoft,
    surfaceContainerHigh = Color(0xFFEEF3F7),
    outline            = BorderColor,
    outlineVariant     = Color(0xFFEDF1F5),
    error              = Danger,
    onError            = Color.White,
)

private val AppDarkColorScheme = darkColorScheme(
    primary              = Color(0xFF5EEAD4),
    onPrimary            = Color(0xFF062E2B),
    primaryContainer     = Color(0xFF12324A),
    onPrimaryContainer   = Color(0xFFD9F5F0),
    secondary            = Color(0xFF7DD3FC),
    onSecondary          = Color(0xFF082F49),
    secondaryContainer   = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary             = Color(0xFF5EEAD4),
    background           = Color(0xFF0B141C),
    onBackground         = Color(0xFFE6EDF3),
    surface              = Color(0xFF111C25),
    onSurface            = Color(0xFFE6EDF3),
    surfaceVariant       = Color(0xFF192733),
    onSurfaceVariant     = Color(0xFFB7C5D1),
    surfaceContainer     = Color(0xFF14212B),
    surfaceContainerHigh = Color(0xFF1B2A36),
    surfaceTint          = Color.Transparent,
    outline              = Color(0xFF40515E),
    outlineVariant       = Color(0xFF293945),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
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
fun AppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColorScheme else AppColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}
