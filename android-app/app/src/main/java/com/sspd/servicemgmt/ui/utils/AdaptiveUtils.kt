package com.sspd.servicemgmt.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment

/**
 * Returns true when the screen width is >= 600dp (tablet/large-screen).
 */
@Composable
fun rememberIsTablet(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 600

/**
 * On compact phones: fillMaxWidth.
 * On tablets (>=600dp): max 560dp centred, still fills up to that width.
 * Usage:  Column(modifier = adaptiveContentWidth().padding(16.dp))
 */
@Composable
fun adaptiveContentWidth(): Modifier {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    return if (isTablet)
        Modifier.widthIn(max = 560.dp).fillMaxWidth()
    else
        Modifier.fillMaxWidth()
}
