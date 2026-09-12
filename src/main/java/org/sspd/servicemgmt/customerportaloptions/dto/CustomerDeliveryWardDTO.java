package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerDeliveryWardDTO {
    private Integer id;
    private Integer townshipId;
    private String townshipName;
    private Integer regionId;
    private String regionName;
    private String name;
    private BigDecimal deliveryCharge;
    private Boolean active;
    private Integer sortOrder;
}
