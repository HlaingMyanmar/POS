package com.sspd.servicemgmt.feature.home

import com.sspd.servicemgmt.core.network.CustomerBranding
import com.sspd.servicemgmt.core.network.DeliveryClosedDate
import com.sspd.servicemgmt.core.network.DeliveryWeekdayHours
import java.util.Calendar

private val ANDROID_DAY_KEYS = listOf("", "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")

data class DeliveryHoursConfig(
    val opensAt: String = "09:00",
    val closesAt: String = "18:00",
    val days: String = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
    val weekdays: List<DeliveryWeekdayHours> = emptyList(),
    val closedDates: List<DeliveryClosedDate> = emptyList(),
    val minLeadDays: Int = 1
) {
    companion object {
        fun from(branding: CustomerBranding?): DeliveryHoursConfig {
            return DeliveryHoursConfig(
                opensAt = branding?.deliveryOpensAt?.take(5) ?: "09:00",
                closesAt = branding?.deliveryClosesAt?.take(5) ?: "18:00",
                days = branding?.deliveryDays ?: "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                weekdays = branding?.deliveryWeekdays.orEmpty(),
                closedDates = branding?.deliveryClosedDates.orEmpty(),
                minLeadDays = branding?.deliveryMinLeadDays ?: 1
            )
        }
    }
}

internal fun deliveryScheduleError(
    value: String?,
    hours: DeliveryHoursConfig,
    now: Calendar = Calendar.getInstance()
): String? {
    if (value.isNullOrBlank() || value.length < 16) return "ပို့မည့် ရက်နှင့် အချိန် ရွေးပါ"
    val requested = parseScheduleAt(value)
    val earliest = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, hours.minLeadDays.coerceIn(0, 30))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (requested.before(earliest)) return leadMessage(hours.minLeadDays)
    deliveryClosedReason(requested, hours)?.let { return it }
    val window = windowFor(requested, hours)
    if (window.open == false) return "ရွေးထားသောနေ့တွင် delivery ပိတ်ထားပါသည်"
    val minutes = requested.get(Calendar.HOUR_OF_DAY) * 60 + requested.get(Calendar.MINUTE)
    if (minutes < mins(window.opensAt) || minutes >= mins(window.closesAt)) {
        return "ပို့မည့်အချိန်ကို ${window.opensAt?.take(5) ?: "09:00"} မှ ${window.closesAt?.take(5) ?: "18:00"} မတိုင်မီ ရွေးပါ"
    }
    return null
}

internal fun scheduleHint(hours: DeliveryHoursConfig): String {
    val open = hours.weekdays.filter { it.open != false }
    val distinct = open.map { "${it.opensAt?.take(5)}-${it.closesAt?.take(5)}" }.toSet()
    if (open.isNotEmpty() && distinct.size > 1) return "နေ့အလိုက် ဆိုင်ဖွင့်ချိန်အတွင်း ရွေးပါ"
    val sample = open.firstOrNull()
    val opensAt = sample?.opensAt?.take(5) ?: hours.opensAt.take(5)
    val closesAt = sample?.closesAt?.take(5) ?: hours.closesAt.take(5)
    return "အနည်းဆုံး ${leadLabel(hours.minLeadDays)} မှ ရွေးနိုင်ပြီး ပို့ချိန်သည် $opensAt မှ $closesAt မတိုင်မီ ဖြစ်ရပါမည်။"
}

internal fun minSelectableDate(hours: DeliveryHoursConfig, now: Calendar = Calendar.getInstance()): Calendar {
    val cal = now.clone() as Calendar
    cal.add(Calendar.DAY_OF_MONTH, hours.minLeadDays.coerceIn(0, 30))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    repeat(60) {
        if (deliveryClosedReason(cal, hours) == null && windowFor(cal, hours).open != false) return cal
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cal
}

internal fun windowFor(date: Calendar, hours: DeliveryHoursConfig): DeliveryWeekdayHours {
    val day = ANDROID_DAY_KEYS[date.get(Calendar.DAY_OF_WEEK)]
    hours.weekdays.firstOrNull { it.day.equals(day, true) }?.let { return it }
    val allowed = hours.days.split(',').filter { it.isNotBlank() }
    return DeliveryWeekdayHours(
        day = day,
        open = allowed.isEmpty() || allowed.contains(day),
        opensAt = hours.opensAt,
        closesAt = hours.closesAt
    )
}

internal fun deliveryClosedReason(date: Calendar, hours: DeliveryHoursConfig): String? {
    val key = String.format(
        "%04d-%02d-%02d",
        date.get(Calendar.YEAR),
        date.get(Calendar.MONTH) + 1,
        date.get(Calendar.DAY_OF_MONTH)
    )
    val hit = hours.closedDates.firstOrNull { it.date?.take(10) == key } ?: return null
    return if (hit.reason.isNullOrBlank()) "ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည်"
    else "ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည် — ${hit.reason}"
}

private fun leadMessage(minLeadDays: Int): String {
    if (minLeadDays <= 0) return "ပို့မည့်ရက်ကို ယနေ့မှ ရွေးပါ"
    if (minLeadDays == 1) return "ပို့မည့်ရက်ကို အနည်းဆုံး မနက်ဖြန်မှ ရွေးပါ"
    return "ပို့မည့်ရက်ကို အနည်းဆုံး $minLeadDays ရက် ကြိုရွေးပါ"
}

private fun leadLabel(minLeadDays: Int): String {
    if (minLeadDays <= 0) return "ယနေ့"
    if (minLeadDays == 1) return "မနက်ဖြန်"
    return "$minLeadDays ရက်ကြို"
}

private fun mins(text: String?): Int {
    val parts = (text ?: "09:00").take(5).split(':')
    return parts.getOrNull(0)?.toIntOrNull()?.times(60)?.plus(parts.getOrNull(1)?.toIntOrNull() ?: 0) ?: 9 * 60
}

private fun parseScheduleAt(value: String?): Calendar {
    val cal = Calendar.getInstance()
    if (value.isNullOrBlank() || value.length < 16) return cal
    return runCatching {
        val y = value.substring(0, 4).toInt()
        val m = value.substring(5, 7).toInt() - 1
        val d = value.substring(8, 10).toInt()
        val h = value.substring(11, 13).toInt()
        val min = value.substring(14, 16).toInt()
        Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }.getOrDefault(cal)
}
