package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookingDetailDTO {
    private Integer id;
    private Integer serviceId;
    private String serviceName;
    private Integer qty;
    private BigDecimal price;
    private BigDecimal subtotal;
}
