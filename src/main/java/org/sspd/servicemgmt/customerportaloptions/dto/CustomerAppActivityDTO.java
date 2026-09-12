package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerAppActivityDTO {
    private Long id;
    private Integer accountId;
    private Integer customerId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
