package com.sspd.servicemgmt.core.util

import com.sspd.servicemgmt.core.network.ApiClient

object AssetUrls {
    /** Resolves server-relative upload paths to absolute URLs (mirrors web resolveAssetUrl). */
    fun resolve(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (value.startsWith("data:") || value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return value
        }
        val base = ApiClient.rawBaseUrl.trimEnd('/')
        val path = if (value.startsWith("/")) value else "/$value"
        return base + path
    }
}
