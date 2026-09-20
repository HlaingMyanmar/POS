package com.sspd.servicemgmt.core.security

object TechnicianAuthorization {
    fun contains(authorities: String, wanted: String): Boolean {
        val normalizedWanted = normalize(wanted)
        return authorities.split(',', ';')
            .asSequence()
            .map(::normalize)
            .any { it == normalizedWanted }
    }

    fun isTechnician(roles: String, staffId: Int, permissions: String): Boolean {
        val technicianRole = roles.split(',', ';')
            .asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .any { it == "TECH" || it == "TECHNICIAN" || it.contains("TECHNICIAN") }
        return technicianRole || (staffId > 0 && (
            contains(permissions, "CAN_ACCESS_SERVICE_JOB_READ") ||
                contains(permissions, "CAN_ACCESS_TECHNICIAN_VISIT_START") ||
                contains(permissions, "CAN_ACCESS_VIDEO_CATALOG_TECHNICIAN")
            ))
    }

    private fun normalize(value: String): String =
        value.trim().removePrefix("ROLE_").uppercase()
}
