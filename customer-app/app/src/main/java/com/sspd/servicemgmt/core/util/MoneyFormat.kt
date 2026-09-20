package com.sspd.servicemgmt.core.util

import kotlin.math.abs

fun formatKs(amount: Double?): String {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

fun formatKsOrDash(amount: Double?, free: Boolean = false): String = when {
    free -> "အခမဲ့"
    amount == null || !amount.isFinite() || amount <= 0.0 -> "—"
    else -> formatKs(amount)
}

fun paymentStatusMm(status: String?): String = when (status?.trim()?.uppercase()) {
    "PAID" -> "ပေးပြီး"
    "PARTIAL" -> "တစ်စိတ်တစ်ပိုင်း"
    "PENDING", "UNPAID" -> "ကျန်ရှိ"
    else -> status?.replace('_', ' ')?.ifBlank { "—" } ?: "—"
}

fun jobStatusMm(status: String?): Triple<String, Boolean, Boolean> {
    val s = status?.uppercase().orEmpty()
    val done = s in setOf("COMPLETED", "DELIVERED", "READY_FOR_DELIVERY")
    val active = s in setOf("IN_PROGRESS", "REPAIRING", "WAITING_PARTS", "ASSIGNED")
    val checking = s in setOf("CHECKING", "INSPECTING", "DIAGNOSED", "RECEIVED")
    val label = when {
        done -> "ပြီးစီးပါပြီ"
        active -> "ပြုပြင်နေသည်"
        checking -> "စစ်ဆေးနေသည်"
        s == "CANCELLED" -> "ပယ်ဖျက်ပြီး"
        else -> status?.replace('_', ' ') ?: "—"
    }
    return Triple(label, done, active)
}
