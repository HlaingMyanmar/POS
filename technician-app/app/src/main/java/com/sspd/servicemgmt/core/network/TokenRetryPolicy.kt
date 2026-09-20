package com.sspd.servicemgmt.core.network

enum class TokenRetryAction {
    STOP,
    USE_LATEST_ACCESS_TOKEN,
    REFRESH
}

object TokenRetryPolicy {
    fun decide(
        responseCount: Int,
        requestPath: String,
        responseBody: String,
        requestAccessToken: String,
        latestAccessToken: String,
        refreshToken: String
    ): TokenRetryAction {
        if (responseCount >= 2 ||
            requestPath.endsWith("/auth/refresh") ||
            responseBody.contains("SESSION_INVALIDATED")
        ) {
            return TokenRetryAction.STOP
        }
        if (latestAccessToken.isNotBlank() && latestAccessToken != requestAccessToken) {
            return TokenRetryAction.USE_LATEST_ACCESS_TOKEN
        }
        return if (refreshToken.isBlank()) TokenRetryAction.STOP else TokenRetryAction.REFRESH
    }
}
