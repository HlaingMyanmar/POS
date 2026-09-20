package com.sspd.servicemgmt.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun formatWarranty(months: Int?, startDate: String? = null, endDate: String? = null): String =
    formatWarranty(null, months, startDate, endDate)

fun formatWarranty(terms: String?, months: Int?, startDate: String? = null, endDate: String? = null): String {
    val t = terms?.trim().orEmpty()
    if (t.isNotEmpty()) return t
    val m = months ?: 0
    if (m > 0) return if (m % 12 == 0) "${m / 12} နှစ်" else "$m လ"
    val start = runCatching { LocalDate.parse(startDate?.take(10)) }.getOrNull()
    val end = runCatching { LocalDate.parse(endDate?.take(10)) }.getOrNull()
    val days = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else 0
    return if (days > 0) "$days ရက်" else ""
}

fun formatWarrantyState(status: String?, daysRemaining: Long?): String = when (status?.uppercase()) {
    "ACTIVE" -> "ကျန် ${daysRemaining ?: 0} ရက်"
    "EXPIRED" -> "သက်တမ်းကုန်"
    else -> ""
}
