package com.sspd.servicemgmt.utils

fun fmtWarranty(months: Int?): String {
    val m = months ?: 0
    if (m <= 0) return ""
    return if (m % 12 == 0) "${m / 12} နှစ်" else "$m လ"
}
