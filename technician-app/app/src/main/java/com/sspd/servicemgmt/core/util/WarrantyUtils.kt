package com.sspd.servicemgmt.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun fmtWarranty(months: Int?): String {
    val m = months ?: 0
    if (m <= 0) return ""
    return if (m % 12 == 0) "${m / 12} နှစ်" else "$m လ"
}

fun fmtWarranty(terms: String?, months: Int?): String {
    val t = terms?.trim().orEmpty()
    if (t.isNotEmpty()) return t
    return fmtWarranty(months)
}

fun fmtWarranty(months: Int?, startDate: String?, endDate: String?): String =
    fmtWarranty(null, months, startDate, endDate)

fun fmtWarranty(terms: String?, months: Int?, startDate: String?, endDate: String?): String {
    val t = terms?.trim().orEmpty()
    if (t.isNotEmpty()) return t
    val m = months ?: 0
    if (m > 0) return fmtWarranty(m)
    val start = runCatching { LocalDate.parse(startDate?.take(10)) }.getOrNull()
    val end = runCatching { LocalDate.parse(endDate?.take(10)) }.getOrNull()
    val days = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else 0
    return if (days > 0) "$days ရက်" else ""
}
