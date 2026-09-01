package org.sspd.servicemgmt.technicianvisitoptions.dto;

public record DeleteGpsHistoryRequest(
        String confirmation,
        String reason
) {
}
