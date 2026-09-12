package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerOrderDeliveryUpdateRequest {
    /** PENDING | PACKING | PACKED | HANDED_TO_RIDER | OUT_FOR_DELIVERY | IN_TRANSIT | DELIVERED */
    private String deliveryStatus;
    private String deliveryCurrentLocation;
    private LocalDateTime deliveryScheduledAt;
    private String deliveryPersonPhone;
    private String note;
}
