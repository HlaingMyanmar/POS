package com.sspd.servicemgmt.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = DarkPrimary,
            onPrimary = OnPrimary,
            background = DarkScreenBg,
            surface = DarkCardBg,
            surfaceVariant = DarkSurfaceSoft,
            onBackground = DarkTextMain,
            onSurface = DarkTextMain,
            onSurfaceVariant = DarkTextMuted,
            outline = DarkBorderColor,
            error = Danger,
            primaryContainer = DarkPrimaryLight,
            onPrimaryContainer = DarkTextMain
        )
    } else {
        lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            background = LightScreenBg,
            surface = LightCardBg,
            surfaceVariant = LightSurfaceSoft,
            onBackground = LightTextMain,
            onSurface = LightTextMain,
            onSurfaceVariant = LightTextMuted,
            outline = LightBorderColor,
            error = Danger,
            primaryContainer = LightPrimaryLight,
            onPrimaryContainer = LightTextMain
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = colorScheme.onBackground),
            titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorScheme.onBackground),
            bodyMedium = TextStyle(fontSize = 14.sp, color = colorScheme.onBackground),
            bodySmall = TextStyle(fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
        ),
        content = content
    )
}
