package com.sspd.servicemgmt.core.network

import java.io.IOException

fun Throwable.toUserNetworkMessage(): String = when (this) {
    is IOException -> "ကွန်ရက် မရောက်ပါ။ ချိတ်ဆက်မှု စစ်ပြီး ပြန်ကြိုးစားပါ။"
    else -> localizedMessage?.takeIf { it.isNotBlank() } ?: "မရရှိပါ — ပြန်ကြိုးစားပါ"
}

fun httpFailureMessage(code: Int, apiMessage: String?): String {
    val body = apiMessage?.takeIf { it.isNotBlank() }
    return when {
        body != null -> body
        code == 401 || code == 403 -> "ခွင့်ပြုချက် မရှိပါ (HTTP $code)"
        code in 500..599 -> "ဆာဗာ အမှား (HTTP $code) — ခဏနေမှ ပြန်ကြိုးစားပါ"
        else -> "ဒေတာ မရပါ (HTTP $code)"
    }
}
