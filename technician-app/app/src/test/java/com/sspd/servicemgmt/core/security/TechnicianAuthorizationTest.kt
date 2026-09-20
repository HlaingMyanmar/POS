package com.sspd.servicemgmt.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicianAuthorizationTest {
    @Test
    fun `normalizes role prefix case and separators`() {
        assertTrue(TechnicianAuthorization.contains("ROLE_ADMIN; technician", "ROLE_TECHNICIAN"))
        assertTrue(TechnicianAuthorization.contains("read,WRITE", "write"))
        assertFalse(TechnicianAuthorization.contains("ROLE_ADMIN", "TECHNICIAN"))
    }

    @Test
    fun `staff with technician permission is treated as technician`() {
        assertTrue(
            TechnicianAuthorization.isTechnician(
                roles = "",
                staffId = 7,
                permissions = "CAN_ACCESS_TECHNICIAN_VISIT_START"
            )
        )
        assertFalse(
            TechnicianAuthorization.isTechnician(
                roles = "",
                staffId = 0,
                permissions = "CAN_ACCESS_TECHNICIAN_VISIT_START"
            )
        )
    }
}
