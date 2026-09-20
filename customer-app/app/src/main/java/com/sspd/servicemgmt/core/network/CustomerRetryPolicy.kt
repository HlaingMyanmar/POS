package com.sspd.servicemgmt.core.network

object CustomerRetryPolicy {
    fun shouldEndSessionOnUnauthorized(
        authorizationHeader: String?,
        requestPath: String
    ): Boolean {
        val auth = authorizationHeader.orEmpty()
        if (!auth.startsWith("Bearer ") || auth.length < 16) return false
        val path = requestPath.lowercase()
        if (path.contains("/auth/")) return false
        if (path.contains("ws-native") || path.contains("ws-clinic")) return false
        return true
    }
}
