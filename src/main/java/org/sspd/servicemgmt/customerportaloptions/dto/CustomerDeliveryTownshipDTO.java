package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerDeliveryTownshipDTO {
    private Integer id;
    private Integer regionId;
    private String regionName;
    private String regionKind;
    private String name;
    private BigDecimal deliveryCharge;
    private Boolean active;
    private Integer sortOrder;
    private List<CustomerDeliveryWardDTO> wards = new ArrayList<>();
}
