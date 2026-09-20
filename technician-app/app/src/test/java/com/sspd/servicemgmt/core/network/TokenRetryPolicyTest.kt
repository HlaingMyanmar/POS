package com.sspd.servicemgmt.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenRetryPolicyTest {
    @Test
    fun `uses token refreshed by another request`() {
        assertEquals(
            TokenRetryAction.USE_LATEST_ACCESS_TOKEN,
            decide(requestToken = "old", latestToken = "new", refreshToken = "refresh")
        )
    }

    @Test
    fun `refreshes when access token is still current`() {
        assertEquals(
            TokenRetryAction.REFRESH,
            decide(requestToken = "same", latestToken = "same", refreshToken = "refresh")
        )
    }

    @Test
    fun `stops retry loops and invalidated sessions`() {
        assertEquals(TokenRetryAction.STOP, decide(responseCount = 2))
        assertEquals(TokenRetryAction.STOP, decide(path = "/api/v1/auth/refresh"))
        assertEquals(TokenRetryAction.STOP, decide(path = "/api/v1/auth/logout"))
        assertEquals(TokenRetryAction.STOP, decide(body = """{"code":"SESSION_INVALIDATED"}"""))
    }

    @Test
    fun `stops when refresh token is missing`() {
        assertEquals(
            TokenRetryAction.STOP,
            decide(requestToken = "same", latestToken = "same", refreshToken = "")
        )
    }

    private fun decide(
        responseCount: Int = 1,
        path: String = "/api/v1/jobs",
        body: String = "",
        requestToken: String = "token",
        latestToken: String = "token",
        refreshToken: String = "refresh"
    ) = TokenRetryPolicy.decide(
        responseCount,
        path,
        body,
        requestToken,
        latestToken,
        refreshToken
    )
}
