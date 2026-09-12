package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerDeliveryRegionDTO {
    private Integer id;
    private String name;
    /** YANGON or STATE */
    private String kind;
    private Boolean active;
    private Integer sortOrder;
    private List<CustomerDeliveryTownshipDTO> townships = new ArrayList<>();
}
