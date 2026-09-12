package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerPortalNotificationDTO {
    private Integer id;
    private Integer orderId;
    private String orderNo;
    private String status;
    private Integer jobId;
    private String jobNo;
    private String channel;
    private String note;
    private LocalDateTime notifiedAt;
}
