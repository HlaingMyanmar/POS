package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceJobNotificationDTO {
    private Integer id;
    private String channel;
    private String note;
    private String actor;
    private LocalDateTime notifiedAt;
}
