package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceJobActivityDTO {
    private Integer id;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String note;
    private String actor;
    private LocalDateTime occurredAt;
}
