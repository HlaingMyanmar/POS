package com.sspd.servicemgmt.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary      = Color(0xFF4F46E5)
val PrimaryDark  = Color(0xFF312E81)
val Accent       = Color(0xFF22C55E)
val Success      = Color(0xFF15803D)
val Warning      = Color(0xFFB45309)
val Danger       = Color(0xFFDC2626)
val Violet       = Color(0xFF4F46E5)
val OnPrimary    = Color(0xFFFFFFFF)

val LightPrimaryLight = Color(0xFFEEF2FF)
val LightSuccessBg    = Color(0xFFECFDF3)
val LightWarningBg    = Color(0xFFFFF8E7)
val LightDangerBg     = Color(0xFFFEF2F2)
val LightTextMain     = Color(0xFF172033)
val LightTextMuted    = Color(0xFF647184)
val LightBorderColor  = Color(0xFFDDE5EC)
val LightCardBg       = Color(0xFFFFFFFF)
val LightScreenBg     = Color(0xFFF4F7FA)
val LightSurfaceSoft  = Color(0xFFF8FAFC)
val LightVioletBg     = Color(0xFFEEF2FF)

val DarkScreenBg    = Color(0xFF0F172A)
val DarkCardBg      = Color(0xFF1E293B)
val DarkSurfaceSoft = Color(0xFF334155)
val DarkTextMain    = Color(0xFFF1F5F9)
val DarkTextMuted   = Color(0xFF94A3B8)
val DarkBorderColor = Color(0xFF475569)
val DarkPrimary     = Color(0xFF818CF8)
val DarkPrimaryLight = Color(0xFF1E1B4B)
val DarkSuccessBg   = Color(0xFF14532D)
val DarkWarningBg   = Color(0xFF422006)
val DarkDangerBg    = Color(0xFF3F1D1D)
val DarkVioletBg    = Color(0xFF1E1B4B)

val PrimaryLight: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkPrimaryLight else LightPrimaryLight
val SuccessBg: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkSuccessBg else LightSuccessBg
val WarningBg: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkWarningBg else LightWarningBg
val DangerBg: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkDangerBg else LightDangerBg
val TextMain: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkTextMain else LightTextMain
val TextMuted: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkTextMuted else LightTextMuted
val BorderColor: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkBorderColor else LightBorderColor
val CardBg: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkCardBg else LightCardBg
val ScreenBg: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkScreenBg else LightScreenBg
val SurfaceSoft: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkSurfaceSoft else LightSurfaceSoft
val VioletBg: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkVioletBg else LightVioletBg
