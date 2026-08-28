package com.sspd.servicemgmt.core.navigation

import androidx.compose.runtime.compositionLocalOf
import com.sspd.servicemgmt.core.connectivity.ServerStatus

val LocalServerStatus = compositionLocalOf { ServerStatus.CHECKING }
