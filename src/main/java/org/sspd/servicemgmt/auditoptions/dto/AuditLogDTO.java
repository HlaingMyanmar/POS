package org.sspd.servicemgmt.auditoptions.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogDTO {
    private Integer       id;
    private String        actor;
    private String        actorRole;
    private String        action;
    private String        module;
    private String        resourceId;
    private String        description;
    private String        ipAddress;
    private String        deviceType;
    private LocalDateTime createdAt;
}
