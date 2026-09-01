package com.sspd.servicemgmt.core.network

import retrofit2.Response

fun <T> Response<ApiResponse<T>>.apiData(): T? = body()?.data

fun <T> Response<ApiResponse<T>>.apiMessage(fallback: String): String =
    body()?.message?.takeIf { it.isNotBlank() } ?: fallback

const val API_EMPTY_DATA = "Server မှ ဒေတာ မရပါ"
