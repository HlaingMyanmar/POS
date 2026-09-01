package com.sspd.servicemgmt.core.navigation

import androidx.lifecycle.SavedStateHandle

fun SavedStateHandle.optionalId(key: String): Int =
    get<Int>(key) ?: get<String>(key)?.toIntOrNull() ?: 0
