package com.sspd.servicemgmt.core.util

enum class WarrantyUnit(val label: String) {
    DAY("ရက်"),
    MONTH("လ"),
    YEAR("နှစ်");

    companion object {
        fun fromLabel(label: String): WarrantyUnit =
            entries.firstOrNull { it.label == label } ?: MONTH
    }
}

data class WarrantyDisplay(
    val value: Int,
    val unit: WarrantyUnit,
    val note: String = ""
)

fun parseWarrantyDisplay(terms: String?, months: Int?): WarrantyDisplay {
    val t = terms.orEmpty().trim()
    val match = Regex("""^(\d+)\s*(ရက်|နှစ်|လ)(.*)$""").find(t)
    if (match != null) {
        return WarrantyDisplay(
            value = match.groupValues[1].toIntOrNull() ?: 0,
            unit = WarrantyUnit.fromLabel(match.groupValues[2]),
            note = match.groupValues[3].trim()
        )
    }
    val m = months ?: 0
    if (m > 0 && m % 12 == 0) return WarrantyDisplay(m / 12, WarrantyUnit.YEAR)
    if (m > 0) return WarrantyDisplay(m, WarrantyUnit.MONTH)
    return WarrantyDisplay(0, WarrantyUnit.MONTH)
}

fun toWarrantyMonths(value: Int, unit: WarrantyUnit): Int = when (unit) {
    WarrantyUnit.DAY -> 0
    WarrantyUnit.YEAR -> value * 12
    WarrantyUnit.MONTH -> value
}

fun warrantyTermsDisplay(value: Int, unit: WarrantyUnit, note: String): String? {
    val cleanNote = note.trim()
    if (value <= 0) return cleanNote.ifBlank { null }
    return if (cleanNote.isBlank()) "$value ${unit.label}" else "$value ${unit.label} $cleanNote"
}

fun fmtWarranty(months: Int?): String {
    val m = months ?: 0
    if (m <= 0) return ""
    return if (m % 12 == 0) "${m / 12} နှစ်" else "$m လ"
}

fun fmtWarranty(terms: String?, months: Int?): String {
    val parsed = parseWarrantyDisplay(terms, months)
    if (parsed.value <= 0) return terms?.trim().orEmpty()
    return warrantyTermsDisplay(parsed.value, parsed.unit, parsed.note).orEmpty()
}
