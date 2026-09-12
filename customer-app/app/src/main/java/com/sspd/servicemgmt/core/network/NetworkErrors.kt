package com.sspd.servicemgmt.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun networkErrorMessage(error: Throwable?): String = when (error) {
    is SocketTimeoutException -> "ဆက်သွယ်မှု ကြာနေပါသည်။ ခဏနေမှ ပြန်ကြိုးစားပါ။"
    is UnknownHostException, is ConnectException -> "အင်တာနက် ချိတ်ဆက်မရပါ။"
    is SSLException -> "ဆာဗာချိတ်ဆက်မရပါ။"
    is IOException -> "ကွန်ရက် ပြတ်တောက်နေပါသည်။"
    else -> error?.message?.takeIf { it.isNotBlank() } ?: "ဆာဗာ ချိတ်ဆက်မရပါ။"
}

fun httpErrorMessage(code: Int, serverMessage: String?): String {
    val fromServer = serverMessage?.trim()?.takeIf { it.isNotBlank() }
    return when {
        code in 500..599 -> fromServer ?: "ဆာဗာ ခဏမရပါ။ ခဏနေမှ ပြန်ကြိုးစားပါ။"
        code == 408 -> "ဆက်သွယ်မှု ကြာနေပါသည်။ ခဏနေမှ ပြန်ကြိုးစားပါ။"
        else -> fromServer ?: "ဒေတာ ဖတ်မရပါ။ ပြန်ကြိုးစားပါ။"
    }
}
